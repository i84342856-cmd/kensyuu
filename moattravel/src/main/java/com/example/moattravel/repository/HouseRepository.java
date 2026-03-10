package com.example.moattravel.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA（データベースとのやり取りを簡単にするフレームワーク）によって提供されるインターフェース
import com.example.moattravel.entity.House;

public interface HouseRepository  extends JpaRepository<House, Integer> {
	public Page<House> findByNameLike(String keyword, Pageable pageable);
	
	// 民宿名または住所で検索（部分一致）
    Page<House> findByNameLikeOrAddressLike(String nameKeyword, String addressKeyword, Pageable pageable);

    //住所（エリア）で検索（部分一致）
    Page<House> findByAddressLike(String area, Pageable pageable);

    // 指定された金額以下の宿泊料金で検索
    Page<House> findByPriceLessThanEqual(Integer price, Pageable pageable);
    
	}


