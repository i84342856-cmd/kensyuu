package com.example.chapter5.program2;

public class Circle extends Shape{
	Point center; 
	int radius;
	
	public Circle() {
		this.center = new Point();
		
		this.center.setX(0);
		this.center.setY(0);
		this.radius = 0;
	}
	
	public Circle(int x, int y ,int r) {
		this.center = new Point();
		this.center.setX(x);
		this.center.setY(y);
		this.radius = r;
	}
	
	public void draw() {
		System.out.println("[円を描画] 中心点(" + center.getX() + "," + center.getY() + ")から半径" + this.radius);
	}
	
	public double getPerimeter() {
		return 2 * radius * Math.PI;
	}
	
}
