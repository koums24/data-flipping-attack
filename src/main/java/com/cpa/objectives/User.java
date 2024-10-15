package com.cpa.objectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

//for traditional Detection
public class User {
    private int id;
    private double[] zipfDistribution;
    private int[] requestCountPerUser;
    private List<Integer> requestListPerUser = new ArrayList<>();
    private boolean malicious;

    public void setId(int id) {
        this.id = id;
    }

    public void setZipfDistribution(double[] zipfDistribution) {
        this.zipfDistribution = zipfDistribution;
    }

    public void setRequestCountPerUser(int[] requestCountPerUser) {
        this.requestCountPerUser = requestCountPerUser;
    }

    public void setRequestListPerUser(List<Integer> requestListPerUser) {
        this.requestListPerUser = requestListPerUser;
    }

    public void setMalicious(boolean malicious) {
        this.malicious = malicious;
    }

    public User(int id, double[] zipfDistribution, int numRequestTypes) {
        this.id = id;
        this.zipfDistribution = zipfDistribution;
        this.requestCountPerUser = new int[numRequestTypes];
    }

    public boolean isMalicious() {
        return malicious;
    }

    public int getId() {
        return id;
    }

    public void sendRequest(Server server, int requestId, int timestamp) {
        requestListPerUser.add(requestId);
        requestCountPerUser[requestId]++;
        server.receiveRequest(requestId, timestamp,id);
    }

    public List<Integer> getRequestListPerUser() {
        return requestListPerUser;
    }

    public double[] getZipfDistribution() {
        return zipfDistribution;
    }

    public int[] getRequestCountPerUser() {
        return requestCountPerUser;
    }

    //generate attack request
    public List<Integer> generateAttackRequest(Integer num) {
        List<Integer> attackRequestList = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            //FLA
            attackRequestList.add(99);
        }
        return attackRequestList;
    }

    public static List<Integer> generateNormalRequest(int size, double mean, double stdDev, int lowerBound, int upperBound) {
        Random random = new Random();
        List<Integer> requests = new ArrayList<>();
        List<Integer> contentList = IntStream.range(0, size)
                .mapToObj(i -> {
                    // generate Gaussian float
                    double value = mean + stdDev * random.nextGaussian();
                    int intValue = (int) Math.round(value);
                    intValue = Math.max(lowerBound, Math.min(intValue, upperBound - 1));
                    return intValue;
                })
                .collect(Collectors.toList());
        for (int i = 0; i < contentList.size(); i++) {
            int content = contentList.get(i);  // generate 0~contentRange-1 Zipf
            requests.add(content);
        }
        return requests;
    }


}
