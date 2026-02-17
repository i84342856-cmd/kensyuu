package com.example.learning13_colection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Code63_main {
	public static void main(String[] args) {
		ArrayList<Integer> points = new ArrayList<Integer>();
		points.add(100);
		points.add(150);

		for(int point : points) {
			System.out.println(point);
		}


		// イテレーターを使用して要素を削除する
		Iterator<Integer> it = points.iterator();
		while(it.hasNext()) {
			Integer e = it.next();
			if(e.equals(150)) {
				it.remove();
			}
		}
		System.out.println(points);
		
		// 重複がないように　HashSet
		Set<String> colors = new HashSet<String>();
		colors.add("赤");
		colors.add("青");
		colors.add("青");
		for(String s : colors) {
			System.out.println(s);
		}
		
		// 辞書順　TreeSet
		Set<String> words = new TreeSet<String>();
		words.add("dog");
		words.add("panda");
		words.add("cat");
		
		for(String w : words) {
			System.out.println(w);
		}
		
		Map<String, Integer> prefs = new HashMap<String, Integer>();
		prefs.put("京都",255);
		prefs.put("東京",2500);
		
		Integer tokyo = prefs.get("東京");
		System.out.println(tokyo);
		
		prefs.remove("京都");
		
		for(int i : prefs.values()) {
			System.out.println(i);
		}	
		
		// セット
		for (Map.Entry<String, Integer> entry : prefs.entrySet()) {
		    String key = entry.getKey();     // キー（"東京"など）を取得
		    Integer value = entry.getValue(); // 値（2500など）を取得
		    System.out.println(key + value);
		}
		
		// セット 2
		for (String key : prefs.keySet()) {
		   int value = prefs.get(key);
		    System.out.println(key + value);
		}
		
		// 練習
		List<Hero> heros = new ArrayList<Hero>();
		Hero h1 = new Hero("港");
		Hero h2 = new Hero("湾");
		
		/*
		heros.add(h1);
		heros.add(h2);
		
		for(Hero i : heros) {
			System.out.println(i.getName());
		}
		*/
	
		
		Map<Hero,Integer> hes = new HashMap<Hero, Integer>();
		hes.put(h1,3);
		hes.put(h2,7);
		for(String key : hes.keySet()) {
			int value = hes.get(key);
			 System.out.println(key + value);
		}
		
		Map<String, Integer> prefs = new HashMap<String, Integer>();
		prefs.put("京都",255);
		prefs.put("東京",2500);
		
	}
}
