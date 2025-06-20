package com.cpa.objectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    //生成攻击请求
    public List<Integer> generateAttackRequest(Integer num) {
        List<Integer> attackRequestList = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            //todo FLA发送的请求
            attackRequestList.add(99);
        }
        return attackRequestList;
    }

    public static List<Integer> generateNormalRequest(int size, double mean, double stdDev, int lowerBound, int upperBound) {
        Random random = new Random();
        List<Integer> requests = new ArrayList<>();
        List<Integer> contentList = IntStream.range(0, size)
                .mapToObj(i -> {
                    // 生成正态分布的浮点数
                    double value = mean + stdDev * random.nextGaussian();
                    // 将值截断到指定范围
                    int intValue = (int) Math.round(value);
                    intValue = Math.max(lowerBound, Math.min(intValue, upperBound - 1));
                    return intValue;
                })
                .collect(Collectors.toList());
        for (int i = 0; i < contentList.size(); i++) {
            int content = contentList.get(i);  // 生成0到contentRange-1之间的Zipf分布内容
            requests.add(content);
        }
        return requests;
    }

    public static List<Integer> generateSinusoidalSequence(int size, int min, int max) {
        List<Integer> sequence = new ArrayList<>();
        double amplitude = (max - min) / 2.0; // 振幅
        double offset = (max + min) / 2.0; // 中心值
        double frequency = (2 * Math.PI) / size; // 频率

        for (int i = 0; i < size; i++) {
            // 生成正弦波值
            double value = amplitude * Math.sin(frequency * i) + offset;
            sequence.add((int) Math.round(value));
        }

        return sequence;
    }

}
