package com.example.learning5_modularity;

public class Code17_CalcApp {
	public static void main(String[] args) {
		int a = 10; int b = 20;
		// 別のクラスからメソッドを呼び出す //
		System.out.println(Code16_CalcLogic.tasu(a,b) + "と" + Code16_CalcLogic.hiku(a,b));
	}

}
