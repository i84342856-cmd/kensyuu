package com.example.cryptotool.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // セキュリティ（ログイン画面への強制リダイレクト）を完全に無効化し、全ての通信を許可する
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // POST送信（設定変更など）を弾かれないようにCSRF対策を無効化
            .csrf(csrf -> csrf.disable())
            // iframe内で画面が弾かれないようにする
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));
            
        return http.build();
    }
}