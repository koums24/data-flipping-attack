package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import static com.cpa.models.CoverageFirstModel.tamperDistributionOnServer;
import static com.cpa.models.RCCModel.selectRandomPercentage;

public class LRUModel {
    private int ServersNumber;
    private int[] OriginSpaceLimits;
    private int DataNumber;
    private int[][] UserBenefits;
    private double AttackRatio;
    private List<EdgeUser> Users;
    private List<EdgeServer> Servers;
    private int Time;
    private List<List<Integer>> RequestsList;

    private List<Double> Revenue;
    private List<Double> MigrationCosts;
    private List<Double> TotalBenefits;
    private List<Double> AverageLatency;
    private List<Double> AverageRevenue;
    private List<Double> CoveragedUsers;
    private List<Double> HitRatio;
    private List<Double> Times;

    private List<List<Integer>> CurrentStorage;
    private int[][] DistanceMetrix;
    private int[] DataSizes;

    private int CurrentTime;
    private int LatencyLimit;
    private int MaxSpace;
    private List<List<Integer>> RequestOnServer;


    public LRUModel(int serversNumber, int[][] userBenefits, int[][] distanceMetrix, int[] spaceLimits, int[] dataSizes,
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
        Times = new ArrayList<>();
        RequestOnServer = new ArrayList<>();

    }

    public void runLRU() {
        CurrentTime = 1;

        while (CurrentTime < Time) {
            RequestOnServer.clear();

            for (int i = 0; i < ServersNumber; i++) {
                RequestOnServer.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;

                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                RequestOnServer.get(u.nearEdgeServers.get(random.nextInt(u.nearEdgeServers.size()))).add(data);

            }

            int hitCount = getLRUCacheAndHR(RequestOnServer);

            double hitRatio = (double) hitCount / Users.size();

            double totalLatency = Users.size() * hitRatio * 10 + Users.size() * (1 - hitRatio) * 200;

            HitRatio.add(hitRatio);

            AverageLatency.add(totalLatency / (Users.size()));

            System.out.println("---- RCC ---- " + CurrentTime);
            System.out.println("AverageLatency: " + totalLatency / (Users.size()));
            System.out.println("HitRatio: " + hitRatio);
            System.out.println();

            CurrentTime++;
        }
    }

    public void runLRUAttack() {
        CurrentTime = 1;

        while (CurrentTime < Time) {
            RequestOnServer.clear();

            //备份user的datalist
            List<List<Integer>> backupDataListOnUser = new ArrayList<>();
            for (int i = 0; i < Users.size(); i++) {
                backupDataListOnUser.add(new ArrayList<>());
            }
            for (EdgeUser u : Users) {
                backupDataListOnUser.set(u.id, new ArrayList<>(u.dataList));
            }

//            System.out.println("\n");
//            System.out.println("backupDataListOnUser: " + backupDataListOnUser);

            //data list collect by server
            for (int i = 0; i < ServersNumber; i++) {
                RequestOnServer.add(new ArrayList<>());
            }

            for (EdgeUser u : Users) {
                if (!RequestsList.get(CurrentTime).contains(u.id))
                    continue;

                Random random = new Random();
                int data = u.dataList.get(CurrentTime);
                RequestOnServer.get(u.nearEdgeServers.get(random.nextInt(u.nearEdgeServers.size()))).add(data);

            }
            List<List<Integer>> backupRequestOnServer = new ArrayList<>();

            for (List<Integer> subList : RequestOnServer) {
                // 对每个子列表进行复制
                List<Integer> subListCopy = new ArrayList<>(subList);
                // 将复制的子列表添加到备份列表中
                backupRequestOnServer.add(subListCopy);
            }
//            System.out.println("backupRequestOnServer: " + backupRequestOnServer);

            //TODO ATTACK
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
//            int[] attackServerList = {0};
            for (int k = 0; k < attackServerList.length; k++) {
                //tamper distribution on server
                List<Integer> requestListAttakced = tamperDistributionOnServer(DataNumber, RequestOnServer, attackServerList[k]);
//                Map<Integer, Integer> swapMap = tamperDistributionOnServerMap(DataNumber, RequestOnServer, attackServerList[k]);
//                System.out.println("requestListAttakced" + requestListAttakced);
                RequestOnServer.set(attackServerList[k], requestListAttakced);
            }

//            List<List<Integer>> decision = getLRUstrategy(MaxSpace);
            int hitCount = getLRUCacheAndHR(backupRequestOnServer);

            //恢复RequestOnServer
            RequestOnServer.clear();

            for (List<Integer> subList : backupRequestOnServer) {
                RequestOnServer.add(new ArrayList<>(subList));
            }

//            double hitRatio = calculateHitRatio(decision);
            double hitRatio = (double) hitCount / Users.size();

            double totalLatency = Users.size() * hitRatio * 10 + Users.size() * (1 - hitRatio) * 200;

            HitRatio.add(hitRatio);

            AverageLatency.add(totalLatency / (Users.size()));

            System.out.println("---- RCC ---- " + CurrentTime);
            System.out.println("AverageLatency: " + totalLatency / (Users.size()));
            System.out.println("HitRatio: " + hitRatio);
            System.out.println();

            CurrentTime++;
        }
    }

    private double calculateHitRatio(List<List<Integer>> decision) {
        int totalHit = 0;
        for (int i = 0; i < ServersNumber; i++) {

            for (Integer data : RequestOnServer.get(i)) {
                if (decision.get(data).contains(i)) {
                    totalHit++;
                }
            }
        }
        return (double) totalHit / (double) Users.size();
    }

    private List<List<Integer>> getLRUstrategy(int spaceLimit) {
        List<List<Integer>> results = new ArrayList<>();
        for (int data = 0; data < DataNumber; data++) {
            results.add(new ArrayList<>());
        }

        int hit = 0;
        for (int i = 0; i < ServersNumber; i++) {
            LinkedHashMap<Integer, Integer> cache = new LinkedHashMap<>(spaceLimit, 0.75f, true);

            for (Integer data : RequestOnServer.get(i)) {
                if (cache.size() >= spaceLimit) {
                    Integer eldestKey = cache.keySet().iterator().next();
                    cache.remove(eldestKey);
                }
                cache.put(data, data);
//                System.out.println("Cached data: " + data + ". Current cache: " + cache.keySet());
            }

            for (Integer data : cache.keySet()) {
                results.get(data).add(i);
            }

        }

        return results;
    }

    private int getLRUCacheAndHR(List<List<Integer>> requestList) {
        int hit = 0;
        for (int i = 0; i < ServersNumber; i++) {
            LinkedHashMap<Integer, Integer> cache = new LinkedHashMap<>(OriginSpaceLimits[i], 0.75f, true);
            for (int j = 0; j < requestList.get(i).size(); j++) {

                Integer data = RequestOnServer.get(i).get(j);
                Integer comingData = requestList.get(i).get(j);
                if (cache.containsKey(comingData)) {
                    hit++;
                }
                if (! cache.containsKey(data)) {
                    if (cache.size() >= OriginSpaceLimits[i]) {
                        Integer eldestKey = cache.keySet().iterator().next();
                        cache.remove(eldestKey);
                    }
                    cache.put(data, data);
                }

            }
        }
        return hit;
    }

    public int getServersNumber() {
        return ServersNumber;
    }

    public void setServersNumber(int serversNumber) {
        ServersNumber = serversNumber;
    }

    public int[] getOriginSpaceLimits() {
        return OriginSpaceLimits;
    }

    public void setOriginSpaceLimits(int[] originSpaceLimits) {
        OriginSpaceLimits = originSpaceLimits;
    }

    public int getDataNumber() {
        return DataNumber;
    }

    public void setDataNumber(int dataNumber) {
        DataNumber = dataNumber;
    }

    public int[][] getUserBenefits() {
        return UserBenefits;
    }

    public void setUserBenefits(int[][] userBenefits) {
        UserBenefits = userBenefits;
    }

    public double getAttackRatio() {
        return AttackRatio;
    }

    public void setAttackRatio(double attackRatio) {
        AttackRatio = attackRatio;
    }

    public List<EdgeUser> getUsers() {
        return Users;
    }

    public void setUsers(List<EdgeUser> users) {
        Users = users;
    }

    public List<EdgeServer> getServers() {
        return Servers;
    }

    public void setServers(List<EdgeServer> servers) {
        Servers = servers;
    }

    public int getTime() {
        return Time;
    }

    public void setTime(int time) {
        Time = time;
    }

    public List<List<Integer>> getRequestsList() {
        return RequestsList;
    }

    public void setRequestsList(List<List<Integer>> requestsList) {
        RequestsList = requestsList;
    }

    public List<Double> getRevenue() {
        return Revenue;
    }

    public void setRevenue(List<Double> revenue) {
        Revenue = revenue;
    }

    public List<Double> getMigrationCosts() {
        return MigrationCosts;
    }

    public void setMigrationCosts(List<Double> migrationCosts) {
        MigrationCosts = migrationCosts;
    }

    public List<Double> getTotalBenefits() {
        return TotalBenefits;
    }

    public void setTotalBenefits(List<Double> totalBenefits) {
        TotalBenefits = totalBenefits;
    }

    public List<Double> getAverageLatency() {
        return AverageLatency;
    }

    public void setAverageLatency(List<Double> averageLatency) {
        AverageLatency = averageLatency;
    }

    public List<Double> getAverageRevenue() {
        return AverageRevenue;
    }

    public void setAverageRevenue(List<Double> averageRevenue) {
        AverageRevenue = averageRevenue;
    }

    public List<Double> getCoveragedUsers() {
        return CoveragedUsers;
    }

    public void setCoveragedUsers(List<Double> coveragedUsers) {
        CoveragedUsers = coveragedUsers;
    }

    public List<Double> getHitRatio() {
        return HitRatio;
    }

    public void setHitRatio(List<Double> hitRatio) {
        HitRatio = hitRatio;
    }

    public List<Double> getTimes() {
        return Times;
    }

    public void setTimes(List<Double> times) {
        Times = times;
    }

    public List<List<Integer>> getCurrentStorage() {
        return CurrentStorage;
    }

    public void setCurrentStorage(List<List<Integer>> currentStorage) {
        CurrentStorage = currentStorage;
    }

    public int[][] getDistanceMetrix() {
        return DistanceMetrix;
    }

    public void setDistanceMetrix(int[][] distanceMetrix) {
        DistanceMetrix = distanceMetrix;
    }

    public int[] getDataSizes() {
        return DataSizes;
    }

    public void setDataSizes(int[] dataSizes) {
        DataSizes = dataSizes;
    }

    public int getCurrentTime() {
        return CurrentTime;
    }

    public void setCurrentTime(int currentTime) {
        CurrentTime = currentTime;
    }

    public int getLatencyLimit() {
        return LatencyLimit;
    }

    public void setLatencyLimit(int latencyLimit) {
        LatencyLimit = latencyLimit;
    }

    public int getMaxSpace() {
        return MaxSpace;
    }

    public void setMaxSpace(int maxSpace) {
        MaxSpace = maxSpace;
    }

    public List<List<Integer>> getRequestOnServer() {
        return RequestOnServer;
    }

    public void setRequestOnServer(List<List<Integer>> requestOnServer) {
        RequestOnServer = requestOnServer;
    }
}