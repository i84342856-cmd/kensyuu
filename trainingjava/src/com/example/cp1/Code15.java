package com.example.cp1;

public class Code15 {
	public static void email(String title, String address, String text) {
		System.out.println(address + "にメールを送信しました");
		System.out.println("件名： " + title);
		System.out.println("本文： " + text);
	}
	
	// メソッドをオーバーロード //
	public static void email(String address, String text) {
		System.out.println(address + "にメールを送信しました");
		System.out.println("本文： " + text);
	}
	
	public static double calcTriangeArea(double bottom, double height) {
		return bottom * height / 2;
	}
	
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
		
		// 三角形と円の面積 //
		System.out.println(calcTriangeArea(3,5));
		System.out.println(calcCircleArea(70));
	}
}
