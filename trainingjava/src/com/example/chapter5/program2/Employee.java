package com.example.chapter5.program2;

public abstract class Employee {
	private String name;
	private String department;
	
	public Employee(String name, String department) {
		this.name = name;
		this.department = department;
	}
	
	public abstract void showInfo();
	
	public String getName() {
		return name;
	}
	
	public String getDepartment() {
		return department;
	}
}

