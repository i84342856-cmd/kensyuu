package com.example.EleventhAssignment;

public class Triangle extends Polygon {
	Point p1;
	Point p2;
	Point p3;
	int angle = 3;
	public Triangle(int x1, int y1, int x2, int y2, int x3, int y3) {
		this.p1 = new Point();
		this.p2 = new Point();
		this.p3 = new Point();
		
		this.p1.setX(x1);
		this.p2.setX(x2);
		this.p3.setX(x3);
		this.p1.setY(y1);
		this.p2.setY(y2);
		this.p3.setY(y3);	
	}
	
	public void draw() {
		System.out.println("\"[三角形を描画] 点1(" + p1.getX() + "," + p1.getY() + ")から点2(" + p2.getX() + "," + p2.getY() + ")、点3(" + + p3.getX() + "," + p3.getY()+ "の三角形" );
	}
	
	public double getPerimeter() {
		return Math.sqrt(Math.pow(p2.getX() - p1.getX(),2) + Math.pow (p2.getY() - p1.getY(),2)) + 
			   Math.sqrt(Math.pow(p3.getX() - p2.getX(),2) + Math.pow (p3.getY() - p2.getY(),2)) +
			   Math.sqrt(Math.pow(p1.getX() - p3.getX(),2) + Math.pow (p1.getY() - p3.getY(),2));
	}
}
