// 予約可能な最大日程（今日から3ヶ月後）を計算
let maxDate = new Date();
maxDate = maxDate.setMonth(maxDate.getMonth() + 3); // 計算結果を maxDate に再代入している

// Flatpickrの初期化
flatpickr('#fromCheckinDateToCheckoutDate', {
    mode: "range",      // 範囲選択モード（チェックイン〜アウト）
    locale: 'ja',       // 日本語化
    minDate: 'today',   // 今日以前は選択不可
    maxDate: maxDate    // 3ヶ月後まで選択可能
});