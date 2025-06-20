package com.cpa.models;

import com.cpa.objectives.EdgeRequest;
import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cpa.models.DDCPADefense.DDCPAdefenseModel;
import static com.cpa.models.LaplaceNoiseDefense.*;

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

//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;

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

    public void runCoverage2(){
        CurrentTime = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();
            List<EdgeRequest> requestListAtT = new ArrayList<>();
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id)) continue;

                int data = u.dataList.get(CurrentTime);
                int serverId = u.nearEdgeServers.get((u.nearEdgeServers.size() - 1) / 2);
                requestListAtT.add(new EdgeRequest(u.id, serverId, data));
            }
            List<EdgeRequest> backupRequestListAtT = new ArrayList<>(requestListAtT);

            List<List<Integer>> strategy = getCoverageEffectiveStrategyByRequest(requestListAtT);
            CurrentStorage.addAll(strategy);
            updateServerDataDistance();

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);

            BCU newBcu = calculateTotalBenefitsByRequest(strategy, backupRequestListAtT, true);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;  

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            double totalLatency = calculateTotalLatencyByRequest(requestListAtT, coveredUsers, latency);

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / requestListAtT.size());  

            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / requestListAtT.size());  
            Times.add(duration);

            System.out.println("---- CFMsafe ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / requestListAtT.size());
            System.out.println("Benefits: " + benefit);
            System.out.println("Covered:" + coveredUsers);
            System.out.println();

            CurrentTime++;
        }
    }

    private double calculateTotalLatencyByRequest(List<EdgeRequest> requestListAtT, double coveredUsers, double edgeLatency) {

        double uncoveredUsers = requestListAtT.size() - coveredUsers;
        
        double totalLatency = edgeLatency * DelayEdgeEdge + uncoveredUsers * DelayEdgeCloud;

        return totalLatency;
    }
    //attack
    public void runCoverageAttack() {
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

                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServerAttack.get(u.nearEdgeServers.get(destServer)).add(data);

            }
            System.out.println("CFM requestOnServer attacked: " + requestOnServerAttack);

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);
            
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

            //TODO attack end

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

    public void runCoverageAttack2() {
        CurrentTime = 0;
        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();

            // TODO Attack
            
            List<EdgeRequest> requestListAtT = new ArrayList<>();
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id)) continue;

                int data = u.dataList.get(CurrentTime);
                int serverId = u.nearEdgeServers.get((u.nearEdgeServers.size() - 1) / 2);  
                requestListAtT.add(new EdgeRequest(u.id, serverId, data));
            }
            List<EdgeRequest> backuprequestListAtT = new ArrayList<>(requestListAtT);
           
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            Set<Integer> attackServerSet = Arrays.stream(attackServerList).boxed().collect(Collectors.toSet());

            
            Map<Integer, Map<Integer, Integer>> flippedMapsPerServer = new HashMap<>();
            for (int sid : attackServerList) {
                Map<Integer, Integer> flippedMap = tamperDistributionOnServerMapFromRequests(
                        DataNumber, requestListAtT, sid
                );
                flippedMapsPerServer.put(sid, flippedMap);
            }

            for (EdgeRequest req : requestListAtT) {
                if (attackServerSet.contains(req.serverId)) {
                    Map<Integer, Integer> flippedMap = flippedMapsPerServer.get(req.serverId);
                    req.dataId = flippedMap.getOrDefault(req.dataId, req.dataId);
                }
            }

            List<List<Integer>> strategy = getCoverageEffectiveStrategyByRequest(requestListAtT);
            CurrentStorage.clear();
            CurrentStorage.addAll(strategy);
            updateServerDataDistance();

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);

            //TODO attack end, recover requestListAtT for cache ratio and latency test

            BCU newBcu = calculateTotalBenefitsbyRequest(strategy, backuprequestListAtT,true);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttackedFromRequests(CurrentStorage, backuprequestListAtT, attackServerSet, true);

            TotalLatency.add(totalLatency);

            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
            Times.add(duration);

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
    //attack
    public void runCoverageRandomAttack() {
        CurrentTime = 0;
        // int totalBenenfits = 0;

        while (CurrentTime < Time) {
            Instant start = Instant.now();
            double benefit = 0;
            double coveredUsers = 0;
            double latency = 0;

            CurrentStorage.clear();

            // TODO Attack
            //backup user datalist
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

            System.out.println("CFM requestOnServer safe: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            System.out.println("attackServerList: " + Arrays.toString(attackServerList));
            randomNoiseAttack(attackServerList, requestOnServer, fromUser);
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

                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServerAttack.get(u.nearEdgeServers.get(destServer)).add(data);

            }
            System.out.println("CFM requestOnServer attacked: " + requestOnServerAttack);

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);
            // before calculate latency, recover original request list
            for (EdgeUser user : Users) {
                // get users' dataList
                List<Integer> userDataList = Users.get(user.id).dataList;

                // clear userDataList, this will nor clear data in backupDataListOnUser
                userDataList.clear();
                // restore
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

            //TODO attack end

            BCU newBcu = calculateTotalBenefits(strategy, true);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;


//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, true, attackServerList, requestOnServer, fromUser);

            TotalLatency.add(totalLatency);

            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(hitRatioAttacked);
            Times.add(duration);

            System.out.println("---- CFMattack ---- " + CurrentTime);
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);

            System.out.println("Coverd:" + coveredUsers);
            System.out.println("HitRatio Attacked:" + hitRatioAttacked);
//             System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println();

            CurrentTime++;
        }

    }

    
    public void runCoverageAttackDefense() {
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
                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                //user send nearest server
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("CFM requestOnServer safe: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

//            System.out.println("CFM requestOnServer defensed: " + requestOnServer);
//            System.out.println("CFM fromUser safe: " + fromUser);


            // TODO attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            System.out.println("attackServerList: " + Arrays.toString(attackServerList));
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
            System.out.println("CFM requestOnServer attacked: " + requestOnServer);
            System.out.println("CFM fromUser safe: " + fromUser);

            //TODO defense
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
//            System.out.println("CFM requestOnServer attacked: " + requestOnServerAttack);

            System.out.println("Time " + CurrentTime + " strategy: " + CurrentStorage);

            // TODO 计算latency前 恢复原request list
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

            //TODO attack end

            BCU newBcu = calculateTotalBenefits(strategy, true);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;


//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;
            double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, true, attackServerList, requestOnServer, fromUser);

            TotalLatency.add(totalLatency);

            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

//             MigrationCosts.add(cost);
//             Objs.add(benefit - cost);
//             AverageRevenue.add(benefit - cost / coveredUsers);
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
//             System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println();

            CurrentTime++;
        }

    }
    
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


            //TODO attack
            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            System.out.println("attackServerList: " + Arrays.toString(attackServerList));
            flipAttack(attackServerList, requestOnServer, fromUser);
//            randomNoiseAttack(attackServerList, requestOnServer, fromUser);

            //TODO detect and defense
            detectAttack(requestOnServer);

            CurrentStorage.clear();

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();

            System.out.println("Time " + CurrentTime + " strategy: " + strategy);

            //TODO 计算latency前 
            for (EdgeUser user : Users) {
               
                List<Integer> userDataList = Users.get(user.id).dataList;

                
                userDataList.clear();

             
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }

            //TODO attack end

            BCU newBcu = calculateTotalBenefits(strategy, true);

            CurrentStorage.addAll(strategy);

            benefit = newBcu.benefit;
            coveredUsers = newBcu.cu;
            latency = newBcu.latency;

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();

//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);

            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

//             MigrationCosts.add(cost);
//             Objs.add(benefit - cost);
//             AverageRevenue.add(benefit - cost / coveredUsers);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);

            System.out.println("---- CFM Defense Convention ---- " + CurrentTime);
//             System.out.println("Revenue: " + (benefit - cost));
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);

            System.out.println("Coverd:" + coveredUsers);
//             System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println("Time:" + duration);
            System.out.println();

            CurrentTime++;
        }

//         int totalR = 0;
//         for (double obj : Objs) {
//         totalR += obj;
//         }
//         System.out.println("Total R = " + totalR);
//         System.out.println();
    }
   
    public void runCoverageSafeDefense() {
        CurrentTime = 0;
        //save Request list
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

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);

            //TODO 
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

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();

//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

//             MigrationCosts.add(cost);
//             Objs.add(benefit - cost);
//             AverageRevenue.add(benefit - cost / coveredUsers);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);

            System.out.println("---- CFMsafe defense ---- " + CurrentTime);
//             System.out.println("Revenue: " + (benefit - cost));
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);

            System.out.println("Coverd:" + coveredUsers);
//             System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println("Time:" + duration);
            System.out.println();

            CurrentTime++;
        }

    }
    
    public void runCoverageSafeCon() {
        CurrentTime = 0;
        //save Request list
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

            List<List<Integer>> strategy = getCoverageEffectiveStrategy();
            System.out.println("Time " + CurrentTime + " strategy: " + strategy);

            //TODO 
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
            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;

            updateServerDataDistance();

//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredUsers) * DelayEdgeCloud;

            TotalLatency.add(totalLatency);
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

//             MigrationCosts.add(cost);
//             Objs.add(benefit - cost);
//             AverageRevenue.add(benefit - cost / coveredUsers);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            HitRatioAttacked.add(0.0);
            Times.add(duration);

            System.out.println("---- CFMsafe defense ---- " + CurrentTime);
//             System.out.println("Revenue: " + (benefit - cost));
            System.out.println("Total Latency: " + totalLatency);
            System.out.println("Average Latency: " + totalLatency / RequestsList.get(CurrentTime).size());
            System.out.println("Benefits: " + benefit);

            System.out.println("Coverd:" + coveredUsers);
//             System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println("Time:" + duration);
            System.out.println();

            CurrentTime++;
        }

    }

    public static List<Integer> tamperDistributionOnServer(int dataNumber, List<List<Integer>> requestOnServer, int attackServerId) {
       
        Map<Integer, Integer> frequencyMap = getFrequencyMap(dataNumber, requestOnServer, attackServerId);

        
        List<Integer> sortedByFrequency = new ArrayList<>(frequencyMap.keySet());
        sortedByFrequency.sort(Comparator.comparingInt(frequencyMap::get));

        
        int size = sortedByFrequency.size();
        List<Integer> mostFrequent = sortedByFrequency.subList(size / 2, size); 
        List<Integer> leastFrequent = sortedByFrequency.subList(0, size / 2);  

       
        Map<Integer, Integer> swapMap = new HashMap<>();
        for (int i = 0; i < leastFrequent.size(); i++) {
            int mostFreq = mostFrequent.get(mostFrequent.size() - 1 - i);
            int leastFreq = leastFrequent.get(i);
            swapMap.put(leastFreq, mostFreq);
            swapMap.put(mostFreq, leastFreq);
        }

        
        List<Integer> modifiedRequestList = new ArrayList<>();
        for (int num : requestOnServer.get(attackServerId)) {
            modifiedRequestList.add(swapMap.getOrDefault(num, num));
        }


        return modifiedRequestList;
    }

    public static Map<Integer, Integer> tamperDistributionOnServerMap(int dataNumber, List<List<Integer>> requestOnServer, int attackServerId) {
       

        Map<Integer, Integer> frequencyMap = getFrequencyMap(dataNumber, requestOnServer, attackServerId);

        
        List<Integer> sortedByFrequency = new ArrayList<>(frequencyMap.keySet());
        sortedByFrequency.sort((a, b) -> {
            int freqCompare = Integer.compare(frequencyMap.get(a), frequencyMap.get(b));
            if (freqCompare != 0) {
                return freqCompare; 
            } else {
                return Integer.compare(b, a); 
            }
        });

        
        int size = sortedByFrequency.size();
        List<Integer> mostFrequent = sortedByFrequency.subList(size / 2, size); 
        List<Integer> leastFrequent = sortedByFrequency.subList(0, size / 2);   
        
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

        System.out.println(attackServerId + " Swap Map: " + swapMap);

        return swapMap;
    }

    public static Map<Integer, Integer> tamperDistributionOnServerMapFromRequests(
            int dataNumber,
            List<EdgeRequest> requestListAtT,
            int attackServerId
    ) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            frequencyMap.put(i, 0);
        }
        
        for (EdgeRequest req : requestListAtT) {
            if (req.serverId == attackServerId) {
                int d = req.dataId;
                frequencyMap.put(d, frequencyMap.getOrDefault(d, 0) + 1);
            }
        }
        
        List<Integer> sortedByFrequency = new ArrayList<>(frequencyMap.keySet());
        sortedByFrequency.sort((a, b) -> {
            int cmp = Integer.compare(frequencyMap.get(a), frequencyMap.get(b));
            if (cmp != 0) return cmp;
            return Integer.compare(b, a); 
        });


        int size = sortedByFrequency.size();
        List<Integer> leastFrequent = sortedByFrequency.subList(0, size / 2);
        List<Integer> mostFrequent = sortedByFrequency.subList(size / 2, size);

        
        Map<Integer, Integer> swapMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            swapMap.put(i, i);
        }

     
        for (int i = 0; i < leastFrequent.size(); i++) {
            int low = leastFrequent.get(i);
            int high = mostFrequent.get(mostFrequent.size() - 1 - i);
            if (low != high) {
                swapMap.put(low, high);
                swapMap.put(high, low);
            }
        }

        System.out.println("Server " + attackServerId + " Swap Map: " + swapMap);
        return swapMap;
    }


    public static List<Integer> tamperRequestListWithGaussianNoise(
            int dataNumber, List<List<Integer>> requestOnServer,  int attackServerId, double noiseMean, double noiseStdDev) {


        Map<Integer, Integer> frequencyMap = getFrequencyMap(dataNumber, requestOnServer, attackServerId);

        int originalTotal = frequencyMap.values().stream().mapToInt(Integer::intValue).sum();

        Random random = new Random();
        Map<Integer, Integer> noisyFrequencyMap = frequencyMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    int originalFreq = entry.getValue();
                    double noise = random.nextGaussian() * noiseStdDev + noiseMean;
                    return Math.max(0, (int) Math.round(originalFreq + noise)); 
                }
        ));

        int noisyTotal = noisyFrequencyMap.values().stream().mapToInt(Integer::intValue).sum();
        int delta = originalTotal - noisyTotal;

        if (delta != 0) {
        
            List<Integer> sortedKeys = noisyFrequencyMap.keySet().stream()
                    .sorted(Comparator.comparingInt(noisyFrequencyMap::get).reversed())
                    .collect(Collectors.toList());

            for (int key : sortedKeys) {
                if (delta == 0) break;
                int currentValue = noisyFrequencyMap.get(key);
                int adjustment = Math.min(Math.abs(delta), currentValue); 
                if (delta > 0) {
                    noisyFrequencyMap.put(key, currentValue + adjustment); 
                    delta -= adjustment;
                } else {
                    noisyFrequencyMap.put(key, currentValue - adjustment); 
                    delta += adjustment;
                }
            }
        }

        List<Integer> noisyRequestList = new ArrayList<>();
        noisyFrequencyMap.forEach((key, value) -> {
            for (int i = 0; i < value; i++) {
                noisyRequestList.add(key);
            }
        });

        Collections.shuffle(noisyRequestList);

        System.out.println("Original Total: " + originalTotal);
        System.out.println("Noisy Total: " + noisyRequestList.size());
        System.out.println("Noisy Frequency Map: " + noisyFrequencyMap);

        return noisyRequestList;
    }

    private static Map<Integer, Integer> getFrequencyMap(int dataNumber, List<List<Integer>> requestOnServer, int attackServerId) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            frequencyMap.put(i, 0); 
        }
        for (int num : requestOnServer.get(attackServerId)) {
            frequencyMap.put(num, frequencyMap.get(num) + 1);
        }
        return frequencyMap;
    }

    public List<List<Integer>> getCurrentStorage() {
        return CurrentStorage;
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

            }else{
                results.get(dbos.data).add(dbos.server);
            }
            spaceLimits[dbos.server] -= DataSizes[dbos.data];
            dbos = getServerAndDataWithMaximumCoveragePerCacheUnit(results, spaceLimits);
        }

//         return fillUpStorage(results, spaceLimits);
        return results;
    }

    public List<List<Integer>> getCoverageEffectiveStrategyByRequest(List<EdgeRequest> requestListAtT) {
        int[] spaceLimits = new int[ServersNumber];

        List<List<Integer>> results = new ArrayList<>();
        for (int data = 0; data < DataNumber; data++) {
            results.add(new ArrayList<>());
        }

        for (int m = 0; m < ServersNumber; m++) {
            spaceLimits[m] = OriginSpaceLimits[m];
        }

        DataBenefitsOnServer dbos = getServerAndDataWithMaximumCoveragePerCacheUnitByRequest(
                results, requestListAtT, spaceLimits);

        while (isSpacesAvailable(spaceLimits) && dbos != null) {
            if (!BlackList.isEmpty()) {
                if (!BlackList.get(dbos.server).contains(dbos.data)) {
                    results.get(dbos.data).add(dbos.server);
                }
            } else {
                results.get(dbos.data).add(dbos.server);
            }
            spaceLimits[dbos.server] -= DataSizes[dbos.data];

            dbos = getServerAndDataWithMaximumCoveragePerCacheUnitByRequest(
                    results, requestListAtT, spaceLimits);
        }

        return results;
    }



    private DataBenefitsOnServer getServerAndDataWithMaximumCoveragePerCacheUnit(List<List<Integer>> dataCacheList,
                                                                                 int[] spaceLimits) {
        // List<DataPopularityOnServer> dataPopularityOnServerList = new ArrayList<>();

        double currentCPC = 0;
//         double beforeBenefit = calculateTotalBenefits(dataCacheList).benefit;
//         double currentBenefit = 0;
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

    private DataBenefitsOnServer getServerAndDataWithMaximumCoveragePerCacheUnitByRequest(
            List<List<Integer>> dataCacheList, List<EdgeRequest> requestListAtT, int[] spaceLimits) {

        double currentCPC = 0;
        int s = -1;
        int d = -1;

        double currentCover = calculateTotalBenefitsByRequest(dataCacheList, requestListAtT, false).cu;

        for (EdgeServer server : Servers) {
            if (spaceLimits[server.getId()] == 0)
                continue;

            for (int data = 0; data < DataNumber; data++) {
                if (dataCacheList.get(data).contains(server.getId()) ||
                        spaceLimits[server.getId()] < DataSizes[data])
                    continue;

                dataCacheList.get(data).add(server.getId());

                BCU b = calculateTotalBenefitsByRequest(dataCacheList, requestListAtT, false);

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

    private BCU calculateTotalBenefitsByRequest(List<List<Integer>> newStorage,
                                                List<EdgeRequest> requestListAtT,
                                                boolean isNoBenefitUserCounted) {
        BCU bcu = new BCU();
        double total = 0;
        int cu = 0;
        int totalLatency = 0;

        Map<Integer, List<EdgeRequest>> userRequestsMap = new HashMap<>();
        for (EdgeRequest req : requestListAtT) {
            userRequestsMap.computeIfAbsent(req.userId, k -> new ArrayList<>()).add(req);
        }

        for (Map.Entry<Integer, List<EdgeRequest>> entry : userRequestsMap.entrySet()) {
            int uid = entry.getKey();
            List<EdgeRequest> userRequests = entry.getValue();

            EdgeUser user = getUserById(uid);
            if (user == null) {
                continue;
            }

            for (EdgeRequest req : userRequests) {
                int data = req.dataId;
                List<Integer> serverList = newStorage.get(data);


                int benefit = 0;
                boolean isFromCloud = true;

                for (int sid : user.nearEdgeServers) {
                    if (serverList.contains(sid) && !ServerDataFromCloud[sid][data]) {

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
                if (!isFromCloud) {
                    cu++;
                    totalLatency += (LatencyLimit - benefit);
                }
            }
        }

        bcu.benefit = total;
        bcu.cu = cu;
        bcu.latency = totalLatency;
        return bcu;
    }



    public class BCU {
        public double benefit;
        public int cu;
        public int latency;
    }

    public BCU calculateTotalBenefitsbyRequest(List<List<Integer>> newStorage, List<EdgeRequest> requestListAtT, boolean isNoBenefitUserCounted) {
        BCU bcu = new BCU();

        double total = 0;
        int cu = 0;
        int totalLatency = 0;

        Map<Integer, List<EdgeRequest>> userRequestsMap = new HashMap<>();
        for (EdgeRequest req : requestListAtT) {
            userRequestsMap.computeIfAbsent(req.userId, k -> new ArrayList<>()).add(req);
        }

        for (Map.Entry<Integer, List<EdgeRequest>> entry : userRequestsMap.entrySet()) {
            int uid = entry.getKey();
            List<EdgeRequest> userRequests = entry.getValue();

            EdgeUser user = getUserById(uid); 
            if (user == null) {
                continue;
            }

            for (EdgeRequest req : userRequests) {
                int data = req.dataId;
                List<Integer> serverList = newStorage.get(data);

                int benefit = 0;
                boolean isFromCloud = true;

                for (int sid : user.nearEdgeServers) {
                    if (serverList.contains(sid) && !ServerDataFromCloud[sid][data]) {
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
                if (!isFromCloud) {
                    cu++;
                    totalLatency += (LatencyLimit - benefit);
                }
            }
        }

        bcu.benefit = total;
        bcu.cu = cu;
        bcu.latency = totalLatency;
        return bcu;
    }

    
    private EdgeUser getUserById(int userId) {
        
        for (EdgeUser user : Users) {
            if (user.id == userId) {
                return user;
            }
        }
        return null;
    }



    private BCU  calculateTotalBenefits(List<List<Integer>> newStorages, boolean isNoBenefitUserCounted) {
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
            // System.out.println("User " + u.id + " - Benefit " + benefit *
            // BenefitUnitCost);
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
                total++; 
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

    private double calculateHitRatioAttackedFromRequests(
            List<List<Integer>> storages,
            List<EdgeRequest> requestListAtT,
            Set<Integer> attackServerSet,
            boolean isNoBenefitUserCounted
    ) {
        double total = 0;
        int cu = 0;

        for (EdgeRequest req : requestListAtT) {
            if (!attackServerSet.contains(req.serverId)) continue;

            total++; 

            int data = req.dataId;
            List<Integer> serverList = storages.get(data);
            List<Integer> candidateServers = Users.get(req.userId).nearEdgeServers;

            boolean isFromCloud = true;
            for (int sid : candidateServers) {
                if (serverList.contains(sid) && !ServerDataFromCloud[sid][data]) {
                    if (isNoBenefitUserCounted) {
                        isFromCloud = false;
                        break;
                    } else if (ServerDataDistance[sid][data] < LatencyLimit) {
                        isFromCloud = false;
                        break;
                    }
                }
            }

            if (!isFromCloud) {
                cu++;
            }
        }

        return total == 0 ? 0 : (double) cu / total;
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

                            java.util.Collections.shuffle(list, random);
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

    public static List<Integer> tamperRequestListWithGaussianNoise(
            int dataNumber, List<List<Integer>> requestOnServer,  double noiseMean, double noiseStdDev) {


        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int i = 0; i < dataNumber; i++) {
            frequencyMap.put(i, 0);
        }
        for (List<Integer> requestList : requestOnServer) {
            for (int data : requestList) {
                frequencyMap.put(data, frequencyMap.getOrDefault(data, 0) + 1);
            }
        }

        int originalTotal = frequencyMap.values().stream().mapToInt(Integer::intValue).sum();

        Random random = new Random();
        Map<Integer, Integer> noisyFrequencyMap = frequencyMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    int originalFreq = entry.getValue();
                    double noise = random.nextGaussian() * noiseStdDev + noiseMean;
                    return Math.max(0, (int) Math.round(originalFreq + noise));
                }
        ));

        int noisyTotal = noisyFrequencyMap.values().stream().mapToInt(Integer::intValue).sum();
        int delta = originalTotal - noisyTotal;

        if (delta != 0) {
            List<Integer> sortedKeys = noisyFrequencyMap.keySet().stream()
                    .sorted(Comparator.comparingInt(noisyFrequencyMap::get).reversed())
                    .collect(Collectors.toList());

            for (int key : sortedKeys) {
                if (delta == 0) break;
                int currentValue = noisyFrequencyMap.get(key);
                int adjustment = Math.min(Math.abs(delta), currentValue);
                if (delta > 0) {
                    noisyFrequencyMap.put(key, currentValue + adjustment);
                    delta -= adjustment;
                } else {
                    noisyFrequencyMap.put(key, currentValue - adjustment);
                    delta += adjustment;
                }
            }
        }

        List<Integer> noisyRequestList = new ArrayList<>();
        noisyFrequencyMap.forEach((key, value) -> {
            for (int i = 0; i < value; i++) {
                noisyRequestList.add(key);
            }
        });

        Collections.shuffle(noisyRequestList);

        System.out.println("Original Total: " + originalTotal);
        System.out.println("Noisy Total: " + noisyRequestList.size());
        System.out.println("Noisy Frequency Map: " + noisyFrequencyMap);

        return noisyRequestList;
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
        double epsilon = 1.0;
        for (int i = 0; i < requestOnServer.size(); i++) {
            int j = 0;
//            List<Integer> noiseRequestOnServer2 = addLaplaceNoiseToRequests(requestOnServer.get(i), epsilon, DataNumber);
            //TODO solution 1
//            List<Integer> noiseRequestOnServer2 = addUniformNoise(requestOnServer.get(i));
//            List<Integer> noiseRequestOnServer = addGaussianNoiseToR
//
//            equests(requestOnServer.get(i), 0,3, DataNumber);
            //TODO solution 2

            List<Integer> noiseRequestOnServer = addSoftmaxNoise(requestOnServer.get(i), DataNumber);
//            List<Integer> noiseRequestOnServer = addGaussianNoiseToRequests(requestOnServer.get(i), 0, requestOnServer.get(i).size()/2
//                    , DataNumber);
//            List<Integer> noiseRequestOnServer = addLaplaceNoiseToRequests(requestOnServer.get(i), epsilon, DataNumber);

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

    public List<Double> getTimes() {
        return Times;
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