package com.example.moattravel3.event;

import org.springframework.context.ApplicationEvent;

import com.example.moattravel3.entity.User;

import lombok.Getter;

/**
 * 会員登録イベント
 * 登録後のメール送信処理などに必要な情報を保持する
 */
@Getter
public class SignupEvent extends ApplicationEvent {
    private final User user;
    private final String requestUrl;

    public SignupEvent(Object source, User user, String requestUrl) {
        super(source);
        this.user = user;
        this.requestUrl = requestUrl;
    }
}