package com.cpa.tool;

import com.cpa.objectives.Request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ZipfGenerator {

    private static final Random random = new Random();

    // 生成符合Zipf分布的概率分布
    public static double[] generateZipfDistribution(int n, double s) {
        double[] zipf = new double[n];
        double sum = 0.0;
        for (int i = 1; i <= n; i++) {
            sum += 1.0 / Math.pow(i, s);
        }
        for (int i = 1; i <= n; i++) {
            zipf[i - 1] = (1.0 / Math.pow(i, s)) / sum;
        }
        return zipf;
    }

    public static double[] generateZipfDistributionDiff(int n, double s) {
        double[] zipf = new double[n];
        double sum = 0.0;
//        int peakShift = new Random().nextInt(n);
        // Calculate the sum of Zipf probabilities
        for (int i = 1; i <= n; i++) {
            sum += 1.0 / Math.pow(i, s);
        }

        // Apply the peak shift while maintaining the Zipf distribution
        for (int i = 1; i <= n; i++) {
            // Adjust the index with the peak shift
            int peakShift = new Random().nextInt(n);
            int shiftedIndex = (i + peakShift - 1) % n + 1;
            zipf[i - 1] = (1.0 / Math.pow(shiftedIndex, s)) / sum;
        }

        return zipf;
    }

    // 生成符合Zipf分布的请求序列
    public static List<Integer> generateZipfRequests(int totalRequests, double[] zipfDistribution) {
        List<Integer> requests = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            double rand = Math.random();
            double cumulativeProbability = 0.0;
            for (int j = 0; j < zipfDistribution.length; j++) {
                cumulativeProbability += zipfDistribution[j];
                if (rand <= cumulativeProbability) {
                    requests.add(j);
                    break;
                }
            }
        }

        // 对生成的请求序列进行乱序
        Collections.shuffle(requests);

        return requests;
    }

    public static List<Request> generateNormalRequest(String user, int size, double mean, double stdDev, int lowerBound, int upperBound) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        List<Integer> contentList = IntStream.range(0, size)
                .mapToObj(i -> {
                    // 生成正态分布的浮点数
                    double value = mean + stdDev * random.nextGaussian();
                    // 将值截断到指定范围
                    int intValue = (int) Math.round(value);
                    intValue = Math.max(lowerBound, Math.min(intValue, upperBound - 1));
                    return intValue;
                })
                .collect(Collectors.toList());
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100; // 随机时间间隔
            currentTime += interval;
            int content = contentList.get(i);  // 生成0到contentRange-1之间的Zipf分布内容
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }

    // 生成符合Zipf分布的序列
    public static List<Integer> generateZipfSameSequence(int size, int min, int max) {
        Random random = new Random();
        List<Integer> sequence = new ArrayList<>();
        double exponent = 1.1; // Zipf指数

        for (int i = 1; i <= size; i++) {
            double zipfValue = (1.0 / Math.pow(i, exponent));
            int value = (int) Math.round(min + (zipfValue * (max - min)));
            sequence.add(value);
        }

        // 将Zipf值映射到0 ， max-1的范围
        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
            int mappedValue = (int) (value * (max - min) / sequence.get(0));
            mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
            mappedSequence.add(mappedValue);
        }

        Collections.shuffle(mappedSequence, random); // 用相同的Random实例打乱顺序

        return mappedSequence;
    }

//    //每个user不同Zipf，合并后不是Zipf
    public static List<Integer> generateZipfDiverseSequence(int userId, int size, int min, int max) {
        List<Integer> sequence = new ArrayList<>();
        Random baseRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
        Random userRandom = new Random(userId + baseRandom.nextInt()); //
        double exponent = 1 + baseRandom.nextDouble(); // 基于userId的随机Zipf指数
//        double exponent = 1; // 基于userId的随机Zipf指数

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
        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1); // 添加额外随机性
            int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); // 添加额外随机性
            mappedValue = Math.min(max, Math.max(min, mappedValue)); // 确保值在min和max之间
            mappedSequence.add(mappedValue);
        }

        // 乱序mappedSequence列表
        Collections.shuffle(mappedSequence, userRandom); // 用相同的Random实例打乱顺序

        return mappedSequence;
    }

    // 将列表随机拆分为多个小列表
    public static List<List<Integer>> randomSplitList(List<Integer> list, int numLists, int elementsPerList) {
        List<List<Integer>> listOfLists = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < numLists; i++) {
            List<Integer> sublist = new ArrayList<>();
            for (int j = 0; j < elementsPerList; j++) {
                // 从原始列表中随机取出一个元素加入子列表
                int randomIndex = random.nextInt(list.size());
                sublist.add(list.remove(randomIndex));
            }
            listOfLists.add(sublist);
        }

        return listOfLists;
    }

}
