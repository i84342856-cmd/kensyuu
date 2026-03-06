package com.example.trainingspring.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * ユーザー情報 検索用リクエストデータ
 */
@Data
public class UserSearchRequest implements Serializable {

  /**
   * ユーザーID
   */
  private Long id;
}
