package com.example.moattravel.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

@Service
public class ReservationService {

    /**
     * 宿泊人数が定員以下かどうかをチェックする
     */
    public boolean isWithinCapacity(Integer numberOfPeople, Integer capacity) {
        return numberOfPeople <= capacity;
    }

    /**
     * 宿泊料金を計算する
     */
    public Integer calculateAmount(LocalDate checkinDate, LocalDate checkoutDate, Integer price) {
        // チェックイン日とチェックアウト日の日数を計算
        long numberOfNights = ChronoUnit.DAYS.between(checkinDate, checkoutDate);
        // 1泊料金 × 宿泊数
        int amount = price * (int) numberOfNights;
        return amount;
    }
}