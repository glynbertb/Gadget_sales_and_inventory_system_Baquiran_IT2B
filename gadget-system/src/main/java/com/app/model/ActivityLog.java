package com.app.model;

public class ActivityLog {
    private int logId;
    private int userId;
    private String userName; // joined, read-only convenience
    private String action;
    private String description;
    private String dateTime;

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDateTime() { return dateTime; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }
}
