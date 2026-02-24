package com.example.chapter5.program2;
import java.util.ArrayList;
import java.util.List;

public class Main {
	public static void main(String[] args) {
		List<Figure> figures = new ArrayList<>();
		figures.add(new Line(7,7,10,10));
		figures.add(new Circle(7,7,10));
		figures.add(new Triangle(7,7,10,10,13,13));
		figures.add(new Rectangle(7,7,10,10));
		figures.add(new Square(7,7,10));
		
		
		
		for(Figure f : figures) {
			f.draw();
			double p = f.getPerimeter();
			System.out.printf("周囲の長さ: %.2f\n", p);
	
				if(f instanceof Polygon) {
					Polygon poly = (Polygon)f;
					int a = poly.getInternalAngle();
					System.out.println(f.getClass().getSimpleName() + "の内角の和: " + a + "度");
			}
			System.out.println(" ");
		}
		
	}

}
