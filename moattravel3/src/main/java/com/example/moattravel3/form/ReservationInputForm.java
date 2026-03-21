package com.example.moattravel3.form;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ReservationInputForm {

    @NotBlank(message = "チェックイン日とチェックアウト日を選択してください。")
    private String fromCheckinDateToCheckoutDate;

    @NotNull(message = "宿泊人数を入力してください。")
    @Min(value = 1, message = "宿泊人数は1人以上に設定してください。")
    private Integer numberOfPeople;

    /**
     * 文字列からチェックイン日を抽出して取得する
     */
    public LocalDate getCheckinDate() {
    	// 1. "2026-03-10 から 2026-03-12" のような文字列を「 から 」という目印で2つに切り分け、配列（箱）に保存
    	String[] checkinDateAndCheckoutDate = getFromCheckinDateToCheckoutDate().split(" から ");
    	// 2. 配列の1番目（0番目：チェックイン日）を取り出し、LocalDate型（日付オブジェクト）に変換して返します
    	return LocalDate.parse(checkinDateAndCheckoutDate[0]);
    }

    /**
     * 文字列からチェックアウト日を抽出して取得する
     */
    public LocalDate getCheckoutDate() {
        String[] checkinDateAndCheckoutDate = getFromCheckinDateToCheckoutDate().split(" から ");
        return LocalDate.parse(checkinDateAndCheckoutDate[1]);
    }
}