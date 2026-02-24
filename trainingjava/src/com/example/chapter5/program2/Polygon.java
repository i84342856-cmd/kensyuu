package com.example.chapter5.program2;

public abstract class Polygon extends Shape{
	protected int angle;  
	
	public abstract void draw() ;
	public abstract double getPerimeter() ;
	// draw() と getPerimeter() は自動的に abstract 扱い
	
	public int getInternalAngle() {
		return (this.angle -2)*180;
	}
}
