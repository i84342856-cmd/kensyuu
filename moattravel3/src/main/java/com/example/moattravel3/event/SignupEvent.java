package com.example.moattravel3.event;

import org.springframework.context.ApplicationEvent;

import com.example.moattravel3.entity.User;

import lombok.Getter;

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