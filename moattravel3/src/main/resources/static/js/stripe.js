
// Stripe公式のワープ装置本体（プログラム）を呼び出す
// Publishable Key（公開可能キー）、test の意味: 現在テスト環境（本物のクレジットカードの請求がいかない安全なモード）
const stripe = Stripe('pk_test_51T9bGyAM5VaZc0wLTb1CBsYfHBdTquMp1lcnRqXzPwIzX9yXO1YXi1DWTX8k5QNVjISIO4lpx58LzRVmeLdMe3OC007en3mE5N');
const paymentButton = document.querySelector('#paymentButton');

paymentButton.addEventListener('click', () => {
    stripe.redirectToCheckout({
		//  電源を入れた「stripe」を使って、セッションID（チケット）と一緒に決済画面へワープ！
        sessionId: sessionId
    });
});