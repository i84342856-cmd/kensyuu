package com.example.moattravel2.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.moattravel2.entity.House;
import com.example.moattravel2.repository.HouseRepository;

@Controller
public class HomeController {
    final HouseRepository houseRepository;
    
    public HomeController(HouseRepository houseRepository) {
    	this.houseRepository = houseRepository;
    }
    
    @GetMapping("/")
    public String index(Model model) {
    	List<House> newHouses = houseRepository.findTop10ByOederByCreatedAtDesc();
    	model.addAttribute("newHouses",newHouses);
    	return "index";
    }
}