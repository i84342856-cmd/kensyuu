package com.example.moattravel.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA（データベースとのやり取りを簡単にするフレームワーク）によって提供されるインターフェース
import com.example.moattravel.entity.House;

public interface HouseRepository  extends JpaRepository<House, Integer> {
	public Page<House> findByNameLike(String keyword, Pageable pageable);
	}


