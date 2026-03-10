package com.example.moattravel.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.moattravel.entity.User;
import com.example.moattravel.repository.UserRepository;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 会員一覧画面
     */
    @GetMapping
    public String index(@RequestParam(name = "keyword", required = false) String keyword,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model) {
        
        Page<User> userPage;

        if (keyword != null && !keyword.isEmpty()) {
            // 検索キーワードがある場合、氏名またはフリガナで部分一致検索を行う
            userPage = userRepository.findByNameLikeOrFuriganaLike(
                "%" + keyword + "%", 
                "%" + keyword + "%", 
                pageable
            );
        } else {
            // 検索キーワードがない場合、全件取得する
            userPage = userRepository.findAll(pageable);
        }

        model.addAttribute("userPage", userPage);
        model.addAttribute("keyword", keyword);

        return "admin/users/index";
    }
    
    /**
     * 会員詳細画面
     */
    @GetMapping("/{id}")
    public String show(@PathVariable(name = "id") Integer id, Model model) {
        User user = userRepository.getReferenceById(id);

        model.addAttribute("user", user);

        return "admin/users/show";
    }
}