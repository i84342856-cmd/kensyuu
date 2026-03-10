package com.example.moattravel.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.moattravel.entity.House;
import com.example.moattravel.entity.Reservation;
import com.example.moattravel.entity.User;
import com.example.moattravel.form.ReservationRegisterForm;
import com.example.moattravel.repository.HouseRepository;
import com.example.moattravel.repository.ReservationRepository;
import com.example.moattravel.repository.UserRepository;

@Service
public class ReservationService {
	
	private final ReservationRepository reservationRepository;
    private final HouseRepository houseRepository;
    private final UserRepository userRepository;
    
 // 資料にないのでAI作成、リポジトリを注入するためのコンストラクタ
    public ReservationService(ReservationRepository reservationRepository, HouseRepository houseRepository, UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
    }
    
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
    
    /**
     * 資料にないのでAI作成、予約データをデータベースに登録する
     */
    @Transactional
    public void create(ReservationRegisterForm reservationRegisterForm) {
        Reservation reservation = new Reservation();
        House house = houseRepository.getReferenceById(reservationRegisterForm.getHouseId());
        User user = userRepository.getReferenceById(reservationRegisterForm.getUserId());
        LocalDate checkinDate = LocalDate.parse(reservationRegisterForm.getCheckinDate());
        LocalDate checkoutDate = LocalDate.parse(reservationRegisterForm.getCheckoutDate());

        reservation.setHouse(house);
        reservation.setUser(user);
        reservation.setCheckinDate(checkinDate);
        reservation.setCheckoutDate(checkoutDate);
        reservation.setNumberOfPeople(reservationRegisterForm.getNumberOfPeople());
        reservation.setAmount(reservationRegisterForm.getAmount());

        reservationRepository.save(reservation);
    }
}