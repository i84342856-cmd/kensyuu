package com.example.moattravel2.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.moattravel2.entity.User;
import com.example.moattravel2.form.UserEditForm;
import com.example.moattravel2.repository.UserRepository;
import com.example.moattravel2.security.UserDetailsImpl;
import com.example.moattravel2.service.UserService;

@Controller
@RequestMapping("/user")
public class UserController {
	
	private final UserRepository userRepository;
	private final UserService userService;
	
	public UserController(UserRepository userRepository,UserService userService) {
		this.userRepository = userRepository;
		this.userService = userService;
	}
	
	@GetMapping
	public String index(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,Model model) {
		User user = userRepository.getReferenceById(userDetailsImpl.getUser().getId());
		model.addAttribute("user",user);
		return "user/index";
	}
	
	@GetMapping("/edit")
	// @AuthenticationPrincipal を記述しなかった場合、引数の userDetailsImpl は null になるか、そもそも値が入らなくなります。
	public String edit(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,Model model) {
		User user = userRepository.getReferenceById(userDetailsImpl.getUser().getId());

		UserEditForm userEditForm = new UserEditForm(
				user.getId(),
				user.getName(),
				user.getFurigana(),
				user.getPostalCode(),
				user.getAddress(),
				user.getPhoneNumber(),
				user.getEmail());
		
		model.addAttribute("userEditForm",userEditForm);
		
				return "user/edit";
	}
	
	@PostMapping("/update")
	public String update(@ModelAttribute @Validated UserEditForm userEditForm, BindingResult bindingResult,RedirectAttributes redirectAttributes) {
		
		if(userService.isEmailChanged(userEditForm) && userService.isEmailRegistered(userEditForm.getEmail())) {
			FieldError fieldError = new FieldError(bindingResult.getObjectName(),"email", "すでに登録済みのメールアドレスです。");
			bindingResult.addError(fieldError);
		}
		
		if(bindingResult.hasErrors()) {
			return "user/edit";
		}
		
		userService.update(userEditForm);
		
		redirectAttributes.addFlashAttribute("successMessage", "会員情報を編集しました。");
		return "redirect:/user";
	}

}



