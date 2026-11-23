package com.example.finance;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 「今天」一律用台北時間；測試可以換成固定時間的 Clock。 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
