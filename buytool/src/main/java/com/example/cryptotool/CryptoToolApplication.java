package com.example.cryptotool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CryptoToolApplication {

/* プロジェクトをクリーン＆ビルド

プロジェクトを右クリック ＞ 「実行」 ＞ 「Maven ビルド...」

ゴール：clean install -DskipTests で実行。

サーバーへ転送

完成した target/-1-0.0.1-SNAPSHOT.jar をWinSCPでサーバーへ送り、buytool.jar に名前変更して上書き。

本番モードで起動

Tera Termで以下のコマンドを実行。

*/
	
    public static void main(String[] args) {
        SpringApplication.run(CryptoToolApplication.class, args);
    }
}