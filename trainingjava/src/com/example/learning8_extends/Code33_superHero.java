package com.example.learning8_extends;

public class Code33_superHero extends Code32_hero{
	boolean flying;
	
	public void attack(Code35_matango m) {
		super.attack(m);
		if(this.flying) {
			super.attack(m);
		}
	}
	
	public void fly() {
	this.flying = true;
	System.out.println(this.name + "飛行した");
	}
	
	public void land() {
	this.flying = false;
	System.out.println(this.name + "着地した");
	}
	
	public void run() {
		System.out.println(this.name + "すぐにげだした");
	}

}
