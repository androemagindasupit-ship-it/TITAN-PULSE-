package com.titanpulse.app.sync;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.titanpulse.app.data.DraftEntity;
import com.titanpulse.app.data.JobEntity;
import com.titanpulse.app.data.ProjectEntity;
import com.titanpulse.app.data.TitanDatabase;
import com.titanpulse.app.data.WorkspaceEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Full owner-scoped sync for workspaces, projects, drafts and jobs. Secrets never enter Firestore. */
public final class BackgroundSyncWorker extends Worker {
    private final TitanDatabase db;
    public BackgroundSyncWorker(@NonNull android.content.Context context, @NonNull WorkerParameters params) { super(context, params); db = TitanDatabase.getInstance(context); }

    @NonNull @Override public Result doWork() {
        try {
            String uid = FirebaseSyncService.get(getApplicationContext()).userId();
            if (uid.isEmpty()) return Result.success();
            syncNow(getApplicationContext(), uid);
            return Result.success();
        } catch (Exception e) {
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }

    public static int syncNow(android.content.Context context, String uid) throws Exception {
        TitanDatabase db = TitanDatabase.getInstance(context);
        FirebaseSyncService fs = FirebaseSyncService.get(context);
        if (uid == null || uid.isEmpty()) throw new IllegalStateException("تسجيل الدخول مطلوب");
        int synced = 0;

        for (WorkspaceEntity w : db.dao().getWorkspacesForOwnerIncludingDeleted(uid)) {
            Map<String,Object> data = new HashMap<>();
            data.put("name", w.name); data.put("revision", w.revision); data.put("deviceId", w.deviceId); data.put("deleted", w.deleted); data.put("updatedAtClient", w.updatedAt);
            Map<String,Object> canonical = fs.syncDocument(uid, "workspaces", w.id, data);
            applyWorkspace(db, w, uid, canonical);
            synced++;
        }

        for (ProjectEntity p : db.dao().getProjectsForOwnerIncludingDeleted(uid)) {
            Map<String,Object> data = new HashMap<>();
            data.put("workspaceId", p.workspaceId); data.put("name", p.name); data.put("prompt", p.prompt); data.put("html", p.html); data.put("css", p.css); data.put("js", p.js);
            data.put("revision", p.revision); data.put("deviceId", p.deviceId); data.put("deleted", p.deleted); data.put("updatedAtClient", p.updatedAt);
            Map<String,Object> canonical = fs.syncDocument(uid, "projects", p.id, data);
            applyProject(db, p, uid, canonical);
            synced++;
        }

        for (DraftEntity d : db.dao().getDraftsForOwnerIncludingDeleted(uid)) {
            Map<String,Object> data = new HashMap<>();
            data.put("workspaceId", d.workspaceId); data.put("payload", d.payload); data.put("revision", d.revision); data.put("deviceId", d.deviceId); data.put("deleted", d.deleted); data.put("updatedAtClient", d.updatedAt);
            Map<String,Object> canonical = fs.syncDocument(uid, "drafts", d.id, data);
            applyDraft(db, d, uid, canonical);
            synced++;
        }

        for (JobEntity j : db.dao().getJobsForUserIncludingDeleted(uid)) {
            Map<String,Object> data = new HashMap<>();
            data.put("projectId", j.projectId); data.put("workspaceId", j.workspaceId); data.put("provider", j.provider); data.put("model", j.model); data.put("prompt", j.prompt); data.put("extra", j.extra);
            data.put("mode", j.mode); data.put("providersJson", j.providersJson); data.put("status", j.status); data.put("progress", j.progress); data.put("retryCount", j.retryCount);
            data.put("createdAt", j.createdAt); data.put("startedAt", j.startedAt); data.put("completedAt", j.completedAt); data.put("errorCode", j.errorCode); data.put("errorMessage", j.errorMessage);
            if (j.resultJson != null && j.resultJson.length() <= 700_000) data.put("resultJson", j.resultJson);
            data.put("revision", j.revision); data.put("deviceId", j.deviceId); data.put("deleted", j.deleted); data.put("updatedAtClient", j.completedAt > 0 ? j.completedAt : j.createdAt);
            Map<String,Object> canonical = fs.syncDocument(uid, "jobs", j.id, data);
            applyJob(db, j, uid, canonical);
            synced++;
        }

        // Pull remote-only records so a second device receives projects/workspaces/jobs it did not create locally.
        for (Map<String,Object> r : fs.pullCollection(uid, "workspaces", 500)) upsertRemoteWorkspace(db, uid, r);
        for (Map<String,Object> r : fs.pullCollection(uid, "projects", 500)) upsertRemoteProject(db, uid, r);
        for (Map<String,Object> r : fs.pullCollection(uid, "drafts", 500)) upsertRemoteDraft(db, uid, r);
        for (Map<String,Object> r : fs.pullCollection(uid, "jobs", 500)) upsertRemoteJob(db, uid, r);
        return synced;
    }

    private static void applyWorkspace(TitanDatabase db, WorkspaceEntity w, String uid, Map<String,Object> r) {
        if (r == null) return; if (r.get("name") != null) w.name = String.valueOf(r.get("name"));
        w.revision = Math.max(w.revision, number(r.get("revision"))); long ts = FirebaseSyncService.firestoreMillis(r.get("serverUpdatedAt")); if (ts > 0) w.updatedAt = ts; if (r.get("deviceId") != null) w.deviceId = String.valueOf(r.get("deviceId"));
        if (r.get("deleted") instanceof Boolean) w.deleted = (Boolean) r.get("deleted"); w.ownerUserId = uid; db.dao().upsertWorkspace(w);
    }
    private static void applyProject(TitanDatabase db, ProjectEntity p, String uid, Map<String,Object> r) {
        if (r == null) return; if (r.get("name") != null) p.name = String.valueOf(r.get("name")); if (r.get("workspaceId") != null) p.workspaceId = String.valueOf(r.get("workspaceId"));
        if (r.get("prompt") != null) p.prompt = String.valueOf(r.get("prompt")); if (r.get("html") != null) p.html = String.valueOf(r.get("html")); if (r.get("css") != null) p.css = String.valueOf(r.get("css")); if (r.get("js") != null) p.js = String.valueOf(r.get("js"));
        p.revision = Math.max(p.revision, number(r.get("revision"))); long ts = FirebaseSyncService.firestoreMillis(r.get("serverUpdatedAt")); if (ts > 0) p.updatedAt = ts; if (r.get("deviceId") != null) p.deviceId = String.valueOf(r.get("deviceId"));
        if (r.get("deleted") instanceof Boolean) p.deleted = (Boolean) r.get("deleted"); p.ownerUserId = uid; db.dao().upsertProject(p);
    }
    private static void applyDraft(TitanDatabase db, DraftEntity d, String uid, Map<String,Object> r) {
        if (r == null) return; if (r.get("workspaceId") != null) d.workspaceId = String.valueOf(r.get("workspaceId")); if (r.get("payload") != null) d.payload = String.valueOf(r.get("payload"));
        d.revision = Math.max(d.revision, number(r.get("revision"))); long ts = FirebaseSyncService.firestoreMillis(r.get("serverUpdatedAt")); if (ts > 0) d.updatedAt = ts; if (r.get("deviceId") != null) d.deviceId = String.valueOf(r.get("deviceId"));
        if (r.get("deleted") instanceof Boolean) d.deleted = (Boolean) r.get("deleted"); d.ownerUserId = uid; db.dao().upsertDraft(d);
    }
    private static void applyJob(TitanDatabase db, JobEntity j, String uid, Map<String,Object> r) {
        if (r == null) return; if (r.get("status") != null) j.status = String.valueOf(r.get("status")); if (r.get("progress") != null) j.progress = (int)number(r.get("progress"));
        if (r.get("errorCode") != null) j.errorCode = String.valueOf(r.get("errorCode")); if (r.get("errorMessage") != null) j.errorMessage = String.valueOf(r.get("errorMessage"));
        if (r.get("resultJson") != null) j.resultJson = String.valueOf(r.get("resultJson")); if (r.get("completedAt") != null) j.completedAt = number(r.get("completedAt"));
        j.revision = Math.max(j.revision, number(r.get("revision"))); if (r.get("deviceId") != null) j.deviceId = String.valueOf(r.get("deviceId")); if (r.get("deleted") instanceof Boolean) j.deleted = (Boolean)r.get("deleted"); j.userId = uid; db.dao().upsertJob(j);
    }

    private static boolean remoteWins(long localRevision, String localDevice, long remoteRevision, String remoteDevice) {
        return remoteRevision > localRevision || (remoteRevision == localRevision && remoteDevice.compareTo(localDevice == null ? "" : localDevice) > 0);
    }
    private static void upsertRemoteWorkspace(TitanDatabase db, String uid, Map<String,Object> r) {
        String id=String.valueOf(r.getOrDefault("_id","")); if(id.isEmpty())return; WorkspaceEntity w=db.dao().getWorkspaceForOwner(uid,id); long rr=number(r.get("revision")); String rd=String.valueOf(r.getOrDefault("deviceId",""));
        if(w!=null && !remoteWins(w.revision,w.deviceId,rr,rd))return; if(w==null)w=new WorkspaceEntity(); w.ownerUserId=uid;w.id=id;w.name=String.valueOf(r.getOrDefault("name",""));w.revision=rr;w.updatedAt=FirebaseSyncService.firestoreMillis(r.get("serverUpdatedAt"));w.deviceId=rd;w.deleted=Boolean.TRUE.equals(r.get("deleted"));db.dao().upsertWorkspace(w);
    }
    private static void upsertRemoteProject(TitanDatabase db, String uid, Map<String,Object> r) {
        String id=String.valueOf(r.getOrDefault("_id","")); if(id.isEmpty())return; ProjectEntity p=db.dao().getProjectForOwner(id,uid); long rr=number(r.get("revision")); String rd=String.valueOf(r.getOrDefault("deviceId",""));
        if(p!=null && !remoteWins(p.revision,p.deviceId,rr,rd))return; if(p==null)p=new ProjectEntity(); p.id=id;p.ownerUserId=uid;p.workspaceId=String.valueOf(r.getOrDefault("workspaceId","default"));p.name=String.valueOf(r.getOrDefault("name","المشروع الحالي"));p.prompt=String.valueOf(r.getOrDefault("prompt",""));p.html=String.valueOf(r.getOrDefault("html",""));p.css=String.valueOf(r.getOrDefault("css",""));p.js=String.valueOf(r.getOrDefault("js",""));p.revision=rr;p.updatedAt=FirebaseSyncService.firestoreMillis(r.get("serverUpdatedAt"));p.deviceId=rd;p.deleted=Boolean.TRUE.equals(r.get("deleted"));db.dao().upsertProject(p);
    }
    private static void upsertRemoteDraft(TitanDatabase db, String uid, Map<String,Object> r) {
        String id=String.valueOf(r.getOrDefault("_id","")); if(id.isEmpty())return; DraftEntity d=db.dao().getDraftForOwner(uid,id); long rr=number(r.get("revision")); String rd=String.valueOf(r.getOrDefault("deviceId",""));
        if(d!=null && !remoteWins(d.revision,d.deviceId,rr,rd))return; if(d==null)d=new DraftEntity(); d.ownerUserId=uid;d.id=id;d.workspaceId=String.valueOf(r.getOrDefault("workspaceId","default"));d.payload=String.valueOf(r.getOrDefault("payload","{}"));d.revision=rr;d.updatedAt=FirebaseSyncService.firestoreMillis(r.get("serverUpdatedAt"));d.deviceId=rd;d.deleted=Boolean.TRUE.equals(r.get("deleted"));db.dao().upsertDraft(d);
    }
    private static void upsertRemoteJob(TitanDatabase db, String uid, Map<String,Object> r) {
        String id=String.valueOf(r.getOrDefault("_id","")); if(id.isEmpty())return; JobEntity j=db.dao().getJobForUserIncludingDeleted(uid,id); long rr=number(r.get("revision")); String rd=String.valueOf(r.getOrDefault("deviceId",""));
        if(j!=null && !remoteWins(j.revision,j.deviceId,rr,rd))return; if(j==null)j=new JobEntity(); j.id=id;j.userId=uid;j.projectId=String.valueOf(r.getOrDefault("projectId",""));j.workspaceId=String.valueOf(r.getOrDefault("workspaceId","default"));j.provider=String.valueOf(r.getOrDefault("provider",""));j.model=String.valueOf(r.getOrDefault("model",""));j.prompt=String.valueOf(r.getOrDefault("prompt",""));j.extra=String.valueOf(r.getOrDefault("extra",""));j.mode=String.valueOf(r.getOrDefault("mode","web"));j.providersJson=String.valueOf(r.getOrDefault("providersJson","[]"));j.status=String.valueOf(r.getOrDefault("status","QUEUED"));j.progress=(int)number(r.get("progress"));j.retryCount=(int)number(r.get("retryCount"));j.createdAt=number(r.get("createdAt"));j.startedAt=number(r.get("startedAt"));j.completedAt=number(r.get("completedAt"));j.errorCode=String.valueOf(r.getOrDefault("errorCode",""));j.errorMessage=String.valueOf(r.getOrDefault("errorMessage",""));j.resultJson=String.valueOf(r.getOrDefault("resultJson",""));j.revision=rr;j.deviceId=rd;j.deleted=Boolean.TRUE.equals(r.get("deleted"));db.dao().upsertJob(j);
    }
    private static long number(Object v){return v instanceof Number ? ((Number)v).longValue() : 0L;}

    public static void enqueueNow(android.content.Context c) {
        Constraints cs = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest r = new OneTimeWorkRequest.Builder(BackgroundSyncWorker.class).setConstraints(cs).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniqueWork("titan-pulse-sync-now", androidx.work.ExistingWorkPolicy.KEEP, r);
    }

    public static void enqueuePeriodic(android.content.Context c) {
        Constraints cs = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(BackgroundSyncWorker.class, 6, TimeUnit.HOURS).setConstraints(cs).build();
        WorkManager.getInstance(c.getApplicationContext()).enqueueUniquePeriodicWork("titan-pulse-sync-periodic", androidx.work.ExistingPeriodicWorkPolicy.KEEP, r);
    }
}
