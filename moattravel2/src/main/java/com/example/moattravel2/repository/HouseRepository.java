package com.example.moattravel2.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.moattravel2.entity.House;

@Repository
public interface HouseRepository extends JpaRepository<House,Integer> {	
    public Page<House> findByNameLike(String keyword,Pageable pageable);
}
