package com.example.moattravel.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
	
	/**
     * メールアドレスをキーにしてユーザーを検索します
     * @param email ログイン時に入力されたメールアドレス
     * @return 見つかったUserエンティティ（存在しない場合はnull）
     */
    User findByEmail(String email);
}
