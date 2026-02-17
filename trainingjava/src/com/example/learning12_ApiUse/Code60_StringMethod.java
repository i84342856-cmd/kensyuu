package com.example.learning12_ApiUse;

import java.util.Date;

public class Code60_StringMethod {
	public static void main(String[] args) {
		String s1 = "java and script java";
		if(s1.contains("java")) {
			System.out.println("文字列はjavaを含んでいます");
		}
		
		if(s1.endsWith("java")){
			System.out.println("文字列はjavaが末尾にあります");
	}
		System.out.println("文字列でjavaが最初に登場する位置は" + s1.indexOf("java") );
		
		System.out.println("文字列の5文字目以降は " + s1.substring(5));
		
		System.out.println("文字列の5～9文字目以降は " + s1.substring(5,9));
		
		StringBuilder sb = new StringBuilder();
		for(int i = 0 ; i < 5 ; i++) {
			sb.append("java");
		}
		
		// これはエラー（StringBuilderはStringではないため）
		// String s = sb; 

		// こう書く必要
		String s = sb.toString();
		System.out.println(s);
		
		// System.out.println(sb); は、
		// 実行時に自動で System.out.println(sb.toString()); と同じ処理に変換されている
		
		System.out.println("java".matches("java"));
		
		System.out.println(isValidPlayerName("java"));
		
		System.out.println("jarefrefeva".matches("j*va"));
		System.out.println("jjva".matches("j*va"));
		System.out.println("jarefrefeva".matches("j.va"));
		System.out.println("jarefrefeva".matches("j.*va"));
		
		System.out.println("java".matches("j.va"));
		
		
		String sp = "abc,def:ghi";
		String[] words = sp.split("[,:]");
		for(String w : words) {
			System.out.print(w + "->");
		}
		
		String r = sp.replaceAll("[beh]","X");
		System.out.println(r);
		
		final String FORMAT = "%-9s %-13s 所持金%,6d";
		// String f = String.format(FORMAT,hero.getName,hero.getJob,hero.getGold());
		System.out.println(s);
		
		long start = System.currentTimeMillis();
		long end = System.currentTimeMillis();
		System.out.println((end - start) + "ミリ秒");
		
		Date now = new Date();
		System.out.println(now);
		System.out.println(now.getTime());
		Date past = new Date(1770882687332L);
		System.out.println(past);
		
		}
	
	
	// Javaでは、staticがついているメソッド（クラスそのものに属する）から、staticがついていないメソッド（インスタンス化しないと存在しない）を直接呼び出すことはできません。//
	public static boolean isValidPlayerName(String name) {
		return name.matches("[A-Z][A-Z0-9]{7}");
    }
}
