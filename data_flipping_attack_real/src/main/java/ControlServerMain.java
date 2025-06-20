
import com.cpa.models.CoverageFirstModel;
import com.cpa.models.LatencyFirstModel;
import com.cpa.models.OptimalModel;
import com.cpa.objectives.EdgeRequest;
import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;
import com.cpa.tool.RandomGraphGenerator;
import com.cpa.tool.RandomServersGenerator;
import com.cpa.tool.RandomUserListGenerator;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ControlServerMain {
    static String[] str = {"AverageLatency: ", "HitRatio: ", "Attacked HitRatio: ", "Covered User: "};
    private static List<String> mLines = new ArrayList<>();


    private static HttpServer httpServer;
    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newFixedThreadPool(30);


    private static int timePerRound = 1;
    private static int serversNumber = 20;
    private static int dataNumber = 30;
    private static int usersNumber = 200;
    private static int maxSpace = 10;
    private static double density = 1;
    private static double attackRatio = 0.6;
    private static int latencyLimit = 2;
    private static double k = 1;
    private static int networkArea = 20;
    private static int time = 20;

    private static final Map<Integer, Map<Integer, Integer>> serverRequestStats = new ConcurrentHashMap<>();
    private static final Map<Integer, String> edgeServerUrls = new ConcurrentHashMap<>();
    private static final Map<Integer, List<List<Integer>>> serverRequestLists = new ConcurrentHashMap<>();


    private static List<EdgeServer> servers;
    private static List<EdgeUser> users;
    private static int[][] distanceMatrix;
    private static int[][] adjacencyMatrix;
    private static int[] spaceLimits;
    private static int[] dataSizes;
    private static int[][] userBenefits;
    private static List<List<Integer>> currentStorage;

    public static void main(String[] args) {
        mLines.clear();

        initializeSystemComponents();
        startControlServer();
        startEdgeSystemThreads();
        processVirtualTimeRequests(users);
        writeResults("HttpCacheExperiment");
        shutdown();
    }

    private static void startControlServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(8000), 0);
            httpServer.createContext("/collect_request", new CollectRequestHandler());
            httpServer.createContext("/register_server", new RegisterServerHandler());
            httpServer.setExecutor(executor);
            httpServer.start();
            System.out.println("ControlServer start port: 8000");
        } catch (IOException e) {
            System.err.println("start ControlServer error: " + e.getMessage());
        }
    }


    private static void initializeSystemComponents() {
        spaceLimits = getSpaceLimits(100, maxSpace);
        dataSizes = getDataSizes(dataNumber);

        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" +
                "usersNumber = " + usersNumber + "\t" + "maxSpace = " + maxSpace + "\t" +
                "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" +
                "attackRatio = " + attackRatio);

        RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
        graphGenerator.createRandomGraph();
        distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
        adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

        RandomServersGenerator serversGenerator = new RandomServersGenerator();
        RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

        servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
        users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers,
                usersNumber, dataNumber, time);

        userBenefits = new int[serversNumber][users.size()];
        for (int i = 0; i < serversNumber; i++) {
            for (int j = 0; j < users.size(); j++) {
                int d = 999;
                for (int s : users.get(j).nearEdgeServers) {
                    if (distanceMatrix[i][s] <= d) {
                        d = distanceMatrix[i][s];
                    }
                }
                userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;
            }
        }

        currentStorage = new ArrayList<>();
        for (int data = 0; data < dataNumber; data++) {
            currentStorage.add(new ArrayList<>());
        }

        for (int i = 0; i < serversNumber; i++) {
            int usedSize = 0;
            while (usedSize <= spaceLimits[i]) {
                int data = ThreadLocalRandom.current().nextInt(dataNumber);
                usedSize += dataSizes[data];
                if (usedSize > spaceLimits[i]) break;
                else currentStorage.get(data).add(i);
            }
        }

    }

    private static void startEdgeSystemThreads() {

        List<Thread> serverThreads = new ArrayList<>();
        List<Thread> userThreads = new ArrayList<>();

        for (int i = 0; i < servers.size(); i++) {
            final EdgeServer server = servers.get(i);
            final int port = 5001 + i;
            final int finalI = i;

            Thread serverThread = new Thread(() -> {
                server.setId(finalI);
                server.startHttpServer(port);

                registerServerToControl(server.getId(), "http://localhost:" + port);

                System.out.println("EdgeServer " + server.getId() + " runing...");
            });

            serverThread.setName("EdgeServer-" + (i ));
            serverThreads.add(serverThread);
            serverThread.start();
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 0; i < users.size(); i++) {
            final EdgeUser user = users.get(i);
            final int userId = i;

            Thread userThread = new Thread(() -> {
                try {
                    String targetServerUrl = selectTargetServer(user, servers);
                    user.setTargetEdgeServer(targetServerUrl);
                    user.sendRequestsToEdgeServer(time);

                    System.out.println("EdgeUser " + userId + "complete sending ");
                } catch (Exception e) {
                    System.out.println("EdgeUser " + userId + " error: " + e.getMessage());
                } finally {
                    user.shutdown();
                }
            });

            userThread.setName("EdgeUser-" + userId);
            userThreads.add(userThread);
            userThread.start();
        }

        waitForAllUsersComplete(userThreads);

        shutdownServers(serverThreads);

    }

    private static void waitForAllUsersComplete(List<Thread> userThreads) {

        for (Thread userThread : userThreads) {
            try {
                userThread.join();
                System.out.println(userThread.getName() + " complete");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("intercept: " + e.getMessage());
            }
        }

    }

    private static void shutdownServers(List<Thread> serverThreads) {
        System.out.println("shut down EdgeServer...");

        for (EdgeServer server : servers) {
            try {
                server.shutdown();
            } catch (Exception e) {
                System.out.println("shut down EdgeServer " + server.getId() + " error: " + e.getMessage());
            }
        }

        for (Thread serverThread : serverThreads) {
            serverThread.interrupt();
        }
    }

    private static String selectTargetServer(EdgeUser user, List<EdgeServer> servers) {
        if (!user.nearEdgeServers.isEmpty()) {
            int serverId =  user.nearEdgeServers.get((user.nearEdgeServers.size() - 1) / 2);
            return "http://localhost:" + (5000 + serverId);
        }
        return "http://localhost:5001";
    }


    private static void registerServerToControl(int serverId, String serverUrl) {
        try {
            ServerRegistration registration = new ServerRegistration(serverId, serverUrl);
            String jsonBody = gson.toJson(registration);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8000/register_server"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Edge server " + serverId + " connected to ControlServer");

        } catch (Exception e) {
            System.err.println("connect error: " + e.getMessage());
        }
    }

    private static void processVirtualTimeRequests(List<EdgeUser> users) {
        for (int currentTime = 0; currentTime < 20; currentTime++) {
            System.out.println("processing time : " + currentTime);

            for (Integer serverId : serverRequestStats.keySet()) {
                List<Integer> currentTimeRequests = getCurrentTimeRequests(serverId, currentTime, users);

                if (currentTimeRequests != null && !currentTimeRequests.isEmpty()) {
                    serverRequestLists.computeIfAbsent(serverId, k -> new ArrayList<>())
                            .add(currentTimeRequests);

                    System.out.println("Edge server " + serverId + " in time " + currentTime +
                            " receive " + currentTimeRequests.size() + " requests");
                }
            }
        }
        int totalUsersNumber = users.size();
        List<List<Integer>> requestsList = new ArrayList<>();
        for (int i = 0; i < time; i++) {

            int requestsNumber = usersNumber;

            List<Integer> requests = new ArrayList<>();
            System.out.print("Time " + i + " has " + requestsNumber + "requests. They are:");

            while (requests.size() < requestsNumber) {
                int temp = ThreadLocalRandom.current().nextInt(totalUsersNumber);
                if (!requests.contains(temp)) {
                    requests.add(temp);
                    System.out.print(" " + temp);
                }
            }
            // System.out.println();
            requestsList.add(requests);
        }

        if (requestsList != null && !requestsList.isEmpty()) {
            runCacheAlgorithmsForServer(requestsList, serverRequestLists);
        }

    }

    private static List<Integer> getCurrentTimeRequests(int serverId, int currentTime, List<EdgeUser> users) {
        List<Integer> requests = new ArrayList<>();

        for (EdgeUser user : users) {
            if (currentTime < user.dataList.size()) {
                int requestedDataId = user.dataList.get(currentTime);

                if (isUserRequestingToServer(user, serverId, currentTime)) {
                    requests.add(requestedDataId);
                }
            }
        }

        return requests;
    }


    private static boolean isUserRequestingToServer(EdgeUser user, int serverId, int currentTime) {
        if (user.nearEdgeServers != null && !user.nearEdgeServers.isEmpty()) {
            int targetServerId = user.nearEdgeServers.get((user.nearEdgeServers.size() - 1) / 2);
            return targetServerId == serverId;
        }

        return false;
    }

    private static void runCacheAlgorithmsForServer(List<List<Integer>> requestsList,  Map<Integer, List<List<Integer>>> serverRequestLists) {

        try {
            CoverageFirstModel cfModel = new CoverageFirstModel(serversNumber, userBenefits,
                    distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber,
                    latencyLimit, time, requestsList, currentStorage, attackRatio);

            LatencyFirstModel lfModel = new LatencyFirstModel(serversNumber, userBenefits,
                    distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber,
                    latencyLimit, time, requestsList, currentStorage, attackRatio);

            OptimalModel optimalModel = new OptimalModel(serversNumber, userBenefits,
                    distanceMatrix, adjacencyMatrix, spaceLimits, dataSizes, users, servers,
                    dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);

            cfModel.runCoverage2();
//            lfModel.runLatency();
//            optimalModel.runOptimal();

            for (Integer serverId : serverRequestLists.keySet()) {
                List<Integer> cacheStrategy = extractCacheStrategy(cfModel);
                sendCacheStrategyToServer(serverId, cacheStrategy);

                recordAlgorithmResults(serverId, cfModel, lfModel, optimalModel);
            }

        } catch (Exception e) {
            System.err.println("calculate srategy failed: " + e.getMessage());
            e.printStackTrace();
        }
    }


    static class CollectRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = readRequestBody(exchange);
                ForwardRequest forwardRequest = gson.fromJson(requestBody, ForwardRequest.class);

                if (forwardRequest != null) {
                    processRequestBatch(forwardRequest);
                    sendResponse(exchange, "{\"status\":\"collected\"}");
                }
            }
        }
    }

    static class RegisterServerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = readRequestBody(exchange);
                ServerRegistration registration = gson.fromJson(requestBody, ServerRegistration.class);

                if (registration != null) {
                    edgeServerUrls.put(registration.serverId, registration.serverUrl);
                    System.out.println("ControlServer: register " + registration.serverId);
                    sendResponse(exchange, "{\"status\":\"registered\"}");
                }
            }
        }
    }

    private static void processRequestBatch(ForwardRequest forwardRequest) {
        int serverId = forwardRequest.edgeServerId;
        EdgeRequest edgeRequest = forwardRequest.originalRequest;
        int contentId = edgeRequest.dataId;
        int userId = edgeRequest.userId;

        Map<Integer, Integer> serverStats = serverRequestStats.computeIfAbsent(serverId,
                k -> new ConcurrentHashMap<>());
        serverStats.merge(contentId, 1, Integer::sum);

        List<List<Integer>> requestsList = serverRequestLists.computeIfAbsent(serverId,
                k -> new ArrayList<>());

        if (requestsList.size() <= time) {
            for (int i = requestsList.size(); i <= time; i++) {
                requestsList.add(new ArrayList<>());
            }
        }

        int currentTimeStep = Math.min(time - 1, requestsList.size() - 1);
        if (!requestsList.get(currentTimeStep).contains(userId)) {
            requestsList.get(currentTimeStep).add(userId);
        }

        System.out.println("ControlServer: process " + serverId + " 's request: " +
                userId + " data: " + contentId);
    }


    private static List<Integer> extractCacheStrategy(CoverageFirstModel model) {
        List<Integer> cacheContents = new ArrayList<>();

        try {
            List<List<Integer>> currentStorage = model.getCurrentStorage();

            if (currentStorage != null) {
                for (int dataId = 0; dataId < currentStorage.size(); dataId++) {
                    List<Integer> serversWithData = currentStorage.get(dataId);
                    if (serversWithData != null && !serversWithData.isEmpty()) {
                        cacheContents.add(dataId);
                    }
                }
            }

            if (cacheContents.isEmpty()) {
                Map<Integer, Integer> contentFreq = new HashMap<>();

                for (Map<Integer, Integer> serverStats : serverRequestStats.values()) {
                    for (Map.Entry<Integer, Integer> entry : serverStats.entrySet()) {
                        contentFreq.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }

                cacheContents = contentFreq.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(maxSpace)
                        .map(Map.Entry::getKey)
                        .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);
            }

        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            for (int i = 0; i < Math.min(maxSpace, dataNumber); i++) {
                cacheContents.add(i);
            }
        }

        System.out.println("caching stragety: " + cacheContents);
        return cacheContents;
    }


    private static void sendCacheStrategyToServer(int serverId, List<Integer> strategy) {
        if (strategy.isEmpty()) {
            return;
        }

        String serverUrl = edgeServerUrls.get(serverId);
        if (serverUrl == null) {
            serverUrl = "http://localhost:500" + serverId;
        }

        try {
            String jsonBody = gson.toJson(strategy);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/cache_update"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("ControlServer: send strategy to edge server " + serverId +
                        " strategy: " + strategy);

                String logEntry = "Server " + serverId + " Cache Strategy: " + strategy +
                        " at " + System.currentTimeMillis();
                mLines.add(logEntry);
            } else {
                System.err.println("ControlServer: send error: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("ControlServer: send error " + serverId + " failed - " + e.getMessage());
        }
    }

    private static void recordAlgorithmResults(int serverId, CoverageFirstModel cf,
                                               LatencyFirstModel lf, OptimalModel opt) {
        try {
            double cfAvgLatency = cf.getAverageLatency().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double cfHitRatio = cf.getHitRatio().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double cfCoveredUsers = cf.getCoveragedUsers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);

            double lfAvgLatency = lf.getAverageLatency().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double lfHitRatio = lf.getHitRatio().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double lfCoveredUsers = lf.getCoveragedUsers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);

            double optAvgLatency = opt.getAverageLatency().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double optHitRatio = opt.getHitRatio().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double optCoveredUsers = opt.getCoveragedUsers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);

            mLines.add("=== Edge server " + serverId + " result ===");
            mLines.add("CFM Results:");
            mLines.add("  Average Latency: " + String.format("%.4f", cfAvgLatency));
            mLines.add("  Hit Ratio: " + String.format("%.4f", cfHitRatio));
            mLines.add("  Covered Users: " + String.format("%.2f", cfCoveredUsers));

            mLines.add("LFM Results:");
            mLines.add("  Average Latency: " + String.format("%.4f", lfAvgLatency));
            mLines.add("  Hit Ratio: " + String.format("%.4f", lfHitRatio));
            mLines.add("  Covered Users: " + String.format("%.2f", lfCoveredUsers));

            mLines.add("Optimal Results:");
            mLines.add("  Average Latency: " + String.format("%.4f", optAvgLatency));
            mLines.add("  Hit Ratio: " + String.format("%.4f", optHitRatio));
            mLines.add("  Covered Users: " + String.format("%.2f", optCoveredUsers));


        } catch (Exception e) {
            System.err.println("record error: " + e.getMessage());
            mLines.add("Error recording results for server " + serverId + ": " + e.getMessage());
        }
    }


    private static String readRequestBody(HttpExchange exchange) throws IOException {
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

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
        }
    }

    private static int[] getSpaceLimits(int base, int maxSpace) {
        int[] limits = new int[serversNumber];
        for (int i = 0; i < serversNumber; i++) {
            limits[i] = Math.min(base, maxSpace);
        }
        return limits;
    }

    private static int[] getDataSizes(int dataNumber) {
        int[] sizes = new int[dataNumber];
        Random random = new Random();
        for (int i = 0; i < dataNumber; i++) {
            sizes[i] = 1 + random.nextInt(4);
        }
        return sizes;
    }

    private static void writeResults(String fileName) {
        try {
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
            String time = dateFormat.format(calendar.getTime());

            Path file = Paths.get(fileName + " " + time + ".txt");
            Files.write(file, mLines, Charset.forName("UTF-8"));
            System.out.println("write successed: " + file.toString());
        } catch (IOException e) {
            System.err.println("write failed: " + e.getMessage());
        }
    }

    private static void shutdown() {
        if (httpServer != null) {
            httpServer.stop(5);
        }
        executor.shutdown();
        System.out.println("ControlServer shut down");
    }
}

class ForwardRequest {
    public int edgeServerId;
    public EdgeRequest originalRequest;
    public long timestamp;
}

class ServerRegistration {
    public int serverId;
    public String serverUrl;

    public ServerRegistration(int serverId, String serverUrl) {
        this.serverId = serverId;
        this.serverUrl = serverUrl;
    }
}
