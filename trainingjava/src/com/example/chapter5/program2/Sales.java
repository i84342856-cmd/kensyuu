package com.example.chapter5.program2;

public class Sales  extends Employee{
	public Sales(String name, String dep) {
		super(name,dep);
	}
	
	
	// 週報を返す
    public void replyWeeklyReport() {
        System.out.println(this.getName() + "は週報の返信をしました。");
    }
    
   // 面談を組む
    public void setMeeting(Engineer e) {
        System.out.println(this.getName()+ "は" + e.getName() +  "との面談を組んだ");
    }

    // 会議をする
    public void meeting() {
        System.out.println(this.getName() + "は新規の打ち合わせをしました。");
    }
    
	public void showInfo() {
		System.out.println("名前：" + super.getName() + "  所属：" + super.getDepartment());
	}
}
