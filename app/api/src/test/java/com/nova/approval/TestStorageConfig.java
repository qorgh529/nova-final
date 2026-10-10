package com.nova.approval;

import com.nova.approval.storage.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 테스트용 인메모리 스토리지. 외부 오브젝트 스토리지 컨테이너 없이 업로드 경로를 검증한다.
 */
@TestConfiguration
public class TestStorageConfig {

    public static class InMemoryStorage implements StorageService {
        public final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String key, byte[] content, String contentType) {
            objects.put(key, content);
        }

        @Override
        public String presignedGetUrl(String key, Duration ttl) {
            return "mem://" + key;
        }
    }

    @Bean
    InMemoryStorage storageService() {
        return new InMemoryStorage();
    }
}
