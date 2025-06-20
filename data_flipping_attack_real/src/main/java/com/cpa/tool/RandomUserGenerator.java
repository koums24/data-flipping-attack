package com.cpa.tool;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.cpa.tool.ZipfGenerator.generateZipfListForUser;
import static com.cpa.tool.ZipfGenerator.generateZipfSequence;

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
        user.dataList = generateZipfSequence(time, 0, dataNumber - 1);
//        user.dataList = generateLDAList(id,time, 0, dataNumber - 1);
//        user.dataList = generateFLAList(id,time, 0, dataNumber - 1);
        //TODO
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
//        // 打印每个数字及其出现次数
//        System.out.println("Number frequencies sorted by occurrence:");
//        for (Map.Entry<Integer, Integer> entry : sortedList) {
//            System.out.println("Number " + entry.getKey() + ": " + entry.getValue() + " times");
//        }
        //FLA attack
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
            Random userRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
//        Random userRandom = new Random(); //
//        double exponent = 1 + userRandom.nextDouble(); // 基于userId的随机Zipf指数
            double exponent = 1.1; // 基于userId的随机Zipf指数

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  // 归一化
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }

            // 将Zipf值映射到[min, max]的范围，考虑更多的随机性

            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); // 添加额外随机性
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); // 添加额外随机性
                mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
                mappedSequence.add(mappedValue);
            }

            // 乱序mappedSequence列表
            Collections.shuffle(mappedSequence, userRandom); // 用相同的Random实例打乱顺序
        }

        return mappedSequence;
    }

    private List<Integer> generateFLAList(int userId, int size, int min, int max) {
        List<Integer> mappedSequence = new ArrayList<>();
        //生成全部为unpopular data的request list
        if(userId % 5 == 0){
            int unpopulardata = max - 1;
            for (int i = 0; i < size; i++) {
                mappedSequence.add(unpopulardata);
            }
        }else{
            //generate Zipf
            List<Integer> sequence= new ArrayList<>();
            Random userRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
//        Random userRandom = new Random(); //
//        double exponent = 1 + userRandom.nextDouble(); // 基于userId的随机Zipf指数
            double exponent = 1.1; // 基于userId的随机Zipf指数

            double sum = 0;
            for (int i = 1; i <= size; i++) {
                sum += (1.0 / Math.pow(i, exponent));
            }

            for (int i = 1; i <= size; i++) {
                double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;  // 归一化
                int value = (int) Math.round(min + (zipfValue * (max - min)));
                sequence.add(value);
            }

            // 将Zipf值映射到[min, max]的范围，考虑更多的随机性

            for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); // 添加额外随机性
                int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); // 添加额外随机性
                mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
                mappedSequence.add(mappedValue);
            }

            // 乱序mappedSequence列表
            Collections.shuffle(mappedSequence, userRandom); // 用相同的Random实例打乱顺序
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


    public ArrayList<Integer> getRandomList(int dataNumber, int time) {
        ArrayList<Integer> list = new ArrayList<Integer>();
        // System.out.println("dataNumber: " + dataNumber + " listNumber" + listNumber);
        Random r = new Random();
        list.add(r.nextInt(dataNumber));

        while (list.size() < time) {

            list.add(r.nextInt(dataNumber));

            // double isKeep = ThreadLocalRandom.current().nextDouble(1);

            // if (list.size() > 3 && isKeep > 0.5) {
            // list.add(list.get(list.size() - 2));
            // } else {
            // list.add(r.nextInt(dataNumber));
            // }

            // System.out.print(temp + " ");
        }
        // System.out.println();
        return list;
    }

}
