package com.example.learning12_ApiUse;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Code61_StringAndLegacyDate {
	public static void main(String[]args) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0;i < 100; i++) {
			sb.append(i+1).append(",");
		}
		String s = sb.toString();
		System.out.println(s);
		
		String[] a = s.split(",");
		
		for(String aa : a) {
			System.out.print(aa);
		}
		
		String folder = "37";
		String file = "38";
		
		System.out.println(" ");
		System.out.println(concatPath(folder,file));
		
		Date now = new Date();
		Calendar c = Calendar.getInstance(); 
		c.setTime(now);
		int day = c.get(Calendar.DAY_OF_MONTH);
		day += 100;
		c.set(Calendar.DAY_OF_MONTH, day);
		Date future = c.getTime();
		SimpleDateFormat f = new SimpleDateFormat("西暦yyyy年mm月dd日");
		System.out.println(f.format(future));
	}
	

			
	public static String concatPath(String folder, String file) {
		if(!folder.endsWith("\\")) {
			folder += "\\";
		}
		return folder + file;
	}
}
