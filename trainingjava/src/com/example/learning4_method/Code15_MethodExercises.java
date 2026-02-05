package com.example.learning4_method;

public class Code15_MethodExercises {
	
	// メール送信メソッド //
	public static void email(String title, String address, String text) {
		System.out.println(address + "にメールを送信しました");
		System.out.println("件名： " + title);
		System.out.println("本文： " + text);
	}
	
	// メール送信メソッドをオーバーロード //
	public static void email(String address, String text) {
		System.out.println(address + "にメールを送信しました");
		System.out.println("本文： " + text);
	}
	
	// 三角形の面積 //
	public static double calcTriangeArea(double bottom, double height) {
		return bottom * height / 2;
	}
	
	// 円の面積 //
	public static double calcCircleArea(double radius) {
		return radius * radius * 3.14;
	}
	
	public static void main(String[] args) {
		String title = "練習の件";
		String address =  "...com";
		String text = "練習の件で連絡します";
		email(title, address, text);
		
		// オーバーロードしたメソッドを使用 //
		email(address, text);
		
		System.out.println(calcTriangeArea(3,5));
		System.out.println(calcCircleArea(70));
	}
}
