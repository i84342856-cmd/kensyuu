package com.example.learning12_ApiUse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Code62_ModernTimeApi {
	public static void main(String[]args) {
		Instant i1 = Instant.now();
		System.out.println(i1);
		Instant i2 = Instant.ofEpochMilli(1600705425827L);
		System.out.println(i2);
		long milli = i2.toEpochMilli();
		System.out.println("数字に戻すと: " + milli);
		
		ZonedDateTime z1 = ZonedDateTime.now();
		System.out.println(z1);
		ZonedDateTime z2 = ZonedDateTime.of(2026,2,3,4,5,6,7, ZoneId.of("Asia/Tokyo"));
		System.out.println(z2);
		
		Instant i3 = z2.toInstant();
		ZonedDateTime z3 = i3.atZone(ZoneId.of("Europe/London"));
		System.out.println(i3);
		System.out.println(z3);
		
		System.out.println("東京：" + z2.getMonth() + z2.getDayOfMonth());
		
		LocalDateTime l1 = LocalDateTime.now();
		System.out.println(l1);
		LocalDateTime l2 = LocalDateTime.of(2026,2,3,4,5,6,7);
		System.out.println(l2);
		
		ZonedDateTime z5 = l2.atZone(ZoneId.of("Europe/London"));
		LocalDateTime l3 = z5.toLocalDateTime();
		System.out.println(z5);
		System.out.println(l3);
		
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/M/d");
		LocalDate ldate =  LocalDate.parse("2026/2/13", fmt); 
		System.out.println(ldate);
		
		LocalDate ldatep = ldate.plusDays(1000);
		String str = ldatep.format(fmt);
		System.out.println(str);
		
		LocalDate now = LocalDate.now();
		System.out.println(now);
		if(now.isBefore(ldatep)) {
			System.out.println("1000日後");
		}
		
		LocalDate d1 = LocalDate.of(2026, 2, 13);
		LocalDate d2 = LocalDate.of(2026, 2, 3);
		
		Period p1 = Period.ofDays(3);
		Period p2 = Period.between(d1,d2);
		
		LocalDate d3 = d2.plus(p2);
		System.out.println(p1);
		System.out.println(p2);
		System.out.println(d3);
	}

}
