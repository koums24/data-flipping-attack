package com.cpa.models;

import com.cpa.objectives.EdgeUser;
import ilog.concert.*;
import ilog.cp.IloCP;

import java.util.ArrayList;
import java.util.List;

public class DelayOrientedModel {

    private int ServersNumber;
    private int[] SpaceLimits;
    private int DataNumber;
    private int[][] UserBenefits;
    private List<EdgeUser> Users;
    private int Time;
    private List<List<Integer>> RequestsList;

    private List<Double> Ps;
    private List<Double> Caches;
    private List<Double> Penalties;
    private List<Double> ServedRequestsNumbers;
    private List<Double> MigrationCosts;
    private List<Double> CMigrationTimes;
    private List<Double> SMigrationTimes;
    private List<Double> AverageLatencies;

    public List<Double> getPs() {
        return Ps;
    }

    public List<Double> getCaches() {
        return Caches;
    }

    public List<Double> getPenalties() {
        return Penalties;
    }

    public List<Double> getServedRequestsNumbers() {
        return ServedRequestsNumbers;
    }

    public List<Double> getMigrationCosts() {
        return MigrationCosts;
    }

    public List<Double> getCMigrationTimes() {
        return CMigrationTimes;
    }

    public List<Double> getSMigrationTimes() {
        return SMigrationTimes;
    }

    public List<Double> getAverageLatencies() {
        return AverageLatencies;
    }

    private int[][] CurrentStorage;
    private int[][] ServerAvailableStorage;
    private int[][] ServerAvailableStorageWithDistance;
    private int[][] DistanceMetrix;

    private int CurrentTime;
    private int LatencyLimit;
    private double CacheUnitCost;
    private double PenaltyUnitCost;
    private double CloudMigrationUnitCost;
    private double ServerMigrationUnitCost;

    private List<String> Texts;

    public DelayOrientedModel(int serversNumber, int[][] userBenefits, int[][] distanceMetrix, int[] spaceLimits,
                              List<EdgeUser> users, int dataNumber, int latencyLimit, double value, double cacheUnitCost,
                              double penaltyUnitCost, double cloudMigrationUnitCost, double serverMigrationUnitCost, double v, int time,
                              List<List<Integer>> requestsList) {
        ServersNumber = serversNumber;
        SpaceLimits = new int[ServersNumber];
        DistanceMetrix = distanceMetrix;
        LatencyLimit = latencyLimit;

        DataNumber = dataNumber;
        UserBenefits = userBenefits;
        Users = users;
        Time = time;
        RequestsList = requestsList;

        CacheUnitCost = cacheUnitCost;
        PenaltyUnitCost = penaltyUnitCost;
        CloudMigrationUnitCost = cloudMigrationUnitCost;
        ServerMigrationUnitCost = serverMigrationUnitCost;

        ServerAvailableStorage = new int[ServersNumber][DataNumber];
        ServerAvailableStorageWithDistance = new int[ServersNumber][DataNumber];
        resetServerAvailableStorage();

        CurrentStorage = new int[ServersNumber][DataNumber];
        for (int i = 0; i < ServersNumber; i++) {
            for (int j = 0; j < DataNumber; j++) {
                CurrentStorage[i][j] = 0;
            }
        }

        for (int i = 0; i < ServersNumber; i++) {
            SpaceLimits[i] = spaceLimits[i];
        }

        Ps = new ArrayList<>();
        Caches = new ArrayList<>();
        Penalties = new ArrayList<>();
        ServedRequestsNumbers = new ArrayList<>();
        MigrationCosts = new ArrayList<>();
        CMigrationTimes = new ArrayList<>();
        SMigrationTimes = new ArrayList<>();
        AverageLatencies = new ArrayList<>();

        Texts = new ArrayList<>();
    }

    private void resetServerAvailableStorage() {
        for (int i = 0; i < ServersNumber; i++) {
            for (int k = 0; k < DataNumber; k++) {
                ServerAvailableStorage[i][k] = 0;
                ServerAvailableStorageWithDistance[i][k] = 999;
            }
        }
    }

    public void runDO() {

        CurrentTime = 1;

        while (CurrentTime < Time) {
            runEMDCIPRound();
        }

        // System.out.println("Total Cost = " + getCost());
        // System.out.println("Benefit/Cost = " + getBenefitPerReplica());

        // Texts.add("Delay Oriented Model");
        // Texts.add(addTexts("Ps: ", Ps));
        // Texts.add(addTexts("Caches: ", Caches));
        // Texts.add(addTexts("Latencies: ", Latencies));
        // Texts.add(addTexts("SRNs: ", ServedRequestsNumbers));
    }

    public List<String> getTexts() {
        return Texts;
    }

    public void runEMDCIPRound() {

        // System.out.println("Index: " + mIndex + " dataNumber: " + dataNumber + "
        // mDataNumber: " + mDataNumber + " mServersNumber: " + mServersNumber);

        try {

            IloCP cp = new IloCP();

            List<IloIntVar[]> rList = new ArrayList<>();
            IloNumExpr cache = cp.linearNumExpr();
            IloNumExpr latency = cp.linearNumExpr();
            IloNumExpr penalty = cp.linearNumExpr();
            IloNumExpr migration = cp.linearNumExpr();
            IloNumExpr sMigrationTimes = cp.linearNumExpr();
            IloNumExpr cMigrationTimes = cp.linearNumExpr();
            IloNumExpr p = cp.linearNumExpr();

            for (int i = 0; i < ServersNumber; i++) {
                IloIntVar[] r = cp.intVarArray(DataNumber, 0, 1);
                rList.add(r);

                cache = cp.sum(cache, cp.sum(r));
            }

            cache = cp.prod(cache, CacheUnitCost);

            for (int i = 0; i < ServersNumber; i++) {
                for (int k = 0; k < DataNumber; k++) {
                    double cost;
                    int distanceFromServer, isFromEdgeServer;
                    if (ServerAvailableStorage[i][k] == 0) {
                        distanceFromServer = 0;
                        isFromEdgeServer = 0;
                        cost = CloudMigrationUnitCost;
                    } else {
                        distanceFromServer = ServerAvailableStorageWithDistance[i][k];
                        isFromEdgeServer = 1;
                        cost = ServerMigrationUnitCost * ServerAvailableStorageWithDistance[i][k];
                    }
                    // TODO: add a distance[i][k] matrix to present the shortest path for i to get k
                    // from edge servers

                    sMigrationTimes = cp.sum(sMigrationTimes,
                            cp.prod(rList.get(i)[k], (1 - CurrentStorage[i][k]) * isFromEdgeServer));
                    cMigrationTimes = cp.sum(cMigrationTimes,
                            cp.prod(rList.get(i)[k], (1 - CurrentStorage[i][k]) * (1 - isFromEdgeServer)));
                    migration = cp.sum(migration, cp.prod(rList.get(i)[k], (1 - CurrentStorage[i][k]) * cost));
                }
            }

            IloIntExpr[][] maxBenifitsExprs = new IloIntExpr[Users.size()][DataNumber];
            IloIntExpr[][][] userBenefitsExprs = new IloIntExpr[Users.size()][DataNumber][ServersNumber];

            for (int i = 0; i < ServersNumber; i++) {
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int task = 0;
                        if (Users.get(j).dataList.get(CurrentTime) == k && RequestsList.get(CurrentTime).contains(j)) {
                            task = 1;
                        }
                        userBenefitsExprs[j][k][i] = cp.prod(UserBenefits[i][j] * task, rList.get(i)[k]);
                    }
                }
            }

            IloIntExpr[][] servedDataExprs = new IloIntExpr[Users.size()][DataNumber];

            IloIntExpr[] benifitsExprs = new IloIntExpr[Users.size()];
            IloIntExpr[] servedExprs = new IloIntExpr[Users.size()];

            for (int j = 0; j < Users.size(); j++) {
                for (int k = 0; k < DataNumber; k++) {
                    maxBenifitsExprs[j][k] = cp.max(userBenefitsExprs[j][k]);
                    servedDataExprs[j][k] = cp.min(1, maxBenifitsExprs[j][k]);
                }
                benifitsExprs[j] = cp.sum(maxBenifitsExprs[j]);
                servedExprs[j] = cp.sum(servedDataExprs[j]);
            }

            IloIntExpr coveredRequestNum = cp.sum(servedExprs);
            penalty = cp.sum(RequestsList.get(CurrentTime).size(), cp.negative(coveredRequestNum));
            penalty = cp.prod(penalty, PenaltyUnitCost);

            p = cp.sum(cache, penalty);
            p = cp.sum(p, migration);

            int csLatency = RequestsList.get(CurrentTime).size() * 20;
            latency = cp.sum(csLatency, cp.prod(cp.sum(benifitsExprs), -1));

            IloIntExpr latencyInESN = cp.diff(cp.prod(LatencyLimit, coveredRequestNum), cp.sum(benifitsExprs));

            IloNumExpr averageLatencyInESN = cp.div(cp.prod(latencyInESN, 1000), coveredRequestNum);

            cp.add(cp.minimize(latency));

            IloConstraint spaceConstraint = cp.le(cp.sum(rList.get(0)), SpaceLimits[0]);
            for (int i = 1; i < ServersNumber; i++) {
                spaceConstraint = cp.and(spaceConstraint, cp.le(cp.sum(rList.get(i)), SpaceLimits[i]));
            }
            cp.add(spaceConstraint);

            cp.setOut(null);

            if (cp.solve()) {

                Ps.add(cp.getValue(p));
                Caches.add(cp.getValue(cache));
                Penalties.add(cp.getValue(latency));
                MigrationCosts.add(cp.getValue(migration));
                CMigrationTimes.add(cp.getValue(cMigrationTimes));
                SMigrationTimes.add(cp.getValue(sMigrationTimes));
                AverageLatencies.add(cp.getValue(averageLatencyInESN) / 1000);

                // TotalCost = TotalCost + cp.getObjValues()[0];

                // System.out.println("Benefits: " + cp.getObjValues()[0]);
                //
                // System.out.println("Selected servers are:");

                double served = 0;
                for (int j = 0; j < Users.size(); j++) {
                    for (int k = 0; k < DataNumber; k++) {
                        if (cp.getValue(maxBenifitsExprs[j][k]) > 0) {
                            served++;
                        }
                    }
                }
                ServedRequestsNumbers.add(served);

                int replica = 0;
                for (int k = 0; k < DataNumber; k++) {
                    // System.out.print("Data " + k + " is stored in: ");
                    for (int i = 0; i < rList.size(); i++) {
                        if (cp.getValue(rList.get(i)[k]) == 1) {
                            CurrentStorage[i][k] = 1;
                            // System.out.print(i + " ");
                            replica++;
                        } else {
                            CurrentStorage[i][k] = 0;
                        }
                    }
                    // System.out.println();
                }

                resetServerAvailableStorage();
                for (int i = 0; i < ServersNumber; i++) {
                    for (int k = 0; k < DataNumber; k++) {
                        int d = 999;
                        for (int j = 0; j < ServersNumber; j++) {
                            if (DistanceMetrix[i][j] <= LatencyLimit && CurrentStorage[j][k] == 1) {
                                // TODO: use a value related to distance
                                ServerAvailableStorage[i][k] = 1;
                                if (DistanceMetrix[i][j] <= d) {
                                    d = DistanceMetrix[i][j];
                                }
                            }
                        }
                        ServerAvailableStorageWithDistance[i][k] = d;
                    }
                }

                System.out.println("---- DO ---- " + CurrentTime);

                System.out.println("AverageLatency (Y) = " + cp.getValue(averageLatencyInESN) / 1000 + ", Replica = "
                        + replica + " - Cache = " + cp.getValue(cache) + " - Penalty = " + cp.getValue(penalty)
                        + " - migration = " + cp.getValue(migration) + " - cMigrationTimes = "
                        + cp.getValue(cMigrationTimes) + " - sMigrationTimes = " + cp.getValue(sMigrationTimes)
                        + " - Benefits = " + cp.getValue(cp.sum(benifitsExprs)) + " - P = " + cp.getValue(p)
                        + " - Servers Number = " + ServersNumber + " - Obj (P) = " + cp.getValue(p)
                        + " - coveredRequestNum = " + cp.getValue(coveredRequestNum) + "  ----  " + served
                        + " total latency = " + cp.getValue(latencyInESN));

                // System.out.println("Replica = " + replica + " - Cache = " +
                // cp.getValue(cache) + " - latency = "
                // + cp.getValue(latency) + " - Obj (P) = " + cp.getValue(p) + " - TotalLatency
                // = "
                // + totalLatency);

                // System.out.println();

                // System.out.println();
                CurrentTime++;

            } else {
                System.out.println(" No solution found ");
            }

            cp.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }

    }

}