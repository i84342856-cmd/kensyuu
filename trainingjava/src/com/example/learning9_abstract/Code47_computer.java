package com.example.learning9_abstract;

public class Code47_computer extends Code45_tangibleAsset {
	String makerName;
	
	public Code47_computer(String name,int price, String color,String makerName) {
		super(name,price,color);
		this.makerName = makerName;
	}
	
	public String getMakerName() {
		return this.makerName;
	}
}
