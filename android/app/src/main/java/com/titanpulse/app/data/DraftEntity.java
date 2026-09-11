package com.titanpulse.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "drafts", primaryKeys = {"ownerUserId", "id"}, indices = {@Index(value = {"ownerUserId"})})
public class DraftEntity {
    @NonNull public String ownerUserId = "";
    @NonNull public String id = "";
    public String workspaceId = "default";
    public String payload = "{}";
    public long updatedAt = 0L;
    public long revision = 0L;
    public String deviceId = "";
    public boolean deleted = false;
}
