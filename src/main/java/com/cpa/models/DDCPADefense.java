package com.cpa.models;

import com.cpa.objectives.EdgeUser;

import java.util.*;
import java.util.stream.Collectors;

public class DDCPADefense {
    //return blacklist on this server
    public static void DDCPAdefenseModel(List<List<Integer>> blackListonServer, List<EdgeUser> users, int numContent, List<Integer> allRequests, int serverId) {
        //初始化popularity
        double[] contentPopularity = new double[numContent];
        // Initialize all elements to 0.5
        for (int i = 0; i < contentPopularity.length; i++) {
            contentPopularity[i] = 0.5;
        }

        double[] requestRatios = new double[numContent]; //total each content
//        double[] increaseConstant = new double[numContent];//increaseConstant
        double[] averageRequestIntensity = new double[numContent];
        //Request Ratios
        requestRatios = calRequestRatios(numContent, allRequests);

        //Average Request Intensity
        for (int i = 0; i < numContent; i++) {
            //calculate increaseConstant

            contentPopularity[i] = (double) contentPopularity[i] * (1 - Math.exp(-0.2)) + requestRatios[i];
            if (contentPopularity[i] == 0) {
                averageRequestIntensity[i] = 0;
            } else {
                averageRequestIntensity[i] = requestRatios[i] / contentPopularity[i];
            }
        }
        // Create an array of IndexValue pairs
        IndexValue[] indexedArray = new IndexValue[averageRequestIntensity.length];
        for (int i = 0; i < averageRequestIntensity.length; i++) {
            indexedArray[i] = new IndexValue(i, averageRequestIntensity[i]);
        }

        // Sort the array in descending order based on the value
        Arrays.sort(indexedArray, Comparator.comparingDouble(IndexValue::getValue).reversed());

        IndexValue maxARI = indexedArray[0];

        if (maxARI.getValue() > 0.7) {
            //record black list
            blackListonServer.get(serverId).add(maxARI.getOriginalIndex());
        }

    }

    private static class IndexValue {
        private final int originalIndex;
        private final double value;

        public IndexValue(int originalIndex, double value) {
            this.originalIndex = originalIndex;
            this.value = value;
        }


        public int getOriginalIndex() {
            return originalIndex;
        }

        public double getValue() {
            return value;
        }
    }

    public static double[] calRequestRatios(int numContent, List<Integer> requests) {

        double[] requestRatios = new double[numContent];
        // count
        Map<Integer, Long> contentCountMap = requests.stream()
                .collect(Collectors.groupingBy(request -> request, Collectors.counting()));

        // calculate request ratio
        int totalRequests = requests.size();
        Map<Integer, Double> contentRatioMap = new HashMap<>();

        for (Map.Entry<Integer, Long> entry : contentCountMap.entrySet()) {
            int content = entry.getKey();
            long count = entry.getValue();
            double ratio = (double) count / totalRequests;
            contentRatioMap.put(content, ratio);
        }

        // print
//        System.out.println("Content -> Request Ratio:");
        for (Map.Entry<Integer, Double> entry : contentRatioMap.entrySet()) {
            requestRatios[entry.getKey()] += entry.getValue();
//            System.out.println("Content: " + entry.getKey() + " -> Ratio: " + entry.getValue());
        }
        return requestRatios;
    }

}