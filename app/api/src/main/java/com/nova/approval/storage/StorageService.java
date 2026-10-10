package com.nova.approval.storage;

import java.time.Duration;

/**
 * 오브젝트 스토리지 추상화. 클라우드 SDK는 이 구현체 안에서만 쓴다 (앱 설계 원칙 2).
 * 드라이버는 nova.storage.driver 로 고른다: s3 (AWS/MinIO) | gcs.
 */
public interface StorageService {

    /** 객체를 저장한다. */
    void put(String key, byte[] content, String contentType);

    /** 다운로드용 서명 URL을 만든다. */
    String presignedGetUrl(String key, Duration ttl);
}
