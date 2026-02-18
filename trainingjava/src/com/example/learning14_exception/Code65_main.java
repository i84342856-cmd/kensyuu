package com.example.learning14_exception;
import java.io.FileWriter;
import java.io.IOException;

public class Code65_main {
	public static void main(String[] args) throws IOException {
		System.out.println("起動");
		
		Code66_person person = new Code66_person();
		
		try {
			person.setAge(13);
		} catch(IllegalArgumentException e) {
			e.printStackTrace();
		}
		
		// try-with-resource 利用
		try(FileWriter fw = new FileWriter("data.txt")){
			fw.write("Hello");
			throw new IOException();
		} catch(IOException e) {
			System.out.println("例外が発生しました");
		}
		
		try {
		throw new Code67_UnsupportedMusicFileException("未対応です");
		} catch(Exception e){
			e.printStackTrace();
		}
		
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
