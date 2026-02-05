package com.example.learning1_basics;

public class Code5_StandardInput {
	public static void main(String[] args) {
		String age = "31";
		int n = Integer.parseInt(age);
		System.out.println("あなたは" + n + "になりますよね");
		int r = new java.util.Random().nextInt(90);
		System.out.println("あなたは" + r + "になりますよね");
		
		// キーボード入力して回答するメソッド //
		System.out.println("あなたの名前");
		String name = new java.util.Scanner(System.in).nextLine();
		System.out.println("あなたの年齢");
		int ag = new java.util.Scanner(System.in).nextInt();
		System.out.println("ようこそ" + ag + name);
		
		//優先順位を上げさせる//
		int x = 5;
		int y = 10;
		System.out.println("あなたの年齢" + (x + y));
	}
}
