package com.example.learning4_method;

public class Code13_MethodReturnOverload {
	public static int add(int x, int y) {
		int ans = x + y;
		return ans;
		// return　後に書いてもエラーになる//
	}
	
	public static int add(int x, int y, int z) {
		return x + y + z;
	}
	
	public static void printArray(int[] array) {
		for(int element : array) {
			System.out.print(element);
		}
	}
	
	public static void main(String[] args) {
		int ans = add(50,80);
		System.out.println(ans);
		System.out.println(add(50,80,90));
		// メソッド名が同じでも仮引数がちがえばオーバーロードできる//
		int[] array = {2,3,5};
		printArray(array);
	}

}
