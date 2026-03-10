package com.example.moattravel.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.moattravel.entity.House;
import com.example.moattravel.repository.HouseRepository;

@Controller
public class HomeController {
	private final HouseRepository houseRepository;
	
	// コンストラクタ注入
    public HomeController(HouseRepository houseRepository) {
        this.houseRepository = houseRepository;
    }
    
    /**
     * トップページを表示します。
     * 新着の民宿を最大10件取得してモデルに登録します。
     */
    
    @GetMapping("/")
    public String index(Model model) {
        // リポジトリから新着10件を取得
        List<House> newHouses = houseRepository.findTop10ByOrderByCreatedAtDesc();
        
        // ビュー（HTML）へ渡すデータをセット
        model.addAttribute("newHouses", newHouses);

        return "index";
    }
}
