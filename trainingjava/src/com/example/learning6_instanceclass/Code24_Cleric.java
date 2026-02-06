package com.example.learning6_instanceclass;

public class Code24_Cleric {
	String name;
	int hp = 50;
	final int MAX_HP = 50;
	int mp = 10;
	final int MAX_MP = 10;
	
	public void selfAid() {
		System.out.println(this.name + "セルフエイドを唱えた");
		this.mp -= 5;
		this.hp = this.MAX_HP;
		System.out.println("HPが最大まで回復した");
	}
	
	public int pray(int sec) {
		System.out.println(this.name + sec + "秒間祈った");
		int recover = new java.util.Random().nextInt(3) + sec;
		int actualRecover = Math.min(recover,this.MAX_MP - this.mp);
		this.mp += actualRecover;
		System.out.println("MPが" + actualRecover + "回復した");
		return actualRecover;
	}
}
