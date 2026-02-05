package com.example.cp1;

public class Code10 {
	public static void main(String[] args) {
		int[][] scores = {{20,30,50},{80,90,100}};
		System.out.println(scores.length);
		System.out.println(scores[1].length);
		
		
		int[] points = new int[4];
		double[] weights = new double[5];
		boolean[] answers = new boolean[3];
		String[] names = new String[3];
		
		int[] moneyList = {121902,8302,300};
		for(int i = 0 ; i< moneyList.length;i++) {
			System.out.println(moneyList[i]);
		}
		
		int[] numbers = {3,5,9};
		System.out.println("数字を入力");
		int input = new java.util.Scanner(System.in).nextInt();
		for(int i = 0; i<numbers.length;i++) {
			if(numbers[i] == input) {
				System.out.println("あたり");
			}
		}
		
		for(int n : numbers) {
			if(n == input) {
				System.out.println("あたり");
			}
		}
		
		}
	}

