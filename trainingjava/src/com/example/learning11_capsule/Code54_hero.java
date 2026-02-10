package com.example.learning11_capsule;

public class Code54_hero {
	private int hp;
	private String name;
	
	public String getName() {
		return this.name;
	}
	
	public void setName(String name) {
		
		if(name == null) {
			throw new IllegalArgumentException("名前がnullなので中断");
		}
		
		if(name.length() <= 1) {
			throw new IllegalArgumentException("名前が短すぎるので中断");
		}
		
		if(name.length() >= 8) {
			throw new IllegalArgumentException("名前が長すぎるので中断");
		}
		
		this.name = name;
	}
	
	public int getHp() {
		return this.hp;
	}
	
	public void setHp(int hp) {
		if(hp < 0) {
		this.hp = 0;
	} else {
		this.hp = hp;
	}
	}
	
	
	public void sleep() {
		this.hp = 100;
		System.out.println(this.name + "は寝て" + this.hp + "回復した" );
	}
	
	private void die() {
		System.out.println(this.name + "die");
	}
	

}
