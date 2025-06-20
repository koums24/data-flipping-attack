package com.cpa.objectives;

import com.cpa.tool.ZipfDistribution;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cpa.tool.ZipfGenerator.splitZipfDistribution;

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

    public List<String> edgeServerUrls = new ArrayList<>();
    private HttpServer httpServer;
    private final HttpClient httpClient;
    private final Gson gson;
    private String controlServerUrl = "http://localhost:8000";
    private final ExecutorService executor;

    public EdgeServer() {
        this.httpClient = HttpClient.newBuilder().build();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(20);
    }

    public void startHttpServer(int port) {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/request", new RequestHandler());
            httpServer.setExecutor(Executors.newFixedThreadPool(10));
            httpServer.start();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = readRequestBody(exchange);

                EdgeRequest edgeRequest = parseToEdgeRequest(requestBody);

                if (edgeRequest != null) {
                    processEdgeRequest(edgeRequest);

                    forwardToControlServer(edgeRequest);

                    sendResponse(exchange, createSuccessResponse(edgeRequest));
                } else {
                    sendErrorResponse(exchange, "Invalid request format");
                }
            } else {
                sendErrorResponse(exchange, "Only POST method supported");
            }
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }
        return requestBody.toString();
    }
    private EdgeRequest parseToEdgeRequest(String jsonString) {
        try {
            EdgeRequest edgeRequest = gson.fromJson(jsonString, EdgeRequest.class);
            return edgeRequest;
        } catch (Exception e) {
            System.err.println("EdgeServer " + id + ": error - " + e.getMessage());
            return null;
        }
    }

    private void processEdgeRequest(EdgeRequest edgeRequest) {
        System.out.println("EdgeServer " + id + ": process : " +
                edgeRequest.userId + " data: " + edgeRequest.dataId);

    }

    private void forwardToControlServer(EdgeRequest edgeRequest) {
        executor.submit(() -> {
            try {
                ForwardRequest forwardRequest = new ForwardRequest(
                        id,
                        edgeRequest,
                        System.currentTimeMillis()
                );

                String jsonBody = gson.toJson(forwardRequest);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(controlServerUrl + "/collect_request"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    System.out.println("EdgeServer " + id + ": success");
                } else {
                    System.err.println("EdgeServer " + id + ": fail - " + response.statusCode());
                }

            } catch (Exception e) {
                System.err.println("EdgeServer " + id + ": fail - " + e.getMessage());
            }
        });
    }

    private String createSuccessResponse(EdgeRequest edgeRequest) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("server_id", id);
        response.put("user_id", edgeRequest.userId);
        response.put("content_id", edgeRequest.dataId);
        response.put("timestamp", System.currentTimeMillis());
        return gson.toJson(response);
    }

    private void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
        }
    }

    private void sendErrorResponse(HttpExchange exchange, String errorMessage) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", errorMessage);
        errorResponse.put("server_id", id);

        String response = gson.toJson(errorResponse);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(400, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
        }
    }

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> status = new HashMap<>();
            status.put("server_id", id);
            status.put("status", "running");
            status.put("users_count", coverUsers.size());
            status.put("requests_processed", mergedRequests.size());

            String response = gson.toJson(status);
            sendResponse(exchange, response);
        }
    }

    public void setControlServerUrl(String url) {
        this.controlServerUrl = url;
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.stop(5);
        }
        executor.shutdown();
    }

    public void addUser(String user) {
        coverUsers.add(user);
    }



    public void generateRequests(int totalRequests, int contentRange) {
        for (String user : coverUsers) {
            List<Request> userRequests = generateZipfRequests(user, totalRequests, contentRange);
            mergedRequests.addAll(userRequests);
        }
        //Zipf-Diverse
//        for (String user : coverUsers) {
//            List<Request> userRequests = generateZipfRequestsDiverse(user, totalRequests, contentRange-1);
//            mergedRequests.addAll(userRequests);
//        }

        //half Zipf, half normal
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
//
//            }
//            mergedRequests.addAll(userRequests);
//        }

    }

    public void generatePerdictRequests(int totalRequests, int contentRange) {
        for (String user : coverUsers) {
            List<Request> userRequests = generateZipfRequests(user, totalRequests, contentRange);
            mergedRequests.addAll(userRequests);
        }

        //Zipf-Diverse
//        for (String user : coverUsers) {
//            List<Request> userRequests = generateZipfRequestsDiverse(user, totalRequests, contentRange-1);
//            mergedRequests.addAll(userRequests);
//        }

//        int midPoint = coverUsers.size() / 2;
//        for (int i = 0; i < coverUsers.size(); i++) {
//            String user = coverUsers.get(i);
//            List<Request> userRequests;
//
//            if (i < midPoint) {
//                userRequests = generateZipfRequests(user, totalRequests, contentRange);
//            } else {
//                userRequests = generateNormalRequest(user, totalRequests, 100, 30, 0, contentRange);
////                userRequests = generateFLARequest(user, totalRequests);
////                List<Request> requests = generateSinusoidalSequence(user, 100, 0, 199);
//
//            }
//            mergedRequests.addAll(userRequests);
//        }
//
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

        ZipfDistribution zipf = new ZipfDistribution(contentRange, 1.0);

        for (int i = 0; i < totalRequests; i++) {
            long interval = random.nextInt(1000) + 100; /
            currentTime += interval;
            int content = zipf.sample();
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
            int peakShift= new Random().nextInt(contentRange / 2);
            // Generate content based on Zipf distribution
            int content = zipf.sample() + peakShift;

            // Ensure content stays within the range by wrapping around
            content = (content - 1) % contentRange+1;

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
                    double value = mean + stdDev * random.nextGaussian();
                    int intValue = (int) Math.round(value);
                    intValue = Math.max(lowerBound, Math.min(intValue, upperBound - 1));
                    return intValue;
                })
                .collect(Collectors.toList());
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100;
            currentTime += interval;
            int content = contentList.get(i);
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
            contentList.add(99);
        }
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100;
            currentTime += interval;
            int content = contentList.get(i);
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }

    public static List<Request> generateSynZipfRequest(String user, int id) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        List<List<Integer>> spiltList = splitZipfDistribution(2000, 20, 100, 0, 199);
        List<Integer> contentList = spiltList.get(id);
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100;
            currentTime += interval;
            int content = contentList.get(i);
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }


    public static List<Request> generateFLARequest(String user) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        List<Integer> contentList = new ArrayList<>(Collections.nCopies(200, 99));;
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100;
            currentTime += interval;
            int content = contentList.get(i);
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }


    public static List<Request> generateSinusoidalSequence(String user, int size, int min, int max) {
        Random random = new Random();
        long currentTime = System.currentTimeMillis();
        List<Request> requests = new ArrayList<>();
        double amplitude = (max - min) / 2.0;
        double offset = (max + min) / 2.0;
        double frequency = (2 * Math.PI) / size; /

        for (int i = 0; i < size; i++) {
            long interval = random.nextInt(1000) + 100;
            currentTime += interval;

            double value = amplitude * Math.sin(frequency * i) + offset;
            int content = (int) Math.round(value);
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
        System.out.println("sequence：" + shuffledRequests);
        return shuffledRequests;
    }

    public double calculateVariance(List<Request> requests) {
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
        Map<Integer, Long> frequencyMap = mergedRequests.stream()
                .collect(Collectors.groupingBy(r -> r.content, Collectors.counting()));

        List<Integer> sortedContents = frequencyMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        int n = sortedContents.size();
        for (int i = 0; i < n / 2; i++) {
            int temp = sortedContents.get(i);
            sortedContents.set(i, sortedContents.get(n - 1 - i));
            sortedContents.set(n - 1 - i, temp);
        }

        Map<Integer, Integer> contentSwapMap = new HashMap<>();
        for (int i = 0; i < sortedContents.size(); i++) {
            contentSwapMap.put(frequencyMap.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList()).get(i), sortedContents.get(i));
        }

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