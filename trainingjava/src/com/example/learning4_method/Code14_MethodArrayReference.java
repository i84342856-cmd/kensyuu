package com.example.learning4_method;

public class Code14_MethodArrayReference {
	public static void incArray(int[] array) {
		for(int i = 0; i< array.length;i++) {
			array[i]++;
		}
	}
	
	public static int[] makeArray(int size) {
		int[] newArray = new int[size];
		for(int i = 0 ; i < newArray.length; i++) {
			newArray[i] = i;
		}
		return newArray;
	}
	
	
	
	// 呼び出し元の配列のアドレスが呼び出し先の引数にコピーされる //
	public static void main(String[] args) {
		int[] array = {2,3,4};
		incArray(array);
		for(int i : array) {
			System.out.println(i);
		}
		
		int[] array2 = makeArray(3);
		for(int i : array2 ) {
			System.out.println(i);
		}
		
	}
	
}
