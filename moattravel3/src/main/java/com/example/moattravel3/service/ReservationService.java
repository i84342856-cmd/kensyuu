package com.example.moattravel3.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.moattravel3.entity.House;
import com.example.moattravel3.entity.Reservation;
import com.example.moattravel3.entity.User;
import com.example.moattravel3.form.ReservationRegisterForm;
import com.example.moattravel3.repository.HouseRepository;
import com.example.moattravel3.repository.ReservationRepository;
import com.example.moattravel3.repository.UserRepository;

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
    
    /**
     * Stripeのセッション完了後に、Map形式のメタデータを受け取って予約を登録する
     */
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
        
     // ▼ここから追記：予約数を+1して更新する
        house.setReservationCount(house.getReservationCount() + 1);
        houseRepository.save(house);
    }
}