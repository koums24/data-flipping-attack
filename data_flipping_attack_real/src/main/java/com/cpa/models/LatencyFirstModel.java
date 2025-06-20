package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.cpa.models.CoverageFirstModel.*;
import static com.cpa.models.DDCPADefense.DDCPAdefenseModel;
import static com.cpa.models.LaplaceNoiseDefense.*;

public class LatencyFirstModel {
    private int ServersNumber;
    private int[] OriginSpaceLimits;
    private int DataNumber;
    private List<EdgeUser> Users;
    private List<EdgeServer> Servers;
    private int Time;
    private List<List<Integer>> RequestsList;
    private double AttackRatio;
    private double BenefitUnitCost = 0.004;
    double DelayEdgeEdge = 0.01;
    double DelayEdgeCloud = 0.2;

    private List<Double> TotalLatency;
    private List<Double> AverageLatency;
    private List<Double> CoveragedUsers;
    private List<Double> Times;
    private List<Double> HitRatio;
    private List<Double> HitRatioAttacked;
    private List<Double> TotalBenefits;

    private List<List<Integer>> CurrentStorage;
    private int[][] DistanceMetrix;
    private int[][] ServerDataDistance;
    private boolean[][] ServerDataFromCloud;
    private int[] DataSizes;


    private int LatencyLimit;

    private int CurrentTime;

    private int MinSize = 99999;

    List<List<Integer>> BlackList;//for detection

    public LatencyFirstModel(int serversNumber, int[][] userBenefits, int[][] distanceMetrix, int[] spaceLimits,
                             int[] dataSizes, List<EdgeUser> users, List<EdgeServer> servers, int dataNumber, int latencyLimit, int time,
                             List<List<Integer>> requestsList, List<List<Integer>> currentStorage, double attackRatio) {
        ServersNumber = serversNumber;
        OriginSpaceLimits = spaceLimits;
        LatencyLimit = latencyLimit;
        DistanceMetrix = distanceMetrix;

        DataSizes = dataSizes;
        AttackRatio = attackRatio;

        DataNumber = dataNumber;
        Users = users;
        Servers = servers;

        Time = time;
        RequestsList = requestsList;

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
        TotalBenefits = new ArrayList<>();
        CoveragedUsers = new ArrayList<>();
        Times = new ArrayList<>();
        HitRatio = new ArrayList<>();
        HitRatioAttacked = new ArrayList<>();
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
    public void runLatency() {
        CurrentTime = 0;

        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;
             CurrentStorage.clear();

            List<List<Integer>> strategy = getCostEffectiveStrategy();
            BCU newBcu = calculateTotalBenefits(strategy);

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

            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);

            System.out.println("---- LFMsafe ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);

            System.out.println("Coverd:" + coveredUsers);
            CurrentTime++;
        }
    }

    //attack
    public void runLatencyAttack() {
        CurrentTime = 0;

        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;
            CurrentStorage.clear();
            // TODO Attack
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }


            //TODO attack START
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
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }


            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            flipAttack(attackServerList, requestOnServer, fromUser);

            //stragtegy
            List<List<Integer>> strategy = getCostEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);
            
            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;
                userDataList.clear();
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }

            //TODO attack end

            BCU newBcu = calculateTotalBenefits(strategy);
            CurrentStorage.addAll(strategy);
            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;
  
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();

//            TotalLatency.add(latency);
//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(strategy, attackServerList, requestOnServer, fromUser);
            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());


            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
//            AttackedHitRatio.add(0.0);
            Times.add(duration);

            System.out.println("---- LFMattack ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);

            CurrentTime++;
        }


    }

    //random modification attack
    public void runLatencyRandomAttack() {
        CurrentTime = 0;

        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;
 
            CurrentStorage.clear();
            // TODO Attack
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            //TODO attack START
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
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
//            flipAttack(attackServerList, requestOnServer, fromUser);
            randomNoiseAttack(attackServerList, requestOnServer, fromUser);

            //stragtegy
            List<List<Integer>> strategy = getCostEffectiveStrategy();

            System.out.println("Time " + CurrentTime + " strategy: " + strategy);
            
            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;
                userDataList.clear();
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }
            //TODO attack end
            BCU newBcu = calculateTotalBenefits(strategy);

            CurrentStorage.addAll(strategy);
            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();

//            TotalLatency.add(latency);
//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(strategy, attackServerList, requestOnServer, fromUser);
            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
//            AttackedHitRatio.add(0.0);
            Times.add(duration);
            System.out.println("---- LFMattack ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);
            CurrentTime++;
        }
    }

    public void runLatencyAttackDefense() {
        CurrentTime = 0;

        // int totalBenenfits = 0;
        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();
            // TODO Attack
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            //TODO attack START
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
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }
            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
//            flipAttack(attackServerList, requestOnServer, fromUser);
            randomNoiseAttack(attackServerList, requestOnServer, fromUser);


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
            System.out.println("LFM requestOnServer attacked: " + requestOnServer);
            System.out.println("LFM fromUser safe: " + fromUser);
            //TODO defense
            addNoiseDefense(requestOnServer, fromUser);
            //stragtegy
            List<List<Integer>> strategy = getCostEffectiveStrategy();

            System.out.println("Time " + CurrentTime + " strategy: " + strategy);
            //TODO 计算latency前 恢复原request list
            for (EdgeUser user : Users) {
               
                List<Integer> userDataList = Users.get(user.id).dataList;
                userDataList.clear();
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }
            //TODO attack end

            BCU newBcu = calculateTotalBenefits(strategy);

            CurrentStorage.addAll(strategy);
            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;
  
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();

            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(strategy, attackServerList, requestOnServer, fromUser);
            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
//            AttackedHitRatio.add(0.0);
            Times.add(duration);
            System.out.println("---- LFMattack defense addnoise ---- " + CurrentTime);
            // System.out.println("Revenue: " + (benefit - cost));
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);
            CurrentTime++;
        }
    }

    public void runLatencyDefenseConvention() {
        CurrentTime = 0;

        // int totalBenenfits = 0;
        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();
            // TODO Attack
            //
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            //TODO attack START
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
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            flipAttack(attackServerList, requestOnServer, fromUser);
//            randomNoiseAttack(attackServerList, requestOnServer, fromUser);

            //TODO detect and defense
            detectAttack(requestOnServer);
            //stragtegy
            List<List<Integer>> strategy = getCostEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);
            for (EdgeUser user : Users) {
                List<Integer> userDataList = Users.get(user.id).dataList;
                userDataList.clear();
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }
            //TODO attack end
            BCU newBcu = calculateTotalBenefits(strategy);
            CurrentStorage.addAll(strategy);
            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
            updateServerDataDistance();
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(strategy, attackServerList, requestOnServer, fromUser);
            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());


            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
//            AttackedHitRatio.add(0.0);
            Times.add(duration);
            System.out.println("---- LFM Defense Convention ---- " + CurrentTime);
            // System.out.println("Revenue: " + (benefit - cost));
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);

            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);
            CurrentTime++;
        }
    }

    public void runLatencySafeDefense() {
        CurrentTime = 0;

        // int totalBenenfits = 0;
        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            //
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
                //user send nearest server
//                int destServer = random.nextInt(u.nearEdgeServers.size());
                int destServer = (u.nearEdgeServers.size() -1)/2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            //TODO defense
            addNoiseDefense(requestOnServer, fromUser);

            CurrentStorage.clear();

            List<List<Integer>> strategy = getCostEffectiveStrategy();

            //TODO 
            for (EdgeUser user : Users) {
               
                List<Integer> userDataList = Users.get(user.id).dataList;
                userDataList.clear();
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }

            BCU newBcu = calculateTotalBenefits(strategy);
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

            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);
            System.out.println("---- LFM safe defense ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);

            CurrentTime++;
        }

    }

    public void runLatencySafeCon() {
        CurrentTime = 0;

        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            //
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
                //user send nearest server
//                int destServer = random.nextInt(u.nearEdgeServers.size());
                int destServer = (u.nearEdgeServers.size() -1)/2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            //TODO defense
            detectAttack(requestOnServer);
            CurrentStorage.clear();
            List<List<Integer>> strategy = getCostEffectiveStrategy();

            //TODO 
            for (EdgeUser user : Users) {
               
                List<Integer> userDataList = Users.get(user.id).dataList;
                userDataList.clear();
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }

            BCU newBcu = calculateTotalBenefits(strategy);
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

            TotalBenefits.add(benefit);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);

            System.out.println("---- LFM safe defense ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Coverd:" + coveredUsers);
            CurrentTime++;
        }

    }

    public List<List<Integer>> getCostEffectiveStrategy() {
        int[] spaceLimits = new int[ServersNumber];

        List<List<Integer>> results = new ArrayList<>();
        for (int data = 0; data < DataNumber; data++) {
            results.add(new ArrayList<>());
        }

        for (int m = 0; m < ServersNumber; m++) {
            spaceLimits[m] = OriginSpaceLimits[m];
        }

        DataBenefitsOnServer dbos = getServerAndDataWithMaximumBenefitsPerCacheUnit(results, spaceLimits);

        while (isSpacesAvailable(spaceLimits) && dbos != null) {
            //  for conventional detection
            if (!BlackList.isEmpty()) {
                if (!BlackList.get(dbos.server).contains(dbos.data)) {
                    results.get(dbos.data).add(dbos.server);
                }

            }else{
                results.get(dbos.data).add(dbos.server);
            }

            spaceLimits[dbos.server] -= DataSizes[dbos.data];
            dbos = getServerAndDataWithMaximumBenefitsPerCacheUnit(results, spaceLimits);
        }

//        return fillUpStorage(results, spaceLimits);
        return results;
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


    private DataBenefitsOnServer getServerAndDataWithMaximumBenefitsPerCacheUnit(List<List<Integer>> dataCacheList,
                                                                                 int[] spaceLimits) {
        // List<DataPopularityOnServer> dataPopularityOnServerList = new ArrayList<>();
        double currentBPC = 0;
 
        int s = -1;
        int d = -1;

        double currentBenefit = calculateTotalBenefits(dataCacheList).benefit;

        for (EdgeServer server : Servers) {
            if (spaceLimits[server.id] == 0)
                continue;
            for (int data = 0; data < DataNumber; data++) {
                if (dataCacheList.get(data).contains(server.id) || spaceLimits[server.id] < DataSizes[data])
                    continue;

                dataCacheList.get(data).add(server.id);

                BCU b = calculateTotalBenefits(dataCacheList);

                dataCacheList.get(data).remove(dataCacheList.get(data).size() - 1);
                double bpc = (b.benefit - currentBenefit) / DataSizes[data];
                if (currentBPC < bpc) {
                    currentBPC = bpc;
                    s = server.id;
                    d = data;
                }
            }
        }

        if (currentBPC == 0)
            return null;

        DataBenefitsOnServer dataPopularityOnServer = new DataBenefitsOnServer();
        dataPopularityOnServer.server = s;
        dataPopularityOnServer.data = d;

        return dataPopularityOnServer;
    }

    private double calculateHitRatioAttacked(List<List<Integer>> storages, int[] attackServerList, List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {

        double total = 0;
        int cu = 0;
        // System.out.println(RequestsList.get(CurrentTime).size());
        for (int i = 0; i < attackServerList.length; i++) {
            int attackServer = attackServerList[i];
            List<Integer> requestData = requestOnServer.get(attackServer);
            List<Integer> userSendingReq = fromUser.get(attackServer);
            for (int j = 0; j < requestData.size(); j++) {
                total++; 
                boolean isFromCloud = true;
                int data = requestData.get(j);
                List<Integer> serverList = storages.get(data);
                for (int sid : Users.get(userSendingReq.get(j)).nearEdgeServers) {
                    if (serverList.contains(sid) && ServerDataFromCloud[sid][data] == false) {
                        isFromCloud = false;
                    }
                }

                if (isFromCloud == false) {
                    cu++;
                }
            }
        }

        return (double) cu / total;
    }


    private DataBenefitsOnServer getServerAndDataWithMaximumCoveragePerCacheUnit(List<List<Integer>> dataCacheList,
                                                                                 int[] spaceLimits) {
        // List<DataPopularityOnServer> dataPopularityOnServerList = new ArrayList<>();

        double currentCPC = 0;
 
        int s = -1;
        int d = -1;

        double currentCover = calculateTotalBenefits(dataCacheList).cu;

        for (EdgeServer server : Servers) {
            if (spaceLimits[server.id] == 0)
                continue;
            for (int data = 0; data < DataNumber; data++) {
                if (dataCacheList.get(data).contains(server.id) || spaceLimits[server.id] < DataSizes[data])
                    continue;

                dataCacheList.get(data).add(server.id);

                BCU b = calculateTotalBenefits(dataCacheList);

                dataCacheList.get(data).remove(dataCacheList.get(data).size() - 1);

                double cpc = (b.cu - currentCover) / DataSizes[data];
                if (currentCPC < cpc) {
                    currentCPC = cpc;
                    s = server.id;
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

    private BCU calculateTotalBenefits(List<List<Integer>> newStorages) {
        BCU bcu = new BCU();

        double total = 0;
        int cu = 0;
        int totallatency = 0;

        for (EdgeUser u : Users) {
            if (!RequestsList.get(CurrentTime).contains(u.id))
                continue;

            int data = u.dataList.get(CurrentTime);
            List<Integer> serverList = newStorages.get(data);

            int benefit = 0;
            boolean isFromCloud = true;
            for (int sid : u.nearEdgeServers) {
                if (serverList.contains(sid) && ServerDataFromCloud[sid][data] == false) {
                    isFromCloud = false;
                    int b = LatencyLimit - ServerDataDistance[sid][data];
                    if (b > benefit) {
                        benefit = b;
                    }
                }
            }
            total += benefit * BenefitUnitCost;
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

    private void randomNoiseAttack(int[] attackServerList, List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {

        for (int k = 0; k < attackServerList.length; k++) {
            //tamper distribution on server
            List<Integer> noiseDistribution = tamperRequestListWithGaussianNoise(DataNumber, requestOnServer, attackServerList[k],0.0,1.0);
            int index = 0;
//                System.out.println("requestListAttakced" + requestListAttakced);
            
//                for (Integer coverUser : Servers.get(attackServerList[k]).directCoveredUsers) {
            for (Integer tamperuser : fromUser.get(attackServerList[k])) {
                if (RequestsList.get(CurrentTime).contains(tamperuser)) {
                    List<Integer> dataList = Users.get(tamperuser).dataList;
                    dataList.set(CurrentTime, noiseDistribution.get(index));
                    index ++;
//                        dataList = Users.get(tamperuser).dataList;
//                        System.out.println("dataList " + tamperuser + " after attack: " + dataList);
                }
            }
        }
    }
    private void addNoiseDefense(List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {
        double epsilon = 1.0;
        for (int i = 0; i < requestOnServer.size(); i++) {
            int j = 0;
//            List<Integer> noiseRequestOnServer2 = addLaplaceNoiseToRequests(requestOnServer.get(i), epsilon, DataNumber);
            //TODO solution 1
//            List<Integer> noiseRequestOnServer2 = addUniformNoise(requestOnServer.get(i));
//            List<Integer> noiseRequestOnServer = addGaussianNoiseToRequests(requestOnServer.get(i), 0,3, DataNumber);
            //TODO solution 2

            List<Integer> noiseRequestOnServer = addSoftmaxNoise(requestOnServer.get(i), DataNumber);
//            List<Integer> noiseRequestOnServer = addGaussianNoiseToRequests(requestOnServer.get(i), 0, requestOnServer.get(i).size()/2
//                    , DataNumber);

//            List<Integer> noiseRequestOnServer = addBiasedNoiseToRequests(requestOnServer.get(i), epsilon, DataNumber);
//            List<Integer> noiseRequestOnServer = addUniformNoiseToRequests(requestOnServer.get(i), 15,28, DataNumber);
//            List<Integer> noiseRequestOnServer = addWhiteNoiseToRequests(requestOnServer.get(i), 2, DataNumber);

//            System.out.println("server" + i + "requests with noise" + noiseRequestOnServer);
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

    private void detectAttack(List<List<Integer>> requestOnServer) {
        for (int i = 0; i < ServersNumber; i++) {
            BlackList.add(new ArrayList<>());
        }

        for (int i = 0; i < ServersNumber; i++) {
            DDCPAdefenseModel(BlackList, Users, DataNumber, requestOnServer.get(i), i);
        }

        System.out.println("BlackList: " + BlackList);
    }

    private boolean isSpacesAvailable(int[] spaceLimits) {

        for (int i : spaceLimits) {
            if (i >= MinSize)
                return true;
        }

        return false;
    }

    private class DataBenefitsOnServer {
        public int server;
        public int data;
    }

    public List<Double> getTimes() {
        return Times;
    }

    public List<Double> getTotalLatency() {
        return TotalLatency;
    }

    public List<Double> getCoveragedUsers() {
        return CoveragedUsers;
    }

    public List<Double> getHitRatio() {
        return HitRatio;
    }

    public List<Double> getHitRatioAttacked() {
        return HitRatioAttacked;
    }

    public List<Double> getAverageLatency() {
        return AverageLatency;
    }

    public List<Double> getTotalBenefits() {
        return TotalBenefits;
    }
}