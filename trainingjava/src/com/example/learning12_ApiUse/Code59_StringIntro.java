package com.example.learning12_ApiUse;

public class Code59_StringIntro {
	public static void main(String[] args) {
		String s1 = "JAVA";
		String s2 = "java";
		
		if(s1.equals(s2)) {
			System.out.println("s2とs3は等しい");
		}
		
		if(s1.equalsIgnoreCase(s2)) {
			System.out.println("s2とs3はケースを区別しなければ等しい");
		}
		
		System.out.println("s1 " + s1.length() + "文字");
		
		if(! s1.isEmpty()) {
			System.out.println("s1 中に文字があります");
		}
	}

}
