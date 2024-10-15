package com.cpa.models;

import com.cpa.objectives.Server;
import com.cpa.objectives.User;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.cpa.objectives.Server.getFrequency;
import static com.cpa.tool.ZipfGenerator.generateZipfDistribution;
import static com.cpa.tool.ZipfGenerator.generateZipfRequests;

public class DDCPA {
    public static void main(String[] args) {
        int time = 1000;
        int N = 0;
        for (int t = 0; t < time; t++) {
            int numUsers = 5;
            int numServers = 20;
            int totalRequests = 100;
            double s = 1.0;
            int numRequestTypes = 200;
            //users request content_i
            Set<Integer>[] usersforContent = new HashSet[numRequestTypes];
            for (int i = 0; i < numRequestTypes; i++) {
                usersforContent[i] = new HashSet<>();
            }

            List<User> users = new ArrayList<>();
            List<Server> servers = new ArrayList<>();
            for (int i = 0; i < numUsers; i++) {
                double[] zipfDistribution = generateZipfDistribution(numRequestTypes, s);
//                double[] zipfDistribution = generateZipfDistributionDiff(numRequestTypes, s);
                users.add(new User(i, zipfDistribution, numRequestTypes));
            }
            for (int i = 0; i < numServers; i++) {
                servers.add(new Server(i, numRequestTypes));
            }

            int currentTime = 0;

            //attack user
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
            for (User user : users) {
                if (!user.isMalicious()) {
                    List<Integer> requests = generateZipfRequests(totalRequests, user.getZipfDistribution());
                    for (int request : requests) {
                        Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                        user.sendRequest(server, request, currentTime);
                        usersforContent[request].add(user.getId());
                        currentTime += ThreadLocalRandom.current().nextInt(1, 5);
                    }
//                    System.out.println("user" + user.getId() + ", requests: " + requests);
                } else {
//                    List<Integer> requests = user.generateAttackRequest(totalRequests);
                    List<Integer> requests = user.generateNormalRequest(100, 100, 5, 0, 200);
//                    List<Integer> requests = generateZipfRequestsDiff(totalRequests, user.getZipfDistribution());
                    for (int request : requests) {
                        Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                        user.sendRequest(server, request, currentTime);
                        usersforContent[request].add(user.getId());
                        currentTime += ThreadLocalRandom.current().nextInt(1, 5);
                    }
//                    System.out.println("user attack" + user.getId() + ", requests: " + requests);
                }
//                user.getRequestListPerUser();
            }


            System.out.println(Arrays.toString(usersforContent));
            double[] contentPopularity = new double[numRequestTypes];
            // Initialize all elements to 0.0
            for (int i = 0; i < contentPopularity.length; i++) {
                contentPopularity[i] = 0.0;
            }


            double[] requestRatios = new double[numRequestTypes]; //total each content
            double[] increaseConstant = new double[numRequestTypes];//increaseConstant
            double[] averageRequestIntensity = new double[numRequestTypes];

            Server server = servers.get(0);
            //TODO
            flippingattack(server, usersforContent);

            // requestRatios  r(c_i)
            for (int i = 0; i < numRequestTypes; i++) {
                requestRatios[i] = Math.round((double) server.getRequestCount(i) / server.getTotalRequests() * 100.0) / 100.0;
            }

            for (int i = 0; i < numRequestTypes; i++) {
                increaseConstant[i] = (double) usersforContent[i].size() / numRequestTypes;
                contentPopularity[i] = (double) contentPopularity[i] * (1 - Math.exp(-0.2)) + increaseConstant[i];
                if (contentPopularity[i] == 0) {
                    averageRequestIntensity[i] = 0;
                } else {
                    averageRequestIntensity[i] = requestRatios[i] / contentPopularity[i];
                }
            }
            // Create an array of IndexValue pairs
            IndexValue[] indexedArray = new IndexValue[averageRequestIntensity.length];
            for (int i = 0; i < averageRequestIntensity.length; i++) {
                indexedArray[i] = new IndexValue(i, averageRequestIntensity[i]);
            }

            // Sort the array in descending order based on the value
            Arrays.sort(indexedArray, Comparator.comparingDouble(IndexValue::getValue).reversed());

            IndexValue maxARI = indexedArray[0];

            if (maxARI.getValue() > 10) {
                N++;
            }
        }
        System.out.println("attack count: " + N);
        System.out.println("ratio: " + (double) N / time);

    }

    private static void flippingattack(Server server, Set<Integer>[] usersforContent) {
        List<Integer> requestListPerServer = server.getRequestListPerServer();
        List<Set<Integer>> usersSetforContent = Arrays.asList(usersforContent);
//        System.out.println("usersSetforContent before: " + usersSetforContent);
        Map<Integer, Integer> frequencyMap = getFrequency(requestListPerServer);

        List<Map.Entry<Integer, Integer>> sortedEntries = new ArrayList<>(frequencyMap.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        int size = sortedEntries.size();
        for (int i = 0; i < size / 2; i++) {
            int maxKey = sortedEntries.get(i).getKey();
            int minKey = sortedEntries.get(size - 1 - i).getKey();
            Collections.swap(usersSetforContent, maxKey, minKey);
//            System.out.println("usersSetforContent after: " + usersSetforContent);
            for (int j = 0; j < requestListPerServer.size(); j++) {
                if (requestListPerServer.get(j) == maxKey) {
                    requestListPerServer.set(j, minKey);
                } else if (requestListPerServer.get(j) == minKey) {
                    requestListPerServer.set(j, maxKey);
                }
            }
            server.setRequestListPerServer(requestListPerServer);
        }
        server.changeRequestCount();
    }


    // Helper class to store index-value pairs
    private static class IndexValue {
        private final int originalIndex;
        private final double value;

        public IndexValue(int originalIndex, double value) {
            this.originalIndex = originalIndex;
            this.value = value;
        }

        public int getOriginalIndex() {
            return originalIndex;
        }

        public double getValue() {
            return value;
        }
    }

}
