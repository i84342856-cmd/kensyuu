package com.example.learning12_ApiUse;

public class Code58_Account {
	String accountNumber;
	int balance;
	
	public String toString() {
		return "\\" + this.balance + "(口座番号" + this.accountNumber + "）";
	}
	
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		if(o instanceof Code58_Account) {
			Code58_Account a = (Code58_Account)o;
			String a1 = a.accountNumber.trim();
			String a2 = this.accountNumber.trim();
			if(a1.equals(a2)) {
				return true;
			}
		}
		 return false;
	}
	
	public static void main(String[] args) {
		Code58_Account a = new Code58_Account();
		
		a.accountNumber = "4649";
		a.balance = 1592;
		
		System.out.println(a);
	}

}
