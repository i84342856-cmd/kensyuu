package com.example.learning8_extends;

public class Code36_poisonMatango extends Code35_matango{
	int attackCount = 5;
	
	public Code36_poisonMatango(char suffix) {
		super(suffix);
	}
	
	public void attack(Code32_hero h) {
		super.attack(h);
		if(attackCount > 0) {
			System.out.println("さらにどくの胞子をばらまいた");
			int attackPoint = h.hp / 5;
			h.hp -= attackPoint;
			System.out.println(attackPoint + "HP消費");
			this.attackCount --;
		}
	}
}
