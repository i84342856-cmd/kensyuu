package com.example.moattravel3.service;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.moattravel3.form.ReservationRegisterForm;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
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

	public String createStripeSession(String houseName, ReservationRegisterForm reservationRegisterForm,
			HttpServletRequest httpServletRequest) {

		Stripe.apiKey = stripeApiKey;

		// ユーザーが今見ているページのURL（例：http://localhost:8080/houses/1/reservations/confirm）を取得する
		String requestUrl = new String(httpServletRequest.getRequestURL());

		SessionCreateParams params = SessionCreateParams.builder()

				.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)

				.addLineItem(
						SessionCreateParams.LineItem.builder()

								.setPriceData(
										SessionCreateParams.LineItem.PriceData.builder()

												.setProductData(
														SessionCreateParams.LineItem.PriceData.ProductData.builder()

																.setName(houseName)
																.build())

												.setUnitAmount((long) reservationRegisterForm.getAmount())

												.setCurrency("jpy")
												.build())

								.setQuantity(1L)
								.build())

				.setMode(SessionCreateParams.Mode.PAYMENT)

				.setSuccessUrl(
						requestUrl.replaceAll("/houses/[0-9]+/reservations/confirm", "") + "/reservations?reserved")

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

	public void processSessionCompleted(Event event) {

		System.out.println("====== Webhook受信完了！ ======");

		try {

			StripeObject stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();

			if (stripeObject != null) {

				Session session = (Session) stripeObject;

				SessionRetrieveParams params = SessionRetrieveParams.builder().addExpand("payment_intent").build();

				// セッションIDを使ってStripeサーバーに再度問い合わせ、決済の詳細データを完全な形で取得し直す
				session = Session.retrieve(session.getId(), params, null);

				Map<String, String> paymentIntentObject = session.getPaymentIntentObject().getMetadata();

				// 追加：返金時に必要になる「PaymentIntentのID」を取得する
				String paymentIntentId = session.getPaymentIntentObject().getId();

				// 取り出したメタデータ（houseIdやuserIdなど）をReservationServiceに渡し、データベース（reservationsテーブル）に保存（INSERT）する！
				// 変更：取り出したメタデータと一緒に、決済ID (paymentIntentId) もReservationServiceに渡す！
				reservationService.create(paymentIntentObject, paymentIntentId);

				// 無事に保存まで完了したことをコンソールに表示
				System.out.println("====== データベースへの予約登録が成功しました！ ======");
			}
		} catch (Exception e) {

			System.out.println("====== 処理中にエラーが発生しました ======");

			e.printStackTrace();
		}
	}

	/**
	 * StripeAPIを使用して返金処理を実行
	 */
	public void refundStripePayment(String paymentIntentId, Integer refundAmount) throws StripeException {

		Stripe.apiKey = stripeApiKey;

		// 返金額が0円以下の場合は処理しない
		if (refundAmount <= 0) {
			return;
		}

		// 返金パラメーターの構築
		RefundCreateParams params = RefundCreateParams.builder()
				.setPaymentIntent(paymentIntentId)
				.setAmount((long) refundAmount) // Stripeの日本円(JPY)はそのままの整数値を渡します
				.build();

		// 返金の実行
		Refund.create(params);
	}
}
