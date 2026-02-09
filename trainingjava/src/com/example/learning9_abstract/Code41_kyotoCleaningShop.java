package com.example.learning9_abstract;

public class Code41_kyotoCleaningShop implements Code40_cleanServide {
	String ownerName;
	String address;
	String phone;
	
	public Shirt washShirt(Shirt s) {
		return s;
	}

	public Towl washTowl(Towl t) {
		return t;
	}
	
	public Coat washCoat(Coat c) {
		return c;
	}
}
