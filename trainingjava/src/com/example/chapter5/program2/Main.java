package com.example.chapter5.program2;

public class Main {
	public static void main(String[] args) {
		
		// 会社を生成
		BT moat = new BT();
		
		// 最初に人事を生成
		moat.MakeEmployee("ジンタ","人事", null);
		moat.employees.get(0).showInfo();
		
		// 「人事」クラスとして扱えるようにキャスト
		HumanResource minato = (HumanResource)moat.employees.get(0);
		
		// 人事が面接を実施、エンジニアを採用
		minato.interview(moat,"エンジ","エンジニア", "java", "採用");
		moat.employees.get(1).showInfo();
		
		// 「エンジニア」クラスとして扱えるようにキャスト、開発を実施
		Engineer enzi = (Engineer)moat.employees.get(1);
		enzi.develop();
		
		// 人事が面接を実施、営業を採用
		minato.interview(moat,"セル","営業", null, "採用");
		moat.employees.get(2).showInfo();
		
		// 「営業」クラスとして扱えるようにキャスト、週報、面談、会議を実施
		Sales eig = (Sales)moat.employees.get(2);
		eig.replyWeeklyReport();
		eig.setMeeting(enzi);
		eig.meeting();
		
		// BTクラスの全従業員情報を開示
		moat.showEmployees();
	}
}
