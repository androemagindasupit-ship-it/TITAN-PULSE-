package com.titanpulse.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ProjectEntity.class, WorkspaceEntity.class, JobEntity.class, SettingsEntity.class, SyncMetadataEntity.class, DraftEntity.class}, version = 7, exportSchema = true)
public abstract class TitanDatabase extends RoomDatabase {
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sync_metadata (key TEXT NOT NULL, value TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(key))");
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts (id TEXT NOT NULL, workspaceId TEXT, payload TEXT, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id))");
        }
    };
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE projects ADD COLUMN ownerUserId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE workspaces ADD COLUMN ownerUserId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE drafts ADD COLUMN ownerUserId TEXT NOT NULL DEFAULT ''");
        }
    };
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_ownerUserId ON projects(ownerUserId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_ownerUserId_workspaceId ON projects(ownerUserId, workspaceId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workspaces_ownerUserId ON workspaces(ownerUserId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_userId ON jobs(userId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_status ON jobs(status)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_jobs_id ON jobs(id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drafts_ownerUserId ON drafts(ownerUserId)");
        }
    };
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS workspaces_new (ownerUserId TEXT NOT NULL, id TEXT NOT NULL, name TEXT, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL, deviceId TEXT, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(ownerUserId,id))");
            db.execSQL("INSERT OR REPLACE INTO workspaces_new(ownerUserId,id,name,updatedAt,revision) SELECT ownerUserId,id,name,updatedAt,revision FROM workspaces");
            db.execSQL("DROP TABLE workspaces");
            db.execSQL("ALTER TABLE workspaces_new RENAME TO workspaces");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workspaces_ownerUserId ON workspaces(ownerUserId)");

            db.execSQL("CREATE TABLE IF NOT EXISTS drafts_new (ownerUserId TEXT NOT NULL, id TEXT NOT NULL, workspaceId TEXT, payload TEXT, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL, deviceId TEXT, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(ownerUserId,id))");
            db.execSQL("INSERT OR REPLACE INTO drafts_new(ownerUserId,id,workspaceId,payload,updatedAt,revision) SELECT ownerUserId,id,workspaceId,payload,updatedAt,revision FROM drafts");
            db.execSQL("DROP TABLE drafts");
            db.execSQL("ALTER TABLE drafts_new RENAME TO drafts");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_drafts_ownerUserId ON drafts(ownerUserId)");
        }
    };
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE projects ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE jobs ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE jobs ADD COLUMN deviceId TEXT");
        }
    };


    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS projects_new (ownerUserId TEXT NOT NULL, id TEXT NOT NULL, workspaceId TEXT, name TEXT, prompt TEXT, html TEXT, css TEXT, js TEXT, updatedAt INTEGER NOT NULL, revision INTEGER NOT NULL, deviceId TEXT, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(ownerUserId,id))");
            db.execSQL("INSERT OR REPLACE INTO projects_new(ownerUserId,id,workspaceId,name,prompt,html,css,js,updatedAt,revision,deviceId,deleted) SELECT ownerUserId,id,workspaceId,name,prompt,html,css,js,updatedAt,revision,deviceId,deleted FROM projects");
            db.execSQL("DROP TABLE projects");
            db.execSQL("ALTER TABLE projects_new RENAME TO projects");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_ownerUserId ON projects(ownerUserId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_ownerUserId_workspaceId ON projects(ownerUserId,workspaceId)");

            db.execSQL("CREATE TABLE IF NOT EXISTS jobs_new (id TEXT NOT NULL, userId TEXT NOT NULL, projectId TEXT, workspaceId TEXT, provider TEXT, model TEXT, prompt TEXT, extra TEXT, mode TEXT, providersJson TEXT, status TEXT, progress INTEGER NOT NULL, retryCount INTEGER NOT NULL, createdAt INTEGER NOT NULL, startedAt INTEGER NOT NULL, completedAt INTEGER NOT NULL, errorCode TEXT, errorMessage TEXT, resultJson TEXT, revision INTEGER NOT NULL, deviceId TEXT, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(userId,id))");
            db.execSQL("INSERT OR REPLACE INTO jobs_new(id,userId,projectId,workspaceId,provider,model,prompt,extra,mode,providersJson,status,progress,retryCount,createdAt,startedAt,completedAt,errorCode,errorMessage,resultJson,revision,deviceId,deleted) SELECT id,userId,projectId,workspaceId,provider,model,prompt,extra,mode,providersJson,status,progress,retryCount,createdAt,startedAt,completedAt,errorCode,errorMessage,resultJson,revision,deviceId,deleted FROM jobs");
            db.execSQL("DROP TABLE jobs");
            db.execSQL("ALTER TABLE jobs_new RENAME TO jobs");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_userId ON jobs(userId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_status ON jobs(status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_userId_projectId ON jobs(userId,projectId)");

            db.execSQL("CREATE TABLE IF NOT EXISTS settings_new (ownerUserId TEXT NOT NULL, key TEXT NOT NULL, value TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(ownerUserId,key))");
            db.execSQL("INSERT OR REPLACE INTO settings_new(ownerUserId,key,value,updatedAt) SELECT '',key,value,updatedAt FROM settings");
            db.execSQL("DROP TABLE settings");
            db.execSQL("ALTER TABLE settings_new RENAME TO settings");

            db.execSQL("CREATE TABLE IF NOT EXISTS sync_metadata_new (ownerUserId TEXT NOT NULL, key TEXT NOT NULL, value TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(ownerUserId,key))");
            db.execSQL("INSERT OR REPLACE INTO sync_metadata_new(ownerUserId,key,value,updatedAt) SELECT '',key,value,updatedAt FROM sync_metadata");
            db.execSQL("DROP TABLE sync_metadata");
            db.execSQL("ALTER TABLE sync_metadata_new RENAME TO sync_metadata");
        }
    };

    public abstract TitanDao dao();
    private static volatile TitanDatabase INSTANCE;

    public static TitanDatabase getInstance(Context context) {
        if (INSTANCE == null) synchronized (TitanDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(), TitanDatabase.class, "titan_pulse.db")
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                        .build();
            }
        }
        return INSTANCE;
    }
}
