package com.example.moattravel.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel.entity.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Integer> {
    
    /**
     * トークン文字列をキーにして認証トークン情報を検索する
     * @param token 検索対象のトークン
     * @return 見つかった場合はVerificationToken、見つからない場合はnull
     */
    public VerificationToken findByToken(String token);

}