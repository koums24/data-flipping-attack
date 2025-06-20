package com.cpa.objectives;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.google.gson.Gson;

import com.cpa.tool.DistanceCalculator;

import static com.cpa.tool.ZipfGenerator.generateZipfListForUser;
import static com.cpa.tool.ZipfGenerator.generateZipfSequence;
import com.cpa.tool.DistanceCalculator;

public class EdgeUser {
    public double location;
    public double lat;
    public double lng;
    public int id;
    public List<Integer> nearEdgeServers = new ArrayList<>();
    public List<Integer> dataList = new ArrayList<>();

    // HTTP客户端相关
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Gson gson;
    private String targetEdgeServerUrl;

    public EdgeUser() {
        this.httpClient = HttpClient.newBuilder().build();
        this.executor = Executors.newFixedThreadPool(10);
        this.gson = new Gson();
    }

    public EdgeUser(int id, String edgeServerUrl) {
        this.id = id;
        this.targetEdgeServerUrl = edgeServerUrl;
        this.httpClient = HttpClient.newBuilder().build();
        this.executor = Executors.newFixedThreadPool(10);
        this.gson = new Gson();
    }

    // 设置目标EdgeServer URL
    public void setTargetEdgeServer(String serverUrl) {
        this.targetEdgeServerUrl = serverUrl;
    }

    // 多线程发送HTTP请求到同一个EdgeServer
    public void sendRequestsToEdgeServer(int totalRequests) {
        System.out.println("EdgeUser " + id + " 开始向 " + targetEdgeServerUrl + " 发送HTTP请求...");

        // 生成请求数据
//        generateRequests(totalRequests, 30, "zipf");

        // 为每个请求创建线程发送到同一个EdgeServer
        for (int i = 0; i < dataList.size(); i++) {
            final int contentId = dataList.get(i);
            final int requestIndex = i;

            executor.submit(() -> {
                try {
                    // 在实际发送请求前添加延迟
                    Thread.sleep(requestIndex * (3000 + new Random().nextInt(100)));

                    EdgeRequest edgeRequest = new EdgeRequest(this.id, this.nearEdgeServers.get(0), contentId);
                    sendHttpRequest(edgeRequest, requestIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("EdgeUser " + id + " 请求 " + requestIndex + " 被中断");
                }
            });
        }
    }
    // 发送HTTP请求到EdgeServer
    private void sendHttpRequest(EdgeRequest edgeRequest, int requestIndex) {
        try {
            String jsonBody = gson.toJson(edgeRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetEdgeServerUrl + "/request"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(java.time.Duration.ofSeconds(100))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("EdgeUser " + id + ": 请求 " + requestIndex +
                        " 成功发送 - 内容: " + edgeRequest.dataId);
            } else {
                System.out.println("EdgeUser " + id + ": HTTP错误 " + response.statusCode());
            }

        } catch (Exception e) {
            System.out.println("EdgeUser " + id + ": 请求失败 - " + e.getMessage());
        }
    }


    public void shutdown() {
        executor.shutdown();
    }


    public void assignIdAndBindServers(int assignedId, List<EdgeServer> servers, double coverageRadius) {
        this.id = assignedId;
        for (EdgeServer server : servers) {
            double d = DistanceCalculator.distance(server.lat, server.lng, this.lat, this.lng);
            if (d <= coverageRadius) {
                this.nearEdgeServers.add(server.id);
                server.directCoveredUsers.add(this.id);
            }
        }
    }

    public void generateRequests(int time, int dataNumber, String mode) {
        switch (mode) {
            case "zipf":
                this.dataList = generateZipfListForUser(this.id, time, 0, dataNumber - 1);
                break;
            case "multi":
                this.dataList = generateMultiSequence(this.id, time, 0, dataNumber - 1);
                break;
            case "zipf_same":
                this.dataList = generateZipfSequence(time, 0, dataNumber - 1);
                break;
            case "fla":
                this.dataList = generateFLAList(this.id, time, 0, dataNumber - 1);
                break;
            case "lda":
                this.dataList = generateLDAList(this.id, time, 0, dataNumber - 1);
                break;
            default:
                throw new IllegalArgumentException("Unsupported request mode: " + mode);
        }
    }

    public ArrayList<Integer> getRandomList(int dataNumber, int time) {
        ArrayList<Integer> list = new ArrayList<Integer>();
        // System.out.println("dataNumber: " + dataNumber + " listNumber" + listNumber);
        Random r = new Random();
        list.add(r.nextInt(dataNumber));

        while (list.size() < time) {

            list.add(r.nextInt(dataNumber));

//             double isKeep = ThreadLocalRandom.current().nextDouble(1);
//
//             if (list.size() > 3 && isKeep > 0.5) {
//             list.add(list.get(list.size() - 2));
//             } else {
//             list.add(r.nextInt(dataNumber));
//             }

            // System.out.print(temp + " ");
        }
        // System.out.println();
        return list;
    }

    private List<Integer> generateMultiSequence(int userId, int size, int min, int max) {
        List<Integer> sequence = new ArrayList<>();
        Random baseRandom = new Random(userId); // Random based on userId
//        generateNormalDistributionSequence(sequence, size, min, max, baseRandom);
        // Divide users into three groups: Normal, Poisson, and Zipf distributions
        if (userId % 3 == 0) {
            // First 1/3 users: Normal distribution centered around mean
            generateNormalDistributionSequence(sequence, size, min, max, baseRandom);
        } else if (userId % 3 == 1) {
            // Second 1/3 users: Poisson distribution
            generatePoissonDistributionSequence(sequence, size, min, max, baseRandom);
        } else {
            // Last 1/3 users: Zipf distribution
            generateZipfDistributionSequence(sequence, size, min, max, baseRandom);
        }

        return sequence;
    }
    // Generates requests based on a normal distribution centered at the midpoint
    private static void generateNormalDistributionSequence(List<Integer> sequence, int size, int min, int max, Random random) {
        double mean = (min + max) / 2.0;
        double stdDev = (max - min) / 6.0; // 99.7% of data falls within this range

        for (int i = 0; i < size; i++) {
            int value = (int) Math.round(random.nextGaussian() * stdDev + mean);
            value = Math.min(max, Math.max(min, value)); // Ensure within bounds
            sequence.add(value);
        }
    }

    // Generates requests based on a Poisson distribution
    private static void generatePoissonDistributionSequence(List<Integer> sequence, int size, int min, int max, Random random) {
        int lambda = (max - min) / 2; // Mean of Poisson distribution

        for (int i = 0; i < size; i++) {
            int value = generatePoisson(lambda, random);
            value = Math.min(max, Math.max(min, value)); // Ensure within bounds
            sequence.add(value);
        }
    }

    // Helper function to generate a Poisson-distributed value
    private static int generatePoisson(int lambda, Random random) {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);
        return k - 1;
    }

    // Generates requests based on a Zipf distribution focusing on the top 1/5 of the data
    private static void generateZipfDistributionSequence(List<Integer> sequence, int size, int min, int max, Random random) {
        double exponent = 0.9; // Zipf指数

        for (int i = 1; i <= size; i++) {
            double zipfValue = (1.0 / Math.pow(i, exponent));
            int value = (int) Math.round(min + (zipfValue * (max - min)));
            sequence.add(value);
        }

        // 将Zipf值映射到0 ， max-1的范围
        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
            int mappedValue = (int) (value * (max - min) / sequence.get(0));
            mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
            mappedSequence.add(mappedValue);
        }

        Collections.shuffle(mappedSequence, random);
        sequence.clear();
        sequence.addAll(mappedSequence);
    }


    private List<Integer> generateLDAList(int userId, int size, int min, int max) {
        List<Integer> mappedSequence = new ArrayList<>();
        Random random = new Random();
        //生成全部为unpopular data的request list
        if(userId % 2 == 0){
            for (int i = 0; i < size; i++) {
                int randomNum = ThreadLocalRandom.current().nextInt(max-5, max);
                mappedSequence.add(randomNum);
            }
        }else{
            //generate Zipf
            List<Integer> sequence= new ArrayList<>();
            Random userRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
//        Random userRandom = new Random(); //
//        double exponent = 1 + userRandom.nextDouble(); // 基于userId的随机Zipf指数
            double exponent = 1.1; // 基于userId的随机Zipf指数

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  // 归一化
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }

            // 将Zipf值映射到[min, max]的范围，考虑更多的随机性

            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); // 添加额外随机性
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); // 添加额外随机性
                mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
                mappedSequence.add(mappedValue);
            }

            // 乱序mappedSequence列表
            Collections.shuffle(mappedSequence, userRandom); // 用相同的Random实例打乱顺序
        }

        return mappedSequence;
    }

    private List<Integer> generateFLAList(int userId, int size, int min, int max) {
        List<Integer> mappedSequence = new ArrayList<>();
        //生成全部为unpopular data的request list
        if(userId % 10 == 0){
            int unpopulardata = max - 1;
            for (int i = 0; i < size; i++) {
                mappedSequence.add(unpopulardata);
            }
        }else{
            //generate Zipf
            List<Integer> sequence= new ArrayList<>();
            Random userRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
//        Random userRandom = new Random(); //
//        double exponent = 1 + userRandom.nextDouble(); // 基于userId的随机Zipf指数
            double exponent = 1.1; // 基于userId的随机Zipf指数

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  // 归一化
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }

            // 将Zipf值映射到[min, max]的范围，考虑更多的随机性

            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); // 添加额外随机性
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); // 添加额外随机性
                mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
                mappedSequence.add(mappedValue);
            }

            // 乱序mappedSequence列表
            Collections.shuffle(mappedSequence, userRandom); // 用相同的Random实例打乱顺序
        }

        return mappedSequence;
    }





}

