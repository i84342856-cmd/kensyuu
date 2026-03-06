package com.example.moattravel.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.moattravel.entity.User;

/**
 * Spring Securityの認証システムと、DB上のUserエンティティを橋渡しするクラス
 */
public class UserDetailsImpl implements UserDetails {
    private final User user;
    private final Collection<GrantedAuthority> authorities;

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