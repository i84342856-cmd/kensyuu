package com.example.moattravel.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.moattravel.entity.House;
import com.example.moattravel.form.HouseEditForm;
import com.example.moattravel.form.HouseRegisterForm;
import com.example.moattravel.repository.HouseRepository;
import com.example.moattravel.service.HouseService;

@Controller
@RequestMapping("/admin/houses")

public class AdminHouseController {

	/*　今回の方法はコンストラクタ注入という。フィールドにfinal定義しておき、
	 * コンストラクタで注入するというもの。
	 * より簡単に書く（コンストラクタの省略）には、
	 * @Controller
	 * @RequiredArgsConstructor // これでコンストラクタを自動作成
	 * public class UserController {
	private final UserService userService; // finalで安全性を確保
	}
	
	 Alt + Shift R = リファクタリング
	 Ctrl + Shift F = 整地
	 */

	private final HouseRepository houseRepository;
	private final HouseService houseService;

	@Autowired
	// コンストラクタインジェクションを使う場合、@Autowired必要。
	// 今回のようにコンストラクタが1つしかない場合は以下のように省略が可能です。
	public AdminHouseController(HouseRepository houseRepository, HouseService houseService) {
		this.houseRepository = houseRepository;
		this.houseService = houseService;
	}

	@GetMapping
	public String index(Model model,
			@PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
			@RequestParam(name = "keyword", required = false) String keyword) {
		List<House> houses = houseRepository.findAll();
		Page<House> housePage = houseRepository.findAll(pageable);

		if (keyword != null && !keyword.isEmpty()) {
			housePage = houseRepository.findByNameLike("%" + keyword + "%", pageable);
		} else { //"%" + "侍" + "%"→ 「前に何があってもいい + 侍 + 後ろに何があってもいい」
			housePage = houseRepository.findAll(pageable);
		}

		model.addAttribute("houses", houses);
		model.addAttribute("housePage", housePage);
		model.addAttribute("keyword", keyword);
		return "admin/houses/index";
	}

	@GetMapping("/{id}")
	public String show(@PathVariable(name = "id") Integer id, Model model) {
		House house = houseRepository.findById(id).orElseThrow(() -> new RuntimeException("指定された民宿が見つかりません。"));
		model.addAttribute("house", house);
		return "admin/houses/show";
	}

	@GetMapping("/register")
	public String register(Model model) {
		model.addAttribute("houseRegisterForm", new HouseRegisterForm());
		return "admin/houses/register";
	}

	@PostMapping("/create")
	public String create(@ModelAttribute @Validated HouseRegisterForm houseRegisterForm, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "admin/houses/register";
		}
		houseService.create(houseRegisterForm);

		// リダイレクト（画面移動）先のページへ、「成功メッセージ」を預ける
		// 通常のModelと違い、リダイレクト後の新しいページでもこのメッセージを受け取ることができる
		redirectAttributes.addFlashAttribute("successMessage", "民宿を登録しました。");

		//ブラウザに対して「/admin/houses（一覧画面）を読み込み直して」と命令を出す
		// 直接その画面を表示するのではなく、URLを切り替えさせることで「ブラウザ更新による二重登録」を防ぐ
		return "redirect:/admin/houses";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable(name = "id") Integer id, Model model) {
		House house = houseRepository.getReferenceById(id);

		// 現在登録されている画像名を取得（画面表示用）
		String imageName = house.getImageName();

		// ※ MultipartFile（画像ファイル）は新規アップロード用なので、ここでは null を渡す
		// これはコンストラクタだけでフィールドをセットしようとしている。
		// そのためにHouseEditFormでは「@AllArgsConstructor」をつけて、コンストラクタの定義を省略している。
		// 通常のセットの場合では、new()生成した後に、「houseEditForm.name = house.setName()」と書かないといけない。
		HouseEditForm houseEditForm = new HouseEditForm(
				house.getId(),
				house.getName(),
				null,
				house.getDescription(),
				house.getPrice(),
				house.getCapacity(),
				house.getPostalCode(),
				house.getAddress(),
				house.getPhoneNumber());

		model.addAttribute("houseEditForm", houseEditForm);
		model.addAttribute("imageName", imageName);

		return "admin/houses/edit";

	}

	@PostMapping("/{id}/update")
	public String update(
			@ModelAttribute @Validated HouseEditForm houseEditForm,
			BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		// 1. 入力チェック（バリデーション）に引っかかった場合、編集画面に戻す
		if (bindingResult.hasErrors()) {
			return "admin/houses/edit";
		}

		// 2. サービス層に処理を依頼してDBを更新する
		houseService.update(houseEditForm);

		// 3. 一時的な成功メッセージをセット
		redirectAttributes.addFlashAttribute("successMessage", "民宿情報を編集しました。");

		// 4. 一覧画面へリダイレクト
		return "redirect:/admin/houses";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable(name = "id") Integer id, RedirectAttributes redirectAttributes) {
		houseRepository.deleteById(id);
		redirectAttributes.addFlashAttribute("successMessage", "民宿を削除しました。");
		return "redirect:/admin/houses";
	}

}
