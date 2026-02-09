package com.example.learning8_extends;

public class Code32_hero {
	String name = "港";
	int hp = 100;
	
	public void attack(Code35_matango m) {
		System.out.println(this.name + "の攻撃");
		m.hp -= 5;
		System.out.println("5p damage");
	}
	
	public void run() {
		System.out.println(this.name + "はにげだした");
	}
	
	public final void slip() {
		this.hp -= 5;
	}

}
