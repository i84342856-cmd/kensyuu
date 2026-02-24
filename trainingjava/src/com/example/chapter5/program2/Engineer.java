package com.example.chapter5.program2;

public class Engineer extends Employee {
	private String lan;
	
	public String getLan() {
		return this.lan;
	}
	
	public Engineer(String name, String dep,String lan) {
		super(name,dep);
		this.lan = lan;
	}
	
	// 開発する
    public void develop() {
        System.out.println(this.getName() + this.getLan() + "を使ってシステムを開発しました。");
    }
    
	public void showInfo() {
		System.out.println("名前：" + super.getName() + "  所属：" + super.getDepartment() + "  言語" + this.getLan());
	}
}
