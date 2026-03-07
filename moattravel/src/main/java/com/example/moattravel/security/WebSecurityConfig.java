package com.example.moattravel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Bean // 「メソッドを動かして、出てきた成果物を登録する」
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    	
    /* http.authorizeHttpRequests(...) や http.build() といったメソッドは、内部でエラーが発生する可能性があるように設計されています。
     * (throws)：自分では処理せず、呼び出し元に「エラーが出るかもしれないよ」と伝える。
     * Exception：あらゆるエラー（例外）の親 */
    	
        http // 「どんなHTTPリクエストを許可するか？」というルールを組み立てる「設定の下書き」
            .authorizeHttpRequests((requests) -> requests
                // すべてのユーザーにアクセスを許可するURL
                .requestMatchers("/css/**", "/images/**", "/js/**", "/storage/**", "/").permitAll()
                // 管理者にのみアクセスを許可するURL
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 上記以外のURLはログインが必要（会員または管理者のどちらでもOK）
                .anyRequest().authenticated()
            )
            .formLogin((form) -> form
            		
                .loginPage("/login")              
                // 許可されていないリクエストがあったらここへ飛ばすという指示
                // つまり、ブラウザは GET /login をリクエストし、AuthController が動いてログイン画面が表示される
                
                .loginProcessingUrl("/login")     
                // コントローラーにはいかず、Spring Securityが「これはログインのPOSTだ！」と横取りする。
                // HTMLの記述: <form th:action="@{/login}" method="post"> 送り先と手段がピッタリ合致！
                // Spring Securityはデフォルトで、「username という名前の箱にID」「password という名前の箱にパスワード」が入っていると期待
                // Spring SecurityがDBからユーザー情報を取ってきます（※ここで UserDetailというクラスが動きます）。
                
                .defaultSuccessUrl("/?loggedIn")  // ログイン成功時のリダイレクト先URL
                .failureUrl("/login?error")       // ログイン失敗時のリダイレクト先URL
                .permitAll()
            )
            .logout((logout) -> logout
                .logoutSuccessUrl("/?loggedOut")  // ログアウト時のリダイレクト先URL
                .permitAll()
            );
        
      /* (requests) や (form) は ただの変数名。自分の好きな名前に変えても、プログラムの動作には影響しない。
       * でも、後で読み返したときにやすいように requests や form といった短い単語 が選ばれている。 */

        return http.build(); // .build() を呼ぶことで、すべての設定が確定し、実際に機能する
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}