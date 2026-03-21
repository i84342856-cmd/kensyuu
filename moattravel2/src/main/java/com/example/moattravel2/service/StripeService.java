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
  
	// application.properties に書かれている Stripeのシークレットキー（sk_test_...）を自動で読み込んでこの変数に入れる
		@Value("${stripe.api-key}")
		private String stripeApiKey;

		// データベースに予約を保存するためのサービス（ReservationService）を使うための準備
		private final ReservationService reservationService;

		// コンストラクタ（このクラスが作られるときに呼ばれる）。ここでReservationServiceを受け取る（DI：依存性の注入）
		public StripeService(ReservationService reservationService) {
			this.reservationService = reservationService;
		}

		// ==============================================================================
		// 【前半戦】決済画面のURLを作るための「注文書（セッション）」を作成し、そのIDを返すメソッド
		// ==============================================================================
		public String createStripeSession(String houseName, ReservationRegisterForm reservationRegisterForm,
				HttpServletRequest httpServletRequest) {
			
			// Stripeのシステムにアクセスするための「合言葉（APIキー）」を設定する
			Stripe.apiKey = stripeApiKey;
			
			// ユーザーが今見ているページのURL（例：http://localhost:8080/houses/1/reservations/confirm）を取得する
			String requestUrl = new String(httpServletRequest.getRequestURL());
			
			// ここから、Stripeに渡す「注文書（セッション）」の中身を組み立てていく（Builderパターンという書き方）
			SessionCreateParams params = SessionCreateParams.builder()
					// 支払い方法を指定する。今回はクレジットカード（CARD）のみを許可。
					.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
					
					// 注文する「商品」の情報を追加する
					.addLineItem(
							SessionCreateParams.LineItem.builder()
									// 金額や商品名のデータをセットする
									.setPriceData(
											SessionCreateParams.LineItem.PriceData.builder()
													// 商品データ（今回は宿）の設定
													.setProductData(
															SessionCreateParams.LineItem.PriceData.ProductData.builder()
																	// 決済画面に表示される商品名（宿の名前）をセット
																	.setName(houseName)
																	.build())
													// 支払う金額（合計金額）をセット。フォームから取得した金額を使う。
													.setUnitAmount((long) reservationRegisterForm.getAmount())
													// 通貨を日本円（jpy）に設定
													.setCurrency("jpy")
													.build())
									// 数量を設定（1回の予約なので1）
									.setQuantity(1L)
									.build())
					
					// 決済のモードを指定。PAYMENTは「1回限りの支払い（都度決済）」という意味。
					.setMode(SessionCreateParams.Mode.PAYMENT)
					
					// 決済が「成功」した後にユーザーを自動で飛ばすURL（成功画面）。現在のURLの末尾を書き換えて「/reservations?reserved」にする。
					.setSuccessUrl(
							requestUrl.replaceAll("/houses/[0-9]+/reservations/confirm", "") + "/reservations?reserved")
					
					// ユーザーが決済画面で「戻る（キャンセル）」を押した時に飛ばすURL。確認画面に戻るようにする。
					.setCancelUrl(requestUrl.replace("/reservations/confirm", ""))

					// ★超重要★ 後でWebhookが来た時のために、Stripe側にコッソリ預けておく「裏データのメモ（メタデータ）」
					.setPaymentIntentData(
							SessionCreateParams.PaymentIntentData.builder()
									// どの宿か（houseId）
									.putMetadata("houseId", reservationRegisterForm.getHouseId().toString())
									// 誰が予約したか（userId）
									.putMetadata("userId", reservationRegisterForm.getUserId().toString())
									// いつから（checkinDate）
									.putMetadata("checkinDate", reservationRegisterForm.getCheckinDate())
									// いつまで（checkoutDate）
									.putMetadata("checkoutDate", reservationRegisterForm.getCheckoutDate())
									// 何人で（numberOfPeople）
									.putMetadata("numberOfPeople", reservationRegisterForm.getNumberOfPeople().toString())
									// いくらで（amount）
									.putMetadata("amount", reservationRegisterForm.getAmount().toString())
									.build())
					.build(); // 注文書の組み立て完了！
			
			try {
				// 組み立てた注文書（params）をStripeサーバーに送信し、決済用の「セッション」を作ってもらう
				Session session = Session.create(params);
				System.out.println(session.getId());
				// コントローラーに、作ってもらったセッションのID（cs_test_...のような文字列）を返す
				return session.getId();
			} catch (StripeException e) {
				// もしStripeとの通信でエラーが起きたら、エラー内容を表示して空文字を返す
				e.printStackTrace();
				return "";
			}
	}
	
	
	public void processSessionCompleted(Event event) {
		// 確認用のログ出力
				System.out.println("====== Webhook受信完了！ ======");
				try {
					// Stripeから送られてきた通知データ（event）の中身を取り出す。
					// ★ deserializeUnsafe() を使うことで、Stripeのバージョン違いによる「無言のスルー」を回避し、強制的に読み込む！
					StripeObject stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();

					if (stripeObject != null) {
						Session session = (Session) stripeObject;
						SessionRetrieveParams params = SessionRetrieveParams.builder().addExpand("payment_intent").build();

						session = Session.retrieve(session.getId(), params, null);
						
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