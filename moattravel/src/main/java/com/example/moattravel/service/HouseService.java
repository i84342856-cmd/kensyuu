	package com.example.moattravel.service;
	
import java.io.IOException;
import java.nio.file.Files; // ファイル操作（コピーなど）を行うためのクラスをインポート
import java.nio.file.Path; // ファイルのパス（住所）を扱うためのクラスをインポート
import java.nio.file.Paths; // パスを生成するためのユーティリティクラスをインポート
import java.util.UUID; // ランダムなID（ユニークな名前）を生成するためのクラスをインポート

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.moattravel.entity.House;
import com.example.moattravel.form.HouseEditForm;
import com.example.moattravel.form.HouseRegisterForm;
import com.example.moattravel.repository.HouseRepository;
	
	@Service
	public class HouseService {
	    private final HouseRepository houseRepository;
	
	    public HouseService(HouseRepository houseRepository) {
	        this.houseRepository = houseRepository;
	    }
	
	    @Transactional // このメソッド内のDB操作をひとまとめにする。エラー時はすべて取り消される（ロールバック）
	    public void create(HouseRegisterForm houseRegisterForm) {
	        House house = new House();
	        // フォームから画像データ（ファイル本体＋情報）を取り出す
	        MultipartFile imageFile = houseRegisterForm.getImageFile(); 
	
	        if (!imageFile.isEmpty()) {
	            String imageName = imageFile.getOriginalFilename(); // 画像の元の名前（例: "shiba.jpg"）を取得
	            String hashedImageName = generateNewFileName(imageName); // 自作メソッドで名前をランダムな英数字に変換（例: "uuid.jpg"）
	           
	            // 保存先のフォルダと新しい名前を合体させて「保存先パス」を作る
	            Path filePath = Paths.get("src/main/resources/static/storage/" + hashedImageName);
	            copyImageFile(imageFile, filePath); // 自作メソッドを使って、画像をそのパスにコピー（保存）する
	            house.setImageName(hashedImageName); // 新しく作ったファイル名をHouseインスタンスにセット
	        }
	        
	        /* メソッドの内容
	         　ブラウザ: my_house.jpg のコピーをサーバーに送ります。
	         　サーバー（Service）:imageFile にそのコピーが入ります。
				generateNewFileName で abc-123.jpg という名前を新しく作ります。
				storage フォルダの中に、新しく abc-123.jpg というファイルを作成し、そこに中身を書き込みます。
				データベース: image_name カラムに abc-123.jpg と記録します。
	         */
	
	        house.setName(houseRegisterForm.getName());
	        house.setDescription(houseRegisterForm.getDescription());
	        house.setPrice(houseRegisterForm.getPrice());
	        house.setCapacity(houseRegisterForm.getCapacity());
	        house.setPostalCode(houseRegisterForm.getPostalCode());
	        house.setAddress(houseRegisterForm.getAddress());
	        house.setPhoneNumber(houseRegisterForm.getPhoneNumber());
	
	        houseRepository.save(house);
	    }
	    
	    @Transactional
	    public void update(HouseEditForm houseEditForm) {
	        // 1. 編集対象となる既存の民宿データを取得
	        House house = houseRepository.getReferenceById(houseEditForm.getId());
	        
	        // 2. 画像ファイルの更新処理（新しいファイルがアップロードされた場合のみ実行）
	        MultipartFile imageFile = houseEditForm.getImageFile();
	        
	        if (!imageFile.isEmpty()) {
	            String imageName = imageFile.getOriginalFilename(); 
	            String hashedImageName = generateNewFileName(imageName);
	            Path filePath = Paths.get("src/main/resources/static/storage/" + hashedImageName);
	            
	            copyImageFile(imageFile, filePath);
	            
	            // 新しい画像名に書き換える
	            house.setImageName(hashedImageName);
	        }
	        
	        // 3. フォームから届いた値をエンティティ（DBの箱）に詰め替える
	        house.setName(houseEditForm.getName());                
	        house.setDescription(houseEditForm.getDescription());
	        house.setPrice(houseEditForm.getPrice());
	        house.setCapacity(houseEditForm.getCapacity());
	        house.setPostalCode(houseEditForm.getPostalCode());
	        house.setAddress(houseEditForm.getAddress());
	        house.setPhoneNumber(houseEditForm.getPhoneNumber());
	        
	        // 4. 変更内容を確定（保存）
	        houseRepository.save(house);
	    }
	    // UUIDを使って生成したファイル名を返す
	    public String generateNewFileName(String fileName) {
	    	// ファイル名を「.（ドット）」で区切って配列にする（例: ["shiba", "jpg"]）
	        String[] fileNames = fileName.split("\\.");
	        
	        for (int i = 0; i < fileNames.length - 1; i++) { // 拡張子以外の部分を処理するためのループ
	            fileNames[i] = UUID.randomUUID().toString(); // ファイル名の部分をランダムな文字列（UUID）に書き換える
	        }
	        
	        // 書き換えた配列を再び「.」で繋ぎ直す
	        String hashedFileName = String.join(".", fileNames);
	        return hashedFileName;
	    }
	
	    // 画像ファイルを指定したファイルにコピーする
	    public void copyImageFile(MultipartFile imageFile, Path filePath) {
	        try {
	            Files.copy(imageFile.getInputStream(), filePath);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	        
	        
	    }
	    
	    
	    
	    
	    
	    
	}