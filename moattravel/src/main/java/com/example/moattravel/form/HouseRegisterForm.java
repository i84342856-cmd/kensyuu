package com.example.moattravel.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class HouseRegisterForm {
    @NotBlank(message = "民宿名を入力してください。")
    private String name;

    /* MultipartFile は、ブラウザから送られてきた「画像のバイナリデータ（0と1の塊）」を、Javaの世界で扱えるように包んでくれている配送トラックのような存在です。
     役割: アップロードされたファイルの「中身（バイト列）」「ファイル名」「サイズ」「形式」を一時的に保持します。
     */
    private MultipartFile imageFile;

    @NotBlank(message = "説明を入力してください。")
    private String description;

    @NotNull(message = "宿泊料金を入力してください。")
    @Min(value = 1, message = "宿泊料金は1円以上に設定してください。")
    private Integer price;

    @NotNull(message = "定員を入力してください。")
    @Min(value = 1, message = "定員は1人以上に設定してください。")
    private Integer capacity;

    @NotBlank(message = "郵便番号を入力してください。")
    private String postalCode;

    @NotBlank(message = "住所を入力してください。")
    private String address;

    @NotBlank(message = "電話番号を入力してください。")
    private String phoneNumber;
}
