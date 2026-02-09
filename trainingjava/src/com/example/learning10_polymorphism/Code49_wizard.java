package com.example.learning10_polymorphism;

public class Code49_wizard extends Code48_character{
	int mp;
	
	public void attack(Code51_matango m) {
		System.out.println(this.name + "の攻撃");
		m.hp -= 3;
	}
	
	public void fireball(Code51_matango m) {
		System.out.println(this.name + "の攻撃fireball");
		m.hp -= 20;
		this.mp -= 5;
	}
}
