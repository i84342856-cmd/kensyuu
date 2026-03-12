package com.example.moattravel2.service;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.moattravel2.form.ReservationRegisterForm;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;

@Service
public class StripeService {
    @Value("${stripe.api-key}")
    private String stripeApiKey;
    
    private final ReservationService reservationService;
    
    public StripeService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }    
    
    // 「Stripeの決済画面（クレジットカード入力画面）を作るための準備をして、
    // その画面への『入場チケット（セッションID）』をもらってくる」
    public String createStripeSession(String houseName, ReservationRegisterForm reservationRegisterForm, HttpServletRequest httpServletRequest) {
        
    	// Stripeのシステムにアクセスするための「合言葉（シークレットキー）」をセット
    	Stripe.apiKey = stripeApiKey;
    	
    	// ユーザーが今開いているページのURL（例: http://localhost:8080/houses/1/reservations/confirm）を取得します。
    	// 後で「決済後に行くページ」を作るために使います。
        String requestUrl = new String(httpServletRequest.getRequestURL());
        
        SessionCreateParams params =
            SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()   
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(houseName)
                                        .build())
                                .setUnitAmount((long)reservationRegisterForm.getAmount())
                                .setCurrency("jpy")                                
                                .build())
                        .setQuantity(1L)
                        .build())
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(requestUrl.replaceAll("/houses/[0-9]+/reservations/confirm", "") + "/reservations?reserved")
                .setCancelUrl(requestUrl.replace("/reservations/confirm", ""))
                .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("houseId", reservationRegisterForm.getHouseId().toString())
                        .putMetadata("userId", reservationRegisterForm.getUserId().toString())
                        .putMetadata("checkinDate", reservationRegisterForm.getCheckinDate())
                        .putMetadata("checkoutDate", reservationRegisterForm.getCheckoutDate())
                        .putMetadata("numberOfPeople", reservationRegisterForm.getNumberOfPeople().toString())
                        .putMetadata("amount", reservationRegisterForm.getAmount().toString())
                        .build())
                .build();
        try {
            Session session = Session.create(params);
            return session.getId();
        } catch (StripeException e) {
            e.printStackTrace();
            return "";
        }
    } 
    
    /* 以下のコードだと、stripeからデータを取得できないので、未使用
    public void processSessionCompleted(Event event) {
        Optional<StripeObject> optionalStripeObject = event.getDataObjectDeserializer().getObject();
        optionalStripeObject.ifPresent(stripeObject -> {
            Session session = (Session)stripeObject;
            SessionRetrieveParams params = SessionRetrieveParams.builder().addExpand("payment_intent").build();

            try {
                session = Session.retrieve(session.getId(), params, null);
                Map<String, String> paymentIntentObject = session.getPaymentIntentObject().getMetadata();
                reservationService.create(paymentIntentObject);
            } catch (StripeException e) {
                e.printStackTrace();
            }
        });
    }
    */
    
    public void processSessionCompleted(Event event) {
		// 確認用のログ出力
		System.out.println("====== Webhook受信完了！ ======");

		try {
			// Stripeから送られてきた通知データ（event）の中身を取り出す。
			// ★ deserializeUnsafe() を使うことで、Stripeのバージョン違いによる「無言のスルー」を回避し、強制的に読み込む！
			StripeObject stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();

			// データが空っぽじゃなければ（ちゃんと読み込めていれば）、中身の処理に進む
			if (stripeObject != null) {
				// 取り出したデータを、決済セッション（Session）の形に変換する
				Session session = (Session) stripeObject;
				
				// Stripeのサーバーから、この決済に関する「詳細データ（payment_intent）」も一緒に取ってくるようにお願いする準備
				SessionRetrieveParams params = SessionRetrieveParams.builder().addExpand("payment_intent").build();

				// セッションIDを使ってStripeサーバーに再度問い合わせ、決済の詳細データを完全な形で取得し直す
				session = Session.retrieve(session.getId(), params, null);
				
				// 取得した詳細データから、前半戦で預けておいた「裏データのメモ（メタデータ）」をMap（辞書）の形式でごっそり取り出す
				Map<String, String> paymentIntentObject = session.getPaymentIntentObject().getMetadata();

				// 取り出したメタデータ（houseIdやuserIdなど）をReservationServiceに渡し、データベース（reservationsテーブル）に保存（INSERT）する！
				reservationService.create(paymentIntentObject);
				
				// 無事に保存まで完了したことをコンソールに表示
				System.out.println("====== データベースへの予約登録が成功しました！ ======");
			}
		} catch (Exception e) {
			// データの読み込み失敗やStripeとの通信エラー、データベースの保存エラーなど、何かしら問題が起きたらここに来る
			System.out.println("====== 処理中にエラーが発生しました ======");
			// エラーの詳しい原因（赤い文字のログ）をコンソールに出力する
			e.printStackTrace();
		}
	}
}
