package com.cpa.objectives;

public class EdgeRequest {
    public int userId;
    public int serverId;
    public int dataId;

    public EdgeRequest(int userId, int serverId, int dataId) {
        this.userId = userId;
        this.serverId = serverId;
        this.dataId = dataId;
    }
}
