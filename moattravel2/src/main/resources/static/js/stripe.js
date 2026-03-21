const stripe = Stripe('pk_test_51T9bGyAM5VaZc0wLTb1CBsYfHBdTquMp1lcnRqXzPwIzX9yXO1YXi1DWTX8k5QNVjISIO4lpx58LzRVmeLdMe3OC007en3mE5N');
const paymentButton = document.querySelector('#paymentButton');

paymentButton.addEventListener('click', () => {
  stripe.redirectToCheckout({
    sessionId: sessionId
  })
});