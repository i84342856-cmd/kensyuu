package com.example.moattravel3.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.moattravel3.entity.House;

public interface HouseRepository extends JpaRepository<House, Integer> {
	public Page<House> findByNameLike(String keyword, Pageable pageable);

	public Page<House> findByNameLikeOrAddressLike(String nameKeyword, String addressKeyword, Pageable pageable);

	Page<House> findByAddressLike(String area, Pageable pageable);

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

	List<House> findTop10ByOrderByCreatedAtDesc();
	
	// 追加設定
	List<House> findTop5ByOrderByReservationCountDesc();

	/* =======================================================
	 * 【新規追加】複合条件検索（キーワード AND エリア AND 予算）
	 * =======================================================
	 * Spring Data JPAのRepositoryインターフェース内に記述される、カスタムクエリ（JPQL）を用いた検索メソッド */
	@Query("SELECT h FROM House h WHERE " +
			"(:keyword IS NULL OR :keyword = '' OR h.name LIKE CONCAT('%', :keyword, '%') OR h.address LIKE CONCAT('%', :keyword, '%')) "
			+
			"AND (:area IS NULL OR :area = '' OR h.address LIKE CONCAT('%', :area, '%')) " +
			"AND (:price IS NULL OR h.price <= :price)")
	Page<House> findBySearchConditions(
			@Param("keyword") String keyword,
			@Param("area") String area,
			@Param("price") Integer price,
			Pageable pageable);
}
