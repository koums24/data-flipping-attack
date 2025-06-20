package com.cpa.models;

import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;
import com.cpa.objectives.Request;

import java.util.*;

import static com.cpa.objectives.MetricsUtil.calRequestRatios;

public class DDCPADefense {
    //return blacklist on this server
    public static void DDCPAdefenseModel(List<List<Integer>> blackListonServer, List<EdgeUser> users, int numContent, List<Integer> allRequests,  int serverId) {

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
}