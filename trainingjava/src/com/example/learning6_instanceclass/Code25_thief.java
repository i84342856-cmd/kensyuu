package com.example.learning6_instanceclass;

public class Code25_thief {
	String name;
	int hp;
	int mp;

	// コンストラクタ //
	public Code25_thief(String name, int hp, int mp) {
		this.name = name;
		this.hp = hp;
		this.mp = mp;
	}
	
	// コンストラクタ //
	public Code25_thief(String name, int hp) {
		this(name,hp,5);
	}
	
	// コンストラクタ //
	public Code25_thief(String name) {
		this(name,40);
	}
	
}
