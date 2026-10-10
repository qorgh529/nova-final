package com.nova.approval.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 데모 기능(대량 시드, 데이터 생성기)은 운영 코드와 섞이지 않게 demo 프로파일에서만 켠다.
 */
@Configuration
@Profile("demo")
@EnableScheduling
@EnableConfigurationProperties(DemoProperties.class)
public class DemoConfig {
}
