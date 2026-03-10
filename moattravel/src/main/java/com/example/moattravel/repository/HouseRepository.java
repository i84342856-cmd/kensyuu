package com.example.moattravel.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data JPA（データベースとのやり取りを簡単にするフレームワーク）によって提供されるインターフェース
import com.example.moattravel.entity.House;

public interface HouseRepository extends JpaRepository<House, Integer> {
	public Page<House> findByNameLike(String keyword, Pageable pageable);

	// 民宿名または住所で検索（部分一致）
	public Page<House> findByNameLikeOrAddressLike(String nameKeyword, String addressKeyword, Pageable pageable);

	//住所（エリア）で検索（部分一致）
	Page<House> findByAddressLike(String area, Pageable pageable);

	// 指定された金額以下の宿泊料金で検索
	Page<House> findByPriceLessThanEqual(Integer price, Pageable pageable);

	/* =======================================================
	 * 並べ替え機能付き検索メソッド
	 * ======================================================= */

	// 1. キーワード検索（民宿名 または 住所）
	Page<House> findByNameLikeOrAddressLikeOrderByCreatedAtDesc(String nameKeyword, String addressKeyword,
			Pageable pageable);

	Page<House> findByNameLikeOrAddressLikeOrderByPriceAsc(String nameKeyword, String addressKeyword,
			Pageable pageable);

	// 2. エリア検索（住所）
	Page<House> findByAddressLikeOrderByCreatedAtDesc(String area, Pageable pageable);

	Page<House> findByAddressLikeOrderByPriceAsc(String area, Pageable pageable);

	// 3. 予算検索（宿泊料金以下）
	Page<House> findByPriceLessThanEqualOrderByCreatedAtDesc(Integer price, Pageable pageable);

	Page<House> findByPriceLessThanEqualOrderByPriceAsc(Integer price, Pageable pageable);

	// 4. 全件取得
	Page<House> findAllByOrderByCreatedAtDesc(Pageable pageable);

	Page<House> findAllByOrderByPriceAsc(Pageable pageable);
	
	/**
     * トップページ用：新しく登録された民宿を最大10件取得する
     */
    List<House> findTop10ByOrderByCreatedAtDesc();
}
