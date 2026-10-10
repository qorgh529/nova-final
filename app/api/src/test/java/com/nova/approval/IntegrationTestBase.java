package com.nova.approval;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres 컨테이너를 띄우는 통합 테스트 베이스.
 * (CI: GitHub Actions ubuntu-latest에는 Docker가 있어 Testcontainers가 동작한다.)
 * 오브젝트 스토리지는 외부 이미지 의존성과 플래키함을 피하려고 인메모리 더블(TestStorageConfig)로 대체한다.
 */
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16").withDatabaseName("nova");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("nova.jwt.secret", () -> "integration-test-secret-key-change-32bytes!!");

        // S3 자동구성을 끄고 인메모리 스토리지 더블을 쓴다
        registry.add("nova.storage.driver", () -> "memory");
        registry.add("nova.storage.bucket", () -> "nova-test");
    }
}
