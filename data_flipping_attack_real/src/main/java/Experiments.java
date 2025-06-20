import com.cpa.models.CoverageFirstModel;
import com.cpa.models.LatencyFirstModel;
import com.cpa.models.OptimalModel;
import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;
import com.cpa.tool.RandomGraphGenerator;
import com.cpa.tool.RandomServersGenerator;
import com.cpa.tool.RandomUserListGenerator;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Experiments {
    static String[] str = {"AverageLatency: ", "HitRatio: ", "Attacked HitRatio: ", "Covered User: "};
    private static List<String> mLines = new ArrayList<>();
    public static void main(String[] args) {
////
        mLines.clear();
        runExperiment();
        writeResults("Experiment");

//        mLines.clear();
//        runExperimentAttackRatio();
//        writeResults("AttackRatio");

//        mLines.clear();
//        runExperimentServers();
//        writeResults("Servers");

//        mLines.clear();
//        runExperimentDataNumber();
//        writeResults("DataNumber");

//        mLines.clear();
//        runExperimentMaxStorage();
//        writeResults("MaxStorage");
//
//        mLines.clear();
//        runExperimentEdgeDensity();
//        writeResults("EdgeDensity");

//        mLines.clear();
//        runExperimentUserNumber();
//        writeResults("UserNumber");

//        mLines.clear();
//        runExperimentLatency();
//        writeResults("Limit");
////
        mLines.clear();
        runExperimentDefense();
        writeResults("Defense");

    }

    private static String selectTargetServer(EdgeUser user, List<EdgeServer> servers) {
        // 根据用户的nearEdgeServers选择服务器
        if (!user.nearEdgeServers.isEmpty()) {
            int serverId = user.nearEdgeServers.get(0);
            return "http://localhost:" + (5000 + serverId);
        }
        // 默认选择第一个服务器
        return "http://localhost:5001";
    }

    private static void runExperiment() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 2;

        double k = 1;
        int networkAera = 20;

        int time = 20;

        int[] spaceLimits = getSpaceLimits(100, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);
        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);


        //generate random Servers Graph
        RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
        graphGenerator.createRandomGraph();
        int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
        int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

        RandomServersGenerator serversGenerator = new RandomServersGenerator();
        RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

        List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
        List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);


        List<Thread> threads = new ArrayList<>();
        // 启动EdgeServer线程
        for (int i = 0; i < servers.size(); i++) {
            final EdgeServer server = servers.get(i);
            final int port = 5001 + i;

            int finalI = i;
            Thread serverThread = new Thread(() -> {
                server.setId(finalI + 1);
                server.startHttpServer(port);
                System.out.println("EdgeServer " + server.getId() + " 运行中...");
            });

            serverThread.setName("EdgeServer-" + (i + 1));
            threads.add(serverThread);
            serverThread.start();
        }
        // 等待服务器启动
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 启动user线程

        for (int i = 0; i < users.size(); i++) {
            final EdgeUser user = users.get(i);
            final int userId = i;

            Thread userThread = new Thread(() -> {

                String targetServerUrl = selectTargetServer(user, servers);
                user.setTargetEdgeServer(targetServerUrl);
                user.generateRequests(time, dataNumber, "zipf");
                user.sendRequestsToEdgeServer(time);
            });

            userThread.setName("EdgeUser-" + userId);
            threads.add(userThread);
            userThread.start();
        }

        int[][] userBenefits = new int[serversNumber][users.size()];

        for (int i = 0; i < serversNumber; i++) {
            for (int j = 0; j < users.size(); j++) {
                int d = 999;
                for (int s : users.get(j).nearEdgeServers) {
                    if (distanceMatrix[i][s] <= d) {
                        d = distanceMatrix[i][s];
                    }
                }

                userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                System.out.print(userBenefits[i][j] + " ");
            }
            System.out.println();
        }

        List<List<Integer>> currentStorage = new ArrayList<>();
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

        //timePerRound次实验
        for (int ii = 0; ii < timePerRound; ii++) {

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

            CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//
            mCFModel.runCoverage();
//            mCFModelattack.runCoverageAttack();
            mCFModelattack.runCoverageRandomAttack();
            mLFModel.runLatency();
//            mLFModelattack.runLatencyAttack();
            mLFModelattack.runLatencyRandomAttack();
            mOptimalModel.runOptimal();
//            mOptimalModelattack.runOptimalAttack();
            mOptimalModelattack.runOptimalRandomAttack();

            List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
            List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
            List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
            List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();

            List<List<Double>> CFMsafe = new ArrayList<>();
            List<List<Double>> CFMattack = new ArrayList<>();
            List<List<Double>> LFMsafe = new ArrayList<>();
            List<List<Double>> LFMattack = new ArrayList<>();
            List<List<Double>> Optimalsafe = new ArrayList<>();
            List<List<Double>> OptimalAttack = new ArrayList<>();

            addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
            addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
            addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
            addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
            addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
            addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());

            CFMResultsSafe.add(CFMsafe);
            CFMResultsAttack.add(CFMattack);
            LFMResultsSafe.add(LFMsafe);
            LFMResultsAttack.add(LFMattack);
            OptimalResultsSafe.add(Optimalsafe);
            OptimalResultsAttack.add(OptimalAttack);

            System.out.println("CFMsafe: " + CFMResultsSafe);
            System.out.println("CFMattack: " + CFMResultsAttack);
            System.out.println("LFMsafe: " + LFMResultsSafe);
            System.out.println("LFMattack: " + LFMResultsAttack);
            System.out.println("Optimalsafe: " + OptimalResultsSafe);
            System.out.println("Optimalattack: " + OptimalResultsAttack);

            mLines.add("");
            mLines.add("--- Results ---");
            mLines.add("");

            int t = time - 1;
            int size = str.length;

            List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
            List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
            List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
            List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
            List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
            List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

            mLines.add("---- Average results when Attack Ratio = " + attackRatio + "----");
            calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
            calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
            calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
            calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
            calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
            calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
        }
    }

    private static void runExperimentAttackRatio() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.2;
        int latencyLimit = 2;


        double k = 1;
        int networkAera = 20;
        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        int time = 20;

        int[] spaceLimits = getSpaceLimits(100, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);


        //随机生成拓扑图
        RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
        graphGenerator.createRandomGraph();
        int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
        int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

        RandomServersGenerator serversGenerator = new RandomServersGenerator();
        RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
        List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
        List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

        int[][] userBenefits = new int[serversNumber][users.size()];

        for (int i = 0; i < serversNumber; i++) {
            for (int j = 0; j < users.size(); j++) {
                int d = 999;
                for (int s : users.get(j).nearEdgeServers) {
                    if (distanceMatrix[i][s] <= d) {
                        d = distanceMatrix[i][s];
                    }
                }

                userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                System.out.print(userBenefits[i][j] + " ");
            }
            System.out.println();
        }

        List<List<Integer>> currentStorage = new ArrayList<>();
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

        for (int jj = 0; jj < 5; jj++) {
            System.out.println(jj + " --- ");

            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when Attack Ratio = " + attackRatio + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                attackRatio = attackRatio + 0.2;
            }
        }
    }

    private static void runExperimentServers() {

        int timePerRound = 1;

        int serversNumber = 10;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 2;


        double k = 1;
        int networkAera = 20;

        int time = 20;
        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        int[] spaceLimits = getSpaceLimits(100, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);

        for (int jj = 0; jj < 5; jj++) {
            //随机生成拓扑图
            RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
            graphGenerator.createRandomGraph();
            int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
            int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

            RandomServersGenerator serversGenerator = new RandomServersGenerator();
            RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
            List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
            List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

            int[][] userBenefits = new int[serversNumber][users.size()];

            for (int i = 0; i < serversNumber; i++) {
                for (int j = 0; j < users.size(); j++) {
                    int d = 999;
                    for (int s : users.get(j).nearEdgeServers) {
                        if (distanceMatrix[i][s] <= d) {
                            d = distanceMatrix[i][s];
                        }
                    }

                    userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                    System.out.print(userBenefits[i][j] + " ");
                }
                System.out.println();
            }

            List<List<Integer>> currentStorage = new ArrayList<>();
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


            System.out.println(jj + " --- ");

            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when Server Number = " + serversNumber + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                serversNumber = serversNumber + 5;
            }
        }
    }

    private static void runExperimentDataNumber() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 10;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 2;


        double k = 1;
        int networkAera = 20;

        int time = 20;
        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        int[] spaceLimits = getSpaceLimits(100, maxSpace);


        for (int jj = 0; jj < 5; jj++) {
            int[] dataSizes = getDataSizes(dataNumber);
            //随机生成拓扑图
            RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
            graphGenerator.createRandomGraph();
            int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
            int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

            RandomServersGenerator serversGenerator = new RandomServersGenerator();
            RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
            List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
            List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

            int[][] userBenefits = new int[serversNumber][users.size()];

            for (int i = 0; i < serversNumber; i++) {
                for (int j = 0; j < users.size(); j++) {
                    int d = 999;
                    for (int s : users.get(j).nearEdgeServers) {
                        if (distanceMatrix[i][s] <= d) {
                            d = distanceMatrix[i][s];
                        }
                    }

                    userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                    System.out.print(userBenefits[i][j] + " ");
                }
                System.out.println();
            }

            List<List<Integer>> currentStorage = new ArrayList<>();
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


            System.out.println(jj + " --- ");

            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when Data Number = " + dataNumber + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                dataNumber = dataNumber + 10;
            }
        }
    }

    private static void runExperimentEdgeDensity() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 2;


        double k = 1;
        int networkAera = 20;

        int time = 20;

        int[] spaceLimits = getSpaceLimits(100, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);
        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        for (int jj = 0; jj < 5; jj++) {
            System.out.println(jj + " --- ");
            //随机生成拓扑图
            RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
            graphGenerator.createRandomGraph();
            int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
            int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

            RandomServersGenerator serversGenerator = new RandomServersGenerator();
            RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
            List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
            List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

            int[][] userBenefits = new int[serversNumber][users.size()];

            for (int i = 0; i < serversNumber; i++) {
                for (int j = 0; j < users.size(); j++) {
                    int d = 999;
                    for (int s : users.get(j).nearEdgeServers) {
                        if (distanceMatrix[i][s] <= d) {
                            d = distanceMatrix[i][s];
                        }
                    }

                    userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                    System.out.print(userBenefits[i][j] + " ");
                }
                System.out.println();
            }

            List<List<Integer>> currentStorage = new ArrayList<>();
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


            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when Edge Desity = " + density + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                density = density + 0.5;
            }
        }
    }

    private static void runExperimentMaxStorage() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 2;

        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        double k = 1;
        int networkAera = 20;

        int time = 20;


        int[] dataSizes = getDataSizes(dataNumber);

        for (int jj = 0; jj < 5; jj++) {

            int[] spaceLimits = getSpaceLimits(100, maxSpace);
            //随机生成拓扑图
            RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
            graphGenerator.createRandomGraph();
            int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
            int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

            RandomServersGenerator serversGenerator = new RandomServersGenerator();
            RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
            List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
            List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

            int[][] userBenefits = new int[serversNumber][users.size()];

            for (int i = 0; i < serversNumber; i++) {
                for (int j = 0; j < users.size(); j++) {
                    int d = 999;
                    for (int s : users.get(j).nearEdgeServers) {
                        if (distanceMatrix[i][s] <= d) {
                            d = distanceMatrix[i][s];
                        }
                    }

                    userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                    System.out.print(userBenefits[i][j] + " ");
                }
                System.out.println();
            }

            List<List<Integer>> currentStorage = new ArrayList<>();
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


            System.out.println(jj + " --- ");

            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when Max Space = " + maxSpace + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                maxSpace = maxSpace + 2;
            }
        }
    }

    private static void runExperimentLatency() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 1;


        double k = 1;
        int networkAera = 20;

        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        int time = 20;

        int[] spaceLimits = getSpaceLimits(50, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);

        for (int jj = 0; jj < 3; jj++) {
            System.out.println(jj + " --- ");
            //随机生成拓扑图
            RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
            graphGenerator.createRandomGraph();
            int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
            int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

            RandomServersGenerator serversGenerator = new RandomServersGenerator();
            RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
            List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
            List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

            int[][] userBenefits = new int[serversNumber][users.size()];

            for (int i = 0; i < serversNumber; i++) {
                for (int j = 0; j < users.size(); j++) {
                    int d = 999;
                    for (int s : users.get(j).nearEdgeServers) {
                        if (distanceMatrix[i][s] <= d) {
                            d = distanceMatrix[i][s];
                        }
                    }

                    userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                    System.out.print(userBenefits[i][j] + " ");
                }
                System.out.println();
            }

            List<List<Integer>> currentStorage = new ArrayList<>();
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

            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when Latency Limit = " + latencyLimit + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                latencyLimit = latencyLimit + 1;
            }
        }
    }

    private static void runExperimentUserNumber() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 100;
        int maxSpace = 10;
        double density = 1;
        double attackRatio = 0.6;
        int latencyLimit = 2;


        double k = 1;
        int networkAera = 20;

        int time = 20;

        int[] spaceLimits = getSpaceLimits(100, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);

        for (int jj = 0; jj < 5; jj++) {
            System.out.println(jj + " --- ");
            //随机生成拓扑图
            RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
            graphGenerator.createRandomGraph();
            int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
            int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

            RandomServersGenerator serversGenerator = new RandomServersGenerator();
            RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

//                List<EdgeServer> servers = serversGenerator.generateEdgeServers(serversNumber, networkAera);
//                List<EdgeUser> users = userListGenerator.generateUsers(serversNumber, usersNumber, networkAera, servers, dataNumber, time);
            List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
            List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

            int[][] userBenefits = new int[serversNumber][users.size()];

            for (int i = 0; i < serversNumber; i++) {
                for (int j = 0; j < users.size(); j++) {
                    int d = 999;
                    for (int s : users.get(j).nearEdgeServers) {
                        if (distanceMatrix[i][s] <= d) {
                            d = distanceMatrix[i][s];
                        }
                    }

                    userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                    System.out.print(userBenefits[i][j] + " ");
                }
                System.out.println();
            }

            List<List<Integer>> currentStorage = new ArrayList<>();
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


            //timePerRound次实验
            for (int ii = 0; ii < timePerRound; ii++) {

                int totalUsersNumber = users.size();
                List<List<Integer>> requestsList = new ArrayList<>();

                //随时间生成request数量
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

//                RCCModel rccModel = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelDefense = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                RCCModel rccModelattack = new RCCModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
                CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
                OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                        dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
//                LRUModel lruModel = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//                LRUModel lruModelattack = new LRUModel(serversNumber, userBenefits, distanceMatrix, spaceLimits, dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, k, attackRatio);
//
//                rccModel.runRCC();
//                rccModelDefense.runRCCDefense();
//                rccModelattack.runRCCAttack();
                mCFModel.runCoverage();
                mCFModelattack.runCoverageAttack();
                mLFModel.runLatency();
                mLFModelattack.runLatencyAttack();
                mOptimalModel.runOptimal();
                mOptimalModelattack.runOptimalAttack();
//                mOptimalModel.runCEDCO();

//                lruModel.runLRU();
//                lruModelattack.runLRUAttack();

//                List<List<List<Double>>> RCCResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsDefense = new ArrayList<>();
//                List<List<List<Double>>> RCCResultsAttack = new ArrayList<>();
                List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
                List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
                List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsSafe = new ArrayList<>();
//                List<List<List<Double>>> LRUResultsAttack = new ArrayList<>();

                List<List<Double>> RCCsafe = new ArrayList<>();
                List<List<Double>> RCCattack = new ArrayList<>();
                List<List<Double>> RCCdefense = new ArrayList<>();
                List<List<Double>> CFMsafe = new ArrayList<>();
                List<List<Double>> CFMattack = new ArrayList<>();
                List<List<Double>> LFMsafe = new ArrayList<>();
                List<List<Double>> LFMattack = new ArrayList<>();
                List<List<Double>> Optimalsafe = new ArrayList<>();
                List<List<Double>> OptimalAttack = new ArrayList<>();
                List<List<Double>> LRUsafe = new ArrayList<>();
                List<List<Double>> LRUattack = new ArrayList<>();

//                addResult(RCCsafe, rccModel.getAverageLatency(), rccModel.getAttackedHitRatio(), rccModel.getCoveragedUsers(), rccModel.getHitRatio());
//                addResult(RCCdefense, rccModelDefense.getAverageLatency(), rccModelDefense.getAverageRevenue(), rccModelDefense.getCoveragedUsers(), rccModelDefense.getHitRatio());
//                addResult(RCCattack, rccModelattack.getAverageLatency(), rccModelattack.getAttackedHitRatio(), rccModelattack.getCoveragedUsers(), rccModelattack.getHitRatio());
                addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
                addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
                addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
                addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
                addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
                addResult(OptimalAttack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
//                addResult(LRUsafe, lruModel.getAverageLatency(), lruModel.getHitRatio(), lruModel.getHitRatio(), lruModel.getHitRatio());
//                addResult(LRUattack, lruModelattack.getAverageLatency(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio(), lruModelattack.getHitRatio());

//                RCCResultsSafe.add(RCCsafe);
//                RCCResultsDefense.add(RCCdefense);
//                RCCResultsAttack.add(RCCattack);
                CFMResultsSafe.add(CFMsafe);
                CFMResultsAttack.add(CFMattack);
                LFMResultsSafe.add(LFMsafe);
                LFMResultsAttack.add(LFMattack);
                OptimalResultsSafe.add(Optimalsafe);
                OptimalResultsAttack.add(OptimalAttack);
//                LRUResultsSafe.add(LRUsafe);
//                LRUResultsAttack.add(LRUattack);

//                System.out.println("RCCsafe: " + RCCResultsSafe);
//                System.out.println("RCCdesense: " + RCCResultsDefense);
//                System.out.println("RCCattack: " + RCCResultsAttack);
                System.out.println("CFMsafe: " + CFMResultsSafe);
                System.out.println("CFMattack: " + CFMResultsAttack);
                System.out.println("LFMsafe: " + LFMResultsSafe);
                System.out.println("LFMattack: " + LFMResultsAttack);
                System.out.println("Optimalsafe: " + OptimalResultsSafe);
                System.out.println("Optimalattack: " + OptimalResultsAttack);
//                System.out.println("LRUSafe: " + LRUResultsSafe);
//                System.out.println("LRUAttack: " + LRUResultsAttack);

                mLines.add("");
                mLines.add("--- Results ---");
                mLines.add("");

                int t = time - 1;
                int size = str.length;

//                List<List<Double>> avRCCSafe = getAverageResults(RCCResultsSafe, size, t, timePerRound);
//                List<List<Double>> avRCCDefense = getAverageResults(RCCResultsDefense, size, t, timePerRound);
//                List<List<Double>> avRCCAttack = getAverageResults(RCCResultsAttack, size, t, timePerRound);
                List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
                List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
                List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
                List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);

//                List<List<Double>> avLRUSafe = getAverageResults(LRUResultsSafe, size, t, timePerRound);
//                List<List<Double>> avLRUAttack = getAverageResults(LRUResultsAttack, size, t, timePerRound);


//                addARToLines(" ------ RCCSafe ------ " , str, avRCCSafe, size, t);
//                addARToLines(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                addARToLines(" ------ RCCAttack ------" , str, avRCCAttack, size, t);
//                addARToLines(" ------ CFMSafe ------", str, avCFMSafe, size, t);
//                addARToLines(" ------ CFMAttack ------", str, avCFMAttack, size, t);
//                addARToLines(" ------ LFMSafe ------", str, avLFMSafe, size, t);
//                addARToLines(" ------ LFMAttack ------", str, avLFMAttack, size, t);
//                addARToLines(" ------ OptimalSafe ------", str, avOptimalSafe, size, t);
//                addARToLines(" ------ OptimalAttack ------", str, avOptimalAttack, size, t);
//                addARToLines(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                addARToLines(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                mLines.add("---- Average results when User Number = " + usersNumber + "----");
//                calculateTotalAverageResults(" ------ RCCSafe ------", str, avRCCSafe, size, t);
//                calculateTotalAverageResults(" ------ RCCSafeDefense ------ maxTime = " + maxTimeRCCSafe, str, avRCCDefense, size, t);
//                calculateTotalAverageResults(" ------ RCCAttack ------ ", str, avRCCAttack, size, t);
                calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
                calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
                calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
                calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
                calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
                calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
//                calculateTotalAverageResults(" ------ LRUSafe ------ maxTime = " + maxTimeRCCAttack, str, avLRUSafe, size, t);
//                calculateTotalAverageResults(" ------ LRUAttack ------ maxTime = " + maxTimeRCCAttack, str, avLRUAttack, size, t);

                usersNumber = usersNumber + 50;
            }
        }
    }


    public static int[] getSpaceLimits(int serversNumber, int maxSpace) {

        int[] spaceLimit = new int[serversNumber];

        for (int i = 0; i < serversNumber; i++) {
            spaceLimit[i] = -1;
            while (spaceLimit[i] <= 0) {
//                spaceLimit[i] = (int) (ThreadLocalRandom.current().nextGaussian() + maxSpace);
                spaceLimit[i] = maxSpace;

                // System.out.println("Server " + i + " has space " + spaceLimit[i]);
                // spaceLimit[i] = 3;
            }

        }

        return spaceLimit;
    }

    public static int[] getDataSizes(int dataNumber) {
        int[] dataSizes = new int[dataNumber];
        for (int i = 0; i < dataNumber; i++) {
//            dataSizes[i] = 1;
            dataSizes[i] = ThreadLocalRandom.current().nextInt(1, 3);
        }
        return dataSizes;
    }

    private static void runExperimentDefense() {

        int timePerRound = 1;

        int serversNumber = 20;
        int dataNumber = 30;
        int usersNumber = 200;
        int maxSpace = 10;
        double density = 2;
        double attackRatio = 0.6;
        int latencyLimit = 2;


        int time = 20;

        int[] spaceLimits = getSpaceLimits(100, maxSpace);
        int[] dataSizes = getDataSizes(dataNumber);
        mLines.add("serversNumber = " + serversNumber + "\t" + "dataNumber = " + dataNumber + "\t" + "usersNumber = " + usersNumber + "\t" +
                "maxSpace = " + maxSpace + "\t" + "density = " + density + "\t" + "latencyLimit = " + latencyLimit + "\t" + "attackRatio = " + attackRatio);

        for (int jj = 0; jj < 1; jj++) {
            System.out.println(jj + " --- ");
        //随机生成拓扑图
        RandomGraphGenerator graphGenerator = new RandomGraphGenerator(serversNumber, density);
        graphGenerator.createRandomGraph();
        int[][] distanceMatrix = graphGenerator.getRandomGraphDistanceMatrix();
        int[][] adjacencyMatrix = graphGenerator.getRandomGraphAdjacencyMatrix();

        RandomServersGenerator serversGenerator = new RandomServersGenerator();
        RandomUserListGenerator userListGenerator = new RandomUserListGenerator();

        List<EdgeServer> servers = serversGenerator.generateEdgeServerListFromRealWorldData(serversNumber);
        List<EdgeUser> users = userListGenerator.generateUserListFromRealWorldData(serversNumber, servers, usersNumber, dataNumber, time);

        int[][] userBenefits = new int[serversNumber][users.size()];

        for (int i = 0; i < serversNumber; i++) {
            for (int j = 0; j < users.size(); j++) {
                int d = 999;
                for (int s : users.get(j).nearEdgeServers) {
                    if (distanceMatrix[i][s] <= d) {
                        d = distanceMatrix[i][s];
                    }
                }

                userBenefits[i][j] = d > latencyLimit ? 0 : latencyLimit - d;

                System.out.print(userBenefits[i][j] + " ");
            }
            System.out.println();
        }

        List<List<Integer>> currentStorage = new ArrayList<>();
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

        //timePerRound次实验
        for (int ii = 0; ii < timePerRound; ii++) {

            int totalUsersNumber = users.size();
            List<List<Integer>> requestsList = new ArrayList<>();

            //随时间生成request数量
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

            CoverageFirstModel mCFModel = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            CoverageFirstModel mCFModelattack = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            CoverageFirstModel mCFModelDefenseCon = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            CoverageFirstModel mCFModelDefenseNoise = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            CoverageFirstModel mCFModelDefenseSafe = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            CoverageFirstModel mCFModelSafeCon = new CoverageFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);

            LatencyFirstModel mLFModel = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModelattack = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModelDefenseCon = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModelDefenseNoise = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModelDefenseSafe = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            LatencyFirstModel mLFModelSafeCon = new LatencyFirstModel(serversNumber, userBenefits, distanceMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);

            OptimalModel mOptimalModel = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModelattack = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModelDefenseCon = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModelDefenseNoise = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModelDefenseSafe = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
            OptimalModel mOptimalModelSafeCon = new OptimalModel(serversNumber, userBenefits, distanceMatrix, adjacencyMatrix, spaceLimits,
                    dataSizes, users, servers, dataNumber, latencyLimit, time, requestsList, currentStorage, attackRatio);
////
            mCFModel.runCoverage();
//            mCFModelattack.runCoverageAttack();
//            mCFModel.runCoverage2();
            mCFModelattack.runCoverageAttack2();
//            mCFModelattack.runCoverageRandomAttack();;
            mCFModelDefenseCon.runCoverageDefenseConvention();
            mCFModelDefenseNoise.runCoverageAttackDefense();
//            mCFModelDefenseSafe.runCoverageSafeDefense();
            mCFModelSafeCon.runCoverageSafeCon();


            mLFModel.runLatency();
            mLFModelattack.runLatencyAttack();
//            mLFModelattack.runLatencyRandomAttack();
            mLFModelDefenseCon.runLatencyDefenseConvention();
            mLFModelDefenseNoise.runLatencyAttackDefense();
            mLFModelDefenseSafe.runLatencySafeDefense();
            mLFModelSafeCon.runLatencySafeCon();

            mOptimalModel.runOptimal();
            mOptimalModelattack.runOptimalAttack();
//            mOptimalModelattack.runOptimalRandomAttack();
            mOptimalModelDefenseCon.runOptimalDefenseCon();
            mOptimalModelDefenseNoise.runOptimalAttackDefense();
            mOptimalModelDefenseSafe.runOptimalSafeDefense();
            mOptimalModelSafeCon.runOptimalSafeCon();


            List<List<List<Double>>> CFMResultsSafe = new ArrayList<>();
            List<List<List<Double>>> CFMResultsAttack = new ArrayList<>();
            List<List<List<Double>>> CFMResultsDefenseCon = new ArrayList<>();
            List<List<List<Double>>> CFMResultsDefenseNoise = new ArrayList<>();
            List<List<List<Double>>> CFMResultsDefenseSafe = new ArrayList<>();
            List<List<List<Double>>> CFMResultsSafeCon = new ArrayList<>();

            List<List<List<Double>>> LFMResultsSafe = new ArrayList<>();
            List<List<List<Double>>> LFMResultsAttack = new ArrayList<>();
            List<List<List<Double>>> LFMResultsDefenseCon = new ArrayList<>();
            List<List<List<Double>>> LFMResultsDefenseNoise = new ArrayList<>();
            List<List<List<Double>>> LFMResultsDefenseSafe = new ArrayList<>();
            List<List<List<Double>>> LFMResultsSafeCon = new ArrayList<>();

            List<List<List<Double>>> OptimalResultsSafe = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsAttack = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsDefenseCon = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsDefenseNoise = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsDefenseSafe = new ArrayList<>();
            List<List<List<Double>>> OptimalResultsSafeCon = new ArrayList<>();


            List<List<Double>> CFMsafe = new ArrayList<>();
            List<List<Double>> CFMattack = new ArrayList<>();
            List<List<Double>> CFMdefenseCon = new ArrayList<>();
            List<List<Double>> CFMdefenseNoise = new ArrayList<>();
            List<List<Double>> CFMdefenseSafe = new ArrayList<>();
            List<List<Double>> CFMsafeCon = new ArrayList<>();
//
            List<List<Double>> LFMsafe = new ArrayList<>();
            List<List<Double>> LFMattack = new ArrayList<>();
            List<List<Double>> LFMdefenseCon = new ArrayList<>();
            List<List<Double>> LFMdefenseNoise = new ArrayList<>();
            List<List<Double>> LFMdefenseSafe = new ArrayList<>();
            List<List<Double>> LFMsafeCon = new ArrayList<>();

            List<List<Double>> Optimalsafe = new ArrayList<>();
            List<List<Double>> Optimalattack = new ArrayList<>();
            List<List<Double>> OptimaldefenseCon = new ArrayList<>();
            List<List<Double>> OptimaldefenseNoise = new ArrayList<>();
            List<List<Double>> OptimaldefenseSafe = new ArrayList<>();
            List<List<Double>> OptimalsafeCon = new ArrayList<>();
//
            addResult(CFMsafe, mCFModel.getAverageLatency(), mCFModel.getHitRatio(), mCFModel.getHitRatioAttacked(), mCFModel.getCoveragedUsers());
            addResult(CFMattack, mCFModelattack.getAverageLatency(), mCFModelattack.getHitRatio(), mCFModelattack.getHitRatioAttacked(), mCFModelattack.getCoveragedUsers());
            addResult(CFMdefenseCon, mCFModelDefenseCon.getAverageLatency(), mCFModelDefenseCon.getHitRatio(), mCFModelDefenseCon.getHitRatioAttacked(), mCFModelDefenseCon.getCoveragedUsers());
            addResult(CFMdefenseNoise, mCFModelDefenseNoise.getAverageLatency(), mCFModelDefenseNoise.getHitRatio(), mCFModelDefenseNoise.getHitRatioAttacked(), mCFModelDefenseNoise.getCoveragedUsers());
            addResult(CFMdefenseSafe, mCFModelDefenseSafe.getAverageLatency(), mCFModelDefenseSafe.getHitRatio(), mCFModelDefenseSafe.getHitRatioAttacked(), mCFModelDefenseSafe.getCoveragedUsers());
            addResult(CFMsafeCon, mCFModelSafeCon.getAverageLatency(), mCFModelSafeCon.getHitRatio(), mCFModelSafeCon.getHitRatioAttacked(), mCFModelSafeCon.getCoveragedUsers());

            addResult(LFMsafe, mLFModel.getAverageLatency(), mLFModel.getHitRatio(), mLFModel.getHitRatioAttacked(), mLFModel.getCoveragedUsers());
            addResult(LFMattack, mLFModelattack.getAverageLatency(), mLFModelattack.getHitRatio(), mLFModelattack.getHitRatioAttacked(), mLFModelattack.getCoveragedUsers());
            addResult(LFMdefenseCon, mLFModelDefenseCon.getAverageLatency(), mLFModelDefenseCon.getHitRatio(), mLFModelDefenseCon.getHitRatioAttacked(), mLFModelDefenseCon.getCoveragedUsers());
            addResult(LFMdefenseNoise, mLFModelDefenseNoise.getAverageLatency(), mLFModelDefenseNoise.getHitRatio(), mLFModelDefenseNoise.getHitRatioAttacked(), mLFModelDefenseNoise.getCoveragedUsers());
            addResult(LFMdefenseSafe, mLFModelDefenseSafe.getAverageLatency(), mLFModelDefenseSafe.getHitRatio(), mLFModelDefenseSafe.getHitRatioAttacked(), mLFModelDefenseSafe.getCoveragedUsers());
            addResult(LFMsafeCon, mLFModelSafeCon.getAverageLatency(), mLFModelSafeCon.getHitRatio(), mLFModelSafeCon.getHitRatioAttacked(), mLFModelSafeCon.getCoveragedUsers());

            addResult(Optimalsafe, mOptimalModel.getAverageLatency(), mOptimalModel.getHitRatio(), mOptimalModel.getHitRatioAttacked(), mOptimalModel.getCoveragedUsers());
            addResult(Optimalattack, mOptimalModelattack.getAverageLatency(), mOptimalModelattack.getHitRatio(), mOptimalModelattack.getHitRatioAttacked(), mOptimalModelattack.getCoveragedUsers());
            addResult(OptimaldefenseCon, mOptimalModelDefenseCon.getAverageLatency(), mOptimalModelDefenseCon.getHitRatio(), mOptimalModelDefenseCon.getHitRatioAttacked(), mOptimalModelDefenseCon.getCoveragedUsers());
            addResult(OptimaldefenseNoise, mOptimalModelDefenseNoise.getAverageLatency(), mOptimalModelDefenseNoise.getHitRatio(), mOptimalModelDefenseNoise.getHitRatioAttacked(), mOptimalModelDefenseNoise.getCoveragedUsers());
            addResult(OptimaldefenseSafe, mOptimalModelDefenseSafe.getAverageLatency(), mOptimalModelDefenseSafe.getHitRatio(), mOptimalModelDefenseSafe.getHitRatioAttacked(), mOptimalModelDefenseSafe.getCoveragedUsers());
            addResult(OptimalsafeCon, mOptimalModelSafeCon.getAverageLatency(), mOptimalModelSafeCon.getHitRatio(), mOptimalModelSafeCon.getHitRatioAttacked(), mOptimalModelSafeCon.getCoveragedUsers());


//
            CFMResultsSafe.add(CFMsafe);
            CFMResultsAttack.add(CFMattack);
            CFMResultsDefenseCon.add(CFMdefenseCon);
            CFMResultsDefenseNoise.add(CFMdefenseNoise);
            CFMResultsDefenseSafe.add(CFMdefenseSafe);
            CFMResultsSafeCon.add(CFMsafeCon);
//
            LFMResultsSafe.add(LFMsafe);
            LFMResultsAttack.add(LFMattack);
            LFMResultsDefenseCon.add(LFMdefenseCon);
            LFMResultsDefenseNoise.add(LFMdefenseNoise);
            LFMResultsDefenseSafe.add(LFMdefenseSafe);
            LFMResultsSafeCon.add(LFMsafeCon);

            OptimalResultsSafe.add(Optimalsafe);
            OptimalResultsAttack.add(Optimalattack);
            OptimalResultsDefenseCon.add(OptimaldefenseCon);
            OptimalResultsDefenseNoise.add(OptimaldefenseNoise);
            OptimalResultsDefenseSafe.add(OptimaldefenseSafe);
            OptimalResultsSafeCon.add(OptimalsafeCon);

            System.out.println("CFMsafe: " + CFMResultsSafe);
            System.out.println("CFMattack: " + CFMResultsAttack);
            System.out.println("LFMsafe: " + LFMResultsSafe);
            System.out.println("LFMattack: " + LFMResultsAttack);
            System.out.println("Optimalsafe: " + OptimalResultsSafe);
            System.out.println("Optimalattack: " + OptimalResultsAttack);


            mLines.add("");
            mLines.add("--- Results ---");
            mLines.add("");

            int t = time - 1;
            int size = str.length;

            List<List<Double>> avCFMSafe = getAverageResults(CFMResultsSafe, size, t, timePerRound);
            List<List<Double>> avCFMAttack = getAverageResults(CFMResultsAttack, size, t, timePerRound);
            List<List<Double>> avCFMDefenseCon = getAverageResults(CFMResultsDefenseCon, size, t, timePerRound);
            List<List<Double>> avCFMDefenseNoise = getAverageResults(CFMResultsDefenseNoise, size, t, timePerRound);
            List<List<Double>> avCFMDefenseSafe = getAverageResults(CFMResultsDefenseSafe, size, t, timePerRound);
            List<List<Double>> avCFMSafeCon = getAverageResults(CFMResultsSafeCon, size, t, timePerRound);
//
            List<List<Double>> avLFMSafe = getAverageResults(LFMResultsSafe, size, t, timePerRound);
            List<List<Double>> avLFMAttack = getAverageResults(LFMResultsAttack, size, t, timePerRound);
            List<List<Double>> avLFMDefenseCon = getAverageResults(LFMResultsDefenseCon, size, t, timePerRound);
            List<List<Double>> avLFMDefenseNoise = getAverageResults(LFMResultsDefenseNoise, size, t, timePerRound);
            List<List<Double>> avLFMDefenseSafe = getAverageResults(LFMResultsDefenseSafe, size, t, timePerRound);
            List<List<Double>> avLFMSafeCon = getAverageResults(LFMResultsSafeCon, size, t, timePerRound);

            List<List<Double>> avOptimalSafe = getAverageResults(OptimalResultsSafe, size, t, timePerRound);
            List<List<Double>> avOptimalAttack = getAverageResults(OptimalResultsAttack, size, t, timePerRound);
            List<List<Double>> avOptimalDefenseCon = getAverageResults(OptimalResultsDefenseCon, size, t, timePerRound);
            List<List<Double>> avOptimalDefenseNoise = getAverageResults(OptimalResultsDefenseNoise, size, t, timePerRound);
            List<List<Double>> avOptimalDefenseSafe = getAverageResults(OptimalResultsDefenseSafe, size, t, timePerRound);
            List<List<Double>> avOptimalSafeCon = getAverageResults(OptimalResultsSafeCon, size, t, timePerRound);


            mLines.add("---- Average results when Attack Ratio = " + attackRatio + "----");
            mLines.add("--- Results CFM---");
            calculateTotalAverageResults(" ------ CFMSafe ------ ", str, avCFMSafe, size, t);
            calculateTotalAverageResults(" ------ CFMAttack ------ ", str, avCFMAttack, size, t);
            calculateTotalAverageResults(" ------ CFMDefenseCon ------", str, avCFMDefenseCon, size, t);
            calculateTotalAverageResults(" ------ CFMDefenseNoise ------", str, avCFMDefenseNoise, size, t);
            calculateTotalAverageResults(" ------ CFMDefenseSafe ------", str, avCFMDefenseSafe, size, t);
            calculateTotalAverageResults(" ------ CFMSafeCon ------", str, avCFMSafeCon, size, t);
            mLines.add("--- Results LFM---");
            calculateTotalAverageResults(" ------ LFMSafe ------ ", str, avLFMSafe, size, t);
            calculateTotalAverageResults(" ------ LFMAttack ------ ", str, avLFMAttack, size, t);
            calculateTotalAverageResults(" ------ LFMDefenseCon ------", str, avLFMDefenseCon, size, t);
            calculateTotalAverageResults(" ------ LFMDefenseNoise ------", str, avLFMDefenseNoise, size, t);
            calculateTotalAverageResults(" ------ LFMDefenseSafe ------", str, avLFMDefenseSafe, size, t);
            calculateTotalAverageResults(" ------ LFMSafeCon ------", str, avLFMSafeCon, size, t);
            mLines.add("--- Results OPT---");
            calculateTotalAverageResults(" ------ OptimalSafe ------ ", str, avOptimalSafe, size, t);
            calculateTotalAverageResults(" ------ OptimalAttack ------ ", str, avOptimalAttack, size, t);
            calculateTotalAverageResults(" ------ OptimalDefenseCon ------", str, avOptimalDefenseCon, size, t);
            calculateTotalAverageResults(" ------ OptimalDefenseNoise ------", str, avOptimalDefenseNoise, size, t);
            calculateTotalAverageResults(" ------ OptimalDefenseSafe ------", str, avOptimalDefenseSafe, size, t);
            calculateTotalAverageResults(" ------ OptimalSafeCon ------", str, avOptimalSafeCon, size, t);

            }
        }
    }

    private static double getMaxTime(List<Double> list) {
        double max = 0;

        for (double time : list) {
            if (max < time) max = time;
        }

        return max;
    }

    private static void addResult(List<List<Double>> result, List<Double> List1, List<Double> List2, List<Double> List3, List<Double> List4) {
        result.add(List1);
        result.add(List2);
        result.add(List3);
        result.add(List4);
    }

    private static List<List<Double>> getAverageResults(List<List<List<Double>>> results, int size, int time, int timePerRound) {
        List<List<Double>> ar = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            List<Double> list = new ArrayList<>();
            for (int j = 0; j < time; j++) {
                double value = 0;
                for (int k = 0; k < timePerRound; k++) {
                    value = value + results.get(k).get(i).get(j);
                }
                list.add(value / timePerRound);
            }
            ar.add(list);
        }

        return ar;
    }

    private static void addARToLines(String str, String[] result, List<List<Double>> ar, int size, int time) {
        mLines.add(str);

        for (int i = 0; i < size; i++) {
            String r = result[i];
            for (int j = 0; j < time; j++) {
                r = r + ar.get(i).get(j) + ", ";
            }
            mLines.add(r);
        }
        mLines.add("");
    }


    private static void calculateTotalAverageResults(String str, String[] result, List<List<Double>> ar, int size, int time) {
        mLines.add(str);

        String r = "";
        for (int i = 0; i < size; i++) {
            double value = 0;
            r = r + result[i];
            for (int j = 0; j < time; j++) {
                value = value + ar.get(i).get(j);
            }
            r = r + (value / time) + ", ";
        }

        mLines.add(r);
    }

    private static void writeResults(String set) {
        try {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("HH-mm-ss");

            // Path file = Paths.get("Data Per Round - results-" +
            // sdf.format(cal.getTime()) + ".txt");
            // Path file = Paths.get("All Success - results-" +
            // sdf.format(cal.getTime())+".txt");
            Path file = Paths.get(set + " Results " + sdf.format(cal.getTime()) + ".txt");
            // Path file = Paths.get("Synthetic - results-" + sdf.format(cal.getTime()) +
            // ".txt");

            // addResults();

            Files.write(file, mLines, Charset.forName("UTF-8"));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}