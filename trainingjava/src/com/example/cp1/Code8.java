package com.example.cp1;

public class Code8 {
	public static void main(String[] args) {
		for(int i = 0; i < 10; i++) {
			System.out.println(i);
		}
		for(int l = 1; l < 10; l++) {
			for(int j = 1; j < 10; j++) {
				System.out.print(l * j);
				System.out.print(" ");
			}
			System.out.println(" ");
		}
	}

}
