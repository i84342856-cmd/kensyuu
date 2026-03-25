package com.example.moattravel3.entity;

import java.sql.Timestamp;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "reservations")
@Data
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	// ======== 外部キーによるリレーションシップ ========

	@ManyToOne
	@JoinColumn(name = "house_id")
	private House house;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	// ======== 予約詳細情報 ========

	@Column(name = "checkin_date")
	private LocalDate checkinDate;

	@Column(name = "checkout_date")
	private LocalDate checkoutDate;

	@Column(name = "number_of_people")
	private Integer numberOfPeople;

	@Column(name = "amount")
	private Integer amount;

	// ======== 監査用（自動更新） ========

	@Column(name = "created_at", insertable = false, updatable = false)
	private Timestamp createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Timestamp updatedAt;

	// 返金処理のためのID
	@Column(name = "stripe_payment_intent_id")
	private String stripePaymentIntentId;
}