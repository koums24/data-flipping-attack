package com.cpa.objectives;

public class Request {
    String user;
    public int content;
    public long timestamp;

    public Request(String user, int content, long timestamp) {
        this.user = user;
        this.content = content;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Request{" +
                "user='" + user + '\'' +
                ", content=" + content +
                ", timestamp=" + timestamp +
                '}';
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getContent() {
        return content;
    }

    public void setContent(int content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}