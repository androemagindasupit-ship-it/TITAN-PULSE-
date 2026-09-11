package com.titanpulse.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "workspaces", primaryKeys = {"ownerUserId", "id"}, indices = {@Index(value = {"ownerUserId"})})
public class WorkspaceEntity {
    @NonNull public String ownerUserId = "";
    @NonNull public String id = "";
    public String name = "";
    public long updatedAt = 0L;
    public long revision = 0L;
    public String deviceId = "";
    public boolean deleted = false;
}
