package com.example.learning9_abstract;

public abstract class Code43_asset {
	String name;
	int price;
	
	public Code43_asset(String name, int price) {
		this.name = name;
		this.price = price;
	}
	
	public String getName() {
		return this.name;
	}
	
	public int getPrice() {
		return this.price;
	}

}
