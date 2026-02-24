package com.example.chapter5.program2;

import com.example.chapter5.program2.BT;
import com.example.chapter5.program2.Employee;

public class HumanResource  extends Employee{
	
	public HumanResource(String name, String dep) {
		super(name,dep);
	}
	
	/* 
	 * 社員面接メソッド
	 *  引数：
	 * 1. 従業員作成メソッドを実行するためBTクラス
	 * 2. そのメソッドの引数として名前、所属、言語（エンジニアではない場合は、null）
	 * 3. 面接の結果として「採用」か、それ以外の文字列
	 */
	public void interview(BT moat,String name,String dep, String lan, String result) {
		System.out.println(getName() + "は" + name + "という応募者に面接を行い、結果は" + result + "だった");
		if(result.equals("採用")) {
				moat.MakeEmployee(name,dep,lan);
		    }
		}
	
	public void showInfo() {
		System.out.println("名前：" + super.getName() + "  所属：" + super.getDepartment());
	}
}
