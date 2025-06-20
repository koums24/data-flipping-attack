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
    public static List<Integer> generateZipfSequence(int size, int min, int max) {
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
    public static List<Integer> generateZipfListForUser(int userId, int size, int min, int max) {
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

//    public static List<Integer> generateZipfListForUser(int userId, int size, int min, int max) {
//        List<Integer> sequence = new ArrayList<>();
//        Random baseRandom = new Random(userId); // Use userId as a seed to ensure different sequences for each user
//        Random userRandom = new Random(userId + baseRandom.nextInt());
//
//        double exponent = 1.2; // Set a fixed Zipf exponent for all users
//
//        // Set the peak for this user, between min and max/2
//        int peakValue = min + userRandom.nextInt((max / 2) - min + 1);
//
//        // Generate the sum of the Zipf distribution
//        double sum = 0;
//        for (int i = 1; i <= size; i++) {
//            sum += (1.0 / Math.pow(i, exponent));
//        }
//
//        // Generate Zipf-distributed values with the same exponent but a different peak for each user
//        for (int i = 1; i <= size; i++) {
//            double zipfValue = (1.0 / Math.pow(i, exponent)) / sum; // Normalize Zipf values
//            int value = (int) Math.round(peakValue + (zipfValue * (max - peakValue))); // Adjust values around the peak
//            sequence.add(value);
//        }
//
//        // Map Zipf values into the range [min, max/2] with some randomness
//        List<Integer> mappedSequence = new ArrayList<>();
//        for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt((max - min + 1) / 2); // Add extra randomness
//            mappedValue = Math.min(max, Math.max(min, mappedValue)); // Ensure values stay within bounds
//            mappedSequence.add(mappedValue);
//        }
//
//        // Shuffle the sequence to introduce randomness
//        Collections.shuffle(mappedSequence, userRandom);
//
//        return mappedSequence;
//    }



    //每个user不同Zipf，合并后是Zipf
//    public static List<Integer> generateZipfListForUser(int userId, int size, int min, int max) {
//        List<Integer> sequence = new ArrayList<>();
//        Random userRandom = new Random(userId); // 使用userId作为种子，以确保每个用户的序列不同
//
//        // 基于 userId 生成的扰动
//        double userExponent = 1.5 + userRandom.nextDouble();  // 在基础指数上加一个小的扰动
//
//        // 计算 Zipf 分布的归一化常数
//        double sum = 0;
//        for (int i = 1; i <= size; i++) {
//            sum += (1.0 / Math.pow(i, userExponent));
//        }
//
//        // 生成用户的 Zipf 序列
//        for (int i = 1; i <= size; i++) {
//            double zipfValue = (1.0 / Math.pow(i, userExponent)) / sum;  // 归一化
//            double scaledValue = min + (zipfValue * (max - min + 1));  // 计算后的浮点数结果
//            int value = (int) Math.floor(scaledValue);  // 使用 Math.floor 来保持较低值
//            sequence.add(value);
//        }
//
//        // 乱序 sequence 列表
//        Collections.shuffle(sequence, userRandom); // 用相同的 Random 实例打乱顺序
//
//        return sequence;
//    }

    /**
     *
     * @param size 总数
     * @param numLists 需要生成的列表数量
     * @param elementsPerList 每个列表的元素个数
     * @param min 最小值
     * @param max 最大值
     * @return
     */
    public static List<List<Integer>> splitZipfDistribution( int size , int numLists, int elementsPerList, int min, int max) {

        List<Integer> zipfSequence = generateZipfSequence(size, min, max);

        // 打乱顺序
        Collections.shuffle(zipfSequence);

        // 将序列分成20个包含100个元素的列表
        List<List<Integer>> listOfLists = randomSplitList(zipfSequence, numLists, elementsPerList);

        // 打印结果
        return listOfLists;
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
