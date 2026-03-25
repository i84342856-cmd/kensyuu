package com.example.moattravel3.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel3.entity.Reservation;
import com.example.moattravel3.entity.User;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

	Page<Reservation> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

}
