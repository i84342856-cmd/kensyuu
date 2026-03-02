package com.example.trainingspring.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.trainingspring.dto.UserRequest;
import com.example.trainingspring.dto.UserSearchRequest;
import com.example.trainingspring.dto.UserUpdateRequest;
import com.example.trainingspring.entity.User;
import com.example.trainingspring.service.UserService;

/* Model構成
 * 「user」テーブルのエンティティ「User」クラス
 * 「user」クラスの情報を入れたリポジトリ「userRepository」クラス
 * 「userRepository」クラスを@Autowired 注入した、検索などのロジックモデル「userService」クラス
 * 「add.html」画面からインプットで読み込んだ情報を指定の要件でエラーとして処理する「UserRequest」クラス
 * 
 * Contoroller 処理について
 * ① UserServiceクラスをオブジェクト注入する
 * ②「/user/list」のgetリクエストがあったら、
 * 「userServie」の「AllFind」で取得した<User>リストを添えて「list.html」表示
 * ※ 「list.html」で「新規登録」をactionした場合、「/user/add」Getリクエスト
 * ③ 「user/add」Getリクエストがあったら、UserRequestクラスをnew 生成してModelに入れて、「add.html」表示
 * ※ 「add.html」で登録情報のaction があると、「/user/create」Postリクエスト
 * ④ 「/user/create」ポストがあった場合、UserRequestクラスのチェックルール実行
 * 　エラーがあったら、指示文をString配列に入れて、modelを経由して、「add.html」を再度表示
 * 　エラーがなければ、「userService」オブジェクトのcreatメソッドに「userRequest」のフィールドを引数にして、
 * 　new Userクラスで生成したインスタンスのフィールドにnameなどをセットする。それをUserRepositoryで保存する。
 * 　「user/list.html」に「redirect」（再度読み込み）して表示する。
 * ⑤ 「list.html」で最新のテーブルを「AllFind」したリスト<User>が表示される。
 * ※ 「list.html」で「詳細」をactionすると、GetMapping("/user/{id}")になる。
 * ⑥　GetMapping("/user/{id}")になると、new Userを生成して、
 * 　　userService.findById(id)で該当するUserエンティティをmodel経由して、「user/view.html」表示
 * ⑦ GetMapping("/user/search") がリクエストされたら、 UserSearchRequest（検索窓の箱）を生成して Model に入れ、
 *   「user/search.html」を表示する。
 * ⑧ PostMapping("/user/id_search") がリクエスト（検索実行）されたら、
 *   入力されたIDを保持した userSearchRequest を「userService」の search メソッドへ渡す。
 *   内部では UserMapper を経由してDBから該当ユーザーを取得し、
 *   その結果（Userエンティティ）を userinfo という名前で Model に入れて、再度 「user/search.html」 を表示する。
 */

@Controller
public class UserController {
	/**
	   * ユーザー情報 Service
	   */
	  @Autowired
	  private UserService userService;

	  /**
	   * ユーザー情報一覧画面を表示
	   * @param model Model
	   * @return ユーザー情報一覧画面
	   */
	  @GetMapping("/user/list")
	  public String displayList(Model model) {
	    List<User> userlist = userService.searchAll();
	    model.addAttribute("userlist", userlist);
	    return "user/list";
	  }

	  /**
	   * ユーザー新規登録画面を表示
	   * @param model Model
	   * @return ユーザー情報一覧画面
	   */
	  @GetMapping("/user/add")
	// 引数に書くだけで、newとaddAttributeを自動でやってくれる
	  public String displayAdd(@ModelAttribute UserRequest userRequest) {
		  // 上記でこの機能も実装　model.addAttribute("userRequest", new UserRequest());
	      return "user/add";
	  }

	  /**
	   * ユーザー新規登録
	   * @param userRequest リクエストデータ
	   * @param model Model
	   * @return ユーザー情報一覧画面
	   */
	  @PostMapping("/user/create")
	  public String create(@Validated @ModelAttribute UserRequest userRequest, BindingResult result, Model model) {
		 // @Validated: UserRequestクラスに書いた「@NotEmpty」などのチェックルールを今すぐ実行
		 // BindingResult result: 入力チェックの結果（エラーがあったかどうか等）がこの変数に自動的に格納,
		 // 必ず@Validatedのすぐ後ろに書くルール
		  
	    if (result.hasErrors()) {
	      // エラーメッセージを格納するためのリストを作成
	      List<String> errorList = new ArrayList<String>();
	      
	     // 発生した全てのエラー（ObjectError）をループで取り出す
	      for (ObjectError error : result.getAllErrors()) {
	        errorList.add(error.getDefaultMessage());
	      }
	      model.addAttribute("validationError", errorList);
	      return "user/add";
	    }
	    // ユーザー情報の登録
	    userService.create(userRequest);
	    return "redirect:/user/list";
	  }

	  /**
	   * ユーザー情報詳細画面を表示
	   * @param id 表示するユーザーID
	   * @param model Model
	   * @return ユーザー情報詳細画面
	   */
	  @GetMapping("/user/{id}")
	  public String displayView(@PathVariable Long id, Model model) {
		  User user = userService.findById(id);
		    model.addAttribute("userData", user);
		    return "user/view";
	  }
	  
	  /**
	   * ユーザー編集画面を表示
	   * @param id 表示するユーザーID
	   * @param model Model
	   * @return ユーザー編集画面
	   */
	  @GetMapping("/user/{id}/edit")
	  public String displayEdit(@PathVariable Long id, Model model) {
	    User user = userService.findById(id);
	    UserUpdateRequest userUpdateRequest = new UserUpdateRequest();
	    userUpdateRequest.setId(user.getId());
	    userUpdateRequest.setName(user.getName());
	    userUpdateRequest.setPhone(user.getPhone());
	    userUpdateRequest.setAddress(user.getAddress());
	    model.addAttribute("userUpdateRequest", userUpdateRequest);
	    return "user/edit";
	  }
	  
	  /**
	   * ユーザー更新
	   * @param userRequest リクエストデータ
	   * @param model Model
	   * @return ユーザー情報詳細画面
	   */
	  @PostMapping("/user/update")
	  public String update(@Validated @ModelAttribute UserUpdateRequest userUpdateRequest, BindingResult result, Model model) {

	    if (result.hasErrors()) {
	      List<String> errorList = new ArrayList<String>();

	      for (ObjectError error : result.getAllErrors()) {
	        errorList.add(error.getDefaultMessage());
	      }
	      model.addAttribute("validationError", errorList);
	      return "user/edit";
	    }

	    // ユーザー情報の更新
	    userService.update(userUpdateRequest);
	    return String.format("redirect:/user/%d", userUpdateRequest.getId());
	  }
	  
	  /**
	   * ユーザー情報検索画面を表示
	   * @param model Model
	   * @return ユーザー情報一覧画面
	   */
	  @GetMapping("/user/search")
	  public String displaySearch(Model model) {
	    model.addAttribute("userSearchRequest", new UserSearchRequest());
	    // new生成を省略する場合
	    // public String displaySearch(@ModelAttribute("変数名") UserSearchRequest userSearchRequest) {
	    return "user/search";
	  }

	  /**
	   * ユーザー情報検索
	   * @param userSearchRequest リクエストデータ
	   * @param model Model
	   * @return ユーザー情報一覧画面
	   */
	  @PostMapping("/user/id_search")
	  public String search(@ModelAttribute UserSearchRequest userSearchRequest, Model model) {
	    User user = userService.search(userSearchRequest);
	    model.addAttribute("userinfo", user);
	    return "user/search";
	  }
	  
	  /**
	     * ユーザー情報 論理削除の実行
	     * @param id 削除するユーザーID
	     * @return ユーザー情報一覧画面へリダイレクト
	     */
	    @GetMapping("/user/{id}/delete")
	    public String delete(@PathVariable Long id) {
	        // Serviceの削除処理を呼び出す
	        userService.delete(id);
	        // 削除後は一覧画面にリダイレクト
	        return "redirect:/user/list";
	    }
	}
