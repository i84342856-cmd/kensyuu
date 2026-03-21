package com.example.moattravel3.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.moattravel3.service.StripeService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

@Controller
public class StripeWebhookController {
    private final StripeService stripeService;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        Stripe.apiKey = stripeApiKey;
        Event event = null;

        try {
        	// ★超厳格なセキュリティチェック（本人確認）
            // 届いた「データ本体（payload）」と「署名（sigHeader）」と「秘密のパスワード（webhookSecret）」の3つを照らし合わせて、
            // 『本当にStripeから送られてきた本物の通知か？』を確認し、安全な Event（通知データ）に変換します。
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
        	// もし署名が一致しなかった場合「400 BAD REQUEST（不正なリクエストです）」
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        
       // Stripeからは色々な種類の通知が届くので、「決済完了（checkout.session.completed）」の通知かどうかを確認します。
        if ("checkout.session.completed".equals(event.getType())) {
            stripeService.processSessionCompleted(event);
        }
        
        // Stripeのサーバーに対して、「200 OK（無事に通知を受け取りました！）」というお返事を返します。
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }
}
