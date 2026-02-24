package com.example.chapter5.program2;

public class Line implements Figure{
	Point p1;
	Point p2;
	
	public Line() {
		this.p1 = new Point();
		this.p2 = new Point();
		this.p1.setX(0);
		this.p1.setY(0);
		this.p2.setX(0);
		this.p2.setY(0);
	}
	
	public Line(int x1,int y1, int x2, int y2) {
		this.p1 = new Point();
		this.p2 = new Point();
		this.p1.setX(x1);
		this.p1.setY(y1);
		this.p2.setX(x2);
		this.p2.setY(y2);
	}
	
	public void draw() {
		System.out.println("[線を描画] 始点(" + p1.getX() + "," + p1.getY() + ")から終点(" + p2.getX() + "," + p2.getY() + ")まで)");
	}
	
	public double getPerimeter() {
		return Math.sqrt(Math.pow(p2.getX() - p1.getX(),2) + Math.pow (p2.getY() - p1.getY(),2)); // 三平方の定理（$a^2 + b^2 = c^2$）
	}

}
