package com.cpa.models;


import com.cpa.objectives.Server;
import com.cpa.objectives.User;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.cpa.tool.GM14Predict.gm14;
import static com.cpa.tool.ZipfGenerator.*;

public class FLAGP {

    public static void main(String[] args) {
        int numUsers = 20;
        int numServers = 4;
        int requestPeruser = 100;
        double s = 1.0; // Zipf分布参数
        int numRequestTypes = 200; // 请求类型数量

        int time = 1000;
        // 创建用户和服务器
        List<User> users = new ArrayList<>();
        List<Server> servers = new ArrayList<>();
        for (int i = 0; i < numUsers; i++) {
            double[] zipfDistribution = generateZipfDistribution(numRequestTypes, s);
//            double[] zipfDistribution = generateZipfDistributionDiff(numRequestTypes, s);
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
        for (User user : users) {
            if (!user.isMalicious()) {
                List<Integer> requests = new ArrayList<>();
                requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                //hybrid
//                if(user.getId()%3 == 0){
//                    requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
//                }else{
//                    requests = user.generateNormalRequest(requestPeruser, 50, 20, 0, numRequestTypes);
//                }
                for (int request : requests) {
                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                    user.sendRequest(server, request, currentTime);
                    usersforContent[request].add(user.getId());
                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
                }
//                System.out.println("user" + user.getId() + ", requests: " + requests);
            } else {
                //FLA
//                List<Integer> requests = user.generateAttackRequest(requestPeruser);
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                }
                //flipping attack
                List<Integer> requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                tamperDistribution(numRequestTypes,requests);
                for (int request : requests) {
                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                    user.sendRequest(server, request, currentTime);
                    usersforContent[request].add(user.getId());
                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
                }
//                System.out.println("user attack" + user.getId() + ", requests: " + requests);
            }
        }
        //Initialize popularity FLAGP
        double[] contentPopularity = new double[numRequestTypes]; // 创建 double 数组
        for (int i = 0; i < contentPopularity.length; i++) {
            contentPopularity[i] = 0.5;
        }
        // 创建存储请求比率 方差 时间间隔标准差的数组
        double[] requestRatios = new double[numRequestTypes]; //total each content
        double[] varianceoRI = new double[numRequestTypes];
        double[] stdDevs = new double[numRequestTypes];

//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));
//            servers.get(0).tamperDistribution();
//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));

        // 计算每个服务器的总请求  累加每个请求类型的计数
        int totalCount = 0;
        int[] totalCountsPerContent = new int[numRequestTypes];
        for (Server server : servers) {
             totalCount += server.getTotalRequests();
            for (int i = 0; i < numRequestTypes; i++) {
                totalCountsPerContent[i] += server.getRequestCount(i);
            }
        }
//        Server server = servers.get(0);
        // 计算每个content 的 request ratio
        for (int i = 0; i < numRequestTypes; i++) {
            requestRatios[i] = Math.round((double) totalCountsPerContent[i] / totalCount * 100.0) / 100.0;
        }
        //每个user发送的content i的方差
        List<Integer> contentFromUser = new ArrayList<>();
        for (int i = 0; i < numRequestTypes; i++) {
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
        for (int i = 0; i < numRequestTypes; i++) {
            stdDevs[i] = Math.round((calculateStdDevOfIntervals(servers, i) / 1000) * 100) / 100.0;
        }

        int count = 0;
        for (int t = 0; t < time; t++) {
            //检测出的攻击数
            boolean isAttacked = flaDetection(numUsers, numServers, requestPeruser, numRequestTypes, contentPopularity, requestRatios, varianceoRI, stdDevs);
            if (isAttacked) {
                count++;
            }
        }
        System.out.println("attack count: " + count);
        System.out.println("ratio: " + (double) count / time);
    }


//    public static double calculateStdDevOfIntervals(Server server, int requestType) {
//        List<Integer> intervals = new ArrayList<>();
//        intervals.addAll(server.getRequestIntervals(requestType));
//
//        if (intervals.size() < 2) {
//            return 0.0;
//        }
//
//        double mean = intervals.stream().mapToLong(val -> val).average().orElse(0.0);
//
//        double variance = 0.0;
//        for (long interval : intervals) {
//            variance += Math.pow(interval - mean, 2);
//        }
//        variance /= (intervals.size() - 1);
//        return Math.sqrt(variance);
//    }

    public static double calculateStdDevOfIntervals(List<Server> servers, int requestType) {
        List<Integer> intervals = new ArrayList<>();
        for (Server server : servers) {
            intervals.addAll(server.getRequestIntervals(requestType));
        }

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
//            double[] zipfDistribution = generateZipfDistributionDiff(numRequestTypes, 1.0);
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

//            Traditional Attack 设定攻击user
            users.get(0).setMalicious(true);
            users.get(1).setMalicious(true);
            users.get(2).setMalicious(true);
            users.get(3).setMalicious(true);

        //NEW ATTACK
//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));
//            servers.get(0).tamperDistribution();
//            System.out.println(ArrayUtils.toString(servers.get(0).getRequestCount()));
        // 为每个用户生成请求序列并分配给服务器
        for (User user : users) {
            if (!user.isMalicious()) {
                List<Integer> requests = new ArrayList<>();
                requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                //hybrid
//                if(user.getId()%3 == 0){
//                    requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
//                }else{
//                    requests = user.generateNormalRequest(requestPeruser, 50, 20, 0, numRequestTypes);
//                }

                for (int request : requests) {
                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                    user.sendRequest(server, request, currentTime);
                    usersforContent[request].add(user.getId());
                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
                }
//                System.out.println("user" + user.getId() + ", requests: " + requests);
            } else {
                //FLA
//                List<Integer> requests = user.generateAttackRequest(requestPeruser);
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                }
                //flipping attack
                List<Integer> requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                tamperDistribution(numRequestTypes,requests);
                for (int request : requests) {
                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                    user.sendRequest(server, request, currentTime);
                    usersforContent[request].add(user.getId());
                    currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
                }
//                System.out.println("user attack" + user.getId() + ", requests: " + requests);
            }
        }

        //更新和预测每个content的popularity
        for (int i = 0; i < numRequestTypes; i++) {
            //list of content(i)'s delta t
            List<Integer> intervals = servers.get(1).getRequestIntervals(i);
            for (int j = 0; j < intervals.size() - 1; j++) {
                double diff = intervals.get(j) ;
                //GM(1,4)模型预测该content的popularity
                double[] predict = gm14(contentPopularity, requestRatios, variances, stdDevs);
//                    System.out.println(Arrays.toString(predict));
                double CurrPopu = contentPopularity[i] * Math.pow(alpha, diff) + beta;
                double maliciousRatio = Math.abs((CurrPopu - predict[i]) / CurrPopu);
                if (maliciousRatio > 0.6) {
                    N++;
                } else {
                    contentPopularity[i] = CurrPopu;
                }
            }
        }

//        System.out.println("total requests on server : " + servers.get(0).getTotalRequests());
//        System.out.println("attack count : " + N);
//        System.out.println("contentPopularity : " + Arrays.toString(contentPopularity));

        if (N > 50) {
            det_response = true;
        }
        return det_response;
    }


    public static List<Integer> tamperDistribution(int dataNumber, List<Integer> requestList) {
        // 1. 统计每个元素的出现次数，包括未出现的数据（视为0次）
        Map<Integer, Integer> frequencyMap = getFrequencyMap(dataNumber, requestList);

        // 2. 根据出现次数对元素排序
        List<Integer> sortedByFrequency = new ArrayList<>(frequencyMap.keySet());
        sortedByFrequency.sort(Comparator.comparingInt(frequencyMap::get));

        // 3. 准备交换：出现次数多的列表和出现次数少的列表
        int size = sortedByFrequency.size();
        List<Integer> mostFrequent = sortedByFrequency.subList(size / 2, size); // 出现次数多的
        List<Integer> leastFrequent = sortedByFrequency.subList(0, size / 2);   // 出现次数少的

        // 4. 交换元素
        Map<Integer, Integer> swapMap = new HashMap<>();
        for (int i = 0; i < leastFrequent.size(); i++) {
            int mostFreq = mostFrequent.get(mostFrequent.size() - 1 - i);
            int leastFreq = leastFrequent.get(i);
            swapMap.put(leastFreq, mostFreq);
            swapMap.put(mostFreq, leastFreq);
        }

        // 5. 构建修改后的requestOnServer列表
        List<Integer> modifiedRequestList = new ArrayList<>();
        for (int num : requestList) {
            modifiedRequestList.add(swapMap.getOrDefault(num, num));
        }


        return modifiedRequestList;
    }

    private static Map<Integer, Integer> getFrequencyMap(int dataNumber, List<Integer> requestList) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            frequencyMap.put(i, 0); // 初始化所有数据的出现次数为0
        }
        for (int num : requestList) {
            frequencyMap.put(num, frequencyMap.get(num) + 1);
        }
        return frequencyMap;
    }
}
