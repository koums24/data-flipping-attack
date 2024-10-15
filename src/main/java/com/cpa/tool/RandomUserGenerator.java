package com.cpa.tool;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.cpa.tool.ZipfGenerator.generateZipfSameSequence;

public class RandomUserGenerator {

    public EdgeUser generateUser(List<EdgeServer> servers, double fromArea, double toArea, int id, int dataNumber,
                                 int time) {
        EdgeUser user = new EdgeUser();
        user.id = id;
        user.location = -1;

        while (!isLocationValid(user.location, servers)) {
            user.location = ThreadLocalRandom.current().nextDouble(fromArea, toArea);
        }
        // System.out.print("User id: " + id + " ");
        //user Zipf
//        user.dataList = getRandomList(dataNumber, time);
        user.dataList = generateZipfSameSequence(time, 0, dataNumber - 1);
//        user.dataList = generateLDAList(id,time, 0, dataNumber - 1);
//        user.dataList = generateFLAList(id,time, 0, dataNumber - 1);

//        different Zipf distribution for each user
//        user.dataList = generateZipfListForUser(id, time, 0, dataNumber - 1);

//        Map<Integer, Integer> frequencyMap = new HashMap<>();
//
//        for (Integer number : user.dataList) {
//            frequencyMap.put(number, frequencyMap.getOrDefault(number, 0) + 1);
//        }
//
//        // 按照出现次数降序排序
//        List<Map.Entry<Integer, Integer>> sortedList = frequencyMap.entrySet()
//                .stream()
//                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
//                .collect(Collectors.toList());
//
//        for (Map.Entry<Integer, Integer> entry : sortedList) {
//            System.out.println("Number " + entry.getKey() + ": " + entry.getValue() + " times");
//        }

        //FLA and LDA attack
//        user.dataList = generateFLAList(id, time, 0, dataNumber - 1);
//        user.dataList = generateLDAList(id, time, 0, dataNumber - 1);


        for (int i = 0; i < servers.size(); i++) {
            if (user.location >= servers.get(i).fromArea && user.location <= servers.get(i).toArea) {
                user.nearEdgeServers.add(i);
                servers.get(i).directCoveredUsers.add(id);
            }
        }

        return user;
    }

    private List<Integer> generateLDAList(int userId, int size, int min, int max) {
        List<Integer> mappedSequence = new ArrayList<>();
        Random random = new Random();
        //生成全部为unpopular data的request list
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
        if(userId % 5 == 0){
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
            double exponent = 1.0; //

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  //
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }

            // 将Zipf值映射到[min, max]的范围，考虑更多的随机性

            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); //
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); //
                mappedValue = Math.min(max, Math.max(min, mappedValue)); //
                mappedSequence.add(mappedValue);
            }

            // 乱序mappedSequence列表
            Collections.shuffle(mappedSequence, userRandom); //
        }

        return mappedSequence;
    }

    private boolean isLocationValid(double location, List<EdgeServer> servers) {
        for (int i = 0; i < servers.size(); i++) {
            if (location >= servers.get(i).fromArea && location <= servers.get(i).toArea) {
                return true;
            }
        }
        return false;
    }

}
