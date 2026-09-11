package com.titanpulse.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;

import com.titanpulse.app.core.JobStatus;
import com.titanpulse.app.data.DraftEntity;
import com.titanpulse.app.data.JobEntity;
import com.titanpulse.app.data.ProjectEntity;
import com.titanpulse.app.data.TitanDatabase;
import com.titanpulse.app.data.WorkspaceEntity;
import com.titanpulse.app.data.SettingsEntity;
import com.titanpulse.app.security.SecureKeyStore;
import com.titanpulse.app.sync.FirebaseSyncService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Narrow, async native bridge. API secrets are never returned to JavaScript. */
public final class TitanPulseBridge {
    static final String PREFS = "titan_pulse_native";
    private static final String CHANNEL_GENERAL = "titan_pulse_general";
    private static final String CHANNEL_BUILD = "titan_pulse_builds";
    private static final int REQUEST_NOTIFICATIONS = 731;
    private static final int NOTIFICATION_ID_BASE = 12000;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final TitanDatabase db;
    private final SecureKeyStore secureKeyStore;
    private final FirebaseSyncService firebase;
    private final String bridgeToken;
    private final ExecutorService io = Executors.newCachedThreadPool();

    public TitanPulseBridge(Activity activity) {
        this(activity, null);
    }

    public TitanPulseBridge(Activity activity, String bridgeToken) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.db = TitanDatabase.getInstance(activity);
        this.secureKeyStore = new SecureKeyStore(activity);
        this.secureKeyStore.bindDeviceOwner(getDeviceId());
        this.firebase = FirebaseSyncService.get(activity);
        this.bridgeToken = bridgeToken == null ? "" : bridgeToken;
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = activity.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel general = new NotificationChannel(CHANNEL_GENERAL, "TITAN PULSE", NotificationManager.IMPORTANCE_DEFAULT);
        general.setDescription("إشعارات TITAN PULSE العامة وحالة المزامنة");
        general.enableVibration(true);
        nm.createNotificationChannel(general);
        NotificationChannel build = new NotificationChannel(CHANNEL_BUILD, "بناء المشاريع", NotificationManager.IMPORTANCE_DEFAULT);
        build.setDescription("إشعارات اكتمال وفشل مهام بناء المشاريع");
        build.enableVibration(true);
        nm.createNotificationChannel(build);
    }

    @JavascriptInterface public void requestNotificationPermission(String payload) {
        try { requirePayload(payload); } catch (Exception ignored) { return; }
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.runOnUiThread(() -> activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS));
        } else {
            flushQueuedNotifications();
        }
    }

    @JavascriptInterface public void queueNotification(String payload) {
        try {
            JSONObject data = requirePayload(payload);
            JSONArray rows;
            try { rows = new JSONArray(prefs.getString("queued_notifications", "[]")); } catch (Exception ignored) { rows = new JSONArray(); }
            JSONObject item = new JSONObject().put("title", data.optString("title", "TITAN PULSE"))
                    .put("body", data.optString("body", "حدث جديد."))
                    .put("createdAt", Math.max(0L, data.optLong("createdAt", System.currentTimeMillis())));
            JSONArray next = new JSONArray();
            next.put(item);
            for (int i = 0; i < rows.length() && next.length() < 20; i++) {
                JSONObject old = rows.optJSONObject(i);
                if (old != null) next.put(old);
            }
            prefs.edit().putString("queued_notifications", next.toString()).apply();
            if (Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) flushQueuedNotifications();
        } catch (Exception ignored) {}
    }

    void flushQueuedNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            JSONArray rows = new JSONArray(prefs.getString("queued_notifications", "[]"));
            if (rows.length() == 0) return;
            for (int i = rows.length() - 1; i >= 0; i--) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;
                String queuedId = "queued-" + item.optLong("createdAt", System.nanoTime());
                showNotification(activity, item.optString("title", "TITAN PULSE"), item.optString("body", "حدث جديد."), queuedId, "", CHANNEL_GENERAL);
            }
            prefs.edit().remove("queued_notifications").apply();
        } catch (Exception ignored) {}
    }

    @JavascriptInterface public String notify(String payload) {
        try {
            JSONObject data = requirePayload(payload);
            showNotification(activity, data.optString("title", "TITAN PULSE"), data.optString("body", "حدث جديد."), data.optString("jobId", ""), data.optString("projectId", ""), CHANNEL_GENERAL);
            return "ok";
        } catch (Exception e) { return errorJson("NOTIFICATION", "تعذر إنشاء الإشعار."); }
    }

    static void sendNativeNotification(Context context, String title, String body, String jobId, String projectId) {
        showNotification(context, title, body, jobId, projectId, CHANNEL_BUILD);
    }

    private static void showNotification(Context context, String title, String body, String jobId, String projectId, String channel) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Intent intent = new Intent(context, MainActivity.class)
                .setAction("com.titanpulse.app.OPEN_JOB")
                .putExtra("jobId", jobId == null ? "" : jobId)
                .putExtra("projectId", projectId == null ? "" : projectId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = stableNotificationId((jobId == null || jobId.isEmpty()) ? (title + "|" + body + "|" + System.nanoTime()) : jobId);
        PendingIntent pi = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_titan)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true);
        try { b.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.titan_pulse_logo)); } catch (Exception ignored) {}
        nm.notify(requestCode, b.build());
    }

    private static int stableNotificationId(String jobId) {
        int value = (jobId == null || jobId.isEmpty()) ? 1 : (jobId.hashCode() & 0x7fffffff);
        return NOTIFICATION_ID_BASE + (value % 100000);
    }

    @JavascriptInterface public void saveState(String payload) { try { JSONObject o=requirePayload(payload); o.remove("bridgeToken"); saveSetting(ownerScopedKey("session_state"), o.toString()); } catch(Exception ignored) {} }
    @JavascriptInterface public String loadState(String payload) { try { requirePayload(payload); return getSetting(ownerScopedKey("session_state"), "{}"); } catch(Exception e) { return "{}"; } }

    @JavascriptInterface public void saveProject(String payload) {
        try {
            JSONObject o = requirePayload(payload);
            String workspaceId = safeId(o.optString("workspaceId", "default"));
            String owner = getOwnerId();
            String projectId = owner + ":" + workspaceId + ":current";
            ProjectEntity p = db.dao().getProjectForOwner(projectId, owner);
            if (p == null) p = new ProjectEntity();
            String name = o.optString("name", "المشروع الحالي");
            String prompt = o.optString("prompt", "");
            String html = o.optString("html", "");
            String css = o.optString("css", "");
            String js = o.optString("js", "");
            boolean changed = p.ownerUserId.isEmpty() || p.deleted || !safeEquals(p.name, name) || !safeEquals(p.prompt, prompt) || !safeEquals(p.html, html) || !safeEquals(p.css, css) || !safeEquals(p.js, js) || !safeEquals(p.workspaceId, workspaceId);
            p.id = projectId;
            p.ownerUserId = owner;
            p.workspaceId = workspaceId;
            p.name = name;
            p.prompt = prompt;
            p.html = html;
            p.css = css;
            p.js = js;
            if (changed) {
                p.updatedAt = Math.max(o.optLong("updatedAt", 0L), System.currentTimeMillis());
                p.revision = Math.max(1L, p.revision + 1L);
                p.deviceId = getDeviceId();
            }
            p.deleted = false;
            db.dao().upsertProject(p);
        } catch (Exception ignored) { }
    }

    @JavascriptInterface public String loadProject(String payload) {
        try { requirePayload(payload);
            String ws = safeId(getSetting(ownerScopedKey("current_workspace"), "default"));
            String owner = getOwnerId();
            ProjectEntity p = db.dao().getProjectForOwner(owner + ":" + ws + ":current", owner);
            if (p == null) return "{}";
            return new JSONObject().put("id", p.id).put("workspaceId", p.workspaceId).put("name", p.name).put("prompt", p.prompt)
                    .put("html", p.html).put("css", p.css).put("js", p.js).put("updatedAt", p.updatedAt).put("revision", p.revision).toString();
        } catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface public void saveWorkspaces(String payload) { try { JSONObject o=requirePayload(payload); o.remove("bridgeToken"); saveWorkspacesToRoom(o.toString()); } catch(Exception ignored) {} }
    @JavascriptInterface public String loadWorkspaces(String payload) { try { requirePayload(payload); return loadWorkspacesFromRoom(); } catch(Exception e) { return "{}"; } }
    @JavascriptInterface public void setCurrentWorkspace(String payload) { try { JSONObject o=requirePayload(payload); String id=o.has("value")?o.optString("value",""):o.optString("id",""); String v=safeId(id); saveSetting(ownerScopedKey("current_workspace"), v.isEmpty()?"default":v); } catch(Exception ignored) {} }
    @JavascriptInterface public String getCurrentWorkspace(String payload) { try { requirePayload(payload); return getSetting(ownerScopedKey("current_workspace"), "default"); } catch(Exception e) { return "default"; } }
    @JavascriptInterface public void setAppForeground(String payload) {
        try { JSONObject o = requirePayload(payload); setAppForegroundValue(o.optString("value", "0")); } catch (Exception ignored) {}
    }
    void setAppForegroundValue(String value) {
        prefs.edit().putString("app_foreground", "1".equals(value) ? "1" : "0").apply();
        maybeScheduleFromActivityState();
    }
    @JavascriptInterface public void setBuildState(String payload) {
        try { JSONObject o = requirePayload(payload); prefs.edit().putString("build_state", o.optString("value", "idle")).apply(); } catch (Exception ignored) {}
    }

    /** Kept for backwards compatibility; never returns plaintext keys. */
    @JavascriptInterface public void saveProviderSecrets(String payload) { }
    @JavascriptInterface public String getProviderSecretMeta(String payload) { try { requirePayload(payload); return secureKeyStore.getAllJsonMasked(getOwnerId()); } catch(Exception e) { return "{}"; } }
    @JavascriptInterface public String getProviderSecret(String ignored) { return "[]"; }

    @JavascriptInterface public void putProviderSecret(String payload) {
        try {
            JSONObject o = requirePayload(payload);
            String id = safeId(o.optString("providerId"));
            String key = o.optString("key", "").trim();
            if (id.isEmpty() || key.isEmpty()) return;
            if (!AiProviderRegistry.isSupported(id)) return;
            String raw = secureKeyStore.get(getOwnerId(), id);
            JSONArray a;
            try { a = new JSONArray(raw.isEmpty() ? "[]" : raw); } catch (Exception ignored) { a = new JSONArray(); }
            for (int i = 0; i < a.length(); i++) if (key.equals(a.optString(i))) return;
            a.put(key);
            secureKeyStore.put(getOwnerId(), id, a.toString());
        } catch (Exception ignored) { }
    }

    @JavascriptInterface public void removeProviderSecret(String payload) {
        try {
            JSONObject o = requirePayload(payload);
            String id = safeId(o.optString("providerId"));
            int idx = o.optInt("index", -1);
            String raw = secureKeyStore.get(getOwnerId(), id);
            JSONArray a = new JSONArray(raw.isEmpty() ? "[]" : raw);
            if (idx < 0 || idx >= a.length()) return;
            JSONArray n = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (i != idx) n.put(a.opt(i));
            if (n.length() == 0) secureKeyStore.remove(getOwnerId(), id); else secureKeyStore.put(getOwnerId(), id, n.toString());
        } catch (Exception ignored) { }
    }

    @JavascriptInterface public String scheduleBackgroundBuild(String payload) {
        try {
            JSONObject o = requirePayload(payload);
            String owner = getOwnerId();
            String jobId = safeId(o.optString("jobId", "job-" + UUID.randomUUID()));
            JobEntity existing = db.dao().getJobForUser(jobId, owner);
            if (existing != null && (JobStatus.QUEUED.equals(existing.status) || JobStatus.RUNNING.equals(existing.status) || JobStatus.RETRYING.equals(existing.status) || JobStatus.WAITING_NETWORK.equals(existing.status))) return existing.id;
            JobEntity job = new JobEntity();
            job.id = jobId;
            job.userId = owner;
            job.workspaceId = safeId(o.optString("workspaceId", "default"));
            String requestedProjectId = safeId(o.optString("projectId", ""));
            job.projectId = requestedProjectId.isEmpty() ? owner + ":" + job.workspaceId + ":current" : requestedProjectId;
            if (!job.projectId.startsWith(owner + ":")) job.projectId = owner + ":" + job.workspaceId + ":current";
            job.prompt = o.optString("prompt", "");
            job.extra = o.optString("extra", "");
            job.mode = "script".equals(o.optString("mode", "web")) ? "script" : "web";
            job.providersJson = o.optString("providers", "[]");
            job.deviceId = getDeviceId();
            job.status = JobStatus.QUEUED;
            job.progress = 0;
            job.createdAt = System.currentTimeMillis();
            job.revision = 1;
            db.dao().upsertJob(job);
            prefs.edit().putString("active_job_id", jobId).putString("build_state", "queued").apply();
            // Do not start the backup worker while the foreground web flow is active; it is a recovery path only.
            if ("0".equals(prefs.getString("app_foreground", "1"))) {
                BackgroundBuildWorker.enqueue(activity.getApplicationContext(), owner, jobId);
            }
            return jobId;
        } catch (Exception e) { return ""; }
    }

    @JavascriptInterface public void cancelBackgroundBuild(String payload) {
        String jobId; try { JSONObject o=requirePayload(payload); jobId=o.optString("value", o.optString("jobId","")); } catch(Exception e){return;}
        if (jobId == null || jobId.isEmpty()) return;
        String owner = getOwnerId();
        JobEntity job = db.dao().getJobForUser(jobId, owner);
        if (job == null) return;
        BackgroundBuildWorker.cancel(activity.getApplicationContext(), getOwnerId(), jobId);
        job.status = JobStatus.CANCELLED;
        job.completedAt = System.currentTimeMillis();
        job.revision++;
        db.dao().upsertJob(job);
    }

    void maybeScheduleFromActivityState() {
        try {
            String owner = getOwnerId();
            if (!"1".equals(prefs.getString("app_foreground", "1"))) {
                String jobId = prefs.getString("active_job_id", "");
                if (!jobId.isEmpty()) {
                    JobEntity j = db.dao().getJobForUser(jobId, getOwnerId());
                    if (j != null && (JobStatus.QUEUED.equals(j.status) || JobStatus.RETRYING.equals(j.status) || JobStatus.WAITING_NETWORK.equals(j.status) || JobStatus.RUNNING.equals(j.status))) BackgroundBuildWorker.enqueue(activity.getApplicationContext(), owner, jobId);
                }
            }
        } catch (Exception ignored) { }
    }

    @JavascriptInterface public void completeBackgroundBuild(String payload) {
        String jobId; try { JSONObject o=requirePayload(payload); jobId=o.optString("value", o.optString("jobId","")); } catch(Exception e){return;}
        if (jobId == null || jobId.isEmpty()) return;
        JobEntity j = db.dao().getJobForUser(jobId, getOwnerId());
        if (j == null) return;
        // A running worker is the background takeover path; do not cancel it when the foreground flow finishes.
        if (JobStatus.QUEUED.equals(j.status) || JobStatus.RETRYING.equals(j.status) || JobStatus.WAITING_NETWORK.equals(j.status)) {
            BackgroundBuildWorker.cancel(activity.getApplicationContext(), getOwnerId(), jobId);
            j.status = JobStatus.CANCELLED;
            j.completedAt = System.currentTimeMillis();
            j.revision++;
            db.dao().upsertJob(j);
        }
        prefs.edit().remove("active_job_id").putString("build_state", "idle").apply();
    }

    static void persistResult(Context context, String jobId, String json) {
        try {
            File f = new File(context.getFilesDir(), "titan_background_result_" + safeFileId(jobId) + ".json");
            Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    @JavascriptInterface public String getBackgroundBuildResult(String payload) {
        try {
            JSONObject o=requirePayload(payload); String jobId=o.optString("value", o.optString("jobId",""));
            String id = (jobId == null || jobId.isEmpty()) ? prefs.getString("active_job_id", "") : jobId;
            if (id.isEmpty()) return "";
            JobEntity job = db.dao().getJobForUser(id, getOwnerId());
            if (job == null || !JobStatus.COMPLETED.equals(job.status)) return "";
            File f = new File(activity.getFilesDir(), "titan_background_result_" + safeFileId(id) + ".json");
            if (!f.exists()) return "";
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (s.length() > 8 * 1024 * 1024) return "";
            f.delete();
            if (id.equals(prefs.getString("active_job_id", ""))) prefs.edit().remove("active_job_id").putString("build_state", "idle").apply();
            return s;
        } catch (Exception e) { return ""; }
    }

    @JavascriptInterface public void clearLocalData(String payload) {
        try {
            requirePayload(payload);
            String owner=getOwnerId();
            db.dao().deleteProjectsForOwner(owner); db.dao().deleteWorkspacesForOwner(owner); db.dao().deleteDraftsForOwner(owner); db.dao().deleteJobsForUser(owner);
            secureKeyStore.removeOwner(owner);
            prefs.edit().remove("active_job_id").remove("build_state").remove("app_foreground").apply();
        } catch(Exception ignored) {}
    }

    @JavascriptInterface public void requestAi(String payload) {
        final String safePayload = payload == null ? "{}" : payload;
        io.execute(() -> {
            String callback = "";
            try {
                JSONObject o = requirePayload(safePayload);
                callback = o.optString("callback", "");
                String providerId = safeId(o.optString("providerId"));
                String model = o.optString("model", "").trim();
                JSONArray messages = o.optJSONArray("messages");
                JSONObject responseSchema = o.optJSONObject("responseSchema");
                if (messages == null || messages.length() == 0 || messages.length() > 100) throw new IllegalArgumentException("عدد رسائل الطلب غير صالح.");
                if (messages.toString().length() > 900_000) throw new IllegalArgumentException("حجم محتوى الطلب كبير جداً.");
                if (responseSchema != null && responseSchema.toString().length() > 64_000) throw new IllegalArgumentException("مخطط الاستجابة كبير جداً.");
                AiProviderRegistry.ProviderSpec spec = AiProviderRegistry.get(providerId);
                if (spec == null) throw new IllegalArgumentException("هذا المزود غير مسموح به في الوضع الآمن.");
                if (model.isEmpty()) throw new IllegalArgumentException("النموذج غير محدد.");
                String raw = secureKeyStore.get(getOwnerId(), providerId);
                JSONArray keys = new JSONArray(raw.isEmpty() ? "[]" : raw);
                Exception last = null; String text = "";
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.optString(i, "").trim();
                    if (key.isEmpty()) continue;
                    try {
                        text = BackgroundBuildWorker.callProviderStatic(providerId, model, key, messages, responseSchema);
                        if (!text.isEmpty()) break;
                    } catch (Exception e) { last = e; }
                }
                if (text.isEmpty()) throw (last == null ? new IllegalStateException("لا يوجد مفتاح صالح لهذا المزود.") : last);
                postBridgeCallback("ai", callback, new JSONObject().put("ok", true).put("text", text).toString());
            } catch (Exception e) {
                postBridgeCallback("ai", callback, errorJson("AI", safeClientMessage(e)));
            }
        });
    }

    @JavascriptInterface public void loginAsync(String payload) {
        final String cb = safeCallbackId(payload);
        try {
            final String oldOwner = getDeviceId();
            final JSONObject o = requirePayload(payload);
            firebase.signInEmailAsync(o.getString("email"), o.getString("password")).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    postBridgeCallback("auth", cb, errorJson("AUTH", safeClientMessage(task.getException())));
                    return;
                }
                io.execute(() -> {
                    String uid = firebase.userId();
                    try {
                        adoptLocalData(oldOwner, uid);
                        postBridgeCallback("auth", cb, authStatus(""));
                    } catch (Exception migrationError) {
                        firebase.signOut();
                        postBridgeCallback("auth", cb, errorJson("AUTH_MIGRATION", "تم تسجيل الدخول لكن تعذر نقل بيانات الجهاز بأمان."));
                    }
                });
            });
        } catch (Exception e) {
            postBridgeCallback("auth", cb, errorJson("AUTH", safeClientMessage(e)));
        }
    }

    @JavascriptInterface public void registerAsync(String payload) {
        final String cb = safeCallbackId(payload);
        try {
            final String oldOwner = getDeviceId();
            final JSONObject o = requirePayload(payload);
            firebase.registerEmailAsync(o.getString("email"), o.getString("password")).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    postBridgeCallback("auth", cb, errorJson("AUTH", safeClientMessage(task.getException())));
                    return;
                }
                io.execute(() -> {
                    String uid = firebase.userId();
                    try {
                        adoptLocalData(oldOwner, uid);
                        postBridgeCallback("auth", cb, authStatus(""));
                    } catch (Exception migrationError) {
                        firebase.signOut();
                        postBridgeCallback("auth", cb, errorJson("AUTH_MIGRATION", "تم إنشاء الحساب لكن تعذر نقل بيانات الجهاز بأمان."));
                    }
                });
            });
        } catch (Exception e) {
            postBridgeCallback("auth", cb, errorJson("AUTH", safeClientMessage(e)));
        }
    }

    @JavascriptInterface public void forgotPasswordAsync(String payload) {
        final String cb = safeCallbackId(payload);
        try {
            JSONObject o = requirePayload(payload);
            firebase.sendPasswordResetAsync(o.getString("email")).addOnCompleteListener(task -> {
                if (task.isSuccessful()) postBridgeCallback("auth", cb, successJson("تم إرسال رابط إعادة تعيين كلمة المرور."));
                else postBridgeCallback("auth", cb, errorJson("AUTH_RESET", safeClientMessage(task.getException())));
            });
        } catch (Exception e) {
            postBridgeCallback("auth", cb, errorJson("AUTH_RESET", safeClientMessage(e)));
        }
    }

    @JavascriptInterface public void deleteAccountAsync(String payload) {
        final String cb = safeCallbackId(payload);
        try {
            final JSONObject request = requirePayload(payload);
            final String uid = firebase.userId();
            if (uid.isEmpty()) throw new IllegalStateException("سجّل الدخول أولاً.");
            final String password = request.optString("password", "");
            firebase.deleteCurrentUserAndDataAsync(password).addOnCompleteListener(task -> io.execute(() -> {
                if (!task.isSuccessful()) {
                    postBridgeCallback("auth", cb, errorJson("DELETE_ACCOUNT", safeClientMessage(task.getException())));
                    return;
                }
                try {
                    db.dao().deleteProjectsForOwner(uid);
                    db.dao().deleteWorkspacesForOwner(uid);
                    db.dao().deleteDraftsForOwner(uid);
                    db.dao().deleteJobsForUser(uid);
                    secureKeyStore.removeOwner(uid);
                    postBridgeCallback("auth", cb, successJsonWithFlag("deleted", true));
                } catch (Exception e) {
                    postBridgeCallback("auth", cb, errorJson("DELETE_ACCOUNT_LOCAL", "تم حذف الحساب السحابي لكن تعذر تنظيف بعض البيانات المحلية."));
                }
            }));
        } catch (Exception e) {
            postBridgeCallback("auth", cb, errorJson("DELETE_ACCOUNT", safeClientMessage(e)));
        }
    }

    @JavascriptInterface public void sendEmailVerificationAsync(String payload) {
        final String cb = safeCallbackId(payload);
        try { requirePayload(payload); } catch (Exception e) {
            postBridgeCallback("auth", cb, errorJson("BRIDGE_AUTH", "تعذر التحقق من التطبيق."));
            return;
        }
        try {
            firebase.sendEmailVerificationAsync().addOnCompleteListener(task -> {
                if (task.isSuccessful()) postBridgeCallback("auth", cb, successJson("تم إرسال رسالة التحقق إلى بريدك."));
                else postBridgeCallback("auth", cb, errorJson("VERIFY_EMAIL", safeClientMessage(task.getException())));
            });
        } catch (Exception e) {
            postBridgeCallback("auth", cb, errorJson("VERIFY_EMAIL", safeClientMessage(e)));
        }
    }

    private static String successJson(String message) {
        try { return new JSONObject().put("ok", true).put("message", message == null ? "تمت العملية." : message).toString(); } catch (Exception e) { return "{\"ok\":true}"; }
    }
    private static String successJsonWithFlag(String key, boolean value) {
        try { return new JSONObject().put("ok", true).put(key, value).toString(); } catch (Exception e) { return "{\"ok\":true}"; }
    }

    @JavascriptInterface public void syncCurrentProjectAsync(String payload) {
        io.execute(() -> {
            String cb = safeCallbackId(payload);
            try { requirePayload(payload); postBridgeCallback("sync", cb, syncCurrentProject()); }
            catch (Exception e) { postBridgeCallback("sync", cb, errorJson("SYNC", safeClientMessage(e))); }
        });
    }

    private String safeCallbackId(String p) { try { return new JSONObject(p == null ? "{}" : p).optString("callbackId", ""); } catch (Exception e) { return ""; } }

    private void postBridgeCallback(String kind, String id, String result) {
        if (id == null || id.isEmpty()) return;
        String jsId = JSONObject.quote(id);
        String jsResult = JSONObject.quote(result == null ? "" : result);
        activity.runOnUiThread(() -> {
            WebView w = getWebView();
            if (w == null) return;
            String fn = "ai".equals(kind) ? "__titanNativeAi" : "auth".equals(kind) ? "__titanNativeAuth" : "__titanNativeSync";
            w.evaluateJavascript("window." + fn + " && window." + fn + "(" + jsId + "," + jsResult + ")", null);
        });
    }

    private WebView getWebView() { try { return ((MainActivity) activity).getBridge().getWebView(); } catch (Exception e) { return null; } }

    @JavascriptInterface public String authStatus(String payload) {
        try { requirePayload(payload); return new JSONObject().put("configured", firebase.isInitialized()).put("userId", firebase.userId()).put("email", firebase.userEmail()).put("emailVerified", firebase.emailVerified()).toString(); }
        catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface public void logout(String payload) {
        try { requirePayload(payload); } catch(Exception e) { return; }
        String owner = firebase.userId();
        try {
            if (!owner.isEmpty()) {
                for (JobEntity job : db.dao().getActiveJobsForUser(owner)) {
                    BackgroundBuildWorker.cancel(activity.getApplicationContext(), owner, job.id);
                    job.status = JobStatus.CANCELLED;
                    job.completedAt = System.currentTimeMillis();
                    job.errorCode = "SIGNED_OUT";
                    job.errorMessage = "تم إيقاف المهمة عند تسجيل الخروج.";
                    job.revision++;
                    db.dao().upsertJob(job);
                }
            }
        } catch(Exception ignored) {}
        firebase.signOut();
        prefs.edit().remove("active_job_id").putString("build_state", "idle").apply();
    }


    private String syncCurrentProject() throws Exception {
        String uid = firebase.userId();
        if (uid.isEmpty()) return errorJson("AUTH_REQUIRED", "سجّل الدخول للمزامنة.");
        adoptLocalData(getDeviceId(), uid);
        int synced = com.titanpulse.app.sync.BackgroundSyncWorker.syncNow(activity.getApplicationContext(), uid);
        saveSetting(ownerScopedKey("last_sync"), String.valueOf(System.currentTimeMillis()));
        return new JSONObject().put("ok", true).put("syncedRecords", synced).put("updatedAt", System.currentTimeMillis()).toString();
    }

    private void adoptLocalData(String oldOwner, String newOwner) throws Exception {
        if (oldOwner == null || oldOwner.isEmpty() || newOwner == null || newOwner.isEmpty() || oldOwner.equals(newOwner)) return;
        List<ProjectEntity> projects = db.dao().getProjectsForOwnerForMigration(oldOwner);
        for (ProjectEntity p : projects) {
            String oldId = p.id;
            if (oldId.startsWith(oldOwner + ":")) p.id = newOwner + oldId.substring(oldOwner.length());
            p.ownerUserId = newOwner;
            db.dao().upsertProject(p);
            if (!oldId.equals(p.id)) db.dao().deleteProject(oldOwner, oldId);
        }
        for (WorkspaceEntity w : db.dao().getWorkspacesForOwnerForMigration(oldOwner)) { w.ownerUserId = newOwner; db.dao().upsertWorkspace(w); }
        for (DraftEntity d : db.dao().getDraftsForOwnerForMigration(oldOwner)) { d.ownerUserId = newOwner; db.dao().upsertDraft(d); }
        for (JobEntity j : db.dao().getJobsForUserForMigration(oldOwner)) {
            boolean active = JobStatus.QUEUED.equals(j.status) || JobStatus.RUNNING.equals(j.status) || JobStatus.RETRYING.equals(j.status) || JobStatus.WAITING_NETWORK.equals(j.status);
            if (active) BackgroundBuildWorker.cancel(activity.getApplicationContext(), oldOwner, j.id);
            j.userId = newOwner;
            db.dao().upsertJob(j);
            if (active && !JobStatus.CANCELLED.equals(j.status) && !JobStatus.COMPLETED.equals(j.status)) BackgroundBuildWorker.enqueue(activity.getApplicationContext(), newOwner, j.id);
        }
        // Migrate legacy owner-scoped settings that were previously stored without an explicit Room owner column.
        try {
            for (SettingsEntity setting : db.dao().getAllSettings()) {
                String suffix = ":" + oldOwner;
                if (setting.ownerUserId.isEmpty() && setting.key.endsWith(suffix)) {
                    SettingsEntity copy = new SettingsEntity();
                    copy.ownerUserId = newOwner;
                    copy.key = setting.key.substring(0, setting.key.length() - suffix.length()) + ":" + newOwner;
                    copy.value = setting.value;
                    copy.updatedAt = setting.updatedAt;
                    db.dao().upsertSetting(copy);
                }
            }
        } catch (Exception ignored) {}
        db.dao().deleteWorkspacesForOwner(oldOwner);
        db.dao().deleteDraftsForOwner(oldOwner);
        db.dao().deleteJobsForUser(oldOwner);
        secureKeyStore.adoptLegacyOwner(newOwner);
        secureKeyStore.migrateOwner(oldOwner, newOwner);
    }

    private JSONObject requirePayload(String payload) throws Exception {
        if (bridgeToken.isEmpty()) throw new SecurityException("bridge token unavailable");
        JSONObject o=new JSONObject(payload==null?"{}":payload);
        if (!bridgeToken.equals(o.optString("bridgeToken",""))) throw new SecurityException("invalid bridge token");
        return o;
    }

    private String ownerScopedKey(String base) { return base + ":" + getOwnerId(); }

    private void saveWorkspacesToRoom(String payload) {
        try {
            JSONObject root = new JSONObject(payload == null ? "{}" : payload);
            String owner = getOwnerId();
            long now = System.currentTimeMillis();
            List<WorkspaceEntity> existingWs = db.dao().getWorkspacesForOwnerIncludingDeleted(owner);
            java.util.HashMap<String, WorkspaceEntity> existingMap = new java.util.HashMap<>();
            for (WorkspaceEntity w : existingWs) existingMap.put(w.id, w);
            java.util.HashSet<String> incoming = new java.util.HashSet<>();
            java.util.Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String id = safeId(keys.next()); if (id.isEmpty()) continue;
                incoming.add(id); JSONObject wo = root.optJSONObject(id); if (wo == null) continue;
                WorkspaceEntity w = existingMap.get(id); if (w == null) w = new WorkspaceEntity();
                String newName = wo.optString("name", "مساحة العمل");
                boolean workspaceChanged = w.ownerUserId.isEmpty() || !newName.equals(w.name) || w.deleted;
                w.ownerUserId = owner; w.id = id; w.name = newName;
                if (workspaceChanged) { w.updatedAt = now; w.revision = Math.max(1L, w.revision + 1L); w.deviceId = getDeviceId(); }
                w.deleted = false;
                db.dao().upsertWorkspace(w);
                java.util.HashSet<String> incomingProjects = new java.util.HashSet<>();
                org.json.JSONArray projects = wo.optJSONArray("projects");
                if (projects != null) for (int i=0;i<projects.length();i++) {
                    JSONObject po = projects.optJSONObject(i); if (po == null) continue;
                    String pid = safeId(po.optString("id", "current")); if (pid.isEmpty()) continue; incomingProjects.add(pid);
                    if (!"current".equals(pid)) continue;
                    String projectId = owner + ":" + id + ":current"; ProjectEntity p = db.dao().getProjectForOwner(projectId, owner); if (p == null) p = new ProjectEntity();
                    String nn=po.optString("name", "المشروع الحالي"), np=po.optString("prompt", ""), nh=po.optString("html", ""), nc=po.optString("css", ""), nj=po.optString("js", "");
                    boolean projectChanged = p.ownerUserId.isEmpty() || !safeEquals(p.name,nn) || !safeEquals(p.prompt,np) || !safeEquals(p.html,nh) || !safeEquals(p.css,nc) || !safeEquals(p.js,nj) || p.deleted;
                    p.id = projectId; p.ownerUserId = owner; p.workspaceId = id; p.name = nn; p.prompt = np; p.html = nh; p.css = nc; p.js = nj;
                    if (projectChanged) { p.updatedAt = now; p.revision = Math.max(1L, p.revision + 1L); p.deviceId = getDeviceId(); }
                    p.deleted = false; db.dao().upsertProject(p);
                }
                for (ProjectEntity p : db.dao().getProjectsForOwnerAndWorkspace(owner, id)) {
                    if (!incomingProjects.contains("current") && !p.deleted) { p.deleted = true; p.revision++; p.updatedAt = now; p.deviceId = getDeviceId(); db.dao().upsertProject(p); }
                }
            }
            for (WorkspaceEntity w : existingWs) if (!incoming.contains(w.id) && !w.deleted) {
                w.deleted = true; w.updatedAt = now; w.revision++; w.deviceId = getDeviceId(); db.dao().upsertWorkspace(w);
                for (ProjectEntity p : db.dao().getProjectsForOwnerAndWorkspace(owner, w.id)) { p.deleted = true; p.updatedAt = now; p.revision++; p.deviceId = getDeviceId(); db.dao().upsertProject(p); }
            }
            saveSetting(ownerScopedKey("workspaces_legacy_cache"), payload == null ? "{}" : payload);
        } catch (Exception ignored) { }
    }

    private String loadWorkspacesFromRoom() {
        try {
            String owner = getOwnerId(); JSONObject root = new JSONObject();
            for (WorkspaceEntity w : db.dao().getWorkspacesForOwner(owner)) {
                JSONObject wo = new JSONObject().put("name", w.name).put("projects", new JSONArray());
                JSONArray arr = wo.getJSONArray("projects");
                for (ProjectEntity p : db.dao().getProjectsForOwnerAndWorkspace(owner, w.id)) if (!p.deleted) {
                    arr.put(new JSONObject().put("id", "current").put("name", p.name).put("prompt", p.prompt).put("html", p.html).put("css", p.css).put("js", p.js).put("updatedAt", p.updatedAt));
                }
                root.put(w.id, wo);
            }
            if (root.length() > 0) return root.toString();
            return getSetting(ownerScopedKey("workspaces_legacy_cache"), "{}");
        } catch (Exception e) { return "{}"; }
    }

    private void saveSetting(String key, String value) {
        SettingsEntity s = new SettingsEntity();
        s.ownerUserId = getOwnerId();
        s.key = key;
        s.value = value == null ? "" : value;
        s.updatedAt = System.currentTimeMillis();
        db.dao().upsertSetting(s);
        prefs.edit().putString(legacyPrefsKey(key), s.value).apply();
    }
    private String getSetting(String key, String fallback) {
        try {
            SettingsEntity s = db.dao().getSetting(getOwnerId(), key);
            if (s != null && s.value != null) return s.value;
        } catch (Exception ignored) {}
        return prefs.getString(legacyPrefsKey(key), fallback);
    }
    private String legacyPrefsKey(String key) { return "setting:" + getOwnerId() + ":" + key; }
    private String getOwnerId() { String uid = firebase.userId(); return uid.isEmpty() ? getDeviceId() : uid; }
    private String getDeviceId() { String id = prefs.getString("device_id", ""); if (id.isEmpty()) { id = UUID.randomUUID().toString(); prefs.edit().putString("device_id", id).apply(); } return id; }
    private static boolean safeEquals(String a,String b){return a==null?b==null:a.equals(b);}
    private static String safeId(String s) { return s == null ? "" : s.trim().replaceAll("[^a-zA-Z0-9._:-]", "_"); }
    private static String safeFileId(String s) { return safeId(s).replace(':', '_'); }
    private static String safeClientMessage(Exception e) { String m = e.getMessage(); if (m == null || m.trim().isEmpty()) return "تعذر تنفيذ العملية."; return m.length() > 240 ? m.substring(0, 240) : m; }
    private static String errorJson(String code, String message) { try { return new JSONObject().put("error", code).put("message", message == null ? "حدث خطأ." : message).toString(); } catch (Exception e) { return "{\"error\":\"UNKNOWN\"}"; } }

    void shutdown() {
        io.shutdownNow();
    }

    @JavascriptInterface public String getJob(String payload) {
        try { JSONObject o=requirePayload(payload); String jobId=o.optString("value", o.optString("jobId","")); JobEntity j = db.dao().getJobForUser(jobId, getOwnerId());
            if (j == null) return "{}";
            return new JSONObject().put("id", j.id).put("status", j.status).put("progress", j.progress).put("errorCode", j.errorCode).put("errorMessage", j.errorMessage).put("resultJson", j.resultJson).put("updatedAt", j.completedAt > 0 ? j.completedAt : j.createdAt).toString();
        } catch (Exception e) { return "{}"; }
    }
}
