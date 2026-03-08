package com.example.moattravel.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.moattravel.entity.Role;
import com.example.moattravel.entity.User;
import com.example.moattravel.form.SignupForm;
import com.example.moattravel.repository.RoleRepository;
import com.example.moattravel.repository.UserRepository;

@Service
public class UserService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User create(SignupForm signupForm) {
		User user = new User();
		Role role = roleRepository.findByName("ROLE_GENERAL");

		// フォームからエンティティへデータを詰め替える
		user.setName(signupForm.getName());
		user.setFurigana(signupForm.getFurigana());
		user.setPostalCode(signupForm.getPostalCode());
		user.setAddress(signupForm.getAddress());
		user.setPhoneNumber(signupForm.getPhoneNumber());
		user.setEmail(signupForm.getEmail());

		// パスワードをハッシュ化してセット
		user.setPassword(passwordEncoder.encode(signupForm.getPassword()));

		// 全ての新規登録者に ROLE_GENERAL（一般ユーザー）を自動的に付与
		user.setRole(role);

		// 登録直後からログイン可能な状態、これでログイン済みになるわけではなく、ログインをしてもいい（１ true）という処理
		user.setEnabled(true);

		return userRepository.save(user);
	}

	/**
	 * メールアドレスが登録済みかどうかをチェックする
	 * @param email チェック対象のメールアドレス
	 * @return 登録済みならtrue、未登録ならfalse
	 */
	public boolean isEmailRegistered(String email) {
		User user = userRepository.findByEmail(email);
		return user != null;
	}
	
	/**
     * パスワードとパスワード（確認用）の入力値が一致するかどうかをチェックする
     * @param password 入力されたパスワード
     * @param passwordConfirmation 入力された確認用パスワード
     * @return 一致していればtrue、一致していなければfalse
     */
    public boolean isSamePassword(String password, String passwordConfirmation) {
        return password.equals(passwordConfirmation);
    }
}