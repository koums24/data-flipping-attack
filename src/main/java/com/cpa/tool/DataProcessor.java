package com.cpa.tool;

import java.io.*;
import java.util.*;

public class DataProcessor {

    public static void main(String[] args) {
        String inputFile = "AttackRatio Results ZIpf-diff.txt";
        String outputFile = "Set_1 AttackRatio Zipf-diff.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            String line;
            Map<String, Double[]> safeData = new HashMap<>();
            Map<String, Double[]> attackData = new HashMap<>();
            String currentGroup = null;
            String currentType = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("---- Average results")) {
                    continue;
                }

                if (line.startsWith("------ ")) {
                    currentGroup = line.split(" ")[1].replace("Safe", "").replace("Attack", "").trim();
                    currentType = line.contains("Safe") ? "Safe" : "Attack";
                } else if (line.startsWith("TotalLatency")) {
                    String[] parts = line.split(":");
                    double totalLatency = Double.parseDouble(parts[1].trim());
                    String metricType = currentType.equals("Safe") ? "Safe" : "Attack";

                    if (currentType.equals("Safe")) {
                        safeData.put(currentGroup, new Double[]{totalLatency, 0.0, 0.0, 0.0});
                    } else {
                        attackData.put(currentGroup, new Double[]{totalLatency, 0.0, 0.0, 0.0});
                    }
                } else if (line.startsWith("HitRatio")) {
                    String[] parts = line.split(":");
                    double hitRatio = Double.parseDouble(parts[1].trim());

                    if (currentType.equals("Safe")) {
                        safeData.get(currentGroup)[1] = hitRatio;
                    } else {
                        attackData.get(currentGroup)[1] = hitRatio;
                    }
                } else if (line.startsWith("Attacked HitRatio")) {
                    String[] parts = line.split(":");
                    double attackedHitRatio = Double.parseDouble(parts[1].trim());

                    if (currentType.equals("Attack")) {
                        attackData.get(currentGroup)[2] = attackedHitRatio;
                    }
                }
            }

            for (String group : safeData.keySet()) {
                if (attackData.containsKey(group)) {
                    Double[] safeMetrics = safeData.get(group);
                    Double[] attackMetrics = attackData.get(group);

                    double latencyIncrease = attackMetrics[0] - safeMetrics[0];
                    double latencyIncreaseRatio = latencyIncrease / safeMetrics[0];
                    double hitRatioLoss = safeMetrics[1] - attackMetrics[1];

                    writer.write("Group: " + group + "\n");
                    writer.write("Latency Increase: " + latencyIncrease + "\n");
                    writer.write("Latency Increase Ratio: " + latencyIncreaseRatio + "\n");
                    writer.write("HitRatio Loss: " + hitRatioLoss + "\n");
                    writer.write("Safe Total Latency: " + safeMetrics[0] + "\n");
                    writer.write("Attack Total Latency: " + attackMetrics[0] + "\n");
                    writer.write("Safe Hit Ratio: " + safeMetrics[1] + "\n");
                    writer.write("Attack Hit Ratio: " + attackMetrics[1] + "\n");
                    writer.write("Attacked HitRatio: " + attackMetrics[2] + "\n");
                    writer.write("\n");
                }
            }

            System.out.println("data processed successful：" + outputFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}