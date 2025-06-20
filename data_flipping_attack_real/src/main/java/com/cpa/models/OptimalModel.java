package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;
import ilog.concert.*;
import ilog.cp.IloCP;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.cpa.models.CoverageFirstModel.tamperDistributionOnServerMap;
import static com.cpa.models.CoverageFirstModel.tamperRequestListWithGaussianNoise;
import static com.cpa.models.DDCPADefense.DDCPAdefenseModel;
import static com.cpa.models.LaplaceNoiseDefense.*;
import static com.cpa.models.RCCModel.selectRandomPercentage;

// Lyapunov without cost
public class OptimalModel {

    private int ServersNumber;
    private int[] SpaceLimits;
    private int DataNumber;
    private int[][] UserBenefits;
    private List<EdgeUser> Users;
    private int Time;
    private List<List<Integer>> RequestsList;
    private double AttackRatio;

    private List<Double> TotalLatency;
    private List<Double> AverageLatency;
    private List<Double> CoveragedUsers;
    private List<Double> Times;
    List<List<Integer>> BlackList;
    int MinSize = 9999;

    public List<Double> getTimes() {
        return Times;
    }

    public List<Double> getTotalLatency() {
        return TotalLatency;
    }

    public List<Double> getAverageLatency() {
        return AverageLatency;
    }

    public List<Double> getCoveragedUsers() {
        return CoveragedUsers;
    }

    private List<List<Integer>> CurrentStorage;

    private int[][] DistanceMetrix;
    private int[][] ServerDataDistance;
    private boolean[][] ServerDataFromCloud;
    private int[][] ServerAvailableStorage;
    private int[] DataSizes;
    private int[][] AdjMetrix;

    private int CurrentTime;
    private int LatencyLimit;
    private List<Double> HitRatio;
    private List<Double> HitRatioAttacked;

    double DelayEdgeEdge = 0.01;
    double DelayEdgeCloud = 0.2;

    private List<String> Texts;

    public OptimalModel(int serversNumber, int[][] userBenefits, int[][] distanceMetrix, int[][] adjMetrix, int[] spaceLimits,
                        int[] dataSizes, List<EdgeUser> users, List<EdgeServer> servers, int dataNumber, int latencyLimit, int time,
                        List<List<Integer>> requestsList, List<List<Integer>> currentStorage, double attackRatio) {
        ServersNumber = serversNumber;
        SpaceLimits = new int[ServersNumber];
        DistanceMetrix = distanceMetrix;
        AdjMetrix = adjMetrix;

        LatencyLimit = latencyLimit;

        DataSizes = dataSizes;

        DataNumber = dataNumber;
        UserBenefits = userBenefits;
        Users = users;
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

        for (int i = 0; i < ServersNumber; i++) {
            SpaceLimits[i] = spaceLimits[i];
        }

        TotalLatency = new ArrayList<>();
        AverageLatency = new ArrayList<>();
        CoveragedUsers = new ArrayList<>();
        HitRatio = new ArrayList<>();
        HitRatioAttacked = new ArrayList<>();
        Times = new ArrayList<>();
        Texts = new ArrayList<>();
        BlackList = new ArrayList<>();

        ServerAvailableStorage = new int[ServersNumber][DataNumber];
        resetServerAvailableStorage();


        for (int size : DataSizes) {
            if (size < MinSize)
                MinSize = size;
        }

        updateServerDataDistance();
    }

    private void resetServerAvailableStorage() {
        for (int i = 0; i < ServersNumber; i++) {
            for (int k = 0; k < DataNumber; k++) {
                ServerAvailableStorage[i][k] = 0;
            }
        }
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

    public void runCEDCO() {
        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            runcedc();
            updateServerDataDistance();
        }
    }

    public void runOptimal() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            runO();
            updateServerDataDistance();
        }


    }

    public void runOptimalAttack() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            // System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            runOAttack();
            updateServerDataDistance();
        }

    }

    public void runOptimalRandomAttack() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            // System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            runORandomAttack();
            updateServerDataDistance();
        }

    }

    public void runOptimalAttackDefense() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            // System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            runOAttackDefense();
            updateServerDataDistance();
        }

    }

    public void runOptimalDefenseCon() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            // System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            run0DefenseCon();
            updateServerDataDistance();
        }

    }

    public void runOptimalSafeDefense() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            // System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            runOSafeDefense();
            updateServerDataDistance();
        }

    }

    public void runOptimalSafeCon() {

        CurrentTime = 0;

        while (CurrentTime < Time) {
            System.out.println(CurrentTime);
            // System.out.println("Request number " + RequestsList.get(CurrentTime).size());
            runOSafeCon();
            updateServerDataDistance();
        }

    }

    public List<String> getTexts() {
        return Texts;
    }

    private void runO() {
        Instant start = Instant.now();

        try {

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
//                         int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (!BlackList.isEmpty()) {
                            if (!BlackList.get(i).contains(k)) {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        } else {
                            if (cp.getValue(rList.get(i)[k]) == 1) {
                                CurrentStorage.get(k).add(i);
                            }
                        }

                    }
                }
//                CurrentStorage = fillUpStorage(CurrentStorage);

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }


//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);

                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(0.0);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void runOAttack() {
        Instant start = Instant.now();

        try {

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            //TODO flip attack
            //
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
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("OPT requestOnServer safe: " + requestOnServer);
            System.out.println("OPT fromUser safe: " + fromUser);

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            flipAttack(attackServerList, requestOnServer, fromUser);
//            randomNoiseAttack(attackServerList, requestOnServer, fromUser);


            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
                        // int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (!BlackList.isEmpty()) {
                            if (!BlackList.get(i).contains(k)) {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        } else {
                            if (cp.getValue(rList.get(i)[k]) == 1) {
                                CurrentStorage.get(k).add(i);
                            }
                        }
                    }
                }

//                CurrentStorage = fillUpStorage(CurrentStorage);


                //TODO 
                for (EdgeUser user : Users) {
                   
                    List<Integer> userDataList = Users.get(user.id).dataList;

                    
                    userDataList.clear();

                 
                    List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                    userDataList.addAll(backupList);
                }

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }
                double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, attackServerList, requestOnServer, fromUser);

//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);
                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(hitRatioAttacked);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal attack ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);
                System.out.println("HitRatio Attacked:" + hitRatioAttacked);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void runORandomAttack() {
        Instant start = Instant.now();

        try {

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            //TODO flip attack
            //
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
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("OPT requestOnServer safe: " + requestOnServer);
            System.out.println("OPT fromUser safe: " + fromUser);

            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            randomNoiseAttack(attackServerList, requestOnServer, fromUser);


            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
                        // int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (!BlackList.isEmpty()) {
                            if (!BlackList.get(i).contains(k)) {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        } else {
                            if (cp.getValue(rList.get(i)[k]) == 1) {
                                CurrentStorage.get(k).add(i);
                            }
                        }
                    }
                }

//                CurrentStorage = fillUpStorage(CurrentStorage);


                //TODO 
                for (EdgeUser user : Users) {
                   
                    List<Integer> userDataList = Users.get(user.id).dataList;

                    
                    userDataList.clear();

                 
                    List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                    userDataList.addAll(backupList);
                }

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }
                double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, attackServerList, requestOnServer, fromUser);

//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);
                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(hitRatioAttacked);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal attack ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);
                System.out.println("HitRatio Attacked:" + hitRatioAttacked);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void run0DefenseCon() {
        Instant start = Instant.now();

        try {

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            //TODO attack
            //
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
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("OPT requestOnServer safe: " + requestOnServer);
            System.out.println("OPT fromUser safe: " + fromUser);


            // attack server number by attack ratio
            int[] attackServerList = selectRandomPercentage(ServersNumber, AttackRatio);
            flipAttack(attackServerList, requestOnServer, fromUser);
//            randomNoiseAttack(attackServerList, requestOnServer, fromUser);

            //TODO detect and defense
            detectAttack(requestOnServer);

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
                        // int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (!BlackList.isEmpty()) {
                            if (!BlackList.get(i).contains(k)) {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        } else {
                            if (cp.getValue(rList.get(i)[k]) == 1) {
                                CurrentStorage.get(k).add(i);
                            }
                        }

                    }
                }

//                CurrentStorage = fillUpStorage(CurrentStorage);


                //TODO 
                for (EdgeUser user : Users) {
                   
                    List<Integer> userDataList = Users.get(user.id).dataList;

                    
                    userDataList.clear();

                 
                    List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                    userDataList.addAll(backupList);
                }

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }
                double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, attackServerList, requestOnServer, fromUser);

//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);
                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(hitRatioAttacked);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal attack ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);
                System.out.println("HitRatio Attacked:" + hitRatioAttacked);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void runOAttackDefense() {
        Instant start = Instant.now();

        try {

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            //TODO flip attack
            //
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
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                //for each  server, record user sending request CurrentTime
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            System.out.println("OPT requestOnServer safe: " + requestOnServer);
            System.out.println("OPT fromUser safe: " + fromUser);

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
            System.out.println("OPT requestOnServer attacked: " + requestOnServer);
            System.out.println("OPT fromUser safe: " + fromUser);
            //TODO defense
            addNoiseDefense(requestOnServer, fromUser);

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
                        // int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (cp.getValue(rList.get(i)[k]) == 1) {
                            if (!BlackList.isEmpty()) {
                                if (!BlackList.get(i).contains(k)) {
                                    if (cp.getValue(rList.get(i)[k]) == 1) {
                                        CurrentStorage.get(k).add(i);
                                    }
                                }
                            } else {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        }
                    }
                }

//                CurrentStorage = fillUpStorage(CurrentStorage);


                //TODO 
                for (EdgeUser user : Users) {
                   
                    List<Integer> userDataList = Users.get(user.id).dataList;

                    
                    userDataList.clear();

                 
                    List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                    userDataList.addAll(backupList);
                }

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }
                double hitRatioAttacked = calculateHitRatioAttacked(CurrentStorage, attackServerList, requestOnServer, fromUser);

//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);
                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());
                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(hitRatioAttacked);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal attack ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);
                System.out.println("HitRatio Attacked:" + hitRatioAttacked);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void runOSafeDefense() {
        Instant start = Instant.now();

        try {
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
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            //TODO defense
            addNoiseDefense(requestOnServer, fromUser);

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
//                         int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (!BlackList.isEmpty()) {
                            if (!BlackList.get(i).contains(k)) {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        } else {
                            if (cp.getValue(rList.get(i)[k]) == 1) {
                                CurrentStorage.get(k).add(i);
                            }
                        }
                    }
                }

//                CurrentStorage = fillUpStorage(CurrentStorage);
                //TODO 
                for (EdgeUser user : Users) {
                   
                    List<Integer> userDataList = Users.get(user.id).dataList;
                    userDataList.clear();
                    List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                    userDataList.addAll(backupList);
                }

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }

                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);

                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(0.0);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }
            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void runOSafeCon() {
        Instant start = Instant.now();

        try {
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
                int destServer = (u.nearEdgeServers.size() - 1) / 2;
                requestOnServer.get(u.nearEdgeServers.get(destServer)).add(data);
                fromUser.get(u.nearEdgeServers.get(destServer)).add(u.id);
            }

            //TODO defense
            detectAttack(requestOnServer);

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
//                         int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }


            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);

            cp.add(cp.maximize(totalBenefits));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (!BlackList.isEmpty()) {
                            if (!BlackList.get(i).contains(k)) {
                                if (cp.getValue(rList.get(i)[k]) == 1) {
                                    CurrentStorage.get(k).add(i);
                                }
                            }
                        } else {
                            if (cp.getValue(rList.get(i)[k]) == 1) {
                                CurrentStorage.get(k).add(i);
                            }
                        }
                    }
                }

//                CurrentStorage = fillUpStorage(CurrentStorage);
                //TODO 
                for (EdgeUser user : Users) {
                   
                    List<Integer> userDataList = Users.get(user.id).dataList;

                    
                    userDataList.clear();

                 
                    List<Integer> backupList = backupDataListOnUser.get(Users.get(user.id).id);
                    userDataList.addAll(backupList);
                }

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }
//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;
                TotalLatency.add(totalLatency);

                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

                CoveragedUsers.add(coveredRequestNum);
                HitRatio.add(coveredRequestNum / Users.size());
                HitRatioAttacked.add(0.0);
                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                System.out.println("---- Optimal ---- " + CurrentTime);
                System.out.println("Total Latency: " + totalLatency);
                System.out.println("Average Latency: " + latency / RequestsList.get(CurrentTime).size());
//                 System.out.println("Revenue: " + cp.getValue(obj));
                System.out.println("Benefits: " + cp.getValue(totalBenefits));
//                 System.out.println("MCost: " + cp.getValue(migration));
                System.out.println("CoveredUser:" + coveredRequestNum);

                CurrentTime++;
            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

    private void runcedc() {
        Instant start = Instant.now();

        try {

            IloCP cp = new IloCP();
            // y is accumulate latency, p is the original obj

            List<IloIntVar[]> rList = new ArrayList<>();

            IloNumExpr cache = cp.linearNumExpr();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);

                cache = cp.sum(cache, cp.sum(r));
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];


            IloNumExpr migration = cp.linearNumExpr();
            IloNumExpr sMigrationTimes = cp.linearNumExpr();
            IloNumExpr cMigrationTimes = cp.linearNumExpr();

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        int isCovered = Users.get(j).nearEdgeServers.contains(i) ? 1 : 0;
                        // int oneMinusTheta = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp
                                .prod((LatencyLimit - ServerDataDistance[i][k]) * task * isCovered, rList.get(i)[k]);
                    }
                }
            }

            for (int i = 0; i < ServersNumber; i++) {
                for (int k = 0; k < DataNumber; k++) {
                    double cost;
                    if (ServerAvailableStorage[i][k] == 1) {
                        cost = 5;
                    } else {
                        cost = 1;
                    }

                    int x = 0;
                    if (CurrentStorage.get(k).contains(i)) {
                        x = 1;
                    }

                    sMigrationTimes = cp.sum(sMigrationTimes,
                            cp.prod(rList.get(i)[k], (1 - x) * ServerAvailableStorage[i][k]));
                    cMigrationTimes = cp.sum(cMigrationTimes,
                            cp.prod(rList.get(i)[k], (1 - x) * (1 - ServerAvailableStorage[i][k])));
                    migration = cp.sum(migration, cp.prod(rList.get(i)[k], (1 - x) * cost));
                }
            }

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];

            IloIntExpr[][] dataRequestsCoveredExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[] userRequestsCoveredExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                    dataRequestsCoveredExprs[j][k] = cp.min(1, maxBenifitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
                userRequestsCoveredExprs[j] = cp.sum(dataRequestsCoveredExprs[j]);
            }

            IloNumExpr totalBenefits = cp.sum(benifitsExprs);
            IloNumExpr totalCoveredRequests = cp.sum(userRequestsCoveredExprs);

//            IloNumExpr obj = cp.sum(cp.prod(cache, 100), cp.prod(totalCoveredRequests, 10), cp.prod(totalBenefits, 0));


            IloNumExpr obj = cp.sum(cp.prod(totalCoveredRequests, 100), cp.prod(totalBenefits, 1));

            cp.add(cp.maximize(obj));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 0; i < ServersNumber; i++) {

                IloNumExpr cachedDataSpaces = cp.linearNumExpr();

                for (int k = 0; k < DataNumber; k++) {
                    cachedDataSpaces = cp.sum(cachedDataSpaces, cp.prod(rList.get(i)[k], DataSizes[k]));
                }

                spaceConstraint = cp.and(spaceConstraint, cp.le(cachedDataSpaces, SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                for (int k = 0; k < DataNumber; k++) {
                    CurrentStorage.get(k).clear();
                    for (int i = 0; i < rList.size(); i++) {
                        if (cp.getValue(rList.get(i)[k]) == 1) {
                            CurrentStorage.get(k).add(i);
                        }
                    }
                }

                CurrentStorage = fillUpStorage(CurrentStorage);

                double coveredRequestNum = 0;
                double latency = 0;

                for (int user : RequestsList.get(CurrentTime)) {
                    int data = Users.get(user).dataList.get(CurrentTime);
                    for (int s : CurrentStorage.get(data)) {
                        if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                            coveredRequestNum++;
                            latency += ServerDataDistance[s][data];
                            break;
                        }
                    }
                }

//                double latency = RequestsList.get(CurrentTime).size() * LatencyLimit - cp.getValue(totalBenefits);
                double totalLatency = latency * DelayEdgeEdge + (Users.size() - coveredRequestNum) * DelayEdgeCloud;

                TotalLatency.add(totalLatency);
                AverageLatency.add(totalLatency / RequestsList.get(CurrentTime).size());

                // System.out.println("totalCoveredRequests = " +
                // cp.getValue(totalCoveredRequests)
                // + " coveredRequestNum = " + coveredRequestNum);

                CoveragedUsers.add(coveredRequestNum);


//				resetServerAvailableStorage();
//				for (int i = 0; i < ServersNumber; i++) {
//					for (int k = 0; k < DataNumber; k++) {
//						for (int j = 0; j < ServersNumber; j++) {
//							if (AdjMetrix[i][j] == 1 && CurrentStorage[j][k] == 1) {
//								ServerAvailableStorage[i][k] = 1;
//								break;
//							}
//						}
//					}
//				}

                Instant end = Instant.now();
                double duration = (double) (Duration.between(start, end).toMillis()) / 1000;
                Times.add(duration);

                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }


    public List<List<Integer>> fillUpStorage(List<List<Integer>> strategy) {

        int[] spaceLimits = new int[ServersNumber];

        for (int i = 0; i < ServersNumber; i++) {
            spaceLimits[i] = SpaceLimits[i];
        }

        for (int data = 0; data < DataNumber; data++) {
            for (int s : strategy.get(data)) {
                spaceLimits[s] -= DataSizes[data];
            }
        }

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

    private boolean isSpacesAvailable(int[] spaceLimits) {

        for (int i : spaceLimits) {
            if (i >= MinSize)
                return true;
        }

        return false;
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
                int data = requestData.get(j);
                int user = userSendingReq.get(j);
                List<Integer> serverList = storages.get(data);
                for (int s : serverList) {
                    if (Users.get(user).nearEdgeServers.contains(s) && !ServerDataFromCloud[s][data]) {
                        cu++;
                        break;
                    }
                }
            }

        }
        return (double) cu / total;
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

    public List<Double> getHitRatio() {
        return HitRatio;
    }


    public List<Double> getHitRatioAttacked() {
        return HitRatioAttacked;
    }

}

