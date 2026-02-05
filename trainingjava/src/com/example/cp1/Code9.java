package com.example.cp1;

public class Code9 {
	public static void main(String[] args) {
		int[] scores = new int[5];
		int num = scores.length;
		System.out.println("要素の数は" + num);
		
		scores[1] = 30;
		System.out.println(scores[1]);
		
		/* 配列に代入
		int[] scores = new int[] {20,30,40,50,60,70}
		int[] scores = {20,30,40,50,60,70}
		*/
		
		//合計や平均//
		int scores2[] = {20,30,40,50,60};
		int sum = 0;
		for(int i =0 ; i< scores2.length ; i++) {
			System.out.println(scores2[i]);
			sum += scores2[i];
		}
		int avg = sum / scores2.length;
		System.out.println(sum);
		System.out.println(avg);
		
		// カウントする//
		int[] scores3 = {20,30,50,70,80};
		int count = 0;
		for(int i =0; i<scores3.length; i++) {
			if(scores3[i] >= 50) {
				count++;
			}
		}
		System.out.println(count);
		
		//記号をランダムに表示する//
		int[] seq = new int[10];
		for (int i =0 ; i<seq.length ; i++) {
			seq[i] = new java.util.Random().nextInt(4);
		}
		for(int i =0 ; i< seq.length; i++) {
			char[] base = {'A','T','G','C'};
			System.out.print(base[seq[i]]);
		}
		
		// 拡張for //
		for(int value : scores) {
			System.out.println(value);
		}
	} 
}
