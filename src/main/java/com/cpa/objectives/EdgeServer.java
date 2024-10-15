package com.cpa.objectives;

import com.cpa.tool.ZipfDistribution;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class EdgeServer {
    public int id;
    public double fromArea;
    public double toArea;
    public double lat;
    public double lng;
    public double radius;
    public int numContent = 200;
    public List<Integer> directCoveredUsers = new ArrayList<>();

    public List<String> coverUsers = new ArrayList<>();
    public List<Request> mergedRequests = new ArrayList<>();
    public List<Integer> requestList = new ArrayList<>();


    public void addUser(String user) {
        coverUsers.add(user);
    }


    public void generateRequests(int totalRequests, int contentRange) {
        //generate Zipf Distribution
        for (String user : coverUsers) {
            List<Request> userRequests = generateZipfRequests(user, totalRequests, contentRange);
            mergedRequests.addAll(userRequests);
        }

        //Zipf-Diverse
//        for (String user : coverUsers) {
//            List<Request> userRequests = generateZipfRequestsDiverse(user, totalRequests, contentRange-1);
//            mergedRequests.addAll(userRequests);
//        }

        //Hybrid
//        int midPoint = coverUsers.size() / 2;
//        for (int i = 0; i < coverUsers.size(); i++) {
//            String user = coverUsers.get(i);
//            List<Request> userRequests;
//
//            if (i < midPoint) {
//                userRequests = generateZipfRequests(user, totalRequests, contentRange);
//            } else {
//                userRequests = generateNormalRequest(user, totalRequests, 100, 30, 0, contentRange);
////                List<Request> requests = generateSinusoidalSequence(user, 100, 0, 199);
//            }
//            mergedRequests.addAll(userRequests);
//        }

    }

    public void generatePerdictRequests(int totalRequests, int contentRange) {
        // all Zipf分布
        for (String user : coverUsers) {
            List<Request> userRequests = generateZipfRequests(user, totalRequests, contentRange);
            mergedRequests.addAll(userRequests);
        }

        //Zipf-Diverse
//        for (String user : coverUsers) {
//            List<Request> userRequests = generateZipfRequestsDiverse(user, totalRequests, contentRange-1);
//            mergedRequests.addAll(userRequests);
//        }

//        Hybrid
//        int midPoint = coverUsers.size() / 2;
//        for (int i = 0; i < coverUsers.size(); i++) {
//            String user = coverUsers.get(i);
//            List<Request> userRequests;
//
//            //Multi && FLA requests
//            if (i < midPoint) {
//                userRequests = generateZipfRequests(user, totalRequests, contentRange);
//            } else {
//                userRequests = generateNormalRequest(user, totalRequests, 100, 30, 0, contentRange);
////                userRequests = generateFLARequest(user, totalRequests);
////                List<Request> requests = generateSinusoidalSequence(user, 100, 0, 199);
//            }
//            mergedRequests.addAll(userRequests);
//        }
//
        //generate Zipf
//        int id =0;
//        for (String user : coverUsers) {
//            List<Request> userRequests = generateSynZipfRequest(user,id);
//            mergedRequests.addAll(userRequests);
//            id++;
//        }

    }

    List<Request> generateZipfRequests(String user, int totalRequests, int contentRange) {
        List<Request> requests = new ArrayList<>();
        Random random = new Random();
        long currentTime = System.currentTimeMillis();

        ZipfDistribution zipf = new ZipfDistribution(contentRange, 1.0);  // 使用Zipf分布生成内容

        for (int i = 0; i < totalRequests; i++) {
            long interval = random.nextInt(1000) + 100; // 随机时间间隔
            currentTime += interval;
            int content = zipf.sample();  // 生成1到contentRange之间的Zipf分布内容
            requests.add(new Request(user, content, currentTime));
        }

        return requests;
    }

    List<Request> generateZipfRequestsDiverse(String user, int totalRequests, int contentRange) {
        List<Request> requests = new ArrayList<>();
        Random random = new Random();
        long currentTime = System.currentTimeMillis();

        // Using Zipf distribution with skewness parameter 1.0
        ZipfDistribution zipf = new ZipfDistribution(contentRange, 1.0);

        for (int i = 0; i < totalRequests; i++) {
            long interval = random.nextInt(1000) + 100; // Random time interval
            currentTime += interval;
            int peakShift = new Random().nextInt(contentRange / 2);
            // Generate content based on Zipf distribution
            int content = zipf.sample() + peakShift;

            // Ensure content stays within the range by wrapping around
            content = (content - 1) % contentRange + 1;

            requests.add(new Request(user, content, currentTime));
        }

        return requests;
    }


    public static List<Request> generateNormalRequest(String user, int size, double mean, double stdDev, int lowerBound, int upperBound) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
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
            long interval = random.nextInt(1000) + 100; // 随机时间间隔
            currentTime += interval;
            int content = contentList.get(i);  // 生成0到contentRange-1之间的Zipf分布内容
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }

    public static List<Request> generateFLARequest(String user, int size) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        List<Integer> contentList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            //FLA
            contentList.add(99);
        }
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100; // 随机时间间隔
            currentTime += interval;
            int content = contentList.get(i);  // 生成0到contentRange-1之间的Zipf分布内容
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }


    public static List<Request> generateFLARequest(String user) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        List<Integer> contentList = new ArrayList<>(Collections.nCopies(200, 99));
        ;
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100; // 随机时间间隔
            currentTime += interval;
            int content = contentList.get(i);  // 生成1到contentRange之间的Zipf分布内容
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }


    public void mergeRequests() {
        mergedRequests.sort(Comparator.comparingLong(r -> r.timestamp));
    }

    public List<Request> generateSendSequence() {
        List<Request> shuffledRequests = new ArrayList<>(mergedRequests);
        Collections.shuffle(shuffledRequests);
        System.out.println("shuffledRequests：" + shuffledRequests);
        return shuffledRequests;
    }

    public double calculateVariance(List<Request> requests) {
        // calculateVariance
        long previousTimestamp = 0;
        List<Long> intervals = new ArrayList<>();

        for (Request request : requests) {
            if (previousTimestamp != 0) {
                intervals.add(request.timestamp - previousTimestamp);
            }
            previousTimestamp = request.timestamp;
        }

        double mean = intervals.stream().mapToLong(val -> val).average().orElse(0.0);
        double variance = intervals.stream()
                .mapToDouble(val -> Math.pow(val - mean, 2))
                .average().orElse(0.0);

        return variance;
    }

    public void swapRequestsBasedOnContentFrequency() {
        // calculate content frequency
        Map<Integer, Long> frequencyMap = mergedRequests.stream()
                .collect(Collectors.groupingBy(r -> r.content, Collectors.counting()));

        // order Request frequency
        List<Integer> sortedContents = frequencyMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // swap
        int n = sortedContents.size();
        for (int i = 0; i < n / 2; i++) {
            int temp = sortedContents.get(i);
            sortedContents.set(i, sortedContents.get(n - 1 - i));
            sortedContents.set(n - 1 - i, temp);
        }

        // mapping pair
        Map<Integer, Integer> contentSwapMap = new HashMap<>();
        for (int i = 0; i < sortedContents.size(); i++) {
            contentSwapMap.put(frequencyMap.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList()).get(i), sortedContents.get(i));
        }

        // reconstruct
        for (Request request : mergedRequests) {
            request.content = contentSwapMap.get(request.content);
        }

        this.setMergedRequests(mergedRequests);
    }

    private void swapRequests(List<Request> requests, int i, int j) {
        Request temp = requests.get(i);
        requests.set(i, requests.get(j));
        requests.set(j, temp);
    }

    private boolean isLocationValid(double location, List<EdgeServer> servers) {
        for (int i = 0; i < servers.size(); i++) {
            if (location >= servers.get(i).fromArea && location <= servers.get(i).toArea) {
                return true;
            }
        }
        return false;
    }

    public List<String> getCoverUsers() {
        return coverUsers;
    }

    public void setCoverUsers(List<String> coverUsers) {
        this.coverUsers = coverUsers;
    }

    public List<Request> getMergedRequests() {
        return mergedRequests;
    }

    public void setMergedRequests(List<Request> mergedRequests) {
        this.mergedRequests = mergedRequests;
    }

    public List<Integer> getRequestList() {
        return requestList;
    }

    public void setRequestList(List<Integer> requestList) {
        this.requestList = requestList;
    }

    public int getNumContent() {
        return numContent;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

}