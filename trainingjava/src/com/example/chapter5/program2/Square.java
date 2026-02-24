package com.example.chapter5.program2;

public class Square extends Rectangle {
	
	public Square(int x, int y, int width) {
        // Rectangleのコンストラクタを呼び出す（幅と高さに同じwidthを渡す）
        super(x, y, width, width); 
    }
	
	public void draw() {
		System.out.println("[正方形を描画] 点((" + p.getX() + "," + p.getY() + ")を基準として幅・高さ" + 
	                       this.width + "の正方形");
	}
}
