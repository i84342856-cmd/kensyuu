package com.example.learning9_abstract;

public class Code38_dancer extends Code37_character {
	public void attack(Matango m) {
		System.out.println(this.name + "の攻撃");
		m.hp -= 3;
		
	}
}
