package com.titanpulse.app.sync;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Authentication and owner-scoped Cloud synchronization.
 * Secrets are never written to Firestore.
 */
public final class FirebaseSyncService {
    private static final long TIMEOUT_SECONDS = 30L;
    private static final int PAGE_SIZE = 200;
    private static volatile FirebaseSyncService INSTANCE;
    private final Context context;
    private volatile boolean initialized;

    private FirebaseSyncService(Context context) { this.context = context.getApplicationContext(); init(); }

    public static FirebaseSyncService get(Context context) {
        if (INSTANCE == null) synchronized (FirebaseSyncService.class) {
            if (INSTANCE == null) INSTANCE = new FirebaseSyncService(context);
        }
        return INSTANCE;
    }

    public boolean isInitialized() { return initialized; }
    public FirebaseAuth auth() { return initialized ? FirebaseAuth.getInstance() : null; }
    public FirebaseFirestore db() { return initialized ? FirebaseFirestore.getInstance() : null; }

    private void init() {
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) { initialized = true; return; }
            AssetManager am = context.getAssets();
            InputStream configStream;
            try { configStream = am.open("public/firebase_config.json"); }
            catch (Exception ignored) { configStream = am.open("firebase_config.json"); }
            try (InputStream in = configStream) {
                byte[] bytes = new byte[in.available()];
                int read = in.read(bytes);
                JSONObject o = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
                if (!o.optBoolean("enabled", false)) return;
                FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                        .setApiKey(o.optString("apiKey"))
                        .setApplicationId(o.optString("appId"))
                        .setProjectId(o.optString("projectId"));
                if (o.has("messagingSenderId")) builder.setGcmSenderId(o.optString("messagingSenderId"));
                if (o.has("storageBucket") && !o.optString("storageBucket").isEmpty()) builder.setStorageBucket(o.optString("storageBucket"));
                FirebaseApp.initializeApp(context, builder.build());
                initialized = true;
            }
        } catch (Exception ignored) { initialized = false; }
    }

    public Task<AuthResult> signInEmailAsync(String email, String password) {
        ensureReady();
        validateCredentials(email, password);
        return auth().signInWithEmailAndPassword(email.trim(), password);
    }

    public Task<AuthResult> registerEmailAsync(String email, String password) {
        ensureReady();
        validateCredentials(email, password);
        return auth().createUserWithEmailAndPassword(email.trim(), password);
    }

    public Task<Void> sendPasswordResetAsync(String email) {
        ensureReady();
        if (email == null || !email.trim().contains("@")) throw new IllegalArgumentException("البريد الإلكتروني غير صالح");
        return auth().sendPasswordResetEmail(email.trim());
    }

    public Task<Void> sendEmailVerificationAsync() {
        ensureReady();
        if (auth().getCurrentUser() == null) throw new IllegalStateException("سجّل الدخول أولاً.");
        return auth().getCurrentUser().sendEmailVerification();
    }

    public String userId() { return auth() != null && auth().getCurrentUser() != null ? auth().getCurrentUser().getUid() : ""; }
    public String userEmail() { return auth() != null && auth().getCurrentUser() != null && auth().getCurrentUser().getEmail() != null ? auth().getCurrentUser().getEmail() : ""; }
    public boolean emailVerified() { return auth() != null && auth().getCurrentUser() != null && auth().getCurrentUser().isEmailVerified(); }
    public void signOut() { if (auth() != null) auth().signOut(); }

    public Task<Void> deleteCurrentUserAndDataAsync(String passwordForReauth) {
        ensureReady();
        if (auth().getCurrentUser() == null) throw new IllegalStateException("يجب تسجيل الدخول أولاً");
        String email = userEmail();
        String uid = userId();
        if (passwordForReauth == null || passwordForReauth.isEmpty() || email.isEmpty()) throw new IllegalStateException("لأسباب أمنية يجب إعادة تأكيد كلمة مرور الحساب قبل الحذف.");
        com.google.firebase.auth.AuthCredential credential = EmailAuthProvider.getCredential(email, passwordForReauth);
        return auth().getCurrentUser().reauthenticate(credential).continueWithTask(task -> {
            if (!task.isSuccessful()) throw new Exception(task.getException() == null ? "تعذر إعادة تأكيد الحساب." : task.getException());
            return deleteAllCollectionsAsync(uid);
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw new Exception(task.getException() == null ? "تعذر حذف البيانات السحابية." : task.getException());
            return db().collection("users").document(uid).delete();
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) throw new Exception(task.getException() == null ? "تعذر حذف سجل الحساب." : task.getException());
            if (auth().getCurrentUser() == null) return com.google.android.gms.tasks.Tasks.forResult(null);
            return auth().getCurrentUser().delete();
        });
    }

    private Task<Void> deleteAllCollectionsAsync(String uid) {
        Task<Void> chain = com.google.android.gms.tasks.Tasks.forResult(null);
        for (String collection : new String[]{"projects", "workspaces", "drafts", "jobs"}) {
            final String c = collection;
            chain = chain.continueWithTask(task -> {
                if (!task.isSuccessful()) throw new Exception(task.getException() == null ? "تعذر حذف البيانات." : task.getException());
                return deleteCollectionAsync(uid, c);
            });
        }
        return chain;
    }

    private Task<Void> deleteCollectionAsync(String uid, String collection) {
        DocumentReference parent = db().collection("users").document(uid);
        return parent.collection(collection).limit(PAGE_SIZE).get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw new Exception(task.getException() == null ? "تعذر قراءة البيانات." : task.getException());
            List<DocumentSnapshot> docs = task.getResult() == null ? new ArrayList<>() : task.getResult().getDocuments();
            if (docs.isEmpty()) return com.google.android.gms.tasks.Tasks.forResult(null);
            com.google.firebase.firestore.WriteBatch batch = db().batch();
            for (DocumentSnapshot d : docs) batch.delete(d.getReference());
            return batch.commit().continueWithTask(commit -> {
                if (!commit.isSuccessful()) throw new Exception(commit.getException() == null ? "تعذر حذف البيانات." : commit.getException());
                return deleteCollectionAsync(uid, collection);
            });
        });
    }

    /** Transactionally wins only by monotonically increasing logical revision and deterministic device tiebreak. */
    public Map<String, Object> syncDocument(String userId, String collection, String documentId, Map<String, Object> local) throws Exception {
        ensureReady();
        if (userId == null || userId.isEmpty()) throw new IllegalStateException("تسجيل الدخول مطلوب");
        if (collection == null || collection.isEmpty() || documentId == null || documentId.isEmpty()) throw new IllegalArgumentException("معرف غير صالح");
        final DocumentReference ref = db().collection("users").document(userId).collection(collection).document(documentId);
        final Map<String, Object> copy = new HashMap<>(local == null ? new HashMap<>() : local);
        copy.put("userId", userId);
        copy.put("ownerUserId", userId);
        copy.put("serverSchema", 1);

        Tasks.await(db().runTransaction(new Transaction.Function<Boolean>() {
            @Override public Boolean apply(@NonNull Transaction transaction) throws Exception {
                DocumentSnapshot remote = transaction.get(ref);
                if (!remote.exists()) {
                    Map<String, Object> data = new HashMap<>(copy);
                    data.put("serverUpdatedAt", FieldValue.serverTimestamp());
                    transaction.set(ref, data, SetOptions.merge());
                    return true;
                }
                long lr = number(copy.get("revision"));
                long rr = number(remote.get("revision"));
                String ld = String.valueOf(copy.getOrDefault("deviceId", ""));
                String rd = remote.getString("deviceId");
                if (rd == null) rd = "";
                boolean localWins = lr > rr || (lr == rr && ld.compareTo(rd) > 0);
                if (localWins) {
                    Map<String, Object> data = new HashMap<>(copy);
                    data.put("serverUpdatedAt", FieldValue.serverTimestamp());
                    transaction.set(ref, data, SetOptions.merge());
                    return true;
                }
                return false;
            }
        }), TIMEOUT_SECONDS, TimeUnit.SECONDS);

        DocumentSnapshot canonical = Tasks.await(ref.get(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return canonical.exists() && canonical.getData() != null ? canonical.getData() : copy;
    }

    public Map<String, Object> syncProject(String userId, String projectId, Map<String, Object> local, long ignoredLocalUpdatedAt) throws Exception {
        return syncDocument(userId, "projects", projectId, local);
    }

    /** Pull all remote documents with deterministic document-id pagination. */
    public List<Map<String, Object>> pullCollection(String userId, String collection, int pageSize) throws Exception {
        ensureReady();
        if (userId == null || userId.isEmpty()) throw new IllegalStateException("تسجيل الدخول مطلوب");
        if (collection == null || collection.isEmpty()) throw new IllegalArgumentException("مجموعة غير صالحة");
        int size = Math.max(1, Math.min(pageSize, PAGE_SIZE));
        Query base = db().collection("users").document(userId).collection(collection).orderBy(FieldPath.documentId());
        List<Map<String, Object>> out = new ArrayList<>();
        DocumentSnapshot last = null;
        while (true) {
            Query query = base.limit(size);
            if (last != null) query = query.startAfter(last);
            List<DocumentSnapshot> docs = Tasks.await(query.get(), TIMEOUT_SECONDS, TimeUnit.SECONDS).getDocuments();
            if (docs.isEmpty()) break;
            for (DocumentSnapshot doc : docs) {
                Map<String, Object> m = new HashMap<>();
                if (doc.getData() != null) m.putAll(doc.getData());
                m.put("_id", doc.getId());
                out.add(m);
            }
            last = docs.get(docs.size() - 1);
            if (docs.size() < size) break;
        }
        return out;
    }

    public static long firestoreMillis(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }

    private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0L; }
    private void ensureReady() { if (!initialized) throw new IllegalStateException("Firebase غير مهيأ"); }

    private static void validateCredentials(String email, String password) {
        if (email == null || !email.trim().contains("@")) throw new IllegalArgumentException("البريد الإلكتروني غير صالح");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("كلمة المرور يجب أن تكون 6 أحرف على الأقل");
    }
}
