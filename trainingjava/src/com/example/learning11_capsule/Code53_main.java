package com.example.learning11_capsule;

public class Code53_main {
	public static void main(String[] args) {
		
		Code54_hero h = new Code54_hero();
		h.setName("minato");
		h.sleep();
		Code56_wand wa = new Code56_wand ();
		wa.setPower(8.7);
		
		Code57_wizard wi = new Code57_wizard();
		wi.setWand(wa);
		wi.heal(h);
	}

}
