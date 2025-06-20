package com.cpa.tool;

import java.util.*;

public class Zipftest {

    public static List<Integer> generateZipfListForUser(int userId, int size, int min, int max) {
        List<Integer> sequence = new ArrayList<>();
        Random userRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
        double exponent = 1.1; // 固定的Zipf指数

        double sum = 0;
        for (int i = 1; i <= size; i++) {
            sum += (1.0 / Math.pow(i, exponent));
        }

        for (int i = 1; i <= size; i++) {
            double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  // 归一化
            int value = (int) Math.round(min + (zipfValue * (max - min)));
            sequence.add(value);
        }

        // 对序列进行偏移，使得每个用户的序列中最频繁和最不频繁的值不同
        int offset = userRandom.nextInt(max - min + 1); // 基于 userId 的随机偏移量
        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
            int mappedValue = value + offset;
            mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在 min 和 max 之间
            mappedSequence.add(mappedValue);
        }

        // 打乱序列
        Collections.shuffle(mappedSequence, userRandom);

        return mappedSequence;
    }

    public static void main(String[] args) {
        int numUsers = 5;
        int size = 10; // 每个用户的请求序列长度
        int min = 1;
        int max = 10;

        List<List<Integer>> allUsersRequests = new ArrayList<>();
        for (int userId = 1; userId <= numUsers; userId++) {
            List<Integer> userRequests = generateZipfListForUser(userId, size, min, max);
            allUsersRequests.add(userRequests);
            System.out.println("User " + userId + ": " + userRequests);
        }

        // 合并所有用户的请求序列
        List<Integer> combinedRequests = new ArrayList<>();
        for (List<Integer> userRequests : allUsersRequests) {
            combinedRequests.addAll(userRequests);
        }

        System.out.println("Combined Requests: " + combinedRequests);
    }
}
