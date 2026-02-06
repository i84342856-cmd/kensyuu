package com.example.learning6_instanceclass;

public class Code22_main {
	public static void main(String[] args) {
		Code21_hero h = new Code21_hero();
		h.name = "minato";
		System.out.println("コンストラクタの設定により、初期HP" + h.hp);
		
		Code23_matango m1 = new Code23_matango();
		m1.hp = 50;
		m1.suffix = 'A';
		
		Code23_matango m2 = new Code23_matango();
		m2.hp = 40;
		m2.suffix = 'B';
		
		System.out.println(h.name + "を生み出しました");
		
		h.sit(5);
		m1.run();
		m2.run();
		h.slip();
		h.sit(25);
		h.run();
	}
}
