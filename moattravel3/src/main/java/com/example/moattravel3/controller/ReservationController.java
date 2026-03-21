package com.example.moattravel3.controller;

import java.time.LocalDate;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.moattravel3.entity.House;
import com.example.moattravel3.entity.Reservation;
import com.example.moattravel3.entity.User;
import com.example.moattravel3.form.ReservationInputForm;
import com.example.moattravel3.form.ReservationRegisterForm;
import com.example.moattravel3.repository.HouseRepository;
import com.example.moattravel3.repository.ReservationRepository;
import com.example.moattravel3.security.UserDetailsImpl;
import com.example.moattravel3.service.ReservationService;
import com.example.moattravel3.service.StripeService;

@Controller
public class ReservationController {
    private final ReservationRepository reservationRepository;
    private final HouseRepository houseRepository;
    private final ReservationService reservationService;
    private final StripeService stripeService; 


    public ReservationController(ReservationRepository reservationRepository, HouseRepository houseRepository, ReservationService reservationService, StripeService stripeService) { 
        this.reservationRepository = reservationRepository; 
        this.houseRepository = houseRepository;
        this.reservationService = reservationService;
        this.stripeService = stripeService;
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
    
    /**
     * 予約入力処理（バリデーションチェック）
     */
    @GetMapping("/houses/{id}/reservations/input")
    public String input(@PathVariable(name = "id") Integer id,
                        @ModelAttribute @Validated ReservationInputForm reservationInputForm,
                        BindingResult bindingResult,
                        RedirectAttributes redirectAttributes,
                        Model model) 
    {
        House house = houseRepository.getReferenceById(id);
        Integer numberOfPeople = reservationInputForm.getNumberOfPeople();
        Integer capacity = house.getCapacity();

     // 定員チェック
        if (numberOfPeople != null) {
            if (!reservationService.isWithinCapacity(numberOfPeople, capacity)) {
                FieldError fieldError = new FieldError(bindingResult.getObjectName(), "numberOfPeople", "宿泊人数が定員を超えています。");
                bindingResult.addError(fieldError);
            }
        }

        // エラーがある場合は詳細ページに戻す
        if (bindingResult.hasErrors()) {
            model.addAttribute("house", house);
            model.addAttribute("errorMessage", "予約内容に不備があります。");
            return "houses/show";
        }

        // 入力内容をフラッシュスコープに保存して確認画面へリダイレクト
        redirectAttributes.addFlashAttribute("reservationInputForm", reservationInputForm);

        return "redirect:/houses/{id}/reservations/confirm";
    }
    
    /**
     * 予約内容の確認画面を表示
     */
    @GetMapping("/houses/{id}/reservations/confirm")
    public String confirm(@PathVariable(name = "id") Integer id,
                          @ModelAttribute ReservationInputForm reservationInputForm,
                          @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                          HttpServletRequest httpServletRequest,
                          Model model) 
    {
        House house = houseRepository.getReferenceById(id);
        User user = userDetailsImpl.getUser();

        // チェックイン日とチェックアウト日を取得する
        LocalDate checkinDate = reservationInputForm.getCheckinDate();
        LocalDate checkoutDate = reservationInputForm.getCheckoutDate();

        // 宿泊料金を計算する
        Integer price = house.getPrice();
        Integer amount = reservationService.calculateAmount(checkinDate, checkoutDate, price);

        // 登録用フォームに必要な情報をセット
        ReservationRegisterForm reservationRegisterForm = new ReservationRegisterForm(
            house.getId(), 
            user.getId(), 
            checkinDate.toString(), 
            checkoutDate.toString(), 
            reservationInputForm.getNumberOfPeople(), 
            amount
        );
        
        String sessionId = stripeService.createStripeSession(house.getName(), reservationRegisterForm, httpServletRequest);

        model.addAttribute("house", house);
        model.addAttribute("reservationRegisterForm", reservationRegisterForm);
        model.addAttribute("sessionId", sessionId);

        return "reservations/confirm";
    }
    
    /*
    @PostMapping("/houses/{id}/reservations/create")
    public String create(@ModelAttribute ReservationRegisterForm reservationRegisterForm) {                
        reservationService.create(reservationRegisterForm);        
        
        return "redirect:/reservations?reserved";
    }
    */
    
}