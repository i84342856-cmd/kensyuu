package com.example.moattravel.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.moattravel.entity.User;
import com.example.moattravel.entity.VerificationToken;
import com.example.moattravel.event.SignupEventPublisher;
import com.example.moattravel.form.SignupForm;
import com.example.moattravel.service.UserService;
import com.example.moattravel.service.VerificationTokenService;

@Controller
public class AuthController {
	
	private final UserService userService;
	private final SignupEventPublisher signupEventPublisher;
	private final VerificationTokenService verificationTokenService;
	
	public AuthController(UserService userService, SignupEventPublisher signupEventPublisher,VerificationTokenService verificationTokenService) {
        this.userService = userService;
        this.signupEventPublisher = signupEventPublisher;
        this.verificationTokenService = verificationTokenService;
    }
	
	@GetMapping("/login")
	public String login() {   
		 return "auth/login";
	}
	
	@GetMapping("/signup")
	public String signup(Model model) {
		model.addAttribute("signupForm", new SignupForm());
		return "auth/signup";
	}
	
	@PostMapping("/signup")
    public String signup(@ModelAttribute @Validated SignupForm signupForm, 
                         BindingResult bindingResult, 
                         RedirectAttributes redirectAttributes,
                         HttpServletRequest httpServletRequest) 
    {
        // 1. メールアドレスが登録済みかチェック
        if (userService.isEmailRegistered(signupForm.getEmail())) {
            FieldError fieldError = new FieldError(bindingResult.getObjectName(), "email", "すでに登録済みのメールアドレスです。");
            bindingResult.addError(fieldError);
        }

        // 2. パスワードの一致チェック
        if (!userService.isSamePassword(signupForm.getPassword(), signupForm.getPasswordConfirmation())) {
            FieldError fieldError = new FieldError(bindingResult.getObjectName(), "password", "パスワードが一致しません。");
            bindingResult.addError(fieldError);
        }

        // 3. バリデーションエラーがあればフォームに戻す
        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }

        
        // ④ ユーザーの仮登録（enabled=falseで保存される）
        User createdUser = userService.create(signupForm);
        
        // ⑤ 認証メール用のURLを取得（例: http://localhost:8080/signup）
        String requestUrl = new String(httpServletRequest.getRequestURL());
        
        // ⑥ イベント発行（Listenerに「メール送って！」と通知する）
        signupEventPublisher.publishSignupEvent(createdUser, requestUrl);
        
        // ⑦ 完了メッセージをセットしてトップページへリダイレクト
        redirectAttributes.addFlashAttribute("successMessage", "ご入力いただいたメールアドレスに認証メールを送信しました。メールに記載されているリンクをクリックし、会員登録を完了してください。");
        return "redirect:/";
    }
	
	// メール認証リンクの処理
    @GetMapping("/signup/verify")
    public String verify(@RequestParam(name = "token") String token, Model model) {
        // 送られてきたトークンが正しいかDBに照会
        VerificationToken verificationToken = verificationTokenService.getVerificationToken(token);
        
        if (verificationToken != null) {
            // トークンが有効なら、紐づくユーザーを有効化(enabled=true)にする
            User user = verificationToken.getUser();  
            userService.enableUser(user);
            model.addAttribute("successMessage", "会員登録が完了しました。");            
        } else {
            // トークンが見つからない、または無効な場合
            model.addAttribute("errorMessage", "トークンが無効です。");
        }
        
        return "auth/verify"; // 認証結果画面を表示
    }
	
}
