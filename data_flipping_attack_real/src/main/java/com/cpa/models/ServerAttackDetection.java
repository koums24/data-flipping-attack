package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.Request;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.cpa.tool.GM14Predict.gm14;

public class ServerAttackDetection {

    public static void main(String[] args) {
        int numberOfServers = 5;
        int numberOfUsers = 6;
        int totalRequests = 100;
        int numContent = 200;
        int time = 500;//实验次数
//        for (int batch = 40; batch < 80; batch += 4) {
//            for (int slice = batch; slice < batch + 4; slice++) {
        List<EdgeServer> servers = new ArrayList<>();
        Random random = new Random();

        // Step 1: 生成服务器
        for (int i = 0; i < numberOfServers; i++) {
            EdgeServer server = new EdgeServer();
            server.id =1;
            // Step 2: 为每个服务器分配coverUsers
            for (int j = 0; j < numberOfUsers; j++) {
                server.addUser("User" + (i * numberOfUsers + j));
            }

            // Step 3: 为每个coverUser生成请求列表, Zipf distribution
            server.generateRequests(totalRequests, numContent);
            servers.add(server);
        }

        // Step 4: 合并请求列表(并生成随机发送序列)
        List<Request> allRequests = new ArrayList<>();
        for (EdgeServer server : servers) {

            server.mergeRequests();
//            for (Request mergedRequest : server.getMergedRequests()) {
//                server.getRequestList().add(mergedRequest.getContent());
//            }
//            System.out.println("before" + server.getRequestList());
//            int[] counts = new int[200];
//            for (int element : server.getRequestList()) {
//                if (element >= 0 && element < 200) {
//                    counts[element]++; // 对应位置的计数器加1
//                }
//            }
//            System.out.println("counts" + Arrays.toString(counts));
//            //TODO Attack
            List<Request> beforeMergedRequest = server.getMergedRequests();
            System.out.println("before Merged requests: " + beforeMergedRequest);
            //Single Attack
//            if (server.getId() == 0) {
//                server.swapRequestsBasedOnContentFrequency();
//            }
            //multi attack
            server.swapRequestsBasedOnContentFrequency();

            List<Request> afterMergedRequest = server.getMergedRequests();
            System.out.println("after Merged requests: " + afterMergedRequest);
            server.setRequestList(new ArrayList<>());
            for (Request mergedRequest : server.getMergedRequests()) {
                server.getRequestList().add(mergedRequest.getContent());
            }
            System.out.println("after" + server.getRequestList());
//            counts = new int[200];
//            for (int element : server.getRequestList()) {
//                if (element >= 0 && element < 200) {
//                    counts[element]++; // 对应位置的计数器加1
//                }
//            }
//            System.out.println("counts" + Arrays.toString(counts));
            allRequests.addAll(server.mergedRequests);


//            System.out.println("allRequests" + allRequests);
//            List<Request> sendSequence = server.generateSendSequence();
//            System.out.println("sendSequence: " + sendSequence);
        }

//        int numContent = servers.get(0).getNumContent();

//        FLAGPModel(numContent, allRequests, servers, time, numberOfServers, numberOfUsers, totalRequests);
        FLAGPModel(numContent, servers.get(0).mergedRequests, servers, time, numberOfServers, numberOfUsers, totalRequests);
//        DDCPAModel(time, numContent, allRequests, servers, numberOfServers);
//        generateCNNDataset(numContent, allRequests, servers, numberOfServers, slice);
//            }
//        }
    }

    private static void generateCNNDataset(int numContent, List<Request> allRequests, List<EdgeServer> servers, int numberOfServers, int slice) {
        double[] requestRatios = new double[numContent]; //total each content
        double[] increaseConstant = new double[numContent];//increaseConstant
        double[] averageRequestIntensity = new double[numContent];
        double[] varianceoRI = new double[numContent];//repeat interest
        double[] stdDevsRequestInterval = new double[numContent];

        //初始化popularity
        double[] contentPopularity = new double[numContent]; // 创建 double 数组
        // Initialize all elements to 0.0
        for (int i = 0; i < contentPopularity.length; i++) {
            contentPopularity[i] = 0.0;
        }
        //Request Ratios
        requestRatios = calculateRequestRatios(numContent, allRequests);

        //Average Request Intensity)
        Map<Integer, Set<EdgeServer>> requestServerMap = new HashMap<>();

        for (EdgeServer server : servers) {
            for (Request request : server.getMergedRequests()) {
                requestServerMap.computeIfAbsent(request.getContent(), k -> new HashSet<>()).add(server);
            }
        }

        for (int i = 0; i < numContent; i++) {
            increaseConstant[i] = requestServerMap.get(i) != null ?
                    (double) requestServerMap.get(i).size() / numberOfServers :
                    0.0;
            contentPopularity[i] = (double) contentPopularity[i] * (1 - Math.exp(-0.2)) + increaseConstant[i];
            if (contentPopularity[i] == 0) {
                averageRequestIntensity[i] = 0; // 或者其他合适的默认值
            } else {
                averageRequestIntensity[i] = requestRatios[i] / contentPopularity[i];
            }
        }

        // Step 5: 针对每个content计算metrics
        varianceoRI = calculateVarianceForEachContent(numContent, servers);

        List<Integer> allContentList = new ArrayList<>();
        for (Request r : allRequests) {
            allContentList.add(r.getContent());
        }

        stdDevsRequestInterval = calculateStdVarianceRequestInterval(numContent, allContentList);

        // generate dataset txt
        String filePath = "timeslice" + slice + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) { // true 参数启用附加模式
            for (int i = 0; i < numContent; i++) {
                writer.write(String.valueOf(requestRatios[i]));
                writer.newLine();
                writer.write(String.valueOf(averageRequestIntensity[i]));
                writer.newLine();
                writer.write(String.valueOf(varianceoRI[i]));
                writer.newLine();
                writer.write(String.valueOf(stdDevsRequestInterval[i]));
                writer.newLine();
            }
            System.out.println("Data successfully appended to " + filePath);
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
        }
    }

    private static void DDCPAModel(int time, int numContent, List<Request> allRequests, List<EdgeServer> servers, int numberOfServers) {
        int count = 0;
        for (int t = 0; t < time; t++) {

            //初始化popularity
            double[] contentPopularity = new double[numContent]; // 创建 double 数组
            // Initialize all elements to 0.5
            for (int i = 0; i < contentPopularity.length; i++) {
                contentPopularity[i] = 0.5;
            }


            double[] requestRatios = new double[numContent]; //total each content
            double[] increaseConstant = new double[numContent];//increaseConstant
            double[] averageRequestIntensity = new double[numContent];
            //Request Ratios
            requestRatios = calculateRequestRatios(numContent, allRequests);

            //Average Request Intensity)
            Map<Integer, Set<EdgeServer>> requestServerMap = new HashMap<>();

            for (EdgeServer server : servers) {
                for (Request request : server.getMergedRequests()) {
                    requestServerMap.computeIfAbsent(request.getContent(), k -> new HashSet<>()).add(server);
                }
            }

            for (int i = 0; i < numContent; i++) {
                increaseConstant[i] = requestServerMap.get(i) != null ?
                        (double) requestServerMap.get(i).size() / numberOfServers :
                        0.0;
                contentPopularity[i] = (double) contentPopularity[i] * (1 - Math.exp(-0.2)) + increaseConstant[i];
                if (contentPopularity[i] == 0) {
                    averageRequestIntensity[i] = 0; // 或者其他合适的默认值
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

            if (maxARI.getValue() > 0.3) {
                count++;
            }

        }
        System.out.println("DDCPA ratio: " + (double) count / time);

    }

    /**
     * GM(1,4) prediction
     *
     * @param numContent
     * @param allRequests
     * @param servers
     * @param time
     * @param numberOfServers
     * @param numberOfUsers
     * @param totalRequests
     */
    private static void FLAGPModel(int numContent, List<Request> allRequests, List<EdgeServer> servers, int time, int numberOfServers, int numberOfUsers, int totalRequests) {
        //Initialize popularity FLAGP
        double[] contentPopularity = new double[numContent];
        for (int i = 0; i < contentPopularity.length; i++) {
            contentPopularity[i] = 0.5;
        }
        // 创建存储请求比率 方差 时间间隔标准差的数组
        double[] requestRatios = new double[numContent]; //total each content
        double[] varianceoRI = new double[numContent];//repeat interest
        double[] stdDevsRequestInterval = new double[numContent];
        // Step 5: 针对每个content计算metrics
        requestRatios = calculateRequestRatios(numContent, allRequests);
//        System.out.println("requestRatios: " + Arrays.toString(requestRatios));


        varianceoRI = calculateVarianceForEachContent(numContent, servers);
//        System.out.println("varianceoRI: " + Arrays.toString(varianceoRI));

        //新生成一个request list 用gm模型检测
        List<Integer> allContentList = new ArrayList<>();
        for (Request r : allRequests) {
            allContentList.add(r.getContent());
        }
        System.out.println("allContentList: " + allContentList);
        stdDevsRequestInterval = calculateStdVarianceRequestInterval(numContent, allContentList);
//        System.out.println("stdDevsRequestInterval: " + Arrays.toString(stdDevsRequestInterval));


        int count = 0;
        for (int t = 0; t < time; t++) {
            //检测出的攻击数
            boolean isAttacked = detection(numberOfServers, numberOfUsers, totalRequests, numContent, requestRatios, varianceoRI, stdDevsRequestInterval, contentPopularity);
            if (isAttacked) {
                count++;
            }
        }
        System.out.println("attack count: " + count);
        System.out.println("FLAGP ratio: " + (double) count / time);
    }

    private static double[] calculateRequestRatios(int numContent, List<Request> allRequests) {

        double[] requestRatios = new double[numContent];
        // 统计每个content的数量
        Map<Integer, Long> contentCountMap = allRequests.stream()
                .collect(Collectors.groupingBy(request -> request.content, Collectors.counting()));

        // 计算每个content的request ratio
        int totalRequests = allRequests.size();
        Map<Integer, Double> contentRatioMap = new HashMap<>();

        for (Map.Entry<Integer, Long> entry : contentCountMap.entrySet()) {
            int content = entry.getKey();
            long count = entry.getValue();
            double ratio = (double) count / totalRequests;
            contentRatioMap.put(content, ratio);
        }

        // 输出结果
//        System.out.println("Content -> Request Ratio:");
        for (Map.Entry<Integer, Double> entry : contentRatioMap.entrySet()) {
            requestRatios[entry.getKey()] += entry.getValue();
//            System.out.println("Content: " + entry.getKey() + " -> Ratio: " + entry.getValue());
        }
        return requestRatios;
    }

    private static double[] calculateVarianceForEachContent(int numContent, List<EdgeServer> servers) {
        double[] varianceoRI = new double[numContent];
        // 获取所有的content
        Set<Integer> allContents = servers.stream()
                .flatMap(server -> server.mergedRequests.stream())
                .map(request -> request.content)
                .collect(Collectors.toSet());

        // 针对每个content计算方差
        for (int content : allContents) {
            List<Long> counts = new ArrayList<>();

            // 统计每个server中该content的数量
            for (EdgeServer server : servers) {
                long count = server.mergedRequests.stream()
                        .filter(request -> request.content == content)
                        .count();
                counts.add(count);
            }

            // 计算方差
            varianceoRI[content] = calculateVariance(counts);
        }
        return varianceoRI;
    }

    private static double calculateVariance(List<Long> counts) {
        double mean = counts.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = counts.stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average().orElse(0.0);
        return variance;
    }

    public static double[] calculateStdVarianceRequestInterval(int numContent, List<Integer> allContentList) {
        double[] stdDevsRequestInterval = new double[numContent];
        Map<Integer, List<Integer>> indexMap = new HashMap<>();

        // 记录每个元素的出现位置
        for (int i = 0; i < allContentList.size(); i++) {
            int num = allContentList.get(i);
            if (!indexMap.containsKey(num)) {
                indexMap.put(num, new ArrayList<>());
            }
            indexMap.get(num).add(i);
        }

        // 计算每个元素的间隔
        Map<Integer, List<Integer>> intervalMap = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : indexMap.entrySet()) {
            List<Integer> indices = entry.getValue();
            List<Integer> intervals = new ArrayList<>();
            for (int i = 1; i < indices.size(); i++) {
                intervals.add(indices.get(i) - indices.get(i - 1));
            }
            intervalMap.put(entry.getKey(), intervals);
        }

        // 计算标准差
        Map<Integer, Double> standardDeviationMap = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : intervalMap.entrySet()) {
            List<Integer> intervals = entry.getValue();
            double mean = intervals.stream().mapToInt(val -> val).average().orElse(0.0);
            double variance = intervals.stream().mapToDouble(val -> Math.pow(val - mean, 2)).sum() / intervals.size();
            double standardDeviation = Math.sqrt(variance);
            standardDeviationMap.put(entry.getKey(), standardDeviation);
        }

        // 输出结果
        for (Map.Entry<Integer, Double> entry : standardDeviationMap.entrySet()) {
            stdDevsRequestInterval[entry.getKey()] += entry.getValue();
        }
        return stdDevsRequestInterval;
    }

    public static boolean detection(int numberOfServers, int numberOfUsers, int totalRequests, int numContent,
                                    double[] requestRatios, double[] varianceoRI, double[] stdDevsRequestInterval, double[] contentPopularity) {
        boolean det_response = false;

        List<EdgeServer> servers = new ArrayList<>();
        Random random = new Random();

        // Step 1: 生成服务器
        for (int i = 0; i < numberOfServers; i++) {
            EdgeServer server = new EdgeServer();
            server.id = i;
            // Step 2: 为每个服务器分配coverUsers
            for (int j = 0; j < numberOfUsers; j++) {
                server.addUser("User" + (i * numberOfUsers + j));
            }
            // Step 3: 为每个coverUser生成请求列表
            server.generatePerdictRequests(totalRequests, numContent);
            servers.add(server);
        }
        // Step 4: 合并请求列表并生成随机发送序列
        List<Request> allRequests = new ArrayList<>();
        for (EdgeServer server : servers) {
            server.mergeRequests();

            for (Request mergedRequest : server.getMergedRequests()) {
                server.getRequestList().add(mergedRequest.getContent());
            }
            System.out.println("before" + server.getRequestList());
            System.out.println("before requestlist size" + server.getRequestList().size());
            int[] counts = new int[numContent];
            for (int element : server.getRequestList()) {
                if (element >= 0 && element < numContent) {
                    counts[element]++; // 对应位置的计数器加1
                }
            }
            System.out.println("counts" + Arrays.toString(counts));
            //TODO Attack
//            if (server.getId() == 0) {
//                server.swapRequestsBasedOnContentFrequency();
//            }
               // muti attack
//            server.swapRequestsBasedOnContentFrequency();
//            server.setRequestList(new ArrayList<>());
//            for (Request mergedRequest : server.getMergedRequests()) {
//                server.getRequestList().add(mergedRequest.getContent());
//            }
//            System.out.println("after" + server.getRequestList());
//            System.out.println("after requestlist size" + server.getRequestList().size());
//
//            counts = new int[numContent];
//            for (int element : server.getRequestList()) {
//                if (element >= 0 && element < 200) {
//                    counts[element]++; // 对应位置的计数器加1
//                }
//            }
//            System.out.println("counts" + Arrays.toString(counts));
            //attack end
            allRequests.addAll(server.mergedRequests);
//            System.out.println("allRequests" + allRequests);
//            List<Request> sendSequence = server.generateSendSequence();
//            System.out.println("sendSequence: " + sendSequence);
        }

        List<Integer> allContentList = new ArrayList<>();
        // attack server
        for (Request r : allRequests) {
            allContentList.add(r.getContent());
        }
        // attack user
//        for (Request mergedRequest : servers.get(0).mergedRequests) {
//            allContentList.add(mergedRequest.getContent());
//        }

        System.out.println("allContentList: " + allContentList);
        System.out.println("allContentList size: " + allContentList.size());

        //计算interval
        Map<Integer, List<Integer>> indexMap = new HashMap<>();
        // 记录每个元素的出现位置
        for (int i = 0; i < allContentList.size(); i++) {
            int num = allContentList.get(i);
            if (!indexMap.containsKey(num)) {
                indexMap.put(num, new ArrayList<>());
            }
            indexMap.get(num).add(i);
        }

        // 计算每个元素的间隔
        Map<Integer, List<Integer>> intervalMap = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : indexMap.entrySet()) {
            List<Integer> indices = entry.getValue();
            List<Integer> intervals = new ArrayList<>();
            for (int i = 1; i < indices.size(); i++) {
                intervals.add(indices.get(i) - indices.get(i - 1));
            }
            intervalMap.put(entry.getKey(), intervals);
        }


        //更新和预测每个content的popularity
        int N = 0; //malicious总数
        for (int i = 0; i < numContent; i++) {
            //calculate 时间间隔
            List<Integer> list = intervalMap.get(i);
            if (list != null && !intervalMap.isEmpty()) {
                for (int j = 0; j < intervalMap.get(i).size() - 1; j++) {
                    double diff = intervalMap.get(i).get(j) / 10;
                    //GM(1,4)模型预测该content的popularity
                    double[] predict = gm14(contentPopularity, requestRatios, varianceoRI, stdDevsRequestInterval);
//                    System.out.println(Arrays.toString(predict));
                    double CurrPopu = contentPopularity[i] * Math.pow(0.5, diff) + 0.5;
                    double maliciousRatio = Math.abs((CurrPopu - predict[i]) / CurrPopu);
                    if (maliciousRatio > 0.6) {
                        N++;
                    } else {
                        contentPopularity[i] = CurrPopu;
                    }
                }
            }
        }
        System.out.println("N:" + N);
        if (N > 20) {
            det_response = true;
        }
        System.out.println(det_response);
        return det_response;
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

