package com.example.learning11_capsule;

public class Code57_wizard {
	private int hp;
	private int mp;
	private String name;
	private Code56_wand wand;
	
	void heal(Code54_hero h) {
		int basepoint = 10;
		int recovpoint = (int)(basepoint * this.getWand().getPower());
		h.setHp(h.getHp() + recovpoint);
		System.out.println(h.getName() + "のHp" + recovpoint + "回復した");
	}
	
	public int getHp() {
		return this.hp;
	}
	
	public int getMp() {
		return this.mp;
	}
	
	
	public String getName() {
		return this.name;
	}
	
	public Code56_wand getWand() {
		return this.wand;
	}
	
	public void setHp(int hp) {
		if(hp < 0) {
			this.hp = 0;
		} else {
			this.hp = hp;
		}
	}
	
	public void setMp(int mp) {
		if(mp < 0) {
			throw new IllegalArgumentException("値が異常なので中断");
		}
		this.mp = mp;
	}
	
	public void setName(String name) {
		if(name == null || name.length() < 3) {
			throw new IllegalArgumentException("名前が短すぎるので中断");
		}
		this.name = name;
	}
	
	public void setWand(Code56_wand wand) {
		if(wand == null) {
			throw new IllegalArgumentException("設定されようとしている杖がnullなので中断");
		}
		this.wand = wand;
	}
}
