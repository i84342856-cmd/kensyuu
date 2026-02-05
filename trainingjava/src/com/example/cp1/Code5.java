package com.example.cp1;

public class Code5 {
	public static void main(String[] args) {
		String age = "31";
		int n = Integer.parseInt(age);
		System.out.println("あなたは" + n + "になりますよね");
		int r = new java.util.Random().nextInt(90);
		System.out.println("あなたは" + r + "になりますよね");
		
		System.out.println("あなたの名前");
		String name = new java.util.Scanner(System.in).nextLine();
		System.out.println("あなたの年齢");
		int ag = new java.util.Scanner(System.in).nextInt();
		System.out.println("ようこそ" + ag + name);
	}
}
