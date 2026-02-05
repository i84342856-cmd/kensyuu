package com.example.learning1_basics;

public class Code6_FortuneGame {
	public static void main(String[] args) {
		System.out.println("ようこそ占いの館");
		System.out.println("名前を入力");
		String name = new java.util.Scanner(System.in).nextLine();
		System.out.println("年齢を入力");
		String ageString = new java.util.Scanner(System.in).nextLine();
		int age = Integer.parseInt(ageString);
		int fortune = new java.util.Random().nextInt(4);
		++fortune;
		System.out.println("占いの結果");
		System.out.println(age + "歳" + name + "の運気番号は" + fortune);
		
	}
}
