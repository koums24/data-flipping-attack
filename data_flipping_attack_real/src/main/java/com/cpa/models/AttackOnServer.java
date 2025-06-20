package com.cpa.models;


import com.cpa.objectives.Request;
import com.cpa.objectives.Server;
import com.cpa.objectives.User;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cpa.tool.GM14Predict.gm14;
import static com.cpa.tool.ZipfGenerator.*;

public class AttackOnServer {

    public static void main(String[] args) {
        //actually server
        int numUsers = 20;
        //actually cloud
        int numServers = 4;

        int requestPeruser = 100;
        double s = 1.0; // Zipf分布参数
        int numContent = 200; // 请求类型数量

        int time = 100;
        // 创建用户和服务器
        List<User> users = new ArrayList<>();
        List<Server> servers = new ArrayList<>();
        for (int i = 0; i < numUsers; i++) {
            double[] zipfDistribution = generateZipfDistribution(numContent, s);
            users.add(new User(i, zipfDistribution, numContent));
        }
        for (int i = 0; i < numServers; i++) {
            servers.add(new Server(i, numContent));
        }
        //users request content_i
        Set<Integer>[] usersforContent = new HashSet[numContent];
        for (int i = 0; i < numContent; i++) {
            usersforContent[i] = new HashSet<>();
        }

        int currentTime = 0;
        // 为每个用户生成请求序列并分配给服务器

//            设定攻击user
//            users.get(0).setMalicious(true);
//            users.get(1).setMalicious(true);
//            users.get(2).setMalicious(true);
//            users.get(3).setMalicious(true);
        // 为每个用户生成请求序列并分配给服务器
//        for (User user : users) {
//            if (!user.isMalicious()) {
//                List<Integer> requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                }
////                System.out.println("user" + user.getId() + ", requests: " + requests);
//            } else {
//                List<Integer> requests = user.generateAttackRequest(requestPeruser);
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                }
////                System.out.println("user attack" + user.getId() + ", requests: " + requests);
//            }
//        }

        List<List<Integer>> spiltList = splitZipfDistribution(2000, 20, 100, 0, 199);
        int index = 0;
        for (User user : users) {
            if (!user.isMalicious()) {
                List<Integer> requests = spiltList.get(index);
                for (int request : requests) {
                    Server server = servers.get(0);
                    user.sendRequest(server, request, currentTime);
                    usersforContent[request].add(user.getId());
                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
                }
                index ++;
//                System.out.println("user" + user.getId() + ", requests: " + requests);
            }
        }

        //Initialize popularity FLAGP
        double[] contentPopularity = new double[numContent]; // 创建 double 数组
        for (int i = 0; i < contentPopularity.length; i++) {
            contentPopularity[i] = 0.0;
        }
        // 创建存储请求比率 方差 时间间隔标准差的数组
        double[] requestRatios = new double[numContent]; //total each content
        double[] varianceoRI = new double[numContent];
        double[] stdDevs = new double[numContent];

//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));
//            servers.get(0).tamperDistribution();
//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));

        Server server = servers.get(0);
        int totalRequests = server.getTotalRequests();
        // 计算每个content 的 request ratio
        for (int i = 0; i < numContent; i++) {
            requestRatios[i] = Math.round((double) server.getRequestCount(i) / totalRequests * 1000.0) / 1000.0;
        }
        //每个user发送的content i的方差
        List<Integer> contentFromUser = new ArrayList<>();
        for (int i = 0; i < numContent; i++) {
            for (User user : users) {
                int[] requestCountPerUser = user.getRequestCountPerUser();
                contentFromUser.add(requestCountPerUser[i]);
            }
            double contentFromUserMean = contentFromUser.stream().mapToLong(val -> val).average().orElse(0.0);
            for (long c : contentFromUser) {
                varianceoRI[i] = Math.pow(c - contentFromUserMean, 2);
            }
        }
        // 计算并存储每个请求类型的时间间隔的标准差
        for (int i = 0; i < numContent; i++) {
            stdDevs[i] = Math.round((calculateStdDevOfIntervals(server, i) / 1000) * 100) / 100.0;
        }

        int count = 0;
        for (int t = 0; t < time; t++) {
            //检测出的攻击数
            boolean isAttacked = flaDetection(numUsers, numServers, requestPeruser, numContent, contentPopularity, requestRatios, varianceoRI, stdDevs);
            if (isAttacked) {
                count++;
            }
        }
        System.out.println("attack count: " + count);
        System.out.println("ratio: " + (double) count / time);
    }


    public static double calculateStdDevOfIntervals(Server server, int requestType) {
        List<Integer> intervals = new ArrayList<>();
        intervals.addAll(server.getRequestIntervals(requestType));

        if (intervals.size() < 2) {
            return 0.0;
        }

        double mean = intervals.stream().mapToLong(val -> val).average().orElse(0.0);

        double variance = 0.0;
        for (long interval : intervals) {
            variance += Math.pow(interval - mean, 2);
        }
        variance /= (intervals.size() - 1);
        return Math.sqrt(variance);
    }

    public static boolean flaDetection(int numUsers, int numServers, int requestPeruser, int numRequestTypes, double[] contentPopularity, double[] requestRatios, double[] variances, double[] stdDevs) {
        boolean det_response = false;
        double alpha = 0.5;
        double beta = 0.5;
        int N = 0;        //counter of suspicious Interests

        // 创建用户和服务器
        List<User> users = new ArrayList<>();
        List<Server> servers = new ArrayList<>();
        for (int i = 0; i < numUsers; i++) {
            double[] zipfDistribution = generateZipfDistribution(numRequestTypes, 1.0);
            users.add(new User(i, zipfDistribution, numRequestTypes));
        }
        for (int i = 0; i < numServers; i++) {
            servers.add(new Server(i, numRequestTypes));
        }
        //users request content_i
        Set<Integer>[] usersforContent = new HashSet[numRequestTypes];
        for (int i = 0; i < numRequestTypes; i++) {
            usersforContent[i] = new HashSet<>();
        }
        int currentTime = 0;
        // 为每个用户生成请求序列并分配给服务器
//////            Traditional Attack 设定攻击user
//            users.get(0).setMalicious(true);
//            users.get(1).setMalicious(true);
//            users.get(2).setMalicious(true);
//            users.get(3).setMalicious(true);
//            users.get(4).setMalicious(true);
//            users.get(5).setMalicious(true);
//            users.get(6).setMalicious(true);
//            users.get(7).setMalicious(true);
//            users.get(8).setMalicious(true);
//            users.get(9).setMalicious(true);
//            users.get(10).setMalicious(true);
//            users.get(11).setMalicious(true);
//            users.get(12).setMalicious(true);

        //NEW ATTACK
//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));
//            servers.get(0).tamperDistribution();
//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));
        // 为每个用户生成请求序列并分配给服务器
//        for (User user : users) {
//            if (!user.isMalicious()) {
//                List<Integer> requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                }
////                System.out.println("user" + user.getId() + ", requests: " + requests);
//            } else {
//                //FLA attack
////                List<Integer> requests = user.generateAttackRequest(requestPeruser);
////                for (int request : requests) {
////                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
////                    user.sendRequest(server, request, currentTime);
////                    usersforContent[request].add(user.getId());
////                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
////                }
//                //生成正态分布request list
//                List<Integer> requests = user.generateNormalRequest(100, 100, 30, 0, 200);
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                }
////                System.out.println("user attack" + user.getId() + ", requests: " + requests);
//            }
//        }

        //20个distribution合成Zipf分布
        List<List<Integer>> spiltList = splitZipfDistribution(2000, 20, 100, 0, 199);
        int index = 0;
        for (User user : users) {
            if (!user.isMalicious()) {
                List<Integer> requests = spiltList.get(index);
                for (int request : requests) {
                    Server server = servers.get(0);
                    user.sendRequest(server, request, currentTime);
                    usersforContent[request].add(user.getId());
                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
                }
                index ++;
//                System.out.println("user" + user.getId() + ", requests: " + requests);
            }
        }
        //更新和预测每个content的popularity
        for (int i = 0; i < numRequestTypes; i++) {
            //list of content(i)'s delta t
            List<Integer> intervals = servers.get(0).getRequestIntervals(i);
            for (int j = 0; j < intervals.size() - 1; j++) {
                double diff = intervals.get(j) /10 ;
                //GM(1,4)模型预测该content的popularity
                double[] predict = gm14(contentPopularity, requestRatios, variances, stdDevs);
//                    System.out.println(Arrays.toString(predict));
                double CurrPopu = contentPopularity[i] * Math.pow(alpha, diff) + beta;
                double maliciousRatio = Math.abs((CurrPopu - predict[i]) / CurrPopu);
                if (maliciousRatio > 3) {
                    N++;
                } else {
                    contentPopularity[i] = CurrPopu;
                }
            }
        }

        System.out.println("total requests on server : " + servers.get(0).getTotalRequests());
        System.out.println("attack count : " + N);
//        System.out.println("contentPopularity : " + Arrays.toString(contentPopularity));

        if (N > 50) {
            det_response = true;
        }
        return det_response;
    }



}
