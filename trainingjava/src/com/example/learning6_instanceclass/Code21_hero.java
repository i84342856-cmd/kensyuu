package com.example.learning6_instanceclass;

public class Code21_hero {
	String name;
	int hp;
	int level = 10; // 固定にするときは final 先頭につける //
	
	//　コンストラクタ
	public Code21_hero() {
		this.hp = 100; // hpフィールドを100　初期化
	}
	public void sleep() {
		this.hp = 100;
		System.out.println(this.name + "は、回復した");
	}
	
	public void sit(int sec) {
		this.hp += sec;
		System.out.println(this.name + "は、" + sec + "秒座った");
		System.out.println("HP " + sec + "ポイント回復した");
	}
	
	public void slip() {
		this.hp -= 5;
		System.out.println(this.name + "は、転んだ");
		System.out.println("5のダメージ");
	}
	
	public void run() {
		System.out.println(this.name + " にげた");
		System.out.println("HP " + this.hp);
	}
}
