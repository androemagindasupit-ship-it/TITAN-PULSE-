package com.titanpulse.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "sync_metadata", primaryKeys = {"ownerUserId", "key"})
public class SyncMetadataEntity {
    @NonNull public String ownerUserId = "";
    @NonNull public String key = "";
    public String value = "";
    public long updatedAt = 0L;
}
