package com.cpa.tool;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomNodeGenerator {
	
	public int getRandomNode(int nodesNumber) {
		return ThreadLocalRandom.current().nextInt(0, nodesNumber);
	}
	
	public int getRandomNodeFromList(List<Integer> list, int nodesNumber, List<Integer> exceptList) {
		int number = ThreadLocalRandom.current().nextInt(0, nodesNumber);
		while (!list.contains(number) || exceptList.contains(number)) {
			number = ThreadLocalRandom.current().nextInt(0, nodesNumber);
		}
		return number;
	}
	
	public int getRandomNodeExceptList(List<Integer> list, int nodesNumber) {
		int number =  list.get(0);
		while (list.contains(number)) {
			// nextInt is normally exclusive of the top value,
			// so use mNodesNumber directly, and the upper limit is mNodesNumber - 1
			number = ThreadLocalRandom.current().nextInt(0, nodesNumber);
			for (int i : list) System.out.print(i + " ");
			System.out.println();
			System.out.println(number);
		}
		return number;
	}
}
