package com.example.moattravel.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.moattravel.entity.House;
import com.example.moattravel.repository.HouseRepository;

@Controller
@RequestMapping("/houses")
public class HouseController {

    private final HouseRepository houseRepository;

    // コンストラクタ注入
    public HouseController(HouseRepository houseRepository) {
        this.houseRepository = houseRepository;
    }

    /**
     * 民宿一覧ページ
     * キーワード、エリア、料金による検索結果をページネーションして表示する
     */
    @GetMapping
    public String index(@RequestParam(name = "keyword", required = false) String keyword,
                        @RequestParam(name = "area", required = false) String area,
                        @RequestParam(name = "price", required = false) Integer price,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model) {
        
        Page<House> housePage;

        // 検索条件の優先順位に従ってデータを取得
        if (keyword != null && !keyword.isEmpty()) {
            housePage = houseRepository.findByNameLikeOrAddressLike("%" + keyword + "%", "%" + keyword + "%", pageable);
        } else if (area != null && !area.isEmpty()) {
            housePage = houseRepository.findByAddressLike("%" + area + "%", pageable);
        } else if (price != null) {
            housePage = houseRepository.findByPriceLessThanEqual(price, pageable);
        } else {
            housePage = houseRepository.findAll(pageable);
        }

        model.addAttribute("housePage", housePage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("area", area);
        model.addAttribute("price", price);

        return "houses/index";
    }

}