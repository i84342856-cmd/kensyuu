package com.example.learning10_polymorphism;

public class Code50_main {
	public static void main(String[] args) {
		
		Code48_character c = new Code49_wizard();
		
		Code49_wizard w = (Code49_wizard)c;
		
		Code51_matango m = new Code51_matango();
		w.name = "港";
		w.attack(m);
		w.fireball(m);
		
		Code48_character [] h = new Code48_character[5];
		h[0] = new Code49_wizard();
		h[1] = new Code49_wizard();
		h[2] = new Code49_wizard();
		h[3] = new Code49_wizard();
		h[4] = new Code49_wizard();
		
		for(Code48_character ch : h) {
			ch.hp += 50;
		}
		
	}

}
