package com.example.cp1;

public class Code7 {
	public static void main(String[] args) {
		boolean tenki = true;
		if(tenki == true) {
			System.out.println("洗濯をします");
		} else {
			System.out.println("DVDを見ます");
		}
		
		System.out.println("あなたの運勢");
		int fortune = new java.util.Random().nextInt(4)+1;
		if (fortune == 1) {
			System.out.println(1);
		} else if(fortune == 2){
			System.out.println(2);
		} else {
			System.out.println(3);
		}
		
		// if-else構文をswitchに書き換える
		switch(fortune) {
		case 1:
			System.out.println(1);
			break;
		case 2:
			System.out.println(2);
			break;
		case 3:
		case 4:
			System.out.println("3～4");
		}
	}

}
