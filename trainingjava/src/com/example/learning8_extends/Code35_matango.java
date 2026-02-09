package com.example.learning8_extends;

public class Code35_matango {
	int hp =50;
	char suffix;
	
	public Code35_matango(char suffix) {
		this.suffix = suffix;
	}
	
	public void attack(Code32_hero h) {
		System.out.println("きのこ" + this.suffix + "アタック");
		h.hp -= 10;
	}

}
