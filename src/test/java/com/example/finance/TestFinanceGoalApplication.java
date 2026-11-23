package com.example.finance;

import org.springframework.boot.SpringApplication;

/**
 * 免裝 MySQL 的預覽模式：用 H2 記憶體資料庫啟動（關掉就清空）。
 * 執行：./mvnw spring-boot:test-run
 */
public class TestFinanceGoalApplication {

    public static void main(String[] args) {
        SpringApplication.from(FinanceGoalApplication::main).withAdditionalProfiles("test").run(args);
    }
}
