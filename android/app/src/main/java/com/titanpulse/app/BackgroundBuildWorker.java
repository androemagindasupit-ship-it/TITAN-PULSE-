package com.titanpulse.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.titanpulse.app.core.JobStatus;
import com.titanpulse.app.data.JobEntity;
import com.titanpulse.app.data.TitanDatabase;
import com.titanpulse.app.security.SecureKeyStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Durable AI build worker. Secrets never leave the native layer. */
public final class BackgroundBuildWorker extends Worker {
    private static final long MAX_RUNTIME_MS = 20L * 60L * 1000L;
    private static final int MAX_RESPONSE_CHARS = 8 * 1024 * 1024;
    public static final String UNIQUE_PREFIX = "titan-pulse-job-";
    public static final String INPUT_JOB_ID = "jobId";
    public static final String INPUT_OWNER_ID = "ownerUserId";
    private static final String CHANNEL_ID = "titan_pulse_builds";
    private static final int NOTIFICATION_ID_BASE = 24000;
    private final TitanDatabase db;
    private final SecureKeyStore secureKeyStore;

    public BackgroundBuildWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = TitanDatabase.getInstance(context);
        secureKeyStore = new SecureKeyStore(context);
    }

    @Override public void onStopped() {
        try {
            String jobId = getInputData().getString(INPUT_JOB_ID);
            String ownerId = getInputData().getString(INPUT_OWNER_ID);
            if (jobId != null && !jobId.isEmpty() && ownerId != null && !ownerId.isEmpty()) {
                JobEntity job = db.dao().getJobByIdForWorker(ownerId, jobId);
                if (job != null && JobStatus.RUNNING.equals(job.status)) {
                    job.status = JobStatus.RETRYING;
                    job.retryCount = Math.max(job.retryCount, getRunAttemptCount() + 1);
                    job.errorCode = "WORK_STOPPED";
                    job.errorMessage = "توقفت المهمة مؤقتاً وسيتم استئنافها تلقائياً.";
                    job.revision++;
                    db.dao().upsertJob(job);
                }
            }
        } catch (Exception ignored) {}
        super.onStopped();
    }

    @NonNull @Override public Result doWork() {
        final long startedNanos = System.nanoTime();
        String jobId = getInputData().getString(INPUT_JOB_ID);
        String ownerId = getInputData().getString(INPUT_OWNER_ID);
        if (jobId == null || jobId.isEmpty() || ownerId == null || ownerId.isEmpty()) return Result.failure();
        JobEntity job = db.dao().getJobByIdForWorker(ownerId, jobId);
        if (job == null) return Result.failure();
        if (JobStatus.COMPLETED.equals(job.status) || JobStatus.CANCELLED.equals(job.status)) return Result.success();
        try {
            createChannel();
            setForegroundAsync(new ForegroundInfo(notificationId(job.id), buildForegroundNotification("جاري تنفيذ الطلب", job.progress))).get(10, TimeUnit.SECONDS);
            update(job, JobStatus.RUNNING, 8, "", "");
            JSONArray providers = new JSONArray(job.providersJson == null ? "[]" : job.providersJson);
            String raw = callWithFallback(job, providers, startedNanos);
            if (raw == null || raw.trim().isEmpty()) throw new BuildException("AI_EMPTY", "لم ترجع خدمة الذكاء الاصطناعي نتيجة.", false);
            update(job, JobStatus.RUNNING, 85, "", "");
            JSONObject bundle = parseBundle(raw, job.mode);
            validateOutputBundle(bundle, job.mode);
            bundle.put("jobId", job.id).put("projectId", job.projectId).put("workspaceId", job.workspaceId).put("prompt", job.prompt).put("completedAt", System.currentTimeMillis());
            job.resultJson = bundle.toString();
            job.completedAt = System.currentTimeMillis();
            job.progress = 100;
            job.status = JobStatus.COMPLETED;
            job.errorCode = "";
            job.errorMessage = "";
            job.revision++;
            db.dao().upsertJob(job);
            TitanPulseBridge.persistResult(getApplicationContext(), job.id, bundle.toString());
            TitanPulseBridge.sendNativeNotification(getApplicationContext(), "تيتان بلس — اكتمل طلبك", "مشروعك جاهز الآن. اضغط هنا لفتحه.", job.id, job.projectId);
            return Result.success();
        } catch (BuildException e) {
            if (e.retryable && getRunAttemptCount() < 3 && !isStopped()) {
                job.retryCount = getRunAttemptCount() + 1;
                update(job, JobStatus.RETRYING, Math.min(80, 20 + job.retryCount * 10), e.code, userMessage(e));
                return Result.retry();
            }
            fail(job, e.code, userMessage(e));
            TitanPulseBridge.sendNativeNotification(getApplicationContext(), "تيتان بلس — تعذر إكمال الطلب", userMessage(e), job.id, job.projectId);
            return Result.failure();
        } catch (Exception e) {
            if (getRunAttemptCount() < 3 && !isStopped()) {
                job.retryCount = getRunAttemptCount() + 1;
                update(job, JobStatus.RETRYING, 20, "UNKNOWN", "حدث خطأ مؤقت.");
                return Result.retry();
            }
            fail(job, "UNKNOWN", "تعذر إكمال الطلب. يمكنك إعادة المحاولة.");
            TitanPulseBridge.sendNativeNotification(getApplicationContext(), "تيتان بلس — تعذر إكمال الطلب", "تعذر إكمال الطلب. يمكنك إعادة المحاولة.", job.id, job.projectId);
            return Result.failure();
        }
    }

    private void update(JobEntity job, String status, int progress, String code, String message) {
        job.status = status;
        job.progress = Math.max(0, Math.min(100, progress));
        job.errorCode = code == null ? "" : code;
        job.errorMessage = message == null ? "" : message;
        if (job.startedAt == 0) job.startedAt = System.currentTimeMillis();
        job.revision++;
        db.dao().upsertJob(job);
        setProgressAsync(new Data.Builder().putInt("progress", job.progress).putString("status", status).build());
    }

    private void fail(JobEntity job, String code, String message) {
        job.status = JobStatus.FAILED;
        job.progress = 100;
        job.completedAt = System.currentTimeMillis();
        job.errorCode = code == null ? "UNKNOWN" : code;
        job.errorMessage = message == null ? "تعذر إكمال الطلب." : message;
        job.revision++;
        db.dao().upsertJob(job);
    }

    private String userMessage(BuildException e) {
        if ("AUTH".equals(e.code)) return "مفتاح مزود الذكاء الاصطناعي غير صالح.";
        if ("NETWORK".equals(e.code) || "TIMEOUT".equals(e.code)) return "تعذر الاتصال بالمزود مؤقتاً. ستتم إعادة المحاولة تلقائياً.";
        return e.getMessage() == null ? "تعذر إكمال الطلب." : e.getMessage();
    }

    private String callWithFallback(JobEntity job, JSONArray providers, long startedNanos) throws Exception {
        Exception last = null;
        for (int i = 0; i < providers.length(); i++) {
            if (isStopped()) throw new BuildException("CANCELLED", "تم إلغاء المهمة.", false);
            if ((System.nanoTime() - startedNanos) / 1_000_000L > MAX_RUNTIME_MS) throw new BuildException("TIMEOUT", "انتهت مهلة تنفيذ المهمة.", true);
            JSONObject p = providers.optJSONObject(i);
            if (p == null) continue;
            String providerId = p.optString("providerId");
            String model = p.optString("model");
            AiProviderRegistry.ProviderSpec spec = AiProviderRegistry.get(providerId);
            if (spec == null || model.isEmpty()) continue;
            String keyPayload = secureKeyStore.get(job.userId, providerId);
            if (keyPayload.isEmpty()) continue;
            JSONArray keys;
            try { keys = new JSONArray(keyPayload); } catch (Exception ignored) { keys = new JSONArray().put(keyPayload); }
            for (int k = 0; k < keys.length(); k++) {
                if (isStopped()) throw new BuildException("CANCELLED", "تم إلغاء المهمة.", false);
                String key = keys.optString(k, "");
                if (key.isEmpty()) continue;
                try {
                    update(job, JobStatus.RUNNING, 20 + i * 15, "", "");
                    return callProviderForKey(providerId, model, key, new JSONArray().put(new JSONObject().put("role", "user").put("content", buildPrompt(job))));
                } catch (BuildException e) {
                    last = e;
                    // Try the next configured key/provider for both auth and temporary failures.
                    // WorkManager retries the whole job only after all viable fallbacks are exhausted.
                    if ("AUTH".equals(e.code) || e.retryable) continue;
                    throw e;
                }
            }
        }
        if (last instanceof BuildException) throw (BuildException) last;
        throw new BuildException("NO_PROVIDER", "لم يتوفر مزود صالح لتنفيذ الطلب.", false);
    }

    private String buildPrompt(JobEntity job) {
        String extra = job.extra == null ? "" : job.extra;
        if ("script".equals(job.mode)) return "أنت مبرمج خبير. أنشئ كوداً كاملاً قابلاً للتشغيل للطلب التالي. أعد JSON صحيحاً فقط يحتوي code وlanguage.\nالطلب:\n" + job.prompt + (extra.isEmpty() ? "" : "\nقيود إضافية:\n" + extra);
        return "أنت مهندس برمجيات وواجهات محترف. أنشئ مشروع ويب كامل ومتجاوب mobile-first وRTL عند الحاجة للطلب التالي. أعد JSON صحيحاً فقط يحتوي html وcss وjs. يجب أن تتطابق معرفات DOM مع JavaScript وأن تكون الملفات مكتملة.\nالطلب:\n" + job.prompt + (extra.isEmpty() ? "" : "\nقيود إضافية:\n" + extra);
    }

    /** Used by the foreground web bridge; it resolves the endpoint from the native allowlist. */
    public static String callProviderStatic(String providerId, String model, String key, JSONArray messages) throws Exception {
        return callProviderStatic(providerId, model, key, messages, null);
    }

    public static String callProviderStatic(String providerId, String model, String key, JSONArray messages, JSONObject responseSchema) throws Exception {
        AiProviderRegistry.ProviderSpec spec = AiProviderRegistry.get(providerId);
        if (spec == null) throw new BuildException("PROVIDER_NOT_ALLOWED", "هذا المزود غير مسموح به داخل التطبيق.", false);
        StringBuilder prompt = new StringBuilder();
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role", "user");
                String content = m.optString("content", "");
                if (content.isEmpty()) continue;
                if (prompt.length() > 0) prompt.append("\n\n");
                prompt.append(role).append(": ").append(content);
            }
        }
        return providerHttp(spec.adapter, spec.baseUrl, model, key, prompt.toString(), responseSchema);
    }

    private String callProviderForKey(String providerId, String model, String key, JSONArray messages) throws Exception {
        return callProviderStatic(providerId, model, key, messages);
    }

    static String providerHttp(String adapter, String base, String model, String key, String prompt) throws Exception {
        return providerHttp(adapter, base, model, key, prompt, null);
    }

    static String providerHttp(String adapter, String base, String model, String key, String prompt, JSONObject responseSchema) throws Exception {
        if (base == null || !base.startsWith("https://")) throw new BuildException("INSECURE_ENDPOINT", "عنوان الخدمة غير آمن.", false);
        if (key == null || key.trim().isEmpty()) throw new BuildException("AUTH", "مفتاح مزود الذكاء الاصطناعي غير موجود.", false);
        URL url;
        JSONObject body = new JSONObject();
        if ("gemini".equals(adapter)) {
            url = new URL(base + "/models/" + URLEncoder.encode(model, "UTF-8") + ":generateContent?key=" + URLEncoder.encode(key, "UTF-8"));
            body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
            JSONObject generationConfig = new JSONObject().put("temperature", 0.25).put("maxOutputTokens", 24000);
            if (responseSchema != null) generationConfig.put("responseMimeType", "application/json").put("responseSchema", responseSchema);
            body.put("generationConfig", generationConfig);
        } else if ("anthropic".equals(adapter)) {
            url = new URL(base + "/messages");
            body.put("model", model).put("max_tokens", 24000).put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
        } else {
            url = new URL(base + "/chat/completions");
            body.put("model", model).put("temperature", 0.25).put("max_tokens", 24000).put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
        }
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(30000);
        c.setReadTimeout(180000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if ("anthropic".equals(adapter)) {
            c.setRequestProperty("x-api-key", key);
            c.setRequestProperty("anthropic-version", "2023-06-01");
        } else if (!"gemini".equals(adapter)) {
            c.setRequestProperty("Authorization", "Bearer " + key);
        }
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code;
        String response;
        try {
            code = c.getResponseCode();
            InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
            response = readAll(stream);
        } finally { c.disconnect(); }
        if (response.length() > MAX_RESPONSE_CHARS) throw new BuildException("RESPONSE_TOO_LARGE", "استجابة المزود كبيرة جداً.", false);
        if (code == 401 || code == 403) throw new BuildException("AUTH", "مفتاح مزود الذكاء الاصطناعي غير صالح.", false);
        if (code == 408 || code == 425 || code == 429 || code >= 500) throw new BuildException("NETWORK", "تعذر الاتصال بالمزود مؤقتاً.", true);
        if (code < 200 || code >= 300) throw new BuildException("PROVIDER_ERROR", "رفض المزود الطلب.", false);
        JSONObject d = new JSONObject(response);
        String text = "";
        if ("gemini".equals(adapter)) {
            JSONArray cs = d.optJSONArray("candidates");
            if (cs != null && cs.length() > 0) {
                JSONObject co = cs.optJSONObject(0);
                JSONObject content = co == null ? null : co.optJSONObject("content");
                JSONArray parts = content == null ? null : content.optJSONArray("parts");
                if (parts != null && parts.length() > 0) text = parts.optJSONObject(0).optString("text", "");
            }
        } else if ("anthropic".equals(adapter)) {
            JSONArray cs = d.optJSONArray("content");
            if (cs != null && cs.length() > 0) text = cs.optJSONObject(0).optString("text", "");
        } else {
            JSONArray cs = d.optJSONArray("choices");
            if (cs != null && cs.length() > 0) {
                JSONObject msg = cs.optJSONObject(0).optJSONObject("message");
                if (msg != null) text = msg.optString("content", "");
            }
        }
        if (text == null || text.trim().isEmpty()) throw new BuildException("AI_EMPTY", "لم ترجع الخدمة نتيجة صالحة.", true);
        return text.trim();
    }

    private JSONObject parseBundle(String raw, String mode) throws Exception {
        if (raw == null || raw.length() > MAX_RESPONSE_CHARS) throw new BuildException("RESPONSE_TOO_LARGE", "النتيجة كبيرة جداً.", false);
        String text = raw.trim();
        if (text.startsWith("```")) {
            int n = text.indexOf('\n');
            int e = text.lastIndexOf("```");
            if (n > 0 && e > n) text = text.substring(n + 1, e).trim();
        }
        JSONObject obj = new JSONObject(text);
        if ("script".equals(mode)) {
            Object codeValue = obj.opt("code"); String code = codeValue instanceof String ? (String)codeValue : "";
            Object langValue = obj.opt("language"); String language = langValue instanceof String ? (String)langValue : "javascript";
            if (code.trim().isEmpty()) throw new BuildException("INVALID_OUTPUT", "استجابة السكربت غير صالحة.", false);
            if (code.length() > 6 * 1024 * 1024) throw new BuildException("OUTPUT_TOO_LARGE", "ملف السكربت أكبر من الحد المسموح.", false);
            return new JSONObject().put("code", code).put("language", language.substring(0, Math.min(language.length(), 40)));
        }
        Object hv=obj.opt("html"), cv=obj.opt("css"), jv=obj.opt("js");
        String html=hv instanceof String?(String)hv:"", css=cv instanceof String?(String)cv:"", js=jv instanceof String?(String)jv:"";
        if (html.isEmpty() && css.isEmpty() && js.isEmpty()) throw new BuildException("INVALID_OUTPUT", "استجابة المشروع فارغة.", false);
        if (html.length()>6*1024*1024 || css.length()>6*1024*1024 || js.length()>6*1024*1024) throw new BuildException("OUTPUT_TOO_LARGE", "أحد ملفات المشروع أكبر من الحد المسموح.", false);
        return new JSONObject().put("html", html).put("css", css).put("js", js);
    }

    private void validateOutputBundle(JSONObject obj, String mode) throws BuildException {
        if (obj == null) throw new BuildException("INVALID_OUTPUT", "استجابة الذكاء الاصطناعي غير صالحة.", false);
        if ("script".equals(mode)) {
            Object code = obj.opt("code");
            Object language = obj.opt("language");
            if (!(code instanceof String) || ((String) code).trim().isEmpty()) throw new BuildException("INVALID_OUTPUT", "لم يتم استلام كود صالح.", false);
            if (language != null && !(language instanceof String)) throw new BuildException("INVALID_OUTPUT", "لغة البرمجة غير صالحة.", false);
            if (((String) code).length() > 6 * 1024 * 1024) throw new BuildException("OUTPUT_TOO_LARGE", "ملف السكربت أكبر من الحد المسموح.", false);
            return;
        }
        for (String field : new String[]{"html", "css", "js"}) {
            Object value = obj.opt(field);
            if (value != null && !(value instanceof String)) throw new BuildException("INVALID_OUTPUT", "الملف " + field + " ليس نصاً صالحاً.", false);
            if (value instanceof String && ((String) value).length() > 6 * 1024 * 1024) throw new BuildException("OUTPUT_TOO_LARGE", "أحد ملفات المشروع أكبر من الحد المسموح.", false);
        }
        if (obj.optString("html", "").isEmpty() && obj.optString("css", "").isEmpty() && obj.optString("js", "").isEmpty()) {
            throw new BuildException("INVALID_OUTPUT", "استجابة المشروع فارغة.", false);
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) {
                s.append(buf, 0, n);
                if (s.length() > MAX_RESPONSE_CHARS) break;
            }
        }
        return s.toString();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "TITAN PULSE — بناء المشاريع", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification buildForegroundNotification(String title, int progress) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_titan)
                .setContentTitle(title)
                .setContentText(Math.max(0, Math.min(100, progress)) + "%")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, Math.min(100, progress)), false);
        try { b.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.titan_pulse_logo)); } catch (Exception ignored) {}
        return b.build();
    }

    private static int notificationId(String jobId) { return NOTIFICATION_ID_BASE + (jobId == null ? 1 : (jobId.hashCode() & 0x7fffffff) % 100000); }

    public static void enqueue(Context context, String ownerId, String jobId) {
        if (ownerId == null || ownerId.isEmpty() || jobId == null || jobId.isEmpty()) return;
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data input = new Data.Builder().putString(INPUT_JOB_ID, jobId).putString(INPUT_OWNER_ID, ownerId).build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(BackgroundBuildWorker.class)
                .setInputData(input).setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(UNIQUE_PREFIX + ownerId + "-" + jobId, androidx.work.ExistingWorkPolicy.KEEP, req);
    }

    public static void cancel(Context context, String ownerId, String jobId) {
        if (ownerId != null && !ownerId.isEmpty() && jobId != null && !jobId.isEmpty()) WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_PREFIX + ownerId + "-" + jobId);
    }

    private static final class BuildException extends Exception {
        final String code; final boolean retryable;
        BuildException(String c, String m, boolean r) { super(m); code = c; retryable = r; }
    }
}
