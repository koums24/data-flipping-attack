package com.cpa.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LaplaceNoiseDefense {


    public static double generateLaplaceNoise(double scale, int mean) {
        Random random = new Random();
        double uniform = random.nextDouble() - 0.5;
        return mean - scale * Math.signum(uniform) * Math.log(1 - 2 * Math.abs(uniform));
    }

    public static List<Integer> addLaplaceNoiseToRequests(List<Integer> requests, double epsilon, int dataNumber) {
        int mean = 0; //
        double scale = 1.0 / epsilon; //
        double maxNoise = dataNumber * 0.8;
        Random random = new Random();

        for (int i = 0; i < requests.size(); i++) {

            double noise = generateLaplaceNoise(scale, mean);


            noise = Math.max(-maxNoise, Math.min(noise, maxNoise));


            int noisyRequest = (int) Math.round(requests.get(i) + noise);


            noisyRequest = Math.max(1, Math.min(noisyRequest, dataNumber - 1));


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


    public static List<Integer> addUniformNoise(List<Integer> requests, double epsilon) {

        double[] softmaxOutputs = toSoftmax(requests);


        for (int i = 0; i < softmaxOutputs.length; i++) {

            softmaxOutputs[i] = Math.log(softmaxOutputs[i]);
        }


        double mean = 0;
        double variance = 1;

        for (int i = 0; i < softmaxOutputs.length; i++) {
            softmaxOutputs[i] += (mean + variance * new Random().nextGaussian());
        }


        for (int i = 0; i < softmaxOutputs.length; i++) {
            softmaxOutputs[i] = Math.max(1, Math.min(Math.round(Math.exp(softmaxOutputs[i])), 30));
            requests.set(i, (int) softmaxOutputs[i]);
        }

        return requests;
    }


    public static double[] toSoftmax(List<Integer> requests) {
        double[] expValues = new double[requests.size()];
        double sumExp = 0;


        for (int i = 0; i < requests.size(); i++) {
            expValues[i] = Math.exp(requests.get(i));
            sumExp += expValues[i];
        }


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

            if (random.nextDouble() < biasProbability) {
                int biasedNoise = 10 + random.nextInt(10);
                request = biasedNoise;
            }

            request = Math.max(1, Math.min(request, dataNumber - 1));

            requests.set(i, request);
        }
        return requests;
    }

    public static List<Integer> addUniformNoise(List<Integer> requests) {
        double min = 0;
        double max = 29;
        double range = max - min;
        Random random = new Random();


        List<Double> normalizedRequests = new ArrayList<>();
        for (int request : requests) {

            double normalizedValue = (double) (request - min) / range;
            normalizedRequests.add(normalizedValue);
        }


        List<Integer> finalResults = new ArrayList<>();
        for (double normalizedValue : normalizedRequests) {

            double noise = (random.nextDouble() * 0.8) - 0.6;
            double noisyValue = normalizedValue + noise;


            noisyValue = Math.max(0, Math.min(noisyValue, 1));


            double finalValue = noisyValue * range + min;
            finalResults.add((int) finalValue);
        }

        return finalResults;
    }

    public static List<Integer> addSoftmaxNoise(List<Integer> requests, int dataNumber) {


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

y    public static List<Double> softmaxWithTemperature(List<Integer> logits, double T) {
        int n = logits.size();
        List<Double> expLogits = new ArrayList<>(n);
        double maxLogit = Collections.max(logits);

        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double expValue = Math.exp((logits.get(i) / T) - (maxLogit / T));
            expLogits.add(expValue);
            sum += expValue;
        }

        for (int i = 0; i < n; i++) {
            expLogits.set(i, expLogits.get(i) / sum);
        }
        return expLogits;
    }

    public static void main(String[] args) {
        List<Integer> request = new ArrayList<>();
        Collections.addAll(request, 22, 29, 23, 25, 24, 28, 27);
        List<Integer> list = addSoftmaxNoise(request, 30);
        System.out.println("Softmax Smoothed Distribution:");
        for (double value : list) {
            System.out.println(value);
        }
    }
}
