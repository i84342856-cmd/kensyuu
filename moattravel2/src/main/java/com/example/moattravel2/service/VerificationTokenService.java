package com.example.moattravel2.service;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.example.moattravel2.entity.User;
import com.example.moattravel2.entity.VerificationToken;
import com.example.moattravel2.repository.VerificationTokenRepository;

@Service
public class VerificationTokenService {
    private final VerificationTokenRepository verificationTokenRepository;
    
    public VerificationTokenService(VerificationTokenRepository verificationTokenRepository) {
    	this.verificationTokenRepository = verificationTokenRepository;
    }
    
    @Transactional
    public void create(User user ,String token) {
    	VerificationToken verificationToken = new VerificationToken();
    	verificationToken.setUser(user);
    	verificationToken.setToken(token);
    	
    	verificationTokenRepository.save(verificationToken);
    }
    
    public VerificationToken getVerificationToken(String token) {
    	return verificationTokenRepository.findByToken(token);
    }
}
