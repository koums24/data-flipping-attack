package com.cpa.tool;
import java.util.Random;

public class ZipfDistribution {
    private int size;
    private double skew;
    private double[] probabilities;

    public ZipfDistribution(int size, double skew) {
        this.size = size;
        this.skew = skew;
        this.probabilities = new double[size];
        calculateProbabilities();
    }

    private void calculateProbabilities() {
        double sum = 0.0;
        for (int i = 1; i <= size; i++) {
            sum += 1.0 / Math.pow(i, skew);
        }
        for (int i = 0; i < size; i++) {
            probabilities[i] = (1.0 / Math.pow(i + 1, skew)) / sum;
        }
    }

    public int sample() {
        double randomValue = new Random().nextDouble();
        double cumulativeProbability = 0.0;
        for (int i = 0; i < size; i++) {
            cumulativeProbability += probabilities[i];
            if (randomValue <= cumulativeProbability) {
                return i;
            }
        }
        return size;
    }
}