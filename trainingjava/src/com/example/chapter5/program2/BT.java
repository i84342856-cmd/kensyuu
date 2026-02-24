package com.example.chapter5.program2;

import java.util.ArrayList;
import java.util.List;

public class BT {
	final String name = "BT";
	List<Employee> employees = new ArrayList<>();
	List<String> departments = new ArrayList<>(List.of("人事", "営業", "エンジニア"));
	
// 従業員作成メソッド（引数で名前、所属、言語（エンジニアではない場合は、null））
public void MakeEmployee(String name,String dep, String lan) {
	Employee emp = null;
	
	if(dep.equals(departments.get(0))) {
		emp = new HumanResource(name,dep);
	}else if(dep.equals(departments.get(1))){
		emp = new Sales(name,dep);
	}else if(dep.equals(departments.get(2))){
		emp = new Engineer(name,dep,lan);
	}
	if(emp != null) {
		employees.add(emp);
	}
}

public void showEmployees() {
	for(Employee emp :employees) {
		emp.showInfo();
	}
}
}


