package com.example.learning11_capsule;

public class Code56_wand {
	private String name;
	private double power;

public String getName() {
	return this.name;
}

public double getPower() {
	return this.power;
}

public void setName(String name) {
	if(name.length() < 3 || name == null) {
		throw new IllegalArgumentException("名前が短すぎるので中断");
	}
	this.name = name;
}

public void setPower(double power) {
	if(power < 0.5 || power > 100) {
		throw new IllegalArgumentException("増幅率の設定値が異常なので中断");
	}
	this.power = power;
}


}
