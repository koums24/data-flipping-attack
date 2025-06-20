package com.cpa.objectives;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetricsUtil {
    public static double[] calRequestRatios(int numContent, List<Integer> requests) {

        double[] requestRatios = new double[numContent];
        // 统计每个content的数量
        Map<Integer, Long> contentCountMap = requests.stream()
                .collect(Collectors.groupingBy(request -> request, Collectors.counting()));

        // 计算每个content的request ratio
        int totalRequests = requests.size();
        Map<Integer, Double> contentRatioMap = new HashMap<>();

        for (Map.Entry<Integer, Long> entry : contentCountMap.entrySet()) {
            int content = entry.getKey();
            long count = entry.getValue();
            double ratio = (double) count / totalRequests;
            contentRatioMap.put(content, ratio);
        }

        // 输出结果
//        System.out.println("Content -> Request Ratio:");
        for (Map.Entry<Integer, Double> entry : contentRatioMap.entrySet()) {
            requestRatios[entry.getKey()] += entry.getValue();
//            System.out.println("Content: " + entry.getKey() + " -> Ratio: " + entry.getValue());
        }
        return requestRatios;
    }
}
