package com.example.learning6_instanceclass;

public class Code22_main {
	public static void main(String[] args) {
		Code21_hero h1 = new Code21_hero();
		h1.name = "minato";
		System.out.println("コンストラクタの設定により、初期HP" + h1.hp);
		
		Code21_hero h2 = new Code21_hero("otan");
		System.out.println("コンストラクタの設定により、初期名" + h2.name);
		
		Code23_matango m1 = new Code23_matango();
		m1.hp = 50;
		m1.suffix = 'A';
		
		Code23_matango m2 = new Code23_matango();
		m2.hp = 40;
		m2.suffix = 'B';
		
		System.out.println(h1.name + "を生み出しました");
		
		h1.sit(5);
		m1.run();
		m2.run();
		h1.slip();
		h1.sit(25);
		h1.run();
		
		int baseHp = 25;
		Code25_thief t = new Code25_thief("ota",baseHp);
		System.out.println(baseHp + t.hp);
		heal(baseHp);
		heal(t);
		System.out.println(baseHp + t.hp);
	}
	
	public static void heal(int hp) {
		hp += 10;
	}
	
	public static void heal(Code25_thief thief) {
		thief.hp += 10;
	}
}
