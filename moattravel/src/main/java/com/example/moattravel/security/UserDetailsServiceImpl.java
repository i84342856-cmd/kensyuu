package com.example.moattravel.security;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.moattravel.entity.User;
import com.example.moattravel.repository.UserRepository;

/**
 * ログイン時にSpring Securityから呼び出され、
 * DBからユーザー情報を検索・取得して「認証用オブジェクト」を作成するクラス
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    // コンストラクタ注入（DIコンテナからリポジトリを受け取る）
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /*「UserDetailsService」インターフェースの中身は驚くほどシンプルです。メソッドがたった一つしかありません。
     * loadUserByUsername(String username)
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            // 1. DBからUserエンティティを取得（ここでデータが「セット」される源泉が見つかる）
            User user = userRepository.findByEmail(email);
            
            if (user == null) {
                throw new UsernameNotFoundException("ユーザーが見つかりませんでした。");
            }

            /* .user.getRole() → userオブジェクト中に入っている Roleクラスオブジェクトを取り出す。
             * .getName() → そのRoleオブジェクトの name（文字列） を取り出す。
             * 「役割（Role）」には名前以外にも情報を持たせることが多いため、独立したクラス（型）にすることが一般的です。*/
            String userRoleName = user.getRole().getName();
            
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            
            /* GrantedAuthority はただの「インターフェース（規格）」
             * インターフェースは new できないので、Spring Securityが用意する「実体」である SimpleGrantedAuthorityを使います。*/
            authorities.add(new SimpleGrantedAuthority(userRoleName));

            // 3. 【重要】ここで UserDetailsImpl を new して、DBから取ってきた user と authorities を渡す
            // これにより、Securityが管理する「ログイン証」が完成する
            return new UserDetailsImpl(user, authorities);

        } catch (Exception e) {
            // 例外が発生した場合は一律で「見つからない」としてSecurityに通知する
            throw new UsernameNotFoundException("ユーザーが見つかりませんでした。");
        }
    }
}