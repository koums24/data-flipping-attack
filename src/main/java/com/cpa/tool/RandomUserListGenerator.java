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

import static com.cpa.tool.ZipfGenerator.generateZipfDiverseSequence;
import static com.cpa.tool.ZipfGenerator.generateZipfSameSequence;


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

    public List<EdgeUser> generateUserListFromRealWorldData(int serversNumber, List<EdgeServer> servers,
                                                            int usersNumber, int dataNumber, int time) {

        RandomUserGenerator ug = new RandomUserGenerator();

        List<EdgeUser> rowUsers = new ArrayList<>();
        List<EdgeUser> allUsers = readUsersFromCsv();

        for (EdgeUser user : allUsers) {

            for (EdgeServer server : servers) {
//                if (distance(server.lat, server.lng, user.lat, user.lng) <= server.radius) {
                if (distance(server.lat, server.lng, user.lat, user.lng) <= 200) {
                    // in the coverage
                    user.nearEdgeServers.add(server.id);
//                     user.id = id;
//                     server.directCoveredUsers.add(id);
                }
            }
            if (user.nearEdgeServers.size() > 0) {
                rowUsers.add(user);
//                 id++;
            }
        }

        List<Integer> userIndex = new ArrayList<>();

        for (EdgeServer server : servers) {
            for (EdgeUser user : rowUsers) {
                int index = rowUsers.indexOf(user);
                if (!userIndex.contains(index) && user.nearEdgeServers.contains(server.id)) {
                    userIndex.add(index);
                    break;
                }
            }
        }

        while (userIndex.size() != usersNumber) {
            if (userIndex.size() == rowUsers.size()) {
                System.out.println("All users added: " + userIndex.size());
                break;
            }
            int index = ThreadLocalRandom.current().nextInt(0, rowUsers.size());
            EdgeUser user = rowUsers.get(index);
            if (!userIndex.contains(index)) {
                for (EdgeServer server : servers) {
                    if (!user.nearEdgeServers.contains(server.id) && !userIndex.contains(index)) {
                        userIndex.add(index);
                    }
                }
            }
        }

        List<EdgeUser> users = new ArrayList<>();
        int id = 0;

        for (int index : userIndex) {
            EdgeUser user = rowUsers.get(index);
            for (EdgeServer server : servers) {
//                if (distance(server.lat, server.lng, user.lat, user.lng) <= server.radius) {
                if (distance(server.lat, server.lng, user.lat, user.lng) <= 200) {
                    // in the coverage
                    user.id = id;
                    server.directCoveredUsers.add(id);
                }
            }
            if (user.nearEdgeServers.size() > 0) {
                users.add(user);
                id++;
            }

            //TODO change user distribution for experiments
        user.dataList = generateZipfSameSequence(time, 0, dataNumber - 1); //same Zipf distribution for each user
        user.dataList = generateZipfDiverseSequence(user.id, time, 0, dataNumber - 1); //different Zipf distribution for each user
//        user.dataList = generateMultiSequence(user.id, time,0,dataNumber - 1); //multi distribution
//            user.dataList = generateFLAList(user.id, time,0,dataNumber - 1); // FLA distribution
//            user.dataList = generateLDAList(user.id, time,0,dataNumber - 1);  // LDA distribution
        }

        return users;
    }


    private List<Integer> generateMultiSequence(int userId, int size, int min, int max) {
        List<Integer> sequence = new ArrayList<>();
        Random baseRandom = new Random(userId); // Random based on userId
//        generateNormalDistributionSequence(sequence, size, min, max, baseRandom);
        // Divide users into three groups: Normal, Poisson, and Zipf distributions
        if (userId % 3 == 0) {
            // First 1/3 users: Normal distribution centered around mean
            generateNormalDistributionSequence(sequence, size, min, max, baseRandom);
        } else if (userId % 3 == 1) {
            // Second 1/3 users: Poisson distribution
            generatePoissonDistributionSequence(sequence, size, min, max, baseRandom);
        } else {
            // Last 1/3 users: Zipf distribution
            generateZipfDistributionSequence(sequence, size, min, max, baseRandom);
        }

        return sequence;
    }

    // Generates requests based on a normal distribution centered at the midpoint
    private static void generateNormalDistributionSequence(List<Integer> sequence, int size, int min, int max, Random random) {
        double mean = (min + max) / 2.0;
        double stdDev = (max - min) / 6.0; // 99.7% of data falls within this range

        for (int i = 0; i < size; i++) {
            int value = (int) Math.round(random.nextGaussian() * stdDev + mean);
            value = Math.min(max, Math.max(min, value)); // Ensure within bounds
            sequence.add(value);
        }
    }

    // Generates requests based on a Poisson distribution
    private static void generatePoissonDistributionSequence(List<Integer> sequence, int size, int min, int max, Random random) {
        int lambda = (max - min) / 2; // Mean of Poisson distribution

        for (int i = 0; i < size; i++) {
            int value = generatePoisson(lambda, random);
            value = Math.min(max, Math.max(min, value)); // Ensure within bounds
            sequence.add(value);
        }
    }

    // Helper function to generate a Poisson-distributed value
    private static int generatePoisson(int lambda, Random random) {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);
        return k - 1;
    }

    // Generates requests based on a Zipf distribution focusing on the top 1/5 of the data
    private static void generateZipfDistributionSequence(List<Integer> sequence, int size, int min, int max, Random random) {
        double exponent = 1.0; //

        for (int i = 1; i <= size; i++) {
            double zipfValue = (1.0 / Math.pow(i, exponent));
            int value = (int) Math.round(min + (zipfValue * (max - min)));
            sequence.add(value);
        }

        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
            int mappedValue = (int) (value * (max - min) / sequence.get(0));
            mappedValue = Math.min(max, Math.max(min, mappedValue)); //
            mappedSequence.add(mappedValue);
        }

        Collections.shuffle(mappedSequence, random);
        sequence.clear();
        sequence.addAll(mappedSequence);
    }


    private List<Integer> generateLDAList(int userId, int size, int min, int max) {
        List<Integer> mappedSequence = new ArrayList<>();
        Random random = new Random();
        //unpopular data的request list
        if(userId % 2 == 0){
            for (int i = 0; i < size; i++) {
                int randomNum = ThreadLocalRandom.current().nextInt(max-5, max);
                mappedSequence.add(randomNum);
            }
        }else{
            //generate Zipf
            List<Integer> sequence= new ArrayList<>();
            Random userRandom = new Random(userId); //
//        Random userRandom = new Random(); //
//        double exponent = 1 + userRandom.nextDouble(); //
            double exponent = 1.1; //

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  // 归一化
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }


            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); //
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); //
                mappedValue = Math.min(max, Math.max(min, mappedValue)); //
                mappedSequence.add(mappedValue);
            }

            Collections.shuffle(mappedSequence, userRandom); //
        }

        return mappedSequence;
    }

    private List<Integer> generateFLAList(int userId, int size, int min, int max) {
        List<Integer> mappedSequence = new ArrayList<>();
        //unpopular data的request list
        if(userId % 10 == 0){
            int unpopulardata = max - 1;
            for (int i = 0; i < size; i++) {
                mappedSequence.add(unpopulardata);
            }
        }else{
            //generate Zipf
            List<Integer> sequence= new ArrayList<>();
            Random userRandom = new Random(userId); //
//        Random userRandom = new Random(); //
//        double exponent = 1 + userRandom.nextDouble(); //
            double exponent = 1.1; //

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  //
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }

            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); //
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); //
                mappedValue = Math.min(max, Math.max(min, mappedValue)); //
                mappedSequence.add(mappedValue);
            }
            Collections.shuffle(mappedSequence, userRandom); //
        }

        return mappedSequence;
    }

    private double distance(double lat1, double lng1, double lat2, double lng2) {
        GeodeticCalculator geoCalc = new GeodeticCalculator();

        Ellipsoid reference = Ellipsoid.WGS84;

        GlobalPosition serverPoint = new GlobalPosition(lat1, lng1, 0.0); // Point A

        GlobalPosition userPoint = new GlobalPosition(lat2, lng2, 0.0); // Point B

        double distance = geoCalc.calculateGeodeticCurve(reference, userPoint, serverPoint).getEllipsoidalDistance();

        return distance;
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
