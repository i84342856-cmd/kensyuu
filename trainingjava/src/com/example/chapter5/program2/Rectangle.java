package com.example.chapter5.program2;

public class Rectangle extends Polygon{
	protected Point p;
	protected int width;
	protected int height;
	
	public Rectangle(int x, int y, int width, int height) {
		this.p = new Point();
		this.p.setX(x);
		this.p.setY(y);
		this.width = width;
		this.height = height;
		super.angle = 4;
	}
	
	public void draw() {
		System.out.println("[長方形(矩形)を描画] 点(" + p.getX() + "," + p.getY() + ")を基準として幅" + 
	                       this.width + "、高さ" + this.height + "の長方形");
	}
	
	public double getPerimeter() {
		return ( width + height ) * 2;
	}

}
