package com.titanpulse.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TitanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertProject(ProjectEntity project);
    @Query("SELECT * FROM projects WHERE id = :id AND ownerUserId = :owner LIMIT 1")
    ProjectEntity getProjectForOwner(String id, String owner);
    @Query("SELECT * FROM projects WHERE ownerUserId = :owner AND deleted = 0 ORDER BY updatedAt DESC")
    List<ProjectEntity> getProjectsForOwner(String owner);
    @Query("SELECT * FROM projects WHERE ownerUserId = :owner ORDER BY updatedAt DESC")
    List<ProjectEntity> getProjectsForOwnerIncludingDeleted(String owner);
    @Query("SELECT * FROM projects WHERE ownerUserId = :oldOwner ORDER BY updatedAt DESC")
    List<ProjectEntity> getProjectsForOwnerForMigration(String oldOwner);
    @Query("DELETE FROM projects WHERE ownerUserId = :owner")
    void deleteProjectsForOwner(String owner);
    @Query("DELETE FROM projects WHERE ownerUserId = :owner AND id = :id")
    void deleteProject(String owner, String id);

    @Query("SELECT * FROM projects WHERE ownerUserId = :owner AND workspaceId = :workspace ORDER BY updatedAt DESC")
    List<ProjectEntity> getProjectsForOwnerAndWorkspace(String owner, String workspace);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertWorkspace(WorkspaceEntity workspace);
    @Query("SELECT * FROM workspaces WHERE ownerUserId = :owner AND deleted = 0 ORDER BY updatedAt DESC")
    List<WorkspaceEntity> getWorkspacesForOwner(String owner);
    @Query("SELECT * FROM workspaces WHERE ownerUserId = :owner AND id = :id LIMIT 1")
    WorkspaceEntity getWorkspaceForOwner(String owner, String id);
    @Query("SELECT * FROM workspaces WHERE ownerUserId = :owner ORDER BY updatedAt DESC")
    List<WorkspaceEntity> getWorkspacesForOwnerIncludingDeleted(String owner);
    @Query("SELECT * FROM workspaces WHERE ownerUserId = :oldOwner ORDER BY updatedAt DESC")
    List<WorkspaceEntity> getWorkspacesForOwnerForMigration(String oldOwner);
    @Query("DELETE FROM workspaces WHERE ownerUserId = :owner")
    void deleteWorkspacesForOwner(String owner);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertJob(JobEntity job);
    @Query("SELECT * FROM jobs WHERE id = :id AND userId = :owner AND deleted = 0 LIMIT 1")
    JobEntity getJobForUser(String id, String owner);
    @Query("SELECT * FROM jobs WHERE userId = :owner AND id = :id LIMIT 1")
    JobEntity getJobByIdForWorker(String owner, String id);
    @Query("SELECT * FROM jobs WHERE userId = :owner AND deleted = 0 ORDER BY createdAt DESC")
    List<JobEntity> getJobsForUser(String owner);
    @Query("SELECT * FROM jobs WHERE userId = :owner ORDER BY createdAt DESC")
    List<JobEntity> getJobsForUserIncludingDeleted(String owner);
    @Query("SELECT * FROM jobs WHERE userId = :owner AND id = :id LIMIT 1")
    JobEntity getJobForUserIncludingDeleted(String owner, String id);
    @Query("SELECT * FROM jobs WHERE userId = :owner AND deleted = 0 AND status IN ('QUEUED','RUNNING','RETRYING','WAITING_NETWORK') ORDER BY createdAt DESC")
    List<JobEntity> getActiveJobsForUser(String owner);
    @Query("SELECT * FROM jobs WHERE userId = :oldOwner ORDER BY createdAt DESC")
    List<JobEntity> getJobsForUserForMigration(String oldOwner);
    @Query("DELETE FROM jobs WHERE userId = :owner")
    void deleteJobsForUser(String owner);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSetting(SettingsEntity setting);
    @Query("SELECT * FROM settings WHERE ownerUserId = :owner AND key = :key LIMIT 1")
    SettingsEntity getSetting(String owner, String key);
    @Query("SELECT * FROM settings WHERE ownerUserId = :owner ORDER BY updatedAt DESC")
    java.util.List<SettingsEntity> getSettingsForOwner(String owner);
    @Query("SELECT * FROM settings ORDER BY updatedAt DESC")
    java.util.List<SettingsEntity> getAllSettings();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSyncMetadata(SyncMetadataEntity item);
    @Query("SELECT * FROM sync_metadata WHERE ownerUserId = :owner AND key = :key LIMIT 1")
    SyncMetadataEntity getSyncMetadata(String owner, String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDraft(DraftEntity item);
    @Query("SELECT * FROM drafts WHERE ownerUserId = :owner AND deleted = 0 ORDER BY updatedAt DESC")
    List<DraftEntity> getDraftsForOwner(String owner);
    @Query("SELECT * FROM drafts WHERE ownerUserId = :owner AND id = :id LIMIT 1")
    DraftEntity getDraftForOwner(String owner, String id);
    @Query("SELECT * FROM drafts WHERE ownerUserId = :owner ORDER BY updatedAt DESC")
    List<DraftEntity> getDraftsForOwnerIncludingDeleted(String owner);
    @Query("SELECT * FROM drafts WHERE ownerUserId = :oldOwner ORDER BY updatedAt DESC")
    List<DraftEntity> getDraftsForOwnerForMigration(String oldOwner);
    @Query("DELETE FROM drafts WHERE ownerUserId = :owner")
    void deleteDraftsForOwner(String owner);
}
