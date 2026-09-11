package com.titanpulse.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "jobs", primaryKeys = {"userId", "id"}, indices = {@Index(value = {"userId"}), @Index(value = {"status"}), @Index(value = {"userId", "projectId"})})
public class JobEntity {
    @NonNull public String id = "";
    @NonNull public String userId = "";
    public String projectId = "";
    public String workspaceId = "default";
    public String provider = "";
    public String model = "";
    public String prompt = "";
    public String extra = "";
    public String mode = "web";
    public String providersJson = "[]";
    public String status = "QUEUED";
    public int progress = 0;
    public int retryCount = 0;
    public long createdAt = 0L;
    public long startedAt = 0L;
    public long completedAt = 0L;
    public String errorCode = "";
    public String errorMessage = "";
    public String resultJson = "";
    public long revision = 0L;
    public String deviceId = "";
    public boolean deleted = false;
}
