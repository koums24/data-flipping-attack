package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cpa.models.DDCPADefense.DDCPAdefenseModel;
import static com.cpa.models.GenerateNoise.addSoftmaxNoise;

public class CoverageFirstModel {
    private int ServersNumber;
    private int[] OriginSpaceLimits;
    private int DataNumber;
    private List<EdgeUser> Users;
    private List<EdgeServer> Servers;
    private int Time;
    private double AttackRatio;
    private List<List<Integer>> RequestsList;

    private List<Double> TotalLatency;
    private List<Double> AverageLatency;
    private List<Double> CoveragedUsers;
    private List<Double> Times;
    private List<Double> HitRatio;
    private List<Double> HitRatioAttacked;

    private List<List<Integer>> CurrentStorage;
    private int[][] DistanceMetrix;
    private int[][] ServerDataDistance;
    private boolean[][] ServerDataFromCloud;
    private int[] DataSizes;

    double DelayEdgeEdge = 0.01;
    double DelayEdgeCloud = 0.2;

    private int LatencyLimit;

    private int CurrentTime;

    private int MinSize = 99999;

    List<List<Integer>> BlackList; //for detection

    public CoverageFirstModel(int serversNumber, int[][] userBenefits, int[][] distanceMetrix, int[] spaceLimits,
                              int[] dataSizes, List<EdgeUser> users, List<EdgeServer> servers, int dataNumber, int latencyLimit, int time,
                              List<List<Integer>> requestsList, List<List<Integer>> currentStorage, double attackRatio) {
        ServersNumber = serversNumber;
        OriginSpaceLimits = spaceLimits;
        LatencyLimit = latencyLimit;
        DistanceMetrix = distanceMetrix;

        DataSizes = dataSizes;

        DataNumber = dataNumber;
        Users = users;
        Servers = servers;

        Time = time;
        RequestsList = requestsList;
        AttackRatio = attackRatio;

        ServerDataDistance = new int[serversNumber][dataNumber];
        ServerDataFromCloud = new boolean[serversNumber][dataNumber];

        CurrentStorage = new ArrayList<>();
        for (int data = 0; data < DataNumber; data++) {
            CurrentStorage.add(new ArrayList<>());
            for (int s : currentStorage.get(data)) {
                CurrentStorage.get(data).add(s);
            }
        }

        TotalLatency = new ArrayList<>();
        AverageLatency = new ArrayList<>();
        CoveragedUsers = new ArrayList<>();
        HitRatio = new ArrayList<>();
        HitRatioAttacked = new ArrayList<>();
        Times = new ArrayList<>();
        BlackList = new ArrayList<>();

        for (int size : DataSizes) {
            if (size < MinSize)
                MinSize = size;
        }

        updateServerDataDistance();
    }

    public void updateServerDataDistance() {
        for (int i = 0; i < ServersNumber; i++) {
            for (int d = 0; d < DataNumber; d++) {
                int distance = LatencyLimit;
                boolean isFromCloud = true;
                for (int s : CurrentStorage.get(d)) {
                    if (DistanceMetrix[i][s] <= distance) {
                        distance = DistanceMetrix[i][s];
                        isFromCloud = false;
                    }
                }
                ServerDataDistance[i][d] = distance;
                ServerDataFromCloud[i][d] = isFromCloud;
            }
        }
    }

    //no attack
    public void runCoverage() {
        CurrentTime = 0;
        //save Request list
        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);


            BCU newBcu = calculateTotalBenefits(strategy, true);
            CurrentStorage.addAll(strategy);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();


            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);

            System.out.println("---- CFMsafe ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("Time:" + duration);
            System.out.println();

            CurrentTime++;
        }

    }

    //attack
    public void runCoverageAttack() {
        CurrentTime = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();

            // Attack
            //backup user的datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
//            System.out.println("\n");
//            System.out.println("backupDataListOnUser: " + backupDataListOnUser);

            //attack START
            //data list collect by server
            List<List<Integer>> requestOnServer = new ArrayList<>();
            List<List<Integer>> fromUser = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }

            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("CFM requestOnServer safe: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            System.out.println("attackServerList: " + Arrays.toString(attackServerList));
            flipAttack(attackServerList, requestOnServer, fromUser);

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            CurrentStorage.clear();
            CurrentStorage.addAll(strategy);
            updateServerDataDistance();
            //print for debug
            List<List<Integer>> requestOnServerAttack = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServerAttack.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;

                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServerAttack.get(u.nearEdgeServers.get(destServer)).add(data);

            }
            System.out.println("CFM requestOnServer attacked: " + requestOnServerAttack);

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);

            // calcute latency by request before attack
            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;

                userDataList.clear();

                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }
            List<List<Integer>> requestOnServerRecover = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServerRecover.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;

                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServerRecover.get(u.nearEdgeServers.get(destServer)).add(data);

            }
//            System.out.println("CFM requestOnServer recover for calculate: " + requestOnServerRecover);

            //attack end

            BCU newBcu = calculateTotalBenefits(strategy, true);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, true, attackServerList, requestOnServer, fromUser);

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);

            System.out.println("---- CFMattack ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);
            System.out.println();

            CurrentTime++;
        }

    }

    // new attack && defense
    public void runCoverageAttackDefense() {
        CurrentTime = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();

            // TODO Attack
            //backup datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
//            System.out.println("\n");
//            System.out.println("backupDataListOnUser: " + backupDataListOnUser);

            //attack START
            //data list collect by server
            List<List<Integer>> requestOnServer = new ArrayList<>();
            List<List<Integer>> requestOnServerAfterAttack = new ArrayList<>();
            List<List<Integer>> fromUser = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServerAfterAttack.add(new ArrayList<>());
            }
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }

            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("CFM requestOnServer safe: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

            //attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            System.out.println("attackServerList: " + Arrays.toString(attackServerList));
            flipAttack(attackServerList, requestOnServer, fromUser);

            requestOnServer.clear();
            fromUser.clear();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }
            System.out.println("CFM requestOnServer attacked: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

            //defense
            addNoiseDefense(requestOnServer, fromUser);
            requestOnServer.clear();
            fromUser.clear();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("CFM requestOnServer defensed: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

//            System.out.println("CFM requestOnServer attacked: " + requestOnServer);
//            System.out.println("CFM fromUser safe: " + fromUser);

//            CurrentStorage.clear();
            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            System.out.println("CFM strategy add noise: " + strategy);
            CurrentStorage.addAll(strategy);
            updateServerDataDistance();
            //print for debug
            List<List<Integer>> requestOnServerAttack = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServerAttack.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;

                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServerAttack.get(u.nearEdgeServers.get(destServer)).add(data);

            }

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);

            // calculate latency by list before attack
            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;

                userDataList.clear();

                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }
            List<List<Integer>> requestOnServerRecover = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServerRecover.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;

                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServerRecover.get(u.nearEdgeServers.get(destServer)).add(data);

            }
            System.out.println("CFM requestOnServer recover for calculate: " + requestOnServerRecover);

            //attack end

            BCU newBcu = calculateTotalBenefits(strategy, true);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, true, attackServerList, requestOnServer, fromUser);

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
            Times.add(duration);

            System.out.println("---- CFMattack defense addnoise ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);
            System.out.println();

            CurrentTime++;
        }

    }

    //attack & tradition defense
    public void runCoverageDefenseConvention() {
        CurrentTime = 0;
        //save Request list
        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            //TODO Attack
            //backup datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            System.out.println("\n");
            System.out.println("originalDataListOnUser: " + backupDataListOnUser);

            //data list collect by server
            List<List<Integer>> requestOnServer = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            List<List<Integer>> fromUser = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }


            //attack
            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            System.out.println("attackServerList: " + Arrays.toString(attackServerList));
            flipAttack(attackServerList, requestOnServer, fromUser);

            //detect and defense
            detectAttack(requestOnServer);

            CurrentStorage.clear();

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();

            System.out.println("Time " + CurrentTime + " strategy: " + strategy);

            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;

                userDataList.clear();

                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }

            //attack end
            BCU newBcu = calculateTotalBenefits(strategy, true);

            CurrentStorage.addAll(strategy);
            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            updateServerDataDistance();

            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);

            System.out.println("---- CFM Defense Convention ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println();

            CurrentTime++;
        }

    }

    //no attack & new defense
    public void runCoverageSafeDefense() {
        CurrentTime = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            //backup datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            System.out.println("\n");
            System.out.println("originalDataListOnUser: " + backupDataListOnUser);

            //data list collect by server
            List<List<Integer>> requestOnServer = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            List<List<Integer>> fromUser = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
//                int destServer = random.nextInt(u.nearEdgeServers.size());
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            //defense
            addNoiseDefense(requestOnServer, fromUser);

            CurrentStorage.clear();

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);

            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;

                userDataList.clear();

                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }


            BCU newBcu = calculateTotalBenefits(strategy, true);
            CurrentStorage.addAll(strategy);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            updateServerDataDistance();

            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);

            System.out.println("---- CFMsafe defense ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println();

            CurrentTime++;
        }
    }

    //no attack & traditional defense
    public void runCoverageSafeCon() {
        CurrentTime = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            //backup datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            System.out.println("\n");
            System.out.println("originalDataListOnUser: " + backupDataListOnUser);

            //data list collect by server
            List<List<Integer>> requestOnServer = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                requestOnServer.add(new ArrayList<>());
            }
            List<List<Integer>> fromUser = new ArrayList<>();
            for (int i = 0; i < ServersNumber; i++) {
                fromUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            //defense
            detectAttack(requestOnServer);

            CurrentStorage.clear();

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);

            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;

                userDataList.clear();

                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }


            BCU newBcu = calculateTotalBenefits(strategy, true);
            CurrentStorage.addAll(strategy);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            updateServerDataDistance();

            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);

            System.out.println("---- CFMsafe defense ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println();

            CurrentTime++;
        }

    }


    public static Map<Integer, Integer> tamperDistributionOnServerMap(int dataNumber, List<List<Integer>> requestOnServer, int attackServerId) {
        // 1. calculate Frequency
        Map<Integer, Integer> frequencyMap = getFrequencyMap(dataNumber, requestOnServer, attackServerId);

        // 2. order
        List<Integer> sortedByFrequency = new ArrayList<>(frequencyMap.keySet());
        sortedByFrequency.sort((a, b) -> {
            int freqCompare = Integer.compare(frequencyMap.get(a), frequencyMap.get(b));
            if (freqCompare != 0) {
                return freqCompare;
            } else {
                return Integer.compare(b, a);
            }
        });

        // 3. pair high and low
        int size = sortedByFrequency.size();
        List<Integer> mostFrequent = sortedByFrequency.subList(size / 2, size);
        List<Integer> leastFrequent = sortedByFrequency.subList(0, size / 2);

        // 4. swap
        Map<Integer, Integer> swapMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            swapMap.put(i, i);
        }

        for (int i = 0; i < leastFrequent.size(); i++) {
            int least = leastFrequent.get(i);
            int most = mostFrequent.get(mostFrequent.size() - 1 - i);

            if (least != most) {
                swapMap.put(least, most);
                swapMap.put(most, least);
            }
        }

//        System.out.println(attackServerId + " Swap Map: " + swapMap);
        return swapMap;
    }

    private static Map<Integer, Integer> getFrequencyMap(int dataNumber, List<List<Integer>> requestOnServer, int attackServerId) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            frequencyMap.put(i, 0); // 初始化所有数据的出现次数为0
        }
        for (int num : requestOnServer.get(attackServerId)) {
            frequencyMap.put(num, frequencyMap.get(num) + 1);
        }
        return frequencyMap;
    }


    public List<List<Integer>> getCoverageEffectiveStrategy() {
        int[] spaceLimits = new int[ServersNumber];

        List<List<Integer>> results = new ArrayList<>();
        for (int data = 0; data < DataNumber; data++) {
            results.add(new ArrayList<>());
        }

        for (int m = 0; m < ServersNumber; m++) {
            spaceLimits[m] = OriginSpaceLimits[m];
        }

        DataBenefitsOnServer dbos = getServerAndDataWithMaximumCoveragePerCacheUnit(results, spaceLimits);

        while (isSpacesAvailable(spaceLimits) && dbos != null) {
            //  for conventional detection
            if (!BlackList.isEmpty()) {
                if (!BlackList.get(dbos.server).contains(dbos.data)) {
                    results.get(dbos.data).add(dbos.server);
                }

            } else {
                results.get(dbos.data).add(dbos.server);
            }
            spaceLimits[dbos.server] -= DataSizes[dbos.data];
            dbos = getServerAndDataWithMaximumCoveragePerCacheUnit(results, spaceLimits);
        }

//         return fillUpStorage(results, spaceLimits);
        return results;
    }


    private DataBenefitsOnServer getServerAndDataWithMaximumCoveragePerCacheUnit(List<List<Integer>> dataCacheList,
                                                                                 int[] spaceLimits) {

        double currentCPC = 0;
        int s = -1;
        int d = -1;

        double currentCover = calculateTotalBenefits(dataCacheList, false).cu;

        for (EdgeServer server : Servers) {
            if (spaceLimits[server.getId()] == 0)
                continue;
            for (int data = 0; data < DataNumber; data++) {
                if (dataCacheList.get(data).contains(server.getId()) || spaceLimits[server.getId()] < DataSizes[data])
                    continue;

                dataCacheList.get(data).add(server.getId());

                BCU b = calculateTotalBenefits(dataCacheList, false);

                dataCacheList.get(data).remove(dataCacheList.get(data).size() - 1);

                double cpc = (b.cu - currentCover) / DataSizes[data];
                if (currentCPC < cpc) {
                    currentCPC = cpc;
                    s = server.getId();
                    d = data;
                }

            }
        }

        if (currentCPC == 0)
            return null;

        DataBenefitsOnServer dataPopularityOnServer = new DataBenefitsOnServer();
        dataPopularityOnServer.server = s;
        dataPopularityOnServer.data = d;

        return dataPopularityOnServer;
    }

    public class BCU {
        public double benefit;
        public int cu;
        public int latency;
    }


    private BCU calculateTotalBenefits(List<List<Integer>> newStorages, boolean isNoBenefitUserCounted) {
        BCU bcu = new BCU();

        double total = 0;
        int cu = 0;
        int totallatency = 0;
//        System.out.println(RequestsList.get(CurrentTime).size());
        for (EdgeUser u : Users) {

            int data = u.dataList.get(CurrentTime);
            List<Integer> serverList = newStorages.get(data);

            int benefit = 0;
            boolean isFromCloud = true;
            for (int sid : u.nearEdgeServers) {
                if (serverList.contains(sid) && ServerDataFromCloud[sid][data] == false) {

                    if (isNoBenefitUserCounted) {
                        isFromCloud = false;
                    } else {
                        if (ServerDataDistance[sid][data] < LatencyLimit) {
                            isFromCloud = false;
                        }
                    }

                    int b = LatencyLimit - ServerDataDistance[sid][data];
                    if (b > benefit) {
                        benefit = b;
                    }
                }
            }
            total += benefit;
            if (isFromCloud == false) {
                cu++;
                totallatency += LatencyLimit - benefit;
            }
        }

        bcu.benefit = total;
        bcu.cu = cu;
        bcu.latency = totallatency;
        return bcu;
    }

    private double calculateHitRatioAttacked(List<List<Integer>> storages, boolean isNoBenefitUserCounted, int[] attackServerList, List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {

        double total = 0;
        int cu = 0;

        // System.out.println(RequestsList.get(CurrentTime).size());
        for (int i = 0; i < attackServerList.length; i++) {
            int attackServer = attackServerList[i];
            List<Integer> requestData = requestOnServer.get(attackServer);
            List<Integer> userSendingReq = fromUser.get(attackServer);
            for (int j = 0; j < requestData.size(); j++) {
                total++; //被attack server收到的请求总数
                boolean isFromCloud = true;
                int data = requestData.get(j);
                List<Integer> serverList = storages.get(data);
                for (int sid : Users.get(userSendingReq.get(j)).nearEdgeServers) {
                    if (serverList.contains(sid) && ServerDataFromCloud[sid][data] == false) {

                        if (isNoBenefitUserCounted) {
                            isFromCloud = false;
                        } else {
                            if (ServerDataDistance[sid][data] < LatencyLimit) {
                                isFromCloud = false;
                            }
                        }
                    }
                }

                if (isFromCloud == false) {
                    cu++;
                }
            }
        }

        return (double) cu / total;
    }

    private boolean isSpacesAvailable(int[] spaceLimits) {

        for (int i : spaceLimits) {
            if (i >= MinSize)
                return true;
        }

        return false;
    }

    public List<List<Integer>> fillUpStorage(List<List<Integer>> strategy, int[] spaceLimits) {
        // random add data into idol spaces
        if (isSpacesAvailable(spaceLimits)) {
            for (int i = 0; i < spaceLimits.length; i++) {
                List<Integer> pendingList = new ArrayList<>();
                if (spaceLimits[i] >= MinSize) {
                    for (int data = 0; data < DataNumber; data++) {
                        if (strategy.get(data).contains(i) || DataSizes[data] > spaceLimits[i])
                            continue;

                        if (ServerDataFromCloud[i][data]) {
                            strategy.get(data).add(i);
                            spaceLimits[i] -= DataSizes[data];
                        } else {
                            pendingList.add(data);
                        }
                    }
                    if (spaceLimits[i] >= MinSize) {
                        for (int data = 0; data < DataNumber; data++) {
                            if (strategy.get(data).contains(i) || DataSizes[data] > spaceLimits[i])
                                continue;
                            strategy.get(data).add(i);
                            spaceLimits[i] -= DataSizes[data];
                        }
                    }
                }
            }
        }
        return strategy;
    }

    /**
     * generate servers to be attacked by percentage
     *
     * @param n          serversNumber
     * @param percentage attackRatio
     * @return
     */
    public static int[] selectRandomPercentage(int n, double percentage) {
        //number changed by attack ratio
        int numberToSelect = (int) Math.round(n * percentage);

        //fixed number
//        int numberToSelect = 6;

        Random random = new Random();

        return IntStream.range(0, n)
                .boxed()
                .collect(Collectors.collectingAndThen(Collectors.toList(),
                        list -> {
                            // 随机打乱列表
                            Collections.shuffle(list, random);
                            // 选择前 numberToSelect 个元素，并将其转为数组
                            return list.stream().limit(numberToSelect).mapToInt(Integer::intValue).toArray();
                        }
                ));
    }

    private void flipAttack(int[] attackServerList, List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {

        for (int k = 0; k < attackServerList.length; k++) {
            //tamper distribution on server
//                List<Integer> requestListAttakced = tamperDistributionOnServer(requestOnServer, attackServerList[k]);
            Map<Integer, Integer> swapMap = tamperDistributionOnServerMap(DataNumber, requestOnServer, attackServerList[k]);
//                System.out.println("requestListAttakced" + requestListAttakced);
//                for (Integer coverUser : Servers.get(attackServerList[k]).directCoveredUsers) {
            for (Integer tamperuser : fromUser.get(attackServerList[k])) {
                if (RequestsList.get(CurrentTime).contains(tamperuser)) {
                    List<Integer> dataList = Users.get(tamperuser).dataList;
                    dataList.set(CurrentTime, swapMap.get(dataList.get(CurrentTime)));
//                        dataList = Users.get(tamperuser).dataList;
//                        System.out.println("dataList " + tamperuser + " after attack: " + dataList);
                }
            }
        }
    }

    private void detectAttack(List<List<Integer>> requestOnServer) {
        for (int i = 0; i < ServersNumber; i++) {
            BlackList.add(new ArrayList<>());
        }

        for (int i = 0; i < ServersNumber; i++) {
            DDCPAdefenseModel(BlackList, Users, DataNumber, requestOnServer.get(i), i);
        }

        System.out.println("BlackList: " + BlackList);
    }

    private class DataBenefitsOnServer {
        public int server;
        public int data;
    }

    private void addNoiseDefense(List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {
        double epsilon = 4.0;
        for (int i = 0; i < requestOnServer.size(); i++) {
            int j = 0;

            List<Integer> noiseRequestOnServer = addSoftmaxNoise(requestOnServer.get(i), DataNumber);
            List<Integer> userList = fromUser.get(i);
            for (int k = 0; k < userList.size(); k++) {
                List<Integer> dataList = Users.get(userList.get(k)).dataList;
                dataList.set(CurrentTime, noiseRequestOnServer.get(k));
            }
        }

        List<List<Integer>> noiseDatalistUser = new ArrayList<>();
        for (int i = 0; i < Users.size(); i++) {
            noiseDatalistUser.add(new ArrayList<>());
        }
        for (EdgeUser u : Users) {
            noiseDatalistUser.set(u.id, new ArrayList<>(u.dataList));
        }
        System.out.println("noiseDatalistUser: " + noiseDatalistUser);
    }

    public List<Double> getTotalLatency() {
        return TotalLatency;
    }

    public List<Double> getCoveragedUsers() {
        return CoveragedUsers;
    }

    public List<Double> getAverageLatency() {
        return AverageLatency;
    }

    public List<Double> getHitRatio() {
        return HitRatio;
    }

    public List<Double> getHitRatioAttacked() {
        return HitRatioAttacked;
    }


}