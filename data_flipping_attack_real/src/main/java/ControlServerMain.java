
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

    // HTTP服务器相关
    private static HttpServer httpServer;
    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newFixedThreadPool(30);

    // 系统参数
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

    // 收集的请求数据
    private static final Map<Integer, Map<Integer, Integer>> serverRequestStats = new ConcurrentHashMap<>();
    private static final Map<Integer, String> edgeServerUrls = new ConcurrentHashMap<>();
    private static final Map<Integer, List<List<Integer>>> serverRequestLists = new ConcurrentHashMap<>();

    // 系统组件
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

        // 2. 初始化系统组件和参数
        initializeSystemComponents();
        // 1. 启动ControlServer HTTP服务器
        startControlServer();
        // 3. 启动EdgeServer和EdgeUser线程
        startEdgeSystemThreads();

        // 4. 等待收集请求数据并运行缓存算法
        processVirtualTimeRequests(users);

        // 5. 写入结果
        writeResults("HttpCacheExperiment");

        // 6. 关闭服务器
        shutdown();
    }

    // 启动ControlServer
    private static void startControlServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(8000), 0);
            httpServer.createContext("/collect_request", new CollectRequestHandler());
            httpServer.createContext("/register_server", new RegisterServerHandler());
            httpServer.setExecutor(executor);
            httpServer.start();
            System.out.println("ControlServer启动在端口: 8000");
        } catch (IOException e) {
            System.err.println("启动ControlServer失败: " + e.getMessage());
        }
    }

    // 初始化系统组件
    private static void initializeSystemComponents() {
        spaceLimits = getSpaceLimits(100, maxSpace);
        dataSizes = getDataSizes(dataNumber);

        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" +
                "usersNumber = " + usersNumber + "\t" + "maxSpace = " + maxSpace + "\t" +
                "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" +
                "attackRatio = " + attackRatio);

        // 生成随机服务器图
        RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
        graphGenerator.createRandomGraph();
        distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
        adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

        RandomServersGenerator serversGenerator = new RandomServersGenerator();
        RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

        servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
        users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers,
                usersNumber, dataNumber, time);

        // 计算用户收益矩阵
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

        // 初始化当前存储
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
        System.out.println("启动EdgeServer和EdgeUser线程...");

        List<Thread> serverThreads = new ArrayList<>();
        List<Thread> userThreads = new ArrayList<>();

        // 启动EdgeServer线程
        for (int i = 0; i < servers.size(); i++) {
            final EdgeServer server = servers.get(i);
            final int port = 5001 + i;
            final int finalI = i;

            Thread serverThread = new Thread(() -> {
                server.setId(finalI);
                server.startHttpServer(port);

                // 注册到ControlServer
                registerServerToControl(server.getId(), "http://localhost:" + port);

                System.out.println("EdgeServer " + server.getId() + " 运行中...");
            });

            serverThread.setName("EdgeServer-" + (i ));
            serverThreads.add(serverThread);
            serverThread.start();
        }

        // 等待服务器启动
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 启动user线程
        for (int i = 0; i < users.size(); i++) {
            final EdgeUser user = users.get(i);
            final int userId = i;

            Thread userThread = new Thread(() -> {
                try {
                    String targetServerUrl = selectTargetServer(user, servers);
                    user.setTargetEdgeServer(targetServerUrl);
                    user.sendRequestsToEdgeServer(time);

                    System.out.println("EdgeUser " + userId + " 完成所有请求发送");
                } catch (Exception e) {
                    System.out.println("EdgeUser " + userId + " 发送请求时出错: " + e.getMessage());
                } finally {
                    // 关闭用户资源
                    user.shutdown();
                }
            });

            userThread.setName("EdgeUser-" + userId);
            userThreads.add(userThread);
            userThread.start();
        }

        System.out.println("所有EdgeServer和EdgeUser线程已启动");

        // 等待所有用户线程完成
        waitForAllUsersComplete(userThreads);

        // 关闭服务器
        shutdownServers(serverThreads);

        System.out.println("所有用户请求完成，系统关闭");
    }

    // 等待所有用户线程完成
    private static void waitForAllUsersComplete(List<Thread> userThreads) {
        System.out.println("等待所有用户完成请求发送...");

        for (Thread userThread : userThreads) {
            try {
                userThread.join(); // 等待线程完成
                System.out.println(userThread.getName() + " 已完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("等待用户线程时被中断: " + e.getMessage());
            }
        }

        System.out.println("所有用户线程已完成");
    }

    // 关闭服务器
    private static void shutdownServers(List<Thread> serverThreads) {
        System.out.println("关闭EdgeServer...");

        // 关闭服务器资源
        for (EdgeServer server : servers) {
            try {
                server.shutdown(); // 需要在EdgeServer中实现shutdown方法
            } catch (Exception e) {
                System.out.println("关闭EdgeServer " + server.getId() + " 时出错: " + e.getMessage());
            }
        }

        // 中断服务器线程
        for (Thread serverThread : serverThreads) {
            serverThread.interrupt();
        }
    }

    // 选择目标服务器
    private static String selectTargetServer(EdgeUser user, List<EdgeServer> servers) {
        if (!user.nearEdgeServers.isEmpty()) {
            int serverId =  user.nearEdgeServers.get((user.nearEdgeServers.size() - 1) / 2);
            return "http://localhost:" + (5000 + serverId);
        }
        return "http://localhost:5001";
    }

    // 注册服务器到ControlServer
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
            System.out.println("服务器 " + serverId + " 已注册到ControlServer");

        } catch (Exception e) {
            System.err.println("注册服务器失败: " + e.getMessage());
        }
    }

    private static void processVirtualTimeRequests(List<EdgeUser> users) {
        for (int currentTime = 0; currentTime < 20; currentTime++) {
            System.out.println("处理时间片: " + currentTime);

            // 收集当前时间片所有用户的请求
            for (Integer serverId : serverRequestStats.keySet()) {
                List<Integer> currentTimeRequests = getCurrentTimeRequests(serverId, currentTime, users);

                if (currentTimeRequests != null && !currentTimeRequests.isEmpty()) {
                    // 将当前时间片的请求添加到该服务器的请求历史中
                    serverRequestLists.computeIfAbsent(serverId, k -> new ArrayList<>())
                            .add(currentTimeRequests);

                    System.out.println("服务器 " + serverId + " 在时间片 " + currentTime +
                            " 收到 " + currentTimeRequests.size() + " 个请求");
                }
            }
        }
        int totalUsersNumber = users.size();
        List<List<Integer>> requestsList = new ArrayList<>();

        //随时间生成request数量
        //requestsList[时间步] = 用户编号列表
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

    // 同时修改getCurrentTimeRequests方法
    private static List<Integer> getCurrentTimeRequests(int serverId, int currentTime, List<EdgeUser> users) {
        List<Integer> requests = new ArrayList<>();

        // 遍历所有用户
        for (EdgeUser user : users) {
            // 检查用户在当前时间是否有请求
            if (currentTime < user.dataList.size()) {
                // 获取用户在currentTime时刻请求的数据ID
                int requestedDataId = user.dataList.get(currentTime);

                // 检查该用户是否向指定serverId发送请求
                if (isUserRequestingToServer(user, serverId, currentTime)) {
                    requests.add(requestedDataId);
                }
            }
        }

        return requests;
    }


    // 辅助方法：判断用户是否向指定服务器发送请求
    private static boolean isUserRequestingToServer(EdgeUser user, int serverId, int currentTime) {
        // 方法1：如果用户有nearEdgeServers列表
        if (user.nearEdgeServers != null && !user.nearEdgeServers.isEmpty()) {
            int targetServerId = user.nearEdgeServers.get((user.nearEdgeServers.size() - 1) / 2);
            return targetServerId == serverId;
        }

        return false; // 默认返回false
    }

    // 为特定服务器运行缓存算法
    private static void runCacheAlgorithmsForServer(List<List<Integer>> requestsList,  Map<Integer, List<List<Integer>>> serverRequestLists) {

        try {
            // 创建缓存模型
            CoverageFirstModel cfModel = new CoverageFirstModel(serversNumber, userBenefits,
                    distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber,
                    latencyLimit, time, requestsList, currentStorage, attackRatio);

            LatencyFirstModel lfModel = new LatencyFirstModel(serversNumber, userBenefits,
                    distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber,
                    latencyLimit, time, requestsList, currentStorage, attackRatio);

            OptimalModel optimalModel = new OptimalModel(serversNumber, userBenefits,
                    distanceMatrix, adjacencyMatrix, spaceLimits, dataSizes, users, servers,
                    dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);

            // 运行算法
            cfModel.runCoverage2();
//            lfModel.runLatency();
//            optimalModel.runOptimal();

            for (Integer serverId : serverRequestLists.keySet()) {
                // 获取缓存策略结果并发送
                List<Integer> cacheStrategy = extractCacheStrategy(cfModel);
                sendCacheStrategyToServer(serverId, cacheStrategy);

                // 记录结果
                recordAlgorithmResults(serverId, cfModel, lfModel, optimalModel);
            }

        } catch (Exception e) {
            System.err.println("运行缓存算法失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // HTTP请求处理器和其他方法...
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
                    System.out.println("ControlServer: 注册服务器 " + registration.serverId);
                    sendResponse(exchange, "{\"status\":\"registered\"}");
                }
            }
        }
    }

    // 其他辅助方法...
    private static void processRequestBatch(ForwardRequest forwardRequest) {
        int serverId = forwardRequest.edgeServerId;
        EdgeRequest edgeRequest = forwardRequest.originalRequest;
        int contentId = edgeRequest.dataId;
        int userId = edgeRequest.userId;

        // 更新服务器请求统计
        Map<Integer, Integer> serverStats = serverRequestStats.computeIfAbsent(serverId,
                k -> new ConcurrentHashMap<>());
        serverStats.merge(contentId, 1, Integer::sum);

        // 收集请求列表用于缓存策略计算
        List<List<Integer>> requestsList = serverRequestLists.computeIfAbsent(serverId,
                k -> new ArrayList<>());

        // 将用户ID添加到当前时间步的请求列表中
        if (requestsList.size() <= time) {
            for (int i = requestsList.size(); i <= time; i++) {
                requestsList.add(new ArrayList<>());
            }
        }

        // 添加用户请求到对应时间步
        int currentTimeStep = Math.min(time - 1, requestsList.size() - 1);
        if (!requestsList.get(currentTimeStep).contains(userId)) {
            requestsList.get(currentTimeStep).add(userId);
        }

        System.out.println("ControlServer: 处理服务器 " + serverId + " 的请求 - 用户: " +
                userId + " 内容: " + contentId);
    }


    private static List<Integer> extractCacheStrategy(CoverageFirstModel model) {
        List<Integer> cacheContents = new ArrayList<>();

        try {
            // 从模型的CurrentStorage中提取缓存策略
            List<List<Integer>> currentStorage = model.getCurrentStorage();

            if (currentStorage != null) {
                // 遍历所有数据，找出被缓存的内容
                for (int dataId = 0; dataId < currentStorage.size(); dataId++) {
                    List<Integer> serversWithData = currentStorage.get(dataId);
                    if (serversWithData != null && !serversWithData.isEmpty()) {
                        // 如果这个数据被至少一个服务器缓存，则添加到策略中
                        cacheContents.add(dataId);
                    }
                }
            }

            // 如果无法从CurrentStorage获取，则基于请求统计生成简单策略
            if (cacheContents.isEmpty()) {
                // 选择请求频率最高的前maxSpace个内容
                Map<Integer, Integer> contentFreq = new HashMap<>();

                // 统计内容请求频率
                for (Map<Integer, Integer> serverStats : serverRequestStats.values()) {
                    for (Map.Entry<Integer, Integer> entry : serverStats.entrySet()) {
                        contentFreq.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }

                // 按频率排序并选择top内容
                cacheContents = contentFreq.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(maxSpace)
                        .map(Map.Entry::getKey)
                        .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);
            }

        } catch (Exception e) {
            System.err.println("提取缓存策略失败: " + e.getMessage());
            // 返回默认策略
            for (int i = 0; i < Math.min(maxSpace, dataNumber); i++) {
                cacheContents.add(i);
            }
        }

        System.out.println("提取的缓存策略: " + cacheContents);
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
                System.out.println("ControlServer: 成功发送缓存策略到服务器 " + serverId +
                        " 策略: " + strategy);

                // 记录发送的策略
                String logEntry = "Server " + serverId + " Cache Strategy: " + strategy +
                        " at " + System.currentTimeMillis();
                mLines.add(logEntry);
            } else {
                System.err.println("ControlServer: 发送缓存策略失败，状态码: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("ControlServer: 发送缓存策略到服务器 " + serverId + " 失败 - " + e.getMessage());
        }
    }

    private static void recordAlgorithmResults(int serverId, CoverageFirstModel cf,
                                               LatencyFirstModel lf, OptimalModel opt) {
        try {
            // 记录CFM结果
            double cfAvgLatency = cf.getAverageLatency().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double cfHitRatio = cf.getHitRatio().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double cfCoveredUsers = cf.getCoveragedUsers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);

            // 记录LFM结果
            double lfAvgLatency = lf.getAverageLatency().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double lfHitRatio = lf.getHitRatio().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double lfCoveredUsers = lf.getCoveragedUsers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);

            // 记录Optimal结果
            double optAvgLatency = opt.getAverageLatency().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double optHitRatio = opt.getHitRatio().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);
            double optCoveredUsers = opt.getCoveragedUsers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0.0);

            // 添加到结果记录
            mLines.add("=== 服务器 " + serverId + " 算法结果 ===");
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
            System.err.println("记录算法结果失败: " + e.getMessage());
            mLines.add("Error recording results for server " + serverId + ": " + e.getMessage());
        }
    }


    // 工具方法
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
            System.out.println("结果已写入文件: " + file.toString());
        } catch (IOException e) {
            System.err.println("写入文件失败: " + e.getMessage());
        }
    }

    private static void shutdown() {
        if (httpServer != null) {
            httpServer.stop(5);
        }
        executor.shutdown();
        System.out.println("ControlServer已关闭");
    }
}

// 数据类
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
