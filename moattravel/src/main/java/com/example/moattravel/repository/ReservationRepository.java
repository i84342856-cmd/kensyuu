package com.example.moattravel.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel.entity.Reservation;
import com.example.moattravel.entity.User;

/**
 * 予約情報のデータベース操作を担当するリポジトリ
 */
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    
	/**
     * 特定のユーザーの予約履歴を、登録日時の新しい順にページネーション形式で取得します
     */
    Page<Reservation> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
}
