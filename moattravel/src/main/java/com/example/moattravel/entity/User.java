package com.example.moattravel.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "furigana")
    private String furigana;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "address")
    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role; // Roleエンティティクラスのオブジェクトを定義、"role_id"　JoinColumnする。
    
    /* @JoinColumn(name = "role_id") というアノテーションが、橋渡し（翻訳）の役割をしています。
		データの読み込み時:
		Spring Data JPAがDBからユーザー情報を取ってくる際、role_id が 1 であることを見つけます。

		自動的な検索:
		JPAは「おっと、role_id は Role エンティティと紐付いているな」と判断し、JPAは「users テーブルの role_id に入っている番号を使って、
		roles テーブルの id を探しに行けばいいんだな」と完璧に理解できるのです。裏側で勝手に SELECT * FROM roles WHERE id = 1 を実行します。

		合体:
		取得したRoleテーブルの情報を Role オブジェクトとして組み立て、User オブジェクトの role フィールドに「ガッチャンコ」とはめ込みます。
     */
    
    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Timestamp updatedAt;
}
