package com.example.moattravel3.controller;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.moattravel3.entity.House;
import com.example.moattravel3.entity.Reservation;
import com.example.moattravel3.entity.User;
import com.example.moattravel3.form.ReservationCancelForm;
import com.example.moattravel3.form.ReservationInputForm;
import com.example.moattravel3.form.ReservationRegisterForm;
import com.example.moattravel3.repository.HouseRepository;
import com.example.moattravel3.repository.ReservationRepository;
import com.example.moattravel3.security.UserDetailsImpl;
import com.example.moattravel3.service.ReservationService;
import com.example.moattravel3.service.StripeService;
import com.stripe.exception.StripeException;

@Controller
public class ReservationController {
	private final ReservationRepository reservationRepository;
	private final HouseRepository houseRepository;
	private final ReservationService reservationService;
	private final StripeService stripeService;

	public ReservationController(ReservationRepository reservationRepository, HouseRepository houseRepository,
			ReservationService reservationService, StripeService stripeService) {
		this.reservationRepository = reservationRepository;
		this.houseRepository = houseRepository;
		this.reservationService = reservationService;
		this.stripeService = stripeService;
	}

	@GetMapping("/reservations")
	public String index(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
			@PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
			Model model) {

		User user = userDetailsImpl.getUser();
		Page<Reservation> reservationPage = reservationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

		model.addAttribute("reservationPage", reservationPage);
		return "reservations/index";
	}

	@GetMapping("/houses/{id}/reservations/input")
	public String input(@PathVariable(name = "id") Integer id,
			@ModelAttribute @Validated ReservationInputForm reservationInputForm,
			BindingResult bindingResult,
			RedirectAttributes redirectAttributes,
			Model model) {
		House house = houseRepository.getReferenceById(id);
		Integer numberOfPeople = reservationInputForm.getNumberOfPeople();
		Integer capacity = house.getCapacity();

		if (numberOfPeople != null) {
			if (!reservationService.isWithinCapacity(numberOfPeople, capacity)) {
				FieldError fieldError = new FieldError(bindingResult.getObjectName(), "numberOfPeople",
						"宿泊人数が定員を超えています。");
				bindingResult.addError(fieldError);
			}
		}

		if (bindingResult.hasErrors()) {
			model.addAttribute("house", house);
			model.addAttribute("errorMessage", "予約内容に不備があります。");
			return "houses/show";
		}

		redirectAttributes.addFlashAttribute("reservationInputForm", reservationInputForm);

		return "redirect:/houses/{id}/reservations/confirm";
	}

	@GetMapping("/houses/{id}/reservations/confirm")
	public String confirm(@PathVariable(name = "id") Integer id,
			@ModelAttribute ReservationInputForm reservationInputForm,
			@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
			HttpServletRequest httpServletRequest,
			Model model) {
		House house = houseRepository.getReferenceById(id);
		User user = userDetailsImpl.getUser();

		LocalDate checkinDate = reservationInputForm.getCheckinDate();
		LocalDate checkoutDate = reservationInputForm.getCheckoutDate();

		Integer price = house.getPrice();
		Integer amount = reservationService.calculateAmount(checkinDate, checkoutDate, price);

		ReservationRegisterForm reservationRegisterForm = new ReservationRegisterForm(
				house.getId(),
				user.getId(),
				checkinDate.toString(),
				checkoutDate.toString(),
				reservationInputForm.getNumberOfPeople(),
				amount);

		String sessionId = stripeService.createStripeSession(house.getName(), reservationRegisterForm,
				httpServletRequest);

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

	// 追加で作成
	@GetMapping("/reservations/{id}/cancel")
	public String cancel(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
			@PathVariable(name = "id") Integer id, Model model) {

		Reservation reservation = reservationRepository.getReferenceById(id);

		if (!reservation.getUser().getId().equals(userDetailsImpl.getUser().getId())) {
			return "redirect:/reservations";
		}

		ReservationCancelForm reservationCancelForm = new ReservationCancelForm(
				reservation.getHouse().getId(),
				reservation.getUser().getId(),
				reservation.getCheckinDate().toString(),
				reservation.getCheckoutDate().toString(),
				reservation.getNumberOfPeople(),
				reservation.getAmount());

		model.addAttribute(reservationCancelForm);
		model.addAttribute("reservation", reservation);
		return "reservations/cancel";
	}

	// 追加で作成
	@PostMapping("/reservations/{id}/cancel")
	public String cancel(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
			@PathVariable(name = "id") Integer id, ReservationCancelForm reservationCancelForm,
			RedirectAttributes redirectAttributes) {

		Reservation reservation = reservationRepository.getReferenceById(id);

		if (!reservation.getUser().getId().equals(userDetailsImpl.getUser().getId())) {
			return "redirect:/reservations";
		}

		// --- 返金額の計算とStripe返金処理 ---
		LocalDate today = LocalDate.now();
		LocalDate checkinDate = reservation.getCheckinDate();

		// 今日からチェックイン日までの日数を計算
		long daysUntilCheckin = ChronoUnit.DAYS.between(today, checkinDate);

		Integer totalAmount = reservation.getAmount();
		Integer refundAmount = 0;

		// キャンセルポリシーに基づく返金額の計算 (Stripeには「キャンセル料」ではなく「返金する金額」を伝えます)
		if (daysUntilCheckin >= 8) {
			// 8日前まで: 無料 (100%返金)
			refundAmount = totalAmount;
		} else if (daysUntilCheckin >= 2) {
			// 2〜7日前: 30%キャンセル料 (70%返金)
			refundAmount = (int) (totalAmount * 0.7);
		} else if (daysUntilCheckin == 1) {
			// 1日前(前日): 50%キャンセル料 (50%返金)
			refundAmount = (int) (totalAmount * 0.5);
		} else {
			// 当日・不泊: 100%キャンセル料 (0%返金)
			refundAmount = 0;
		}

		// 返金額が1円以上の場合はStripeの返金APIを呼び出す
		if (refundAmount > 0) {
			try {
				String paymentIntentId = reservation.getStripePaymentIntentId();
				stripeService.refundStripePayment(paymentIntentId, refundAmount);

			} catch (StripeException e) {
				redirectAttributes.addFlashAttribute("errorMessage", "決済の返金処理に失敗しました。管理者にお問い合わせください。");
				return "redirect:/reservations";
			}
		}

		reservationRepository.deleteById(id);

		NumberFormat nf = NumberFormat.getNumberInstance();

		if (refundAmount > 0) {
			String formattedAmount = nf.format(refundAmount);
			redirectAttributes.addFlashAttribute("successMessage",
					"予約のキャンセルが完了しました。キャンセルポリシーに基づき、" + formattedAmount
							+ "円の返金処理を行いました。※ご利用のクレジットカード会社により、実際の返金まで数日〜数週間かかる場合があります。");
		} else {
			redirectAttributes.addFlashAttribute("successMessage",
					"予約のキャンセルが完了しました。規定によりキャンセル料が100%発生するため、今回のご返金はございません。");
		}

		return "redirect:/reservations";
	}

}