package com.example.EleventhAssignment;

public abstract class Shape implements Figure {
	public abstract void draw() ;
	public abstract double getPerimeter() ;
	// draw() と getPerimeter() は自動的に abstract 扱い
}
