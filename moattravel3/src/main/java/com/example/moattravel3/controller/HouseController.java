package com.example.moattravel3.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.moattravel3.entity.House;
import com.example.moattravel3.form.ReservationInputForm;
import com.example.moattravel3.repository.HouseRepository;

@Controller
@RequestMapping("/houses")
public class HouseController {

	private final HouseRepository houseRepository;

	public HouseController(HouseRepository houseRepository) {
		this.houseRepository = houseRepository;
	}

	@GetMapping
	public String index(@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "area", required = false) String area,
			@RequestParam(name = "price", required = false) Integer price,
			@RequestParam(name = "order", required = false) String order,
			@PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
			Model model) {

		// 追加設定 リクエストに応じたソート(並べ替え)条件の設定
		Sort sort;
		if ("priceAsc".equals(order)) {
			sort = Sort.by(Direction.ASC, "price");
		} else if ("popular".equals(order)) {
			sort = Sort.by(Direction.DESC, "reservationCount");
		} else if ("updatedAtDesc".equals(order)) {
			sort = Sort.by(Direction.DESC, "updatedAt");
		} else {
			sort = Sort.by(Direction.DESC, "createdAt"); // デフォルトは新着順
			order = "createdAtDesc"; // 画面の表示用にセット
		}

		//  追加設定 ページネーション情報にソート条件を結合
		Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

		//  追加設定 複合条件での検索（すべての条件を一つのメソッドで処理）
		Page<House> housePage = houseRepository.findBySearchConditions(keyword, area, price, sortedPageable);

		/* 改修のため使用しない。
		Page<House> housePage;
		
		 // 1. キーワード検索（民宿名 または 住所）
		if (keyword != null && !keyword.isEmpty()) {
		    if (order != null && order.equals("priceAsc")) {
		        housePage = houseRepository.findByNameLikeOrAddressLikeOrderByPriceAsc("%" + keyword + "%", "%" + keyword + "%", pageable);
		    } else {
		        housePage = houseRepository.findByNameLikeOrAddressLikeOrderByCreatedAtDesc("%" + keyword + "%", "%" + keyword + "%", pageable);
		    }
		
		// 2. エリア検索
		} else if (area != null && !area.isEmpty()) {
		    if (order != null && order.equals("priceAsc")) {
		        housePage = houseRepository.findByAddressLikeOrderByPriceAsc("%" + area + "%", pageable);
		    } else {
		        housePage = houseRepository.findByAddressLikeOrderByCreatedAtDesc("%" + area + "%", pageable);
		    }
		
		// 3. 予算検索
		} else if (price != null) {
		    if (order != null && order.equals("priceAsc")) {
		        housePage = houseRepository.findByPriceLessThanEqualOrderByPriceAsc(price, pageable);
		    } else {
		        housePage = houseRepository.findByPriceLessThanEqualOrderByCreatedAtDesc(price, pageable);
		    }
		
		// 4. 全件取得（検索条件なし）
		} else {
		    if (order != null && order.equals("priceAsc")) {
		        housePage = houseRepository.findAllByOrderByPriceAsc(pageable);
		    } else {
		        housePage = houseRepository.findAllByOrderByCreatedAtDesc(pageable);
		    }
		}
		*/

		model.addAttribute("housePage", housePage);
		model.addAttribute("keyword", keyword);
		model.addAttribute("area", area);
		model.addAttribute("price", price);
		model.addAttribute("order", order);

		return "houses/index";
	}

	@GetMapping("/{id}")
	public String show(@PathVariable(name = "id") Integer id, Model model) {
		House house = houseRepository.getReferenceById(id);

		model.addAttribute("house", house);
		model.addAttribute("reservationInputForm", new ReservationInputForm());

		return "houses/show";
	}

}