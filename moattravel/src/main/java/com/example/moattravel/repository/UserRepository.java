package com.example.moattravel.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
	
	/**
     * メールアドレスをキーにしてユーザーを検索します
     * @param email ログイン時に入力されたメールアドレス
     * @return 見つかったUserエンティティ（存在しない場合はnull）
     */
    User findByEmail(String email);
    
    // 氏名またはフリガナで曖昧検索を行い、ページネーションされた結果を返す
    public Page<User> findByNameLikeOrFuriganaLike(String nameKeyword, String furiganaKeyword, Pageable pageable);
    
}
