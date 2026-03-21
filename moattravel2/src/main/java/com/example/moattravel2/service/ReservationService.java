package com.example.moattravel2.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.example.moattravel2.entity.House;
import com.example.moattravel2.entity.Reservation;
import com.example.moattravel2.entity.User;
import com.example.moattravel2.form.ReservationRegisterForm;
import com.example.moattravel2.repository.HouseRepository;
import com.example.moattravel2.repository.ReservationRepository;
import com.example.moattravel2.repository.UserRepository;

@Service
public class ReservationService {
    public final ReservationRepository reservationRepository;
    public final UserRepository userRepository;
    public final HouseRepository houseRepository;
    public ReservationService(ReservationRepository reservationRepository,UserRepository userRepository,HouseRepository houseRepository) {
    	this.reservationRepository = reservationRepository;
    	this.userRepository = userRepository;
    	this.houseRepository = houseRepository;
    }
    
    public boolean isWithinCapacity(Integer capasity, Integer numberOfPeople) {
    	return capasity >= numberOfPeople;
    }
    
    public Integer calculateAmount(Integer price,LocalDate checkinDate,LocalDate checkoutDate) {
    	long numberOfNights = ChronoUnit.DAYS.between(checkinDate, checkoutDate);
    	int amount = price * (int)numberOfNights;
    	return amount;
    }
    
    // 決済処理を経由せずに予約テーブルに追加する場合
    @Transactional
    public void create(ReservationRegisterForm reservationRegisterForm) {
    	Reservation reservation = new Reservation();
    	User user = userRepository.getReferenceById(reservationRegisterForm.getUserId());
    	House house = houseRepository.getReferenceById(reservationRegisterForm.getHouseId());
    	LocalDate checkinDate = LocalDate.parse(reservationRegisterForm.getCheckinDate());
    	LocalDate checkoutDate = LocalDate.parse(reservationRegisterForm.getCheckoutDate());
    	
    	reservation.setNumberOfPeople(reservationRegisterForm.getNumberOfPeople());
    	reservation.setAmount(reservationRegisterForm.getAmount());
    	reservation.setUser(user);
    	reservation.setHouse(house);
    	reservation.setCheckinDate(checkinDate);
    	reservation.setCheckoutDate(checkoutDate);
    	
    	reservationRepository.save(reservation);
    }
    
    
    @Transactional
    public void create(Map<String, String> paymentIntentObject) {
        Reservation reservation = new Reservation();

        // Mapから値を取り出して型変換を行う
        Integer houseId = Integer.valueOf(paymentIntentObject.get("houseId"));
        Integer userId = Integer.valueOf(paymentIntentObject.get("userId"));
        Integer numberOfPeople = Integer.valueOf(paymentIntentObject.get("numberOfPeople"));
        Integer amount = Integer.valueOf(paymentIntentObject.get("amount"));
        LocalDate checkinDate = LocalDate.parse(paymentIntentObject.get("checkinDate"));
        LocalDate checkoutDate = LocalDate.parse(paymentIntentObject.get("checkoutDate"));

        // IDをもとにエンティティを取得
        House house = houseRepository.getReferenceById(houseId);
        User user = userRepository.getReferenceById(userId);

        // 予約エンティティにセット
        reservation.setHouse(house);
        reservation.setUser(user);
        reservation.setCheckinDate(checkinDate);
        reservation.setCheckoutDate(checkoutDate);
        reservation.setNumberOfPeople(numberOfPeople);
        reservation.setAmount(amount);

        // 保存
        reservationRepository.save(reservation);
    }
    
}
