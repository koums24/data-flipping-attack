package com.cpa.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenerateNoise {

    public static List<Integer> addSoftmaxNoise(List<Integer> requests, int dataNumber) {
        // Counter to count the frequency of each data
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

    public static List<Double> softmaxWithTemperature(List<Integer> logits, double T) {
        int n = logits.size();
        List<Double> expLogits = new ArrayList<>(n);
        double maxLogit = Collections.max(logits);  // 找到最大值避免溢出

        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double expValue = Math.exp((logits.get(i) / T) - (maxLogit / T));
            expLogits.add(expValue);
            sum += expValue;
        }
        //normalize
        for (int i = 0; i < n; i++) {
            expLogits.set(i, expLogits.get(i) / sum);
        }
        return expLogits;
    }

}
