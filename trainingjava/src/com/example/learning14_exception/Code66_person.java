package com.example.learning14_exception;

public class Code66_person {
	int age;
	public void setAge(int age) {
		if(age < 15) {
			throw new IllegalArgumentException("年齢は15以上で設定すべきです");
		}
		this.age = age;
	}
}
