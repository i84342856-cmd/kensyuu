package com.example.moattravel.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.moattravel.entity.User;

/**
 * Spring Securityの認証システムと、DB上のUserエンティティを橋渡しするクラス
 * この UserDetailsImpl は 「HTMLのフォーム」や「URLのパラメータ」から直接作られるものではありません。
 * このクラスを new してフィールドをセット（インスタンス化）するのは、 
 * UserDetailsServiceImpl という「サービス（Service）クラス」の中です。
 * 
 * UserDetailsService (探偵/受付係):「test@example.com の人はいる？」「あ、DBにいました！この人ですね」と、データを見つけ出すのが仕事。
 * UserDetailsImpl (身分証/パスポート):見つかったデータを、Spring Securityが読める形式（UserDetails）にパッケージングして、常に持ち歩くのが仕事。
 */

public class UserDetailsImpl implements UserDetails {
    /* UserDetails インターフェースの全貌このインターフェースには、7つのメソッドが定義されています。
     * これを実装することで、Spring Securityがあなたのアプリのユーザー情報を正しく扱えるようになります。
     * 
     * getAuthorities() ユーザーが持つ権限（ロール）のコレクションを返す
     * getPassword() DBに保存されているハッシュ化されたパスワードを返す
     * getUsername() ログインID（メールアドレスなど）を返す
     * isAccountNonExpired() アカウントが期限切れでないか（有効なら true）
     * isAccountNonLocked ()アカウントがロックされていないか（有効なら true）
     * isCredentialsNonExpired() パスワードが期限切れでないか（有効なら true）
     * isEnabled() ユーザー自体が有効か（退会済みなどでなければ true） */
	
	private final User user;
    /* UserDetailsImpl は「ログイン証」です。Aさんがログインしたら、Aさんの情報が入ったログイン証が1枚発行されます。
     * そのログイン証の持ち主（user）を途中でBさんに書き換えられたら困りますよね？だから final にして固定するのです。*/
    
    private final Collection<GrantedAuthority> authorities;
    /* Collection：権限（証明書）を複数まとめて入れる「カゴ」。
       GrantedAuthority：Spring Securityが認識できる「権限の証明書」。
       手書きメソッドの理由：インターフェースが決めた「メソッド名」に、自分のエンティティの「値」を当てはめる必要があるから。
       
       GrantedAuthority 実際に中に入っているのは、多くの場合 SimpleGrantedAuthority というクラスのインスタンスです。
       // イメージ的な中身（Listの場合）
        [ SimpleGrantedAuthority("ROLE_USER"),
          SimpleGrantedAuthority("ROLE_ADMIN")
          
          DBの User テーブルには、ロールが単なる文字列（例: "ADMIN"）として保存されていることが多いですよね。
          Spring Securityはそれをそのままでは使えず、GrantedAuthority という「専用の型」に変換してほしがります。
          「DBの生データ（String）」を「Security専用の型（SimpleGrantedAuthority）」に料理する場所として、UserDetailsService が必要なのです。
        
        「1人で複数の顔を持つ」 ことがあり得るからです。
        例: あるユーザーが「一般会員（ROLE_USER）」であり、同時に「記事の編集者（ROLE_EDITOR）」でもある、というケース。
        この場合、コレクションの中には 2枚の名札 が入ることになります。
         2つの権限を持っている状態
          authorities = [ "ROLE_USER", "ROLE_EDITOR" ]
     */
    
    public UserDetailsImpl(User user, Collection<GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    // 元のUserエンティティを取得するためのカスタムメソッド
    public User getUser() {
        return user;
    }
    
    // --- UserDetails インターフェースのメソッド実装 ---

    @Override
    public String getPassword() {
        return user.getPassword(); // ハッシュ化済みのパスワード
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // ログインIDとしてメールアドレスを使用
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities; // ロールのコレクション
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // アカウントの期限切れチェック（常に有効として扱う）
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // アカウントのロックチェック（常に有効として扱う）
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // パスワードの期限切れチェック（常に有効として扱う）
    }

    @Override
    public boolean isEnabled() {
        return user.getEnabled(); // ユーザーの有効・無効状態をDBの値に基づき返す
    }
}