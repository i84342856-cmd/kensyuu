package com.example.trainingspring.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.trainingspring.dto.UserRequest;
import com.example.trainingspring.dto.UserSearchRequest;
import com.example.trainingspring.dto.UserUpdateRequest;
import com.example.trainingspring.entity.User;
import com.example.trainingspring.repository.UserRepository;

/**
 * ユーザー情報 Service
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserService {
  /**
   * ユーザー情報 Repository
   */
  @Autowired
  private UserRepository userRepository;

  /**
   * ユーザー情報 全検索
   * @return 検索結果
   */
  public List<User> searchAll() {
 // 従来の「userRepository.findAll()」を先ほど作ったメソッドに変更
    return userRepository.findAllByDeleteDateIsNull();
  }
  
  /**
   * ユーザー情報 主キー検索
   * @return 検索結果
   */
  public User findById(Long id) {
    return userRepository.findById(id).get();
  }
  
  /**
   * ユーザー情報 新規登録
   * @param user ユーザー情報
   */
  public void create(UserRequest userRequest) {
    Date now = new Date();
    User user = new User();
// エンティティクラスは「データを取ってくるためだけのもの」ではなく、「新しいデータを作るための型」
    // userRequestには「lombok.Data」がインポートしているため、getフィールドが使用できる。
    // Userエンティティのフィールド（インスタンスごとに１行）にuserRequestの値を入れる。
    user.setName(userRequest.getName());
    user.setAddress(userRequest.getAddress());
    user.setPhone(userRequest.getPhone());
    user.setCreateDate(now);
    user.setUpdateDate(now);
    userRepository.save(user);
    // repository.save(user);（ここでSQLの INSERT 文が自動生成され、DBに書き込まれる）
  }
  
  /**
   * ユーザー情報 更新
   * @param user ユーザー情報
   */
 public void update(UserUpdateRequest userUpdateRequest) {
   User user = findById(userUpdateRequest.getId());
   user.setAddress(userUpdateRequest.getAddress());
   user.setName(userUpdateRequest.getName());
   user.setPhone(userUpdateRequest.getPhone());
   user.setUpdateDate(new Date());
   userRepository.save(user);
 }

 /**
  * ユーザー情報検索
* @param userSearchRequest リクエストデータ
  * @return 検索結果
  */
 public User search(UserSearchRequest userSearchRequest) {
     return userRepository.findById(userSearchRequest.getId()).orElse(null);
 }
 
 /**
  * ユーザー情報 論理削除
  * @param id ユーザーID
  */
 public void delete(Long id) {
     User user = findById(id);
     user.setDeleteDate(new Date()); // 削除日時をセット
     userRepository.save(user);      // 更新して保存
 }
 
}
