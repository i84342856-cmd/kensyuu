package com.example.cp1;

public class Code8 {
	public static void main(String[] args) {
		for(int i = 0; i < 10; i++) {
			System.out.println(i);
		}
		for(int l = 1; l < 10; l++) {
			for(int j = 1; j < 10; j++) {
				System.out.print(l * j);
				System.out.print(" ");
			}
			System.out.println(" ");
		}
		
		/* 3-1
		int weight == 60;
		(age1 + age2)*2 > 60;
		age % 2 == 1;
		name.equals('港');
		*/	
		
		//3-3//
		int isHungry = 1;
		String food = "適切な食べもの";
		if(isHungry == 0) {
			System.out.println("おなかが一杯");
		} else {
			System.out.println("はらぺこです");
			System.out.println(food + "いただきます");
		}
		
		//3-5//
		System.out.println("メニュー　1:検索 2：登録 3：削除 4：変更");
		int selected = new java.util.Scanner(System.in).nextInt();
		switch(selected) {
		case 1:
			System.out.println("検索します");
			break;
		case 2:
			System.out.println("登録します");
			break;
		case 3:
			System.out.println("削除します");
			break;
		case 4:
			System.out.println("変更します");
			break;
		}
		
		// 3-6 //
		int ans = new java.util.Random().nextInt(10);
		for(int i = 0; i<5;++i) {
			System.out.println("数字を入力してください");
			int num = new java.util.Scanner(System.in).nextInt();
			if(ans == num) {
				System.out.println("あたり");
			} else {
				System.out.println("ちがいます");
			}
		}
		
	}
}
