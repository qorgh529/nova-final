package com.nova.approval;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;

/**
 * Postgres + MinIO 컨테이너를 띄우는 통합 테스트 베이스.
 * (CI: GitHub Actions ubuntu-latest에는 Docker가 있어 Testcontainers가 동작한다.)
 */
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16").withDatabaseName("nova");

    static final MinIOContainer MINIO =
        new MinIOContainer("minio/minio:RELEASE.2024-06-13T22-53-53Z");

    static final String BUCKET = "nova-test";

    static {
        POSTGRES.start();
        MINIO.start();
        createBucket();
    }

    private static void createBucket() {
        try (S3Client s3 = S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("nova.jwt.secret", () -> "integration-test-secret-key-change-32bytes!!");

        registry.add("nova.storage.driver", () -> "s3");
        registry.add("nova.storage.bucket", () -> BUCKET);
        registry.add("nova.storage.s3.endpoint", MINIO::getS3URL);
        registry.add("nova.storage.s3.region", () -> "us-east-1");
        registry.add("nova.storage.s3.access-key", MINIO::getUserName);
        registry.add("nova.storage.s3.secret-key", MINIO::getPassword);
        registry.add("nova.storage.s3.path-style", () -> "true");
    }
}
