package com.example.moattravel.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.moattravel.entity.Reservation;
import com.example.moattravel.entity.User;
import com.example.moattravel.repository.ReservationRepository;
import com.example.moattravel.security.UserDetailsImpl;

@Controller
public class ReservationController {
    private final ReservationRepository reservationRepository;

    public ReservationController(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    /**
     * ログイン済みユーザーの予約一覧ページを表示します。
     * ページネーションを適用し、作成日時順に並び替えて取得します。
     */
    @GetMapping("/reservations")
    public String index(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model) 
    {
        // 1. ログイン中のユーザー情報を取得
        User user = userDetailsImpl.getUser();
        
        // 2. ユーザーに紐づく予約履歴をリポジトリから取得（新着順）
        Page<Reservation> reservationPage = reservationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        // 3. ビューへ渡すデータをモデルに登録
        model.addAttribute("reservationPage", reservationPage);

        return "reservations/index";
    }
}