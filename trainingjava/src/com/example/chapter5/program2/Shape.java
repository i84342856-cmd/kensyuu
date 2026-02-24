package com.example.chapter5.program2;

public abstract class Shape implements Figure {
	public abstract void draw() ;
	public abstract double getPerimeter() ;
	// draw() と getPerimeter() は自動的に abstract 扱い 
}
