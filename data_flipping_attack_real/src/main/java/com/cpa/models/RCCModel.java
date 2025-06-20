package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.cpa.models.CoverageFirstModel.tamperDistributionOnServer;
import static com.cpa.models.CoverageFirstModel.tamperDistributionOnServerMap;
import static com.cpa.models.DDCPADefense.DDCPAdefenseModel;
import static com.cpa.models.LaplaceNoiseDefense.addLaplaceNoiseToRequests;

public class RCCModel {
    private int ServersNumber;
    private int[] OriginSpaceLimits;
    private int DataNumber;
    private int[][] UserBenefits;
    private double AttackRatio;
    private List<EdgeUser> Users;
    private List<EdgeServer> Servers;
    private int Time;
    private List<List<Integer>> RequestsList;
    List<List<Integer>> BlackList;

    private List<Double> Revenue;
    private List<Double> MigrationCosts;
    private List<Double> TotalBenefits;
    private List<Double> AverageLatency;
    private List<Double> AverageRevenue;
    private List<Double> CoveragedUsers;
    private List<Double> HitRatio;
    private List<Double> AttackedHitRatio;
    private List<Double> Times;

    private List<List<Integer>> CurrentStorage;
    private int[][] DistanceMetrix;
    private int[][] ServerDataDistance;
    private boolean[][] ServerDataFromCloud;
    private int[] DataSizes;


    private int CurrentTime;
    private int LatencyLimit;
    //    private double BenefitUnitCost = 1.5;
//    private double CloudMigrationUnitCost = 10;
//    private double ServerMigrationUnitCost = 2;
    private double BenefitUnitCost = 0.004;
    private double CloudMigrationUnitCost = 0.016;
    private double ServerMigrationUnitCost = 0.006;

    public RCCModel(int serversNumber, int[][] userBenefits, int[][] distanceMetrix, int[] spaceLimits, int[] dataSizes,
                    List<EdgeUser> users, List<EdgeServer> servers, int dataNumber, int latencyLimit, int time, List<List<Integer>> requestsList,
                    double k, double attackRatio) {
        //初始化
        ServersNumber = serversNumber;
        OriginSpaceLimits = spaceLimits;
        DistanceMetrix = distanceMetrix;

        DataSizes = dataSizes;
        LatencyLimit = latencyLimit;
        DataNumber = dataNumber;
        UserBenefits = userBenefits;
        Users = users;
        Servers = servers;
        RequestsList = requestsList;
        AttackRatio = attackRatio;

        Time = time;

        ServerDataDistance = new int[serversNumber][dataNumber];
        ServerDataFromCloud = new boolean[serversNumber][dataNumber];
        CurrentStorage = new ArrayList<>();
        for (int data = 0; data < DataNumber; data++) {
            CurrentStorage.add(new ArrayList<>());
        }

        Revenue = new ArrayList<>();
        MigrationCosts = new ArrayList<>();
        TotalBenefits = new ArrayList<>();
        AverageRevenue = new ArrayList<>();
        AverageLatency = new ArrayList<>();
        CoveragedUsers = new ArrayList<>();
        HitRatio = new ArrayList<>();
        AttackedHitRatio = new ArrayList<>();
        Times = new ArrayList<>();
        BlackList = new ArrayList<>();

//        updateServerDataDistance();

    }

    public void runRCC() {
        CurrentTime = 0;

        while (CurrentTime < Time) {

            Instant start = Instant.now();

            //TODO
            //detect and defense
//            detectAttack(requestOnServer);

            List<List<Integer>> decision = getDecisionWithMaxCoveredUser();

            System.out.println("caching strategy" + decision.toString());

//            for (int data = 0; data < DataNumber; data++) {
//                System.out.print("data " + data + " is cached on servers ");
//                for (int server : decision.get(data)) {
//                    System.out.print(server + " ");
//                }
//                System.out.println();
//            }
//            updateServerDataDistance();

            double benefit = calculateTotalBenefits(decision);

//            BCU bcu = calculateTotalBenefits(decision, false);

            double cost = migrationCost(decision);
            double coveredUsers = calculateTotalCoveredUsers(decision);

            if (benefit > cost) {
                CurrentStorage.clear();
                CurrentStorage.addAll(decision);
            } else {
                System.out.println("benefit < cost: " + benefit + " < " + cost);
                cost = 0;
            }


            coveredUsers = calculateTotalCoveredUsers(CurrentStorage);
            benefit = calculateTotalBenefits(CurrentStorage);


            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
            TotalBenefits.add(benefit);
            MigrationCosts.add(cost);
            Revenue.add(benefit - cost);
            AverageRevenue.add(benefit - cost / coveredUsers);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            AttackedHitRatio.add(0.0);
//            HitRatio.add((coveredUsers - 100) / (Users.size() - 100));
//            HitRatio.add( coveredUsers / (Users.size() - 40));

            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
            double totalLatency = latency * 0.01 + (Users.size() - coveredUsers) *0.2;
            //calculate latency
//            double mecLatency = calculateMECLatency(CurrentStorage);
//            double cloudLatency = (Users.size() - coveredUsers) * 200;
//
//            double totalLatency = mecLatency + cloudLatency;

//            double totalLatency = calculateTotalLatency(CurrentStorage);

            AverageLatency.add(totalLatency / (Users.size()));
            Times.add(duration);

            System.out.println("---- RCC ---- " + CurrentTime);
            System.out.println("Revenue: " + (benefit - cost));
            System.out.println("AverageLatency: " + totalLatency / (Users.size()));
            System.out.println("Benefits: " + benefit);
            System.out.println("MCost: " + cost);
            System.out.println("Coverd:" + coveredUsers);
//            System.out.println("Hit Ratio:" + hitRatio);
            System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println();

            CurrentTime++;
        }

    }

    public void runRCCDefense() {
        CurrentTime = 0;

        while (CurrentTime < Time) {

            Instant start = Instant.now();

            //备份user的datalist
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

            //backup
//            for (EdgeUser u : Users) {
//                if (!RequestsList.get(CurrentTime).contains(u.id))
//                    continue;
//
//                Random random = new Random();
//                int data = u.dataList.get(CurrentTime);
//                requestOnServer.get(u.nearEdgeServers.get(random.nextInt(u.nearEdgeServers.size()))).add(data);
//            }
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
            System.out.println("requestOnServer safe" + requestOnServer);
            //TODO
            //detect and defense
//            detectAttack(requestOnServer);

            //add noise defense
            addNoiseDefense(requestOnServer, fromUser);


            List<List<Integer>> decision = getDecisionWithMaxCoveredUser();

            System.out.println("caching strategy" + decision.toString());

//            for (int data = 0; data < DataNumber; data++) {
//                System.out.print("data " + data + " is cached on servers ");
//                for (int server : decision.get(data)) {
//                    System.out.print(server + " ");
//                }
//                System.out.println();
//            }

            double benefit = calculateTotalBenefits(decision);
            double cost = migrationCost(decision);
            double coveredUsers = calculateTotalCoveredUsers(decision);

            if (benefit > cost) {
                CurrentStorage.clear();
                CurrentStorage.addAll(decision);
            } else {
                System.out.println("benefit < cost: " + benefit + " < " + cost);
                cost = 0;
            }

            //恢复datalist to calculate
            for (EdgeUser user : Users) {
                // 获取用户的 dataList
                List<Integer> userDataList = Users.get(user.id).dataList;

                // 清空 userDataList，不影响 backupDataListOnUser 中的数据
                userDataList.clear();

                // 从 backupDataListOnUser 获取独立的副本进行添加
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }

            coveredUsers = calculateTotalCoveredUsers(CurrentStorage);
            benefit = calculateTotalBenefits(CurrentStorage);


            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
            TotalBenefits.add(benefit);
            MigrationCosts.add(cost);
            Revenue.add(benefit - cost);
            AverageRevenue.add(benefit - cost / coveredUsers);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
//            HitRatio.add((coveredUsers - 100) / (Users.size() - 100));
//            HitRatio.add( coveredUsers / (Users.size() - 40));
            double mecLatency = calculateMECLatency(CurrentStorage);
            double cloudLatency = (Users.size() - coveredUsers) * 200;

            double totalLatency = mecLatency + cloudLatency;
//            double totalLatency = calculateTotalLatency(CurrentStorage);

            AverageLatency.add(totalLatency / (Users.size()));
            Times.add(duration);

            System.out.println("---- RCC ---- " + CurrentTime);
            System.out.println("Revenue: " + (benefit - cost));
            System.out.println("AverageLatency: " + totalLatency / (Users.size()));
            System.out.println("Benefits: " + benefit);
            System.out.println("MCost: " + cost);
            System.out.println("Coverd:" + coveredUsers);
//            System.out.println("Hit Ratio:" + hitRatio);
            System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println();

            CurrentTime++;
        }

    }

    private void addNoiseDefense(List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {
        double epsilon = 1.0;
        for (int i = 0; i < requestOnServer.size(); i++) {
            int j = 0;
            List<Integer> noiseRequestOnServer = addLaplaceNoiseToRequests(requestOnServer.get(i), epsilon, DataNumber);
            System.out.println("server" + i + "requests with noise" + noiseRequestOnServer);
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

    public void runRCCAttack() {
        CurrentTime = 0;

        while (CurrentTime < Time) {

            Instant start = Instant.now();
            // TODO Attack
            //备份user的datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }
            System.out.println("\n");
            System.out.println("backupDataListOnUser: " + backupDataListOnUser);

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
                int destServer = (u.nearEdgeServers.size() -1)/2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("RCC requestOnServer safe: " + requestOnServer);
            System.out.println("RCC fromUser safe: " + fromUser);

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            flipAttack(attackServerList, requestOnServer, fromUser);

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
                int destServer = (u.nearEdgeServers.size() -1)/2;
                requestOnServerAttack.get(u.nearEdgeServers.get(destServer)).add(data);

            }
            System.out.println("RCC requestOnServer attacked: " + requestOnServerAttack);

            //Detect and defense
            //TODO
            //conventional detect and defense
//            detectAttack(requestOnServer);

            //add noise defense
//            double epsilon =1.0;
//            for (int i = 0; i < requestOnServerAttack.size(); i++) {
//                int j = 0;
//                List<Integer> noiseRequestOnServer = addLaplaceNoiseToRequests(requestOnServerAttack.get(i), epsilon, DataNumber);
//                System.out.println("server" + i + "requests with noise" + noiseRequestOnServer);
//                List<Integer> userList = fromUser.get(i);
//                for (int k = 0; k < userList.size(); k++) {
//                    List<Integer> dataList = Users.get(userList.get(k)).dataList;
//                    dataList.set(CurrentTime, noiseRequestOnServer.get(k));
//                }
//            }


            //cache strategy
            List<List<Integer>> decision = getDecisionWithMaxCoveredUser();
            System.out.println("caching strategy" + decision.toString());

//            for (int data = 0; data < DataNumber; data++) {
//                System.out.print("data " + data + " is cached on servers ");
//                for (int server : decision.get(data)) {
//                    System.out.print(server + " ");
//                }
//                System.out.println();
//            }

            //attack end

//            计算latency前 恢复原request list
            for (EdgeUser user : Users) {
                // 获取用户的 dataList
                List<Integer> userDataList = Users.get(user.id).dataList;

                // 清空 userDataList，不影响 backupDataListOnUser 中的数据
                userDataList.clear();

                // 从 backupDataListOnUser 获取独立的副本进行添加
                List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                userDataList.addAll(backupList);
            }



            double benefit = calculateTotalBenefits(decision);
            double cost = migrationCost(decision);
            double coveredUsers = calculateTotalCoveredUsers(decision);

            if (benefit > cost) {
                CurrentStorage.clear();
                CurrentStorage.addAll(decision);
            } else {
                System.out.println("benefit < cost: " + benefit + " < " + cost);
                cost = 0;
            }

            coveredUsers = calculateTotalCoveredUsers(CurrentStorage);


            double attackedHitRatio = calculateAttackedHitRatio(CurrentStorage,attackServerList,fromUser,requestOnServer);
            benefit = calculateTotalBenefits(CurrentStorage);
//            double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - benefit;
//            double latency =calculateTotalLatency(CurrentStorage);

            Instant end = Instant.now();
            double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
            TotalBenefits.add(benefit);
            MigrationCosts.add(cost);
            Revenue.add(benefit - cost);
            AverageRevenue.add(benefit - cost / coveredUsers);
            CoveragedUsers.add(coveredUsers);
            HitRatio.add(coveredUsers / Users.size());
            AttackedHitRatio.add(attackedHitRatio);
//            HitRatio.add(coveredUsers/ (Users.size()-40));
            double mecLatency = calculateMECLatency(CurrentStorage);
            double cloudLatency = (Users.size() - coveredUsers) * 200;
            double totalLatency = mecLatency + cloudLatency;
            AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
            Times.add(duration);


            System.out.println("---- RCC ---- " + CurrentTime);
            System.out.println("Revenue: " + (benefit - cost));
            System.out.println("Benefits: " + benefit);
            System.out.println("MCost: " + cost);
            System.out.println("Coverd:" + coveredUsers);
            System.out.println("AverageLatency: " + totalLatency / RequestsList.get(CurrentTime).size());
//            System.out.println("Hit Ratio:" + hitRatio);
            System.out.println("RPCU:" + (benefit - cost) / coveredUsers);
            System.out.println();

            CurrentTime++;
        }
    }

    private void flipAttack(int[] attackServerList, List<List<Integer>> requestOnServer, List<List<Integer>> fromUser) {

        for (int k = 0; k < attackServerList.length; k++) {
            //tamper distribution on server
//                List<Integer> requestListAttakced = tamperDistributionOnServer(requestOnServer, attackServerList[k]);
            Map<Integer, Integer> swapMap = tamperDistributionOnServerMap(DataNumber, requestOnServer, attackServerList[k]);
//                System.out.println("requestListAttakced" + requestListAttakced);
            //通过sever上对调过的request 篡改user的datalist
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


    public List<List<Integer>> getDecisionWithMaxCoveredUser() {
        List<List<Integer>> results = new ArrayList<>();
        List<List<Integer>> banned = new ArrayList<>();
//         List<Integer> servedUsers = new ArrayList<>();

        int[] spaceLimits = new int[ServersNumber];

        for (int m = 0; m < ServersNumber; m++) {
            spaceLimits[m] = OriginSpaceLimits[m];
        }

        for (int data = 0; data < DataNumber; data++) {
            results.add(new ArrayList<>());
            banned.add(new ArrayList<>());
        }

        int newCovered = 1;

        while (isSpacesAvailable(spaceLimits) && newCovered > 0) {

            int currentCU = calculateTotalCoveredUsers(results);
            double currentBenefit = calculateTotalBenefits(results);
            double currentCost = migrationCost(results);

            int maxCU = currentCU;
            int selectedData = -1;
            int selectedServer = -1;
            for (int data = 0; data < DataNumber; data++) {
                for (int i = 0; i < ServersNumber; i++) {
                    if (spaceLimits[i] < DataSizes[data] || banned.get(data).contains(i))
                        continue;

                    results.get(data).add(i);

                    int tmpCU = calculateTotalCoveredUsers(results);

                    results.get(data).remove(results.get(data).size() - 1);

                    if (maxCU < tmpCU) {
                        maxCU = tmpCU;
                        selectedData = data;
                        selectedServer = i;
                    }
                }
            }


            if (selectedServer == -1)
                break;

            spaceLimits[selectedServer] -= DataSizes[selectedData];

            //defense by blacklist
            if (!BlackList.isEmpty()) {
                if (!BlackList.get(selectedServer).contains(selectedData)) {
                    results.get(selectedData).add(selectedServer);
                }
            }else{
                results.get(selectedData).add(selectedServer);
            }


            double newBenefit = calculateTotalBenefits(results);
            double newCost = migrationCost(results);
            //新的收益增量小于新的成本增量
            if (newBenefit - currentBenefit < newCost - currentCost) {
                results.get(selectedData).remove(results.get(selectedData).size() - 1);
                banned.get(selectedData).add(selectedServer);
            }

        }

        return results;
    }

    private int calculateTotalCoveredUsers(List<List<Integer>> storages) {

        int cu = 0;
        for (EdgeUser u : Users) {
            List<Integer> serverList = storages.get(u.dataList.get(CurrentTime));

            //有edge server缓存了data？
            int benefit = 0;
            for (int sid : serverList) {
                if (UserBenefits[sid][u.id] > benefit) {
                    benefit = UserBenefits[sid][u.id];
                }
            }

            if (benefit > 0)
                cu++;
        }

        return cu;
    }

    private double calculateTotalBenefits(List<List<Integer>> storages) {
        double total = 0;
        for (EdgeUser u : Users) {
            List<Integer> serverList = storages.get(u.dataList.get(CurrentTime));

            int benefit = 0;
            for (int sid : serverList) {
                if (UserBenefits[sid][u.id] > benefit) {
                    benefit = UserBenefits[sid][u.id];
                }
            }
            total += benefit * BenefitUnitCost;
        }

        return total;
    }


    private double calculateTotalLatency(List<List<Integer>> storages) {
        double latency = 0;

        for (EdgeUser u : Users) {

            List<Integer> serverList = storages.get(u.dataList.get(CurrentTime));

            int benefit = 0;
            for (int sid : serverList) {
                if (UserBenefits[sid][u.id] > benefit) {
                    benefit = UserBenefits[sid][u.id];
                }
            }

            if (benefit > 0) {
                //user 被 cover
                latency += (LatencyLimit - benefit) * ServerMigrationUnitCost;
            } else {
                latency += CloudMigrationUnitCost;
            }

        }

        return latency;
    }

    //计算被攻击的server所覆盖范围的hit ratio
    private double calculateAttackedHitRatio(List<List<Integer>> storages, int[] attackServerList,List<List<Integer>> fromUser,List<List<Integer>> requestOnServer ) {
        int total = 0;
        int cu = 0;
        for (int i = 0; i < attackServerList.length; i++) {
            int attackServer = attackServerList[i];
            List<Integer> userSendingReq = fromUser.get(attackServer);
            List<Integer> requestData = requestOnServer.get(attackServer);
            for (int j = 0; j < requestData.size(); j++) {
                total ++;
                List<Integer> serverList = storages.get(requestData.get(j));

                //有edge server缓存了data？
                int benefit = 0;
                for (int sid : serverList) {
                    if (UserBenefits[sid][userSendingReq.get(j)] > benefit) {
                        benefit = UserBenefits[sid][userSendingReq.get(j)];
                    }
                }

                if (benefit > 0)
                    cu++;
            }
        }
        return (double) cu / total;
    }

    private double calculateMECLatency(List<List<Integer>> storages) {
        double latency = 0;
        double total = 0;
        for (EdgeUser u : Users) {
            List<Integer> serverList = storages.get(u.dataList.get(CurrentTime));

            int benefit = 0;
            for (int sid : serverList) {
                if (UserBenefits[sid][u.id] > benefit) {
                    benefit = UserBenefits[sid][u.id];
                }
            }
            if (benefit > 0) {
                latency += (LatencyLimit - benefit) * 10;
                total++;
            }
        }

        return latency;
    }


    private double migrationCost(List<List<Integer>> storages) {
        double cost = 0;

        for (int data = 0; data < DataNumber; data++) {
            for (int sid : storages.get(data)) {
                if (!CurrentStorage.get(data).contains(sid)) {
                    double distance = CloudMigrationUnitCost / ServerMigrationUnitCost;
                    for (int s : CurrentStorage.get(data)) {
                        if (distance > DistanceMetrix[sid][s]) {
                            distance = DistanceMetrix[sid][s];
                        }
                    }
                    cost = cost + distance * ServerMigrationUnitCost * DataSizes[data];
                }
            }
        }

        return cost;
    }


    private boolean isSpacesAvailable(int[] spaceLimits) {

        int minSize = 9999;
        for (int size : DataSizes) {
            if (size < minSize)
                minSize = size;
        }

        for (int i : spaceLimits) {
            if (i >= minSize)
                return true;
        }

        return false;
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
                            java.util.Collections.shuffle(list, random);
                            // 选择前 numberToSelect 个元素，并将其转为数组
                            return list.stream().limit(numberToSelect).mapToInt(Integer::intValue).toArray();
                        }
                ));
    }

    public List<Double> getTimes() {
        return Times;
    }

    public List<Double> getMigrationCosts() {
        return MigrationCosts;
    }

    public List<Double> getTotalBenefits() {
        return TotalBenefits;

    }

    public List<Double> getAverageRevenue() {
        return AverageRevenue;
    }

    public List<Double> getAverageBenefits() {
        return AverageRevenue;
    }

    public List<Double> getCoveragedUsers() {
        return CoveragedUsers;
    }

    public List<Double> getHitRatio() {
        return HitRatio;
    }

    public List<Double> getAttackedHitRatio() {
        return AttackedHitRatio;
    }

    public List<Double> getAverageLatency() {
        return AverageLatency;
    }

    public class BCU {
        public double benefit;
        public int cu;
    }

    private BCU calculateTotalBenefits(List<List<Integer>> newStorages, boolean isNoBenefitUserCounted) {
        BCU bcu = new BCU();

        double total = 0;
        int cu = 0;

        // System.out.println(RequestsList.get(CurrentTime).size());

        for (EdgeUser u : Users) {
            if (!RequestsList.get(CurrentTime).contains(u.id))
                continue;

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
            }

            // System.out.println("User " + u.id + " - Benefit " + benefit *
            // BenefitUnitCost);
        }

        bcu.benefit = total;
        bcu.cu = cu;

        return bcu;
    }

}

