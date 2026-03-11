const stripe = Stripe('pk_test_51MwfEuAAEPgBtL4yo6bxzh8EaMy8VWkUXeNYYS3e23eQ6ReO4LSZabuGqH5MI9bEne0lXNjHn5HRu5pISVDAhaXt008vq0knOw');
const paymentButton = document.querySelector('#paymentButton');

paymentButton.addEventListener('click', () => {
  stripe.redirectToCheckout({
    sessionId: sessionId
  })
});