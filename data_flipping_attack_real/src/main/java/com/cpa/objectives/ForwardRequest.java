package com.cpa.objectives;

public class ForwardRequest {
    public int edgeServerId;
    public EdgeRequest originalRequest;
    public long timestamp;

    public ForwardRequest(int edgeServerId, EdgeRequest originalRequest, long timestamp) {
        this.edgeServerId = edgeServerId;
        this.originalRequest = originalRequest;
        this.timestamp = timestamp;
    }
}
