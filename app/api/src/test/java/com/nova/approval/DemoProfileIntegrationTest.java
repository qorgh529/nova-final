package com.nova.approval;

import com.nova.approval.TestStorageConfig.InMemoryStorage;
import com.nova.approval.attachment.AttachmentService;
import com.nova.approval.demo.DemoDataGenerator;
import com.nova.approval.domain.Document;
import com.nova.approval.domain.DocumentStatus;
import com.nova.approval.history.ApprovalHistoryService;
import com.nova.approval.history.ChainVerifyCommand;
import com.nova.approval.repo.ApprovalHistoryRepository;
import com.nova.approval.repo.DocumentRepository;
import com.nova.approval.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * demo 프로파일(시드·생성기), 체인 검증 CLI, 권한 점검 SQL을 실제 Postgres로 검증한다.
 * 해시 체인을 일부러 망가뜨리는 단계가 있어서 다른 통합 테스트와 DB를 공유하지 않도록 컨테이너를 따로 띄운다.
 */
@SpringBootTest(properties = {
    "nova.demo.seed.users=10",
    "nova.demo.seed.documents=40",
    // 테스트 중 자동 실행되지 않게 주기를 길게 두고 generateOne()을 직접 호출한다
    "nova.demo.generator.interval=PT1H",
})
@ActiveProfiles("demo")
@Import(TestStorageConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoProfileIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16").withDatabaseName("nova_demo");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nova.jwt.secret", () -> "integration-test-secret-key-change-32bytes!!");
        registry.add("nova.storage.driver", () -> "memory");
        registry.add("nova.storage.bucket", () -> "nova-test");
    }

    @Autowired
    UserRepository users;

    @Autowired
    DocumentRepository documents;

    @Autowired
    ApprovalHistoryService history;

    @Autowired
    ApprovalHistoryRepository historyRepo;

    @Autowired
    DemoDataGenerator generator;

    @Autowired
    InMemoryStorage storage;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @Order(1)
    void seedCreatesUsersDocumentsInEveryStateAndEicarAttachment() {
        assertThat(users.findByLoginId("demo01")).isPresent();
        assertThat(users.findByLoginId("demo10")).isPresent();
        // 시드 문서 40건 + EICAR 첨부 문서 1건
        assertThat(documents.count()).isEqualTo(41);

        Set<DocumentStatus> statuses = documents.findAll().stream()
            .map(Document::getStatus).collect(Collectors.toSet());
        assertThat(statuses).containsExactlyInAnyOrder(DocumentStatus.values());

        // EICAR 표준 테스트 파일의 SHA-256 (공개된 값)
        String eicarSha = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f";
        assertThat(storage.objects.values())
            .anyMatch(b -> AttachmentService.sha256Hex(b).equals(eicarSha));

        ApprovalHistoryService.IntegrityResult chain = history.verify();
        assertThat(chain.valid()).isTrue();
        assertThat(chain.count()).isPositive();
    }

    @Test
    @Order(2)
    void generatorCreatesFullyApprovedDocumentAndKeepsChainValid() {
        long before = history.verify().count();

        Long id = generator.generateOne().orElseThrow();

        Document doc = documents.findById(id).orElseThrow();
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(doc.getTitle()).startsWith("[GEN]");
        ApprovalHistoryService.IntegrityResult chain = history.verify();
        assertThat(chain.valid()).isTrue();
        // 상신 1 + 승인 1~2
        assertThat(chain.count() - before).isBetween(2L, 3L);
    }

    @Test
    @Order(3)
    void adminCheckSqlPassesOnCleanDataAndFlagsUnapprovedAdmin() throws Exception {
        POSTGRES.copyFileToContainer(
            MountableFile.forHostPath(Path.of("../../scripts/db/check-admin-accounts.sql")),
            "/tmp/check-admin-accounts.sql");

        Container.ExecResult clean = runAdminCheck();
        assertThat(clean.getExitCode()).as(clean.getStdout() + clean.getStderr()).isZero();

        // 모의 백도어가 남기는 것과 같은 숨은 관리자 계정
        jdbc.update("INSERT INTO users (login_id, password_hash, name) VALUES ('svc-sync', 'x', 'svc')");
        jdbc.update("INSERT INTO user_roles (user_id, role) SELECT id, 'ADMIN' FROM users WHERE login_id = 'svc-sync'");

        Container.ExecResult flagged = runAdminCheck();
        assertThat(flagged.getExitCode()).isEqualTo(3);
        assertThat(flagged.getStdout()).contains("svc-sync").contains("승인 명단에 없음");
        assertThat(flagged.getStdout()).doesNotContain("| admin ");
    }

    private Container.ExecResult runAdminCheck() throws Exception {
        return POSTGRES.execInContainer(StandardCharsets.UTF_8,
            "psql", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
            "-v", "ON_ERROR_STOP=1",
            "-v", "approved_admins=admin",
            "-v", "compromised_at=2999-01-01T00:00:00Z",
            "-f", "/tmp/check-admin-accounts.sql");
    }

    @Test
    @Order(4)
    void chainVerifyCommandChecksExpectedHeadAndDetectsTampering() {
        // 러너(run)는 System.exit을 부르므로 테스트에서는 검증 로직(verify)만 직접 호출한다
        ChainVerifyCommand cli = new ChainVerifyCommand(history, historyRepo, null, null, "");

        String head = history.verify().headHash();
        String older = jdbc.queryForObject("SELECT hash FROM approval_history WHERE seq = 5", String.class);
        assertThat(cli.verify(null).exitCode()).isEqualTo(ChainVerifyCommand.OK);
        assertThat(cli.verify(head).expectedHeadFound()).isTrue();
        assertThat(cli.verify(older.toUpperCase()).exitCode()).isEqualTo(ChainVerifyCommand.OK);
        assertThat(cli.verify("f".repeat(64)).exitCode()).isEqualTo(ChainVerifyCommand.EXPECTED_HEAD_MISSING);

        // 이력 한 행의 결재자를 바꿔치기한다
        jdbc.update("UPDATE approval_history SET actor_id = 1 WHERE seq = 3");
        ChainVerifyCommand.Report tampered = cli.verify(head);
        assertThat(tampered.exitCode()).isEqualTo(ChainVerifyCommand.CHAIN_BROKEN);
        assertThat(tampered.chain().brokenAtSeq()).isEqualTo(3L);
    }
}
