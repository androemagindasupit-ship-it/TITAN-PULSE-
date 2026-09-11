package com.titanpulse.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "projects", primaryKeys = {"ownerUserId", "id"}, indices = {@Index(value = {"ownerUserId"}), @Index(value = {"ownerUserId", "workspaceId"})})
public class ProjectEntity {
    @NonNull public String ownerUserId = "";
    @NonNull public String id = "";
    public String workspaceId = "default";
    public String name = "المشروع الحالي";
    public String prompt = "";
    public String html = "";
    public String css = "";
    public String js = "";
    public long updatedAt = 0L;
    public long revision = 0L;
    public String deviceId = "";
    public boolean deleted = false;
}
