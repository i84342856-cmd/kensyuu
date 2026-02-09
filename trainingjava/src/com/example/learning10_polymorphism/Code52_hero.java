package com.example.learning10_polymorphism;

public class Code52_hero {
	public void attack(monster m) {
		System.out.println(this.name + "の攻撃");
		m.jp -= 10;
	}

}
