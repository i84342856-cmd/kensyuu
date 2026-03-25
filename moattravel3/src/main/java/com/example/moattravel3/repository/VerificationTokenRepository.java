package com.example.moattravel3.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.moattravel3.entity.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Integer> {
    
    
    public VerificationToken findByToken(String token);

}