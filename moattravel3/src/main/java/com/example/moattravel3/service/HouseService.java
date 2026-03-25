	package com.example.moattravel3.service;
	
import java.io.IOException;
import java.nio.file.Files; // ファイル操作（コピーなど）を行うためのクラスをインポート
import java.nio.file.Path; // ファイルのパス（住所）を扱うためのクラスをインポート
import java.nio.file.Paths; // パスを生成するためのユーティリティクラスをインポート
import java.util.UUID; // ランダムなID（ユニークな名前）を生成するためのクラスをインポート

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.moattravel3.entity.House;
import com.example.moattravel3.form.HouseEditForm;
import com.example.moattravel3.form.HouseRegisterForm;
import com.example.moattravel3.repository.HouseRepository;
	
	@Service
	public class HouseService {
	    private final HouseRepository houseRepository;
	
	    public HouseService(HouseRepository houseRepository) {
	        this.houseRepository = houseRepository;
	    }
	
	    @Transactional 
	    public void create(HouseRegisterForm houseRegisterForm) {
	        House house = new House();
	        
	        MultipartFile imageFile = houseRegisterForm.getImageFile(); 
	
	        if (!imageFile.isEmpty()) {
	            String imageName = imageFile.getOriginalFilename(); // 画像の元の名前（例: "shiba.jpg"）を取得
	            String hashedImageName = generateNewFileName(imageName); // 自作メソッドで名前をランダムな英数字に変換（例: "uuid.jpg"）
	           
	         
	            Path filePath = Paths.get("src/main/resources/static/storage/" + hashedImageName);
	            copyImageFile(imageFile, filePath); 
	            house.setImageName(hashedImageName); 
	        }
	        
	
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
	       
	        House house = houseRepository.getReferenceById(houseEditForm.getId());
	        
	        
	        MultipartFile imageFile = houseEditForm.getImageFile();
	        
	        if (!imageFile.isEmpty()) {
	            String imageName = imageFile.getOriginalFilename(); 
	            String hashedImageName = generateNewFileName(imageName);
	            Path filePath = Paths.get("src/main/resources/static/storage/" + hashedImageName);
	            
	            copyImageFile(imageFile, filePath);
	            
	           
	            house.setImageName(hashedImageName);
	        }
	        
	        
	        house.setName(houseEditForm.getName());                
	        house.setDescription(houseEditForm.getDescription());
	        house.setPrice(houseEditForm.getPrice());
	        house.setCapacity(houseEditForm.getCapacity());
	        house.setPostalCode(houseEditForm.getPostalCode());
	        house.setAddress(houseEditForm.getAddress());
	        house.setPhoneNumber(houseEditForm.getPhoneNumber());
	        
	        
	        houseRepository.save(house);
	    }
	   
	    public String generateNewFileName(String fileName) {
	    	
	        String[] fileNames = fileName.split("\\.");
	        
	        for (int i = 0; i < fileNames.length - 1; i++) { // 拡張子以外の部分を処理するためのループ
	            fileNames[i] = UUID.randomUUID().toString(); 
	        }
	        
	        
	        String hashedFileName = String.join(".", fileNames);
	        return hashedFileName;
	    }
	
	   
	    public void copyImageFile(MultipartFile imageFile, Path filePath) {
	        try {
	            Files.copy(imageFile.getInputStream(), filePath);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	        
	        
	    }
	    
	    
	    
	    
	    
	    
	}