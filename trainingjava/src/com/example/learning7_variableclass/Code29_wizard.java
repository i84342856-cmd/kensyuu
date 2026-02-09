package com.example.learning7_variableclass;

public class Code29_wizard {
	String name;
	int hp;
	
	public void heal(Code28_hero h) {
		h.hp += 10;
		System.out.println(h.name + "HP回復した");
	}

}
