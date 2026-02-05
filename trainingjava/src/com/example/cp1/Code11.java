package com.example.cp1;

public class Code11 {
	public static void main(String[] args) {
	hello();
	methodA();
	System.out.println("メソッドを呼び出した");
	}
	
	public static void hello() {
		System.out.println("こんにちは");
	}
	
	public static void methodA() {
		System.out.println("methodA");
		methodB();
	}
	
	public static void methodB() {
		System.out.println("methodB");
	}
}

