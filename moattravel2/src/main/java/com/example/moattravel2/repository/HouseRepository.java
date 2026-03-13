package com.example.moattravel2.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.moattravel2.entity.House;

@Repository
public interface HouseRepository extends JpaRepository<House,Integer> {	
    
}
