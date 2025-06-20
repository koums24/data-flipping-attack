package com.cpa.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LaplaceNoiseDefense {

    // 生成拉普拉斯噪声
    public static double generateLaplaceNoise(double scale, int mean) {
        Random random = new Random();
        double uniform = random.nextDouble() - 0.5; // 生成一个在 [-0.5, 0.5] 之间的随机数
        return mean - scale * Math.signum(uniform) * Math.log(1 - 2 * Math.abs(uniform));
    }

    // 对请求序列添加拉普拉斯噪声，确保结果在指定范围内
//    public static List<Integer> addLaplaceNoiseToRequests(List<Integer> requests, double epsilon, int dataNumber) {
//        List<Integer> noisyRequests = new ArrayList<>();
////        epsilon = 8;
//        double scale = 1.0 / epsilon; // 拉普拉斯噪声的尺度参数，epsilon越小噪声越大
//
//        for (int request : requests) {
//            double noise = generateLaplaceNoise(scale); // 生成拉普拉斯噪声
//            int noisyRequest = (int) Math.round(request + noise); // 原始请求加噪声并取整
//
//            // 确保加噪声后的结果在范围 (0, dataNumber) 之间
//            noisyRequest = Math.max(1, Math.min(noisyRequest, dataNumber - 1));
//
//            noisyRequests.add(noisyRequest);
//        }
//
//        return noisyRequests;
//    }

    //同上 直接更新requestlist
//    public static List<Integer> addLaplaceNoiseToRequests(List<Integer> requests, double epsilon, int dataNumber) {
////        epsilon = 0.05;
//            double scale = 1.0 / epsilon; // 拉普拉斯噪声的尺度参数，epsilon越小噪声越大
//
//        for (int i = 0; i < requests.size(); i++) {
//            double noise = generateLaplaceNoise(scale); // 生成拉普拉斯噪声
//            int noisyRequest = (int) Math.round(requests.get(i) + noise); // 原始请求加噪声并取整
//
//            // 确保加噪声后的结果在范围 (0, dataNumber) 之间
//            noisyRequest = Math.max(1, Math.min(noisyRequest, dataNumber - 1));
//
//            // 更新原列表中的请求
//            requests.set(i, noisyRequest);
//        }
//        return requests;
//    }
    public static List<Integer> addLaplaceNoiseToRequests(List<Integer> requests, double epsilon, int dataNumber) {
        int mean = 0; // 设置平均值为 0，以便允许负噪声
        double scale = 1.0 / epsilon; // 拉普拉斯噪声的尺度
        double maxNoise = dataNumber * 0.8; // 最大噪声阈值（20% 的 dataNumber）
        Random random = new Random();

        for (int i = 0; i < requests.size(); i++) {
            // 生成拉普拉斯噪声
            double noise = generateLaplaceNoise(scale, mean);

            // 限制噪声在范围 [-maxNoise, +maxNoise]
            noise = Math.max(-maxNoise, Math.min(noise, maxNoise));

            // 应用噪声并取整
            int noisyRequest = (int) Math.round(requests.get(i) + noise);

            // 确保加噪声后的请求在范围 (1, dataNumber) 内
            noisyRequest = Math.max(1, Math.min(noisyRequest, dataNumber - 1));

            // 更新请求列表
            requests.set(i, noisyRequest);
        }
        return requests;
    }

    public static List<Integer> addGaussianNoiseToRequests(List<Integer> requests, double mean, double stdDev, int dataNumber) {
        Random random = new Random();

        for (int i = 0; i < requests.size(); i++) {
            // Generate Gaussian noise
            double noise = random.nextGaussian() * stdDev + mean;

            // Apply noise and round
            int noisyRequest = (int) Math.round(requests.get(i) + noise);

            // Ensure noisy request is within range (1, dataNumber)
            noisyRequest = Math.max(0, Math.min(noisyRequest, dataNumber - 1));

            // Update request list
            requests.set(i, noisyRequest);
        }
        return requests;
    }

    // 添加均匀分布噪声的主方法
    public static List<Integer> addUniformNoise(List<Integer> requests, double epsilon) {
        // 计算 logits 的 softmax 逆操作
        double[] softmaxOutputs = toSoftmax(requests);

        // 使得分布均匀
        for (int i = 0; i < softmaxOutputs.length; i++) {
            // 将每个 softmax 输出值转回 logits（假设为 log(softmax)）
            softmaxOutputs[i] = Math.log(softmaxOutputs[i]);
        }

        // 将 logits 加入偏置以使其均匀分布
        double mean = 0; // 均匀分布的均值
        double variance = 1; // 设置方差为1以增加分布的均匀性

        for (int i = 0; i < softmaxOutputs.length; i++) {
            softmaxOutputs[i] += (mean + variance * new Random().nextGaussian());
        }

        // 确保最终请求在范围内
        for (int i = 0; i < softmaxOutputs.length; i++) {
            softmaxOutputs[i] = Math.max(1, Math.min(Math.round(Math.exp(softmaxOutputs[i])), 30));
            requests.set(i, (int) softmaxOutputs[i]);
        }

        return requests;
    }

    // 计算 softmax 输出
    public static double[] toSoftmax(List<Integer> requests) {
        double[] expValues = new double[requests.size()];
        double sumExp = 0;

        // 计算每个请求的指数值
        for (int i = 0; i < requests.size(); i++) {
            expValues[i] = Math.exp(requests.get(i));
            sumExp += expValues[i];
        }

        // 计算 softmax 值
        double[] softmaxValues = new double[requests.size()];
        for (int i = 0; i < requests.size(); i++) {
            softmaxValues[i] = expValues[i] / sumExp;
        }

        return softmaxValues;
    }

    public static List<Integer> addBiasedNoiseToRequests(List<Integer> requests, double biasProbability, int dataNumber) {
        Random random = new Random();

        for (int i = 0; i < requests.size(); i++) {
            int request = requests.get(i);

            // 根据概率决定是否添加偏置
            if (random.nextDouble() < biasProbability) {
                int biasedNoise = 10 + random.nextInt(10); // 生成15到30之间的随机数
                request = biasedNoise;
            }

            // 确保请求值在范围 (0, dataNumber) 之间
            request = Math.max(1, Math.min(request, dataNumber - 1));

            requests.set(i, request); // 更新请求序列
        }
        return requests;
    }

    public static List<Integer> addUniformNoise(List<Integer> requests) {
        double min = 0; // 最小值
        double max = 29; // 最大值
        double range = max - min; // 范围
        Random random = new Random();

        // 对原始请求进行标准化
        List<Double> normalizedRequests = new ArrayList<>();
        for (int request : requests) {
            // 将每个请求标准化到 [0, 1]
            double normalizedValue = (double) (request - min) / range;
            normalizedRequests.add(normalizedValue);
        }

        // 为每个标准化值添加均匀噪声并重新缩放到 [0, 30]
        List<Integer> finalResults = new ArrayList<>();
        for (double normalizedValue : normalizedRequests) {
            // 添加均匀噪声，范围在 [-0.1, 0.1] 之间
            double noise = (random.nextDouble() * 0.8) - 0.6;
            double noisyValue = normalizedValue + noise;

            // 确保 noisyValue 在 [0, 1] 之间
            noisyValue = Math.max(0, Math.min(noisyValue, 1));

            // 重新缩放到 [0, 30]
            double finalValue = noisyValue * range + min;
            finalResults.add((int) finalValue);
        }

        return finalResults;
    }

    public static List<Integer> addSoftmaxNoise(List<Integer> requests, int dataNumber) {
        // Counter 计算元素的频次

        int size = requests.size();
        List<Integer> distribution = counter(requests, dataNumber);

// Calculate the standard softmax, with temperature T
        List<Double> smoothedDistributions = softmaxWithTemperature(distribution, 8);

// Modify the distribution by ensuring the sum stays equal to 'size'
        int totalElements = 0;
        for (int i = 0; i < smoothedDistributions.size(); i++) {
            int adjustedValue = (int) Math.round(smoothedDistributions.get(i) * size);
            distribution.set(i, adjustedValue);
            totalElements += adjustedValue;
        }

// Adjust the distribution so the total matches the original 'size'
        int diff = size - totalElements;
        if (diff > 0) {
            // Add the missing elements by incrementing some entries
            for (int i = 0; i < diff; i++) {
                distribution.set(i % distribution.size(), distribution.get(i % distribution.size()) + 1);
            }
        } else if (diff < 0) {
            // Remove extra elements by decrementing some entries
            for (int i = 0; i < -diff; i++) {
                distribution.set(i % distribution.size(), distribution.get(i % distribution.size()) - 1);
            }
        }

// Clear and reconstruct the 'requests' list based on the adjusted distribution
        requests.clear();
        for (int i = 0; i < distribution.size(); i++) {
            for (int j = 0; j < distribution.get(i); j++) {
                requests.add(i); // Rebuild the requests list
            }
        }

// Shuffle the reconstructed list to randomize the order
        Collections.shuffle(requests);

        return requests;

    }

    // Function to compute softmax with temperature

    public static List<Integer> counter(List<Integer> array, int size) {
        List<Integer> counter = new ArrayList<>(Collections.nCopies(size, 0));
        for (int element : array) {
            counter.set(element, counter.get(element) + 1);
        }
        return counter;
    }

    // 计算 softmax 并添加温度参数 T
    public static List<Double> softmaxWithTemperature(List<Integer> logits, double T) {
        int n = logits.size();
        List<Double> expLogits = new ArrayList<>(n);
        double maxLogit = Collections.max(logits);  // 找到最大值避免溢出

        // 计算带有温度参数的 softmax
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double expValue = Math.exp((logits.get(i) / T) - (maxLogit / T));
            expLogits.add(expValue);
            sum += expValue;
        }

        // 归一化
        for (int i = 0; i < n; i++) {
            expLogits.set(i, expLogits.get(i) / sum);
        }
        return expLogits;
    }

    public static void main(String[] args) {
        // 示例列表（包含重复元素的列表）
        List<Integer> request = new ArrayList<>();
        Collections.addAll(request, 22, 29, 23, 25, 24, 28, 27);

        List<Integer> list = addSoftmaxNoise(request, 30);
        // 将分布打印出来
        System.out.println("Softmax Smoothed Distribution:");
        for (double value : list) {
            System.out.println(value);
        }
    }
}
