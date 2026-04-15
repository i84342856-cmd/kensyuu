package com.example.cryptotool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// ▼ ここに (exclude = ...) を追加します
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CryptoToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoToolApplication.class, args);
    }
}