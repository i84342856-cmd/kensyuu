package com.example.learning5_modularity;
/* パッケージをインポートしておけば呼び出すときに省略できる
 * import com.example.learning5_modularity2.Code16_CalcLogic2;
 * import com.example.learning5_modularity2.*; 全クラスをインポートできる
*/
public class Code17_CalcApp {
	public static void main(String[] args) {
		int a = 10; int b = 20;
		// 別のクラスからメソッドを呼び出す //
		System.out.println(Code16_CalcLogic.tasu(a,b) + "と" + Code16_CalcLogic.hiku(a,b));
		
		// 別のパッケージのクラスからメソッドを呼び出す //
		System.out.println(com.example.learning5_modularity2.Code16_CalcLogic2.tasu(a,b) + "と" + com.example.learning5_modularity2.Code16_CalcLogic2.hiku(a,b));
	}
}