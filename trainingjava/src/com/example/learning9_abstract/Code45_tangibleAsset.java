package com.example.learning9_abstract;

public abstract class Code45_tangibleAsset extends Code43_asset implements Code44_thing{

	String color;
	double weight;
	
	public Code45_tangibleAsset(String name, int price,String color) {
		super(name,price);
		this.color = color;
	}
	
	
	public String getColor() {
		return this.color;
	}
	
	public double getWeigth() {
		return this.weight;
	}
	
	public void setWeight(double weight) {
		this.weight = weight;
	}
}
