package com.example.learning7_variableclass;

public class Code31_thief {
	String name;
	int hp;
	int mp;
	
	public Code31_thief(String name, int hp, int mp) {
		this.name = name;
		this.hp = hp;
		this.mp = mp;
	}
	
	public Code31_thief(String name, int hp) {
		this(name, hp ,5);
	}
	
	public Code31_thief(String name) {
		this(name,40);
	}
}


