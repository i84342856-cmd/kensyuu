package com.example.learning13_colection;
import java.io.IOException;

public class Code65_Exception {
	public static void main(String[] args) throws IOException {
		System.out.println("起動");
		
		
		String s = null;
		
		try{
			System.out.println(s.length());
		} catch(NullPointerException e){
			System.out.println("NullPointerException");
			e.printStackTrace();
		}
		
		try {
			int i = Integer.parseInt("七");
		} catch(NumberFormatException e) {
			e.printStackTrace();
		}
		
		throw new IOException();
	}
}
