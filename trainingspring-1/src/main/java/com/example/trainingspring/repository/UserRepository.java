package com.example.trainingspring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.trainingspring.entity.User;

/**
 * ユーザー情報 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	// 削除日時が null のユーザーだけを取得するメソッドを定義
    List<User> findAllByDeleteDateIsNull();
}
