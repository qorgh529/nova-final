package com.nova.approval.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.net.URI;
import java.time.Duration;

/**
 * S3 호환 스토리지 드라이버. AWS S3와 MinIO(로컬) 모두를 커버한다.
 * nova.storage.driver=s3 일 때만 활성화된다.
 */
@Service
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(name = "nova.storage.driver", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageService(StorageProperties props) {
        this.props = props;
        StorageProperties.S3 s3 = props.getS3();
        var creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
        var region = Region.of(s3.getRegion());
        var serviceConfig = S3Configuration.builder()
            .pathStyleAccessEnabled(s3.isPathStyle())
            .build();

        var clientBuilder = S3Client.builder()
            .region(region)
            .credentialsProvider(creds)
            .serviceConfiguration(serviceConfig);
        var presignerBuilder = S3Presigner.builder()
            .region(region)
            .credentialsProvider(creds)
            .serviceConfiguration(serviceConfig);
        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            clientBuilder.endpointOverride(URI.create(s3.getEndpoint()));
            presignerBuilder.endpointOverride(URI.create(s3.getEndpoint()));
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(content));
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        var get = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
            .bucket(props.getBucket())
            .key(key)
            .build();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(get)
            .build());
        return presigned.url().toString();
    }
}
