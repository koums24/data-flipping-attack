package com.cpa.objectives;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetricsUtil {
    public static double[] calRequestRatios(int numContent, List<Integer> requests) {

        double[] requestRatios = new double[numContent];
        Map<Integer, Long> contentCountMap = requests.stream()
                .collect(Collectors.groupingBy(request -> request, Collectors.counting()));

        int totalRequests = requests.size();
        Map<Integer, Double> contentRatioMap = new HashMap<>();

        for (Map.Entry<Integer, Long> entry : contentCountMap.entrySet()) {
            int content = entry.getKey();
            long count = entry.getValue();
            double ratio = (double) count / totalRequests;
            contentRatioMap.put(content, ratio);
        }

//        System.out.println("Content -> Request Ratio:");
        for (Map.Entry<Integer, Double> entry : contentRatioMap.entrySet()) {
            requestRatios[entry.getKey()] += entry.getValue();
//            System.out.println("Content: " + entry.getKey() + " -> Ratio: " + entry.getValue());
        }
        return requestRatios;
    }
}
