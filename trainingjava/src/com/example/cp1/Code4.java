package com.example.cp1;

public class Code4 {
	public static void main(String[] args) {
		int a = 5;
		int b = 20;
		b = a + b;
		System.out.println(a);
		System.out.println(b);
		System.out.println("私の好きな記号は二重引用符（\")です");
		int c = 100;
		c++;
		System.out.println(c);
		int age = (int)3.2;
		System.out.println(age);
		/* キャスト演算子 エラーになるところを強制的に大きい型に変換する*/
		double d = 8.5 / 2;
		long l = 5 + 2L;
		System.out.println(d);
		System.out.println(l);
		String msg = "私の年齢は" + 23;
		System.out.println(msg);
		int m = Math.max(a,b);
		int e =5;
		int f = 25;
		System.out.println(e +"と"+ f +"とで大きいのは" + m);
	}

}
