package com.example.moattravel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

	@GetMapping("/login")
	public String login() {   
		 return "auth/login";
		 
	/* 
	 * 1. Spring Securityが「POST」を横取りしてくれる
	通常、私たちが作るコントローラーは「自分が書いたメソッド」が動きます。しかし、ログインに関しては Spring Securityというフレームワークが、背後で待ち構えています。
	HTML <form th:action="@{/login}" method="post">
	そして、設定クラス（WebSecurityConfig）でこう設定しました：
	.formLogin((form) -> form
    .loginProcessingUrl("/login") // ←ここがポイント！
    ...
	)
	この設定があるおかげで、ユーザーがログインボタンを押して /login にPOSTリクエストが飛んだ瞬間、
	Spring Securityが「あ、これは俺の仕事だ！」と横取りして、自動的にユーザー名やパスワードのチェック（認証処理）を行ってくれます。*/
	}
}
