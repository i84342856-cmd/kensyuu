package com.example.learning9_abstract;

public class Code46_book extends Code45_tangibleAsset {
	String isbn;
	
	public Code46_book(String name, int price, String color, String isbn) {
		super(name,price,color);
		this.isbn = isbn;
	}
	
	public String getIsbn() {
		return this.isbn;
	}
}
