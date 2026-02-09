package com.example.learning7_variableclass;

public class Code28_hero {
	String name;
	int hp;
	Code27_sword sword;
	
	public Code28_hero(String name){
		this.hp = 100;
		this.name = name;
	}
	
	// 同一内の別のコンストラクタを呼び出すようにJVMに依頼 //
	public Code28_hero(){
		this("ダミー");
	}
	
	public void attack() {
		System.out.println(this.name + "攻撃");
	}
}
	