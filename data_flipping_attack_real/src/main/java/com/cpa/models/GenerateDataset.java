package com.cpa.models;

import com.cpa.objectives.Server;
import com.cpa.objectives.User;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.cpa.models.FLAGP.tamperDistribution;
import static com.cpa.objectives.Server.getFrequency;
import static com.cpa.tool.ZipfGenerator.*;

public class GenerateDataset {

    public static void main(String[] args) {
        int numUsers = 20;
        int numServers = 4;
        int requestPeruser = 100;
        double s = 1.0;
        int numRequestTypes = 200;
        for (int j = 40; j < 80; j += 4) {
            for (int slice = j; slice < j + 4; slice++) {

                List<User> users = new ArrayList<>();
                List<Server> servers = new ArrayList<>();
                for (int i = 0; i < numUsers; i++) {
                    double[] zipfDistribution = generateZipfDistribution(numRequestTypes, s);
//                    double[] zipfDistribution = generateZipfDistributionDiff(numRequestTypes, 1.0);

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
////            traditional attack 设定攻击user
                users.get(0).setMalicious(true);
                users.get(1).setMalicious(true);
                users.get(2).setMalicious(true);
                users.get(3).setMalicious(true);
                users.get(4).setMalicious(true);
                users.get(5).setMalicious(true);
                users.get(6).setMalicious(true);
                users.get(7).setMalicious(true);
                users.get(8).setMalicious(true);
                users.get(9).setMalicious(true);
                users.get(10).setMalicious(true);
                users.get(11).setMalicious(true);
                users.get(12).setMalicious(true);
                users.get(13).setMalicious(true);
                users.get(14).setMalicious(true);
                users.get(15).setMalicious(true);
                users.get(16).setMalicious(true);

                for (User user : users) {
                    if (!user.isMalicious()) {
                        List<Integer> requests = new ArrayList<>();
//                        requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                        //hybrid
                    if(user.getId()%3 == 0){
                        requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                    }else{
                        requests = user.generateNormalRequest(requestPeruser, 50, 20, 0, numRequestTypes);
                    }

                        for (int request : requests) {
                            Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
                            user.sendRequest(server, request, currentTime);
                            usersforContent[request].add(user.getId());
                            currentTime += ThreadLocalRandom.current().nextInt(1, 5);
                        }
                    } else {
                        //FLA
//                List<Integer> requests = user.generateAttackRequest(requestPeruser);
//                for (int request : requests) {
//                    Server server = servers.get(ThreadLocalRandom.current().nextInt(numServers));
//                    user.sendRequest(server, request, currentTime);
//                    usersforContent[request].add(user.getId());
//                    currentTime += ThreadLocalRandom.current().nextInt(1, 5);
//                }
                        //flipping attack
                        List<Integer> requests = new ArrayList<>();
//                        requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                        if(user.getId()%3 == 0){
                            requests = generateZipfRequests(requestPeruser, user.getZipfDistribution());
                        }else{
                            requests = user.generateNormalRequest(requestPeruser, 50, 20, 0, numRequestTypes);
                        }
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


//            List<List<Integer>> spiltList = splitZipfDistribution(2000, 20, 100, 0, 199);
//            int index = 0;
//            for (User user : users) {
//                if (!user.isMalicious()) {
//                    List<Integer> requests = spiltList.get(index);
//                    for (int request : requests) {
//                        Server server = servers.get(0);
//                        user.sendRequest(server, request, currentTime);
//                        usersforContent[request].add(user.getId());
//                        currentTime += ThreadLocalRandom.current().nextInt(1, 5); // 模拟时间流逝
//                    }
//                    index ++;
////                System.out.println("user" + user.getId() + ", requests: " + requests);
//                }
//            }


                double[] requestRatios = new double[numRequestTypes]; //M1 Request Ratio each content
                double[] varianceIRI = new double[numRequestTypes]; //M2 Individual Request Intensity
                double[] stdDevsRI = new double[numRequestTypes]; //M3 Standard Deviation of Request Intervals

                double[] increaseConstant = new double[numRequestTypes];//increaseConstant for calculate averageRequestIntensity
                double[] averageRequestIntensity = new double[numRequestTypes]; //M4

                //TODO
                //tamper distribution attack
//            tamperDistributionAttack(servers.get(0), usersforContent);

                Server server = servers.get(0);
                int totalRequests = server.getTotalRequests();
                // request ratio
                for (int i = 0; i < numRequestTypes; i++) {
                    requestRatios[i] = Math.round((double) server.getRequestCount(i) / totalRequests * 1000.0) / 1000.0;
                }

                //calculate variance of Individual Request Intensity
                for (int i = 0; i < numRequestTypes; i++) {
                    varianceIRI[i] = calculatevarianceIRI(users, requestRatios, i);
                }

                for (int i = 0; i < numRequestTypes; i++) {
                    stdDevsRI[i] = Math.round((calculateStdDevOfIntervals(servers, i) / 1000) * 100) / 100.0;
                }

                double[] popularityCNN = new double[numRequestTypes];
                // Initialize all elements to 0.0
                for (int i = 0; i < popularityCNN.length; i++) {
                    popularityCNN[i] = 0.0;
                }

                for (int i = 0; i < numRequestTypes; i++) {
                    increaseConstant[i] = (double) usersforContent[i].size() / numRequestTypes;
                    popularityCNN[i] = popularityCNN[i] * (1 - Math.exp(-0.2)) + increaseConstant[i];
                    if (popularityCNN[i] == 0) {
                        averageRequestIntensity[i] = 0;
                    } else {
                        averageRequestIntensity[i] = requestRatios[i] / popularityCNN[i];
                    }
                }

//          prepare data set for CNN
                String filePath = "timeslice" + slice + ".txt";

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                    for (int i = 0; i < numRequestTypes; i++) {
                        writer.write(String.valueOf(requestRatios[i]));
                        writer.newLine();
                        writer.write(String.valueOf(averageRequestIntensity[i]));
                        writer.newLine();
                        writer.write(String.valueOf(varianceIRI[i]));
                        writer.newLine();
                        writer.write(String.valueOf(stdDevsRI[i]));
                        writer.newLine();
                    }
                    System.out.println("Data successfully appended to " + filePath);
                } catch (IOException e) {
                    System.err.println("An error occurred while writing to the file: " + e.getMessage());
                }

                System.out.println("finished");
            }
        }
    }


    private static void tamperDistributionAttack(Server server, Set<Integer>[] usersforContent) {
        List<Integer> requestListPerServer = server.getRequestListPerServer();
        List<Set<Integer>> usersSetforContent = Arrays.asList(usersforContent);
        Map<Integer, List<Integer>> requestTimestamp = server.getRequestTimestamp();
//        System.out.println("usersSetforContent before: " + usersSetforContent);
        Map<Integer, Integer> frequencyMap = getFrequency(requestListPerServer);

        List<Map.Entry<Integer, Integer>> sortedEntries = new ArrayList<>(frequencyMap.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        int size = sortedEntries.size();
        for (int i = 0; i < size / 2; i++) {

            int maxKey = sortedEntries.get(i).getKey();
            int minKey = sortedEntries.get(size - 1 - i).getKey();

            Collections.swap(usersSetforContent, maxKey, minKey);

            List<Integer> maxKeyValue = requestTimestamp.get(maxKey);
            List<Integer> minKeyValue = requestTimestamp.get(minKey);
            requestTimestamp.put(maxKey, minKeyValue);
            requestTimestamp.put(minKey, maxKeyValue);
            server.setRequestTimestamps(requestTimestamp);
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

    public static double calculateVarianceOfRequests(List<Server> servers, int requestType) {
        List<Integer> requestCounts = new ArrayList<>();
        for (Server server : servers) {
            requestCounts.add(server.getRequestCount(requestType));
        }

        double mean = requestCounts.stream().mapToInt(val -> val).average().orElse(0.0);

        double variance = 0.0;
        for (int count : requestCounts) {
            variance += Math.pow(count - mean, 2);
        }
//        variance /= requestCounts.size();
        return variance;
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

    public static double calculatevarianceIRI(List<User> user, double[] requestRatios, int requestType) {
        double IRIperuser = 0.0;
        for (int i = 0; i < requestType; i++) {
            for (User u : user) {
                int[] requestCountPerUser = u.getRequestCountPerUser();
                // user j for content
                int userTotalrequest = u.getRequestListPerUser().size();
                IRIperuser += Math.pow(((double) requestCountPerUser[requestType] / userTotalrequest - requestRatios[requestType]), 2);
            }
        }
        return IRIperuser;
    }


    public static double calculateMean(double[] array) {
        double sum = 0.0;
        for (double num : array) {
            sum += num;
        }
        return sum / array.length;
    }

}

