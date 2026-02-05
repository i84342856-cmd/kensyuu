package com.example.cp1;

public class Code12 {
	public static void main(String[] args) {
		System.out.println("メソッドを呼び出す");
		go("港");
		go("湾");
		go("湖");
		add(50,80);
		add(70,90);
		System.out.println("メソッドを呼び出した");
	}
	
	public static void go(String name) {
		System.out.println(name + "にいきました" );
	}

	public static void add(int x, int y) {
		int ans = x + y;
		System.out.println(ans);
	}
	
}
