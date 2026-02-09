package com.example.learning7_variableclass;

public class Code26_main {
	public static void main(String[] args) {
		Code27_sword s = new Code27_sword();
		s.name = "剣";
		s.damage = 10;
		
		Code28_hero h = new Code28_hero();
		h.name = "港";
		h.hp = 100;
		h.sword = s;
		
		Code29_wizard w = new Code29_wizard();
		w.name = "ウィザー";
		w.hp = 100;
		
		Code31_thief t = new Code31_thief("シェフ");
		System.out.println(t.name + ":" + t.hp + "：" + t.mp);
				
		System.out.println("所有しているものは" + s.name);
		
		w.heal(h);
		System.out.println(h.name + "のhpを回復させた");
	}
	
}
