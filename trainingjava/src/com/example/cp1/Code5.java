package com.example.cp1;

public class Code5 {
	public static void main(String[] args) {
		String age = "31";
		int n = Integer.parseInt(age);
		System.out.println("あなたは" + n + "になりますよね");
		int r = new java.util.Random().nextInt(90);
		System.out.println("あなたは" + r + "になりますよね");
	}
}
