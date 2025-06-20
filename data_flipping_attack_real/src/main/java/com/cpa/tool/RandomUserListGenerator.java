package com.cpa.tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalPosition;


public class RandomUserListGenerator {

    public List<EdgeUser> generateUsers(int serversNumber, int totalUsersNumber, double networkArea,
                                        List<EdgeServer> servers, int fileNumber, int time) {
        List<EdgeUser> users = new ArrayList<>();
        RandomUserGenerator ug = new RandomUserGenerator();

         System.out.println("totalUsersNumber: " + totalUsersNumber);

         //all the user sample from this distribution
//        double[] zipfDistribution = ZipfGenerator.generateZipfDistribution(fileNumber, 1.0);

        int id = 0;
        // make sure each server at least server one user, otherwise we have this edge server
        for (EdgeServer server : servers) {
            EdgeUser user = ug.generateUser(servers, server.fromArea, server.toArea, id, fileNumber, time);
            users.add(user);
            id++;

        }

        for (int i = 0; i < totalUsersNumber - serversNumber; i++) {
            EdgeUser user = ug.generateUser(servers, 0, networkArea, id, fileNumber, time);
            users.add(user);
            id++;
        }

        return users;
    }

    /**
     * generate user from real world dataset (EUA)
     * @param serversNumber
     * @param servers
     * @param usersNumber
     * @param dataNumber
     * @param time
     * @return
     */
    public List<EdgeUser> generateUserListFromRealWorldData(int serversNumber, List<EdgeServer> servers,
                                                            int usersNumber, int dataNumber, int time) {

        List<EdgeUser> allUsers = readUsersFromCsv();
        List<EdgeUser> candidateUsers = new ArrayList<>();

        // Step 1: 找出所有被至少一个服务器覆盖的用户
        for (EdgeUser user : allUsers) {
            for (EdgeServer server : servers) {
                if (DistanceCalculator.distance(server.lat, server.lng, user.lat, user.lng) <= 200) {
                    user.nearEdgeServers.add(server.id);
                }
            }
            if (!user.nearEdgeServers.isEmpty()) {
                candidateUsers.add(user);
            }
        }

        // Step 2: 确保每个服务器至少被一个用户覆盖
        Set<Integer> selectedIndices = new HashSet<>();
        for (EdgeServer server : servers) {
            for (int i = 0; i < candidateUsers.size(); i++) {
                if (candidateUsers.get(i).nearEdgeServers.contains(server.id)) {
                    selectedIndices.add(i);
                    break;
                }
            }
        }

        // Step 3: 随机补充用户直到达到 usersNumber
        Random rand = new Random();
        while (selectedIndices.size() < usersNumber && selectedIndices.size() < candidateUsers.size()) {
            int index = rand.nextInt(candidateUsers.size());
            selectedIndices.add(index);
        }

        // Step 4: 为选中的用户分配 ID、绑定服务器、生成请求序列
        List<EdgeUser> finalUsers = new ArrayList<>();
        int id = 0;
        // 保持手动添加，移除assignIdAndBindServers
        for (int index : selectedIndices) {
            EdgeUser user = candidateUsers.get(index);
            user.id = id;  // 只设置ID

            // 手动设置服务器的directCoveredUsers
            for (int serverId : user.nearEdgeServers) {
                servers.get(serverId).directCoveredUsers.add(id);
            }

            user.generateRequests(time, dataNumber, "zipf_same");
            finalUsers.add(user);
            id++;
        }

        return finalUsers;
    }

    public List<EdgeUser> readUsersFromCsv() {
        File file = new File("src/main/resources/dataset/users-melbcbd-generated.csv");

        List<EdgeUser> users = new ArrayList<>();

        Scanner sc;
        try {
            sc = new Scanner(file);
            sc.nextLine();
            while (sc.hasNextLine()) {
                EdgeUser user = new EdgeUser();
                String[] location = sc.nextLine().replaceAll(" ", "").split(",");
                user.lat = Double.parseDouble(location[0]);
                user.lng = Double.parseDouble(location[1]);
                users.add(user);
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return users;
    }

}
