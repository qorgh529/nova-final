package com.nova.approval;

import com.nova.approval.TestStorageConfig.InMemoryStorage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestStorageConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApprovalE2EIntegrationTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InMemoryStorage storage;

    // DevSeed: admin=1, approver1=2, approver2=3, emp1=4

    private String login(String loginId) {
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/login",
            Map.of("loginId", loginId, "password", "password"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("token");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @Order(1)
    void leaveApprovalFlowEndToEnd() {
        String emp = login("emp1");

        // 휴가 신청 작성 (결재자 2명: approver1, approver2)
        Map<String, Object> body = Map.of(
            "type", "LEAVE",
            "title", "연차 신청",
            "approverIds", List.of(2, 3),
            "leave", Map.of("leaveType", "ANNUAL", "startDate", "2026-10-20", "endDate", "2026-10-21"));
        ResponseEntity<Map> created = rest.exchange("/api/documents", HttpMethod.POST,
            new HttpEntity<>(body, bearer(emp)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Integer docId = (Integer) created.getBody().get("id");
        assertThat(docId).isNotNull();
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");

        // 상신
        Map submitted = post(docId, "submit", emp);
        assertThat(submitted.get("status")).isEqualTo("SUBMITTED");

        // 1단계 승인(approver1) → 아직 SUBMITTED
        Map afterStep1 = post(docId, "approve", login("approver1"));
        assertThat(afterStep1.get("status")).isEqualTo("SUBMITTED");

        // 2단계 승인(approver2) → APPROVED
        Map afterStep2 = post(docId, "approve", login("approver2"));
        assertThat(afterStep2.get("status")).isEqualTo("APPROVED");

        // 이력 3건 (SUBMIT, APPROVE, APPROVE)
        List<?> history = (List<?>) afterStep2.get("history");
        assertThat(history).hasSize(3);

        // 관리자 무결성 검증 → 정상
        ResponseEntity<Map> integrity = rest.exchange("/api/admin/integrity", HttpMethod.GET,
            new HttpEntity<>(bearer(login("admin"))), Map.class);
        assertThat(integrity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(integrity.getBody().get("valid")).isEqualTo(true);
    }

    @Test
    @Order(2)
    void wrongApproverIsForbidden() {
        String emp = login("emp1");
        Map<String, Object> body = Map.of(
            "type", "APPROVAL", "title", "권한 확인", "approverIds", List.of(2));
        ResponseEntity<Map> created = rest.exchange("/api/documents", HttpMethod.POST,
            new HttpEntity<>(body, bearer(emp)), Map.class);
        Integer docId = (Integer) created.getBody().get("id");
        post(docId, "submit", emp);

        // approver2(3)는 이 문서의 결재자가 아니다 → 403
        ResponseEntity<Map> res = rest.exchange("/api/documents/" + docId + "/approve", HttpMethod.POST,
            new HttpEntity<>(bearer(login("approver2"))), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void attachmentUploadAndDownloadRoundTrip() {
        String emp = login("emp1");
        Map<String, Object> body = Map.of(
            "type", "APPROVAL", "title", "첨부 테스트", "approverIds", List.of(2));
        ResponseEntity<Map> created = rest.exchange("/api/documents", HttpMethod.POST,
            new HttpEntity<>(body, bearer(emp)), Map.class);
        Integer docId = (Integer) created.getBody().get("id");

        byte[] content = "hello nova attachment".getBytes(StandardCharsets.UTF_8);
        String expectedSha = HexFormat.of().formatHex(sha256(content));

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(emp);
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        ByteArrayResource file = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "note.txt";
            }
        };
        form.add("file", file);

        ResponseEntity<Map> up = rest.exchange("/api/documents/" + docId + "/attachments",
            HttpMethod.POST, new HttpEntity<>(form, uploadHeaders), Map.class);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(up.getBody().get("sha256")).isEqualTo(expectedSha);

        // 스토리지에 실제 바이트가 저장됐는지 확인
        assertThat(storage.objects.values()).anyMatch(b -> java.util.Arrays.equals(b, content));

        // 문서 상세에 첨부가 보이고 sha256이 일치한다
        ResponseEntity<Map> detail = rest.exchange("/api/documents/" + docId, HttpMethod.GET,
            new HttpEntity<>(bearer(emp)), Map.class);
        List<?> atts = (List<?>) detail.getBody().get("attachments");
        assertThat(atts).hasSize(1);
        assertThat(((Map<?, ?>) atts.get(0)).get("sha256")).isEqualTo(expectedSha);
    }

    @Test
    @Order(99)
    void tamperedHashChainIsDetected() {
        // DB에서 가장 오래된 이력 행의 hash를 조작한다 (DB 권한을 가진 공격자 가정)
        jdbc.update("UPDATE approval_history SET hash = ? WHERE seq = (SELECT MIN(seq) FROM approval_history)",
            "0".repeat(64));

        ResponseEntity<Map> integrity = rest.exchange("/api/admin/integrity", HttpMethod.GET,
            new HttpEntity<>(bearer(login("admin"))), Map.class);
        assertThat(integrity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(integrity.getBody().get("valid")).isEqualTo(false);
        assertThat(integrity.getBody().get("reason")).isNotNull();
    }

    private Map post(Integer docId, String action, String token) {
        ResponseEntity<Map> res = rest.exchange("/api/documents/" + docId + "/" + action,
            HttpMethod.POST, new HttpEntity<>(bearer(token)), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
