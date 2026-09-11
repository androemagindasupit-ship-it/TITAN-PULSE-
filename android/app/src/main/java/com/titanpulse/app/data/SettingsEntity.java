package com.titanpulse.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "settings", primaryKeys = {"ownerUserId", "key"})
public class SettingsEntity {
    @NonNull public String ownerUserId = "";
    @NonNull public String key = "";
    public String value = "";
    public long updatedAt = 0L;
}
