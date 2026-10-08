package com.nova.approval.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 공개 버전 엔드포인트. 전환 후 "클린 파이프라인이 서명한 이미지가 돈다"를 화면으로 증명한다.
 * 이미지 digest는 앱이 스스로 알 수 없으므로 배포 매니페스트에서 IMAGE_DIGEST로 주입한다.
 */
@RestController
public class VersionController {

    private final BuildProperties build;
    private final GitProperties git;
    private final String cloud;
    private final String imageDigest;

    public VersionController(@Nullable BuildProperties build,
                             @Nullable GitProperties git,
                             @Value("${nova.runtime.cloud:local}") String cloud,
                             @Value("${nova.runtime.image-digest:unknown}") String imageDigest) {
        this.build = build;
        this.git = git;
        this.cloud = cloud;
        this.imageDigest = imageDigest;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", build != null ? build.getName() : "nova-approval-api");
        out.put("version", build != null ? build.getVersion() : "dev");
        out.put("commit", git != null ? git.getShortCommitId() : "unknown");
        out.put("buildTime", build != null && build.getTime() != null ? build.getTime().toString() : "unknown");
        out.put("imageDigest", imageDigest);
        out.put("cloud", cloud);
        return out;
    }
}
