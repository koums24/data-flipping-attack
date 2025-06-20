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

        Collections.shuffle(requests);

        return requests;
    }

    public static List<Request> generateNormalRequest(String user, int size, double mean, double stdDev, int lowerBound, int upperBound) {
        Random random = new Random();
        List<Request> requests = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        List<Integer> contentList = IntStream.range(0, size)
                .mapToObj(i -> {

                    double value = mean + stdDev * random.nextGaussian();

                    int intValue = (int) Math.round(value);
                    intValue = Math.max(lowerBound, Math.min(intValue, upperBound - 1));
                    return intValue;
                })
                .collect(Collectors.toList());
        for (int i = 0; i < contentList.size(); i++) {
            long interval = random.nextInt(1000) + 100;
            currentTime += interval;
            int content = contentList.get(i);
            requests.add(new Request(user, content, currentTime));
        }
        return requests;
    }


    public static List<Integer> generateZipfSequence(int size, int min, int max) {
        Random random = new Random();
        List<Integer> sequence = new ArrayList<>();
        double exponent = 1.1;

        for (int i = 1; i <= size; i++) {
            double zipfValue = (1.0 / Math.pow(i, exponent));
            int value = (int) Math.round(min + (zipfValue * (max - min)));
            sequence.add(value);
        }


        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
            int mappedValue = (int) (value * (max - min) / sequence.get(0));
            mappedValue = Math.min(max, Math.max(min, mappedValue));
            mappedSequence.add(mappedValue);
        }

        Collections.shuffle(mappedSequence, random);

        return mappedSequence;
    }


    public static List<Integer> generateZipfListForUser(int userId, int size, int min, int max) {
        List<Integer> sequence = new ArrayList<>();
        Random baseRandom = new Random(userId);
        Random userRandom = new Random(userId + baseRandom.nextInt()); //
        double exponent = 1 + baseRandom.nextDouble();


        double sum = 0;
        for (int i = 1; i <= size; i++) {
            sum += (1.0 / Math.pow(i, exponent));
        }

        for (int i = 1; i <= size; i++) {
            double zipfValue = (1.0 / Math.pow(i, exponent)) / sum;
            int value = (int) Math.round(min + (zipfValue * (max - min)));
            sequence.add(value);
        }


        List<Integer> mappedSequence = new ArrayList<>();
        for (int value : sequence) {
//            int mappedValue = value + userRandom.nextInt(max - min + 1);
            int mappedValue = value + userRandom.nextInt((max - min + 1) / 2);
            mappedValue = Math.min(max, Math.max(min, mappedValue));
            mappedSequence.add(mappedValue);
        }


        Collections.shuffle(mappedSequence, userRandom);

        return mappedSequence;
    }


    public static List<List<Integer>> splitZipfDistribution( int size , int numLists, int elementsPerList, int min, int max) {

        List<Integer> zipfSequence = generateZipfSequence(size, min, max);

        Collections.shuffle(zipfSequence);

        List<List<Integer>> listOfLists = randomSplitList(zipfSequence, numLists, elementsPerList);

        return listOfLists;
    }

    public static List<List<Integer>> randomSplitList(List<Integer> list, int numLists, int elementsPerList) {
        List<List<Integer>> listOfLists = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < numLists; i++) {
            List<Integer> sublist = new ArrayList<>();
            for (int j = 0; j < elementsPerList; j++) {

                int randomIndex = random.nextInt(list.size());
                sublist.add(list.remove(randomIndex));
            }
            listOfLists.add(sublist);
        }

        return listOfLists;
    }

}
