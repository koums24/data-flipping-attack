package com.cpa.objectives;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private int id;
    private int totalRequests;
    private int[] requestCounts;
    private List<Integer> requestListPerServer;
    private double[] requestRatios;
    private List<Integer> requestFromUser;
    private Map<Integer, List<Integer>> requestTimestamps;


    public void setRequestListPerServer(List<Integer> requestListPerServer) {
        this.requestListPerServer = requestListPerServer;
    }

    public Server(int id, int numRequestTypes) {
        this.id = id;
        this.totalRequests = 0;
        this.requestCounts = new int[numRequestTypes];
        this.requestRatios = new double[numRequestTypes];
        this.requestListPerServer = new ArrayList<>();
        this.requestTimestamps = new HashMap<>();
        this.requestFromUser = new ArrayList<>();
        for (int i = 0; i < numRequestTypes; i++) {
            requestTimestamps.put(i, new ArrayList<>());
        }
    }

    public List<Integer> getRequestTimestamp(int requestId) {
        return requestTimestamps.get(requestId);
    }

    public Map<Integer, List<Integer>> getRequestTimestamp(){
        return requestTimestamps;
    }

    public void receiveRequest(int requestId, int timestamp, int userID) {
        requestListPerServer.add(requestId);
        requestFromUser.add(userID);
        requestCounts[requestId] ++;
        this.totalRequests++;
        requestTimestamps.get(requestId).add(timestamp);
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public int getId() {
        return id;
    }

    public int getRequestCount(int requestType) {
        return requestCounts[requestType];
    }

    public int[] getRequestCount(){
        return requestCounts;
    }


    public List<Integer> getRequestListPerServer() {
        return requestListPerServer;
    }

    public double getRequestRatio(int requestType) {
        return requestRatios[requestType];
    }

    public void calculateRequestRatios() {
        for (int i = 0; i < requestCounts.length; i++) {
            if (totalRequests > 0) {
                requestRatios[i] = (double) requestCounts[i] / totalRequests;
            }
        }
    }

    public List<Integer> getRequestIntervals(int requestType) {
        List<Integer> timestamps = requestTimestamps.get(requestType);
        List<Integer> intervals = new ArrayList<>();
        if (timestamps.size() < 2) {
            return intervals;
        }

        for (int i = 1; i < timestamps.size(); i++) {
            intervals.add(timestamps.get(i) - timestamps.get(i - 1));
        }
        return intervals;
    }

    public void tamperDistribution() {
        //1 swap
        SwapByFrequency(this.requestListPerServer);

        changeRequestCount();

//        System.out.println("distribution is tampered on sever " + this.id);

    }

    public void setRequestTimestamps(Map<Integer, List<Integer>> requestTimestamps) {
        this.requestTimestamps = requestTimestamps;
    }

    public List<Integer> SwapByFrequency(List<Integer> requestListPerServer) {
        // count
        Map<Integer, Integer> frequencyMap = getFrequency(requestListPerServer);

        // order
        List<Map.Entry<Integer, Integer>> sortedEntries = new ArrayList<>(frequencyMap.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        // swap
        int size = sortedEntries.size();
        for (int i = 0; i < size / 2; i++) {
            int maxKey = sortedEntries.get(i).getKey();
            int minKey = sortedEntries.get(size - 1 - i).getKey();

            for (int j = 0; j < requestListPerServer.size(); j++) {
                if (requestListPerServer.get(j) == maxKey) {
                    requestListPerServer.set(j, minKey);
                } else if (requestListPerServer.get(j) == minKey) {
                    requestListPerServer.set(j, maxKey);
                }
            }
        }

        return requestListPerServer;
    }

    //request count
    public void changeRequestCount() {
        for (int i = 0; i < this.requestCounts.length; i++) {
            this.requestCounts[i] = 0;
        }
        for (Integer num : requestListPerServer) {
            this.requestCounts[num]++;
        }
    }

    public static Map<Integer, Integer> getFrequency(List<Integer> requestListPerServer) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (Integer num : requestListPerServer) {
            frequencyMap.put(num, frequencyMap.getOrDefault(num, 0) + 1);
        }
        return frequencyMap;
    }


}