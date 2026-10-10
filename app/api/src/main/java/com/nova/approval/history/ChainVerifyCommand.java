package com.nova.approval.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.approval.repo.ApprovalHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 체인 검증 CLI. 복원 스크립트가 복원한 DB를 대상으로 실행한다 (verify-chain 프로파일).
 * <p>
 * 체인을 처음부터 재계산해 자체 일관성을 보고, 불변 백업 매니페스트에 기록해 둔 헤드 해시(expected-head)가
 * 체인 안에 있는지도 본다. 체인 전체를 다시 계산해 바꿔치기하면 자체 일관성은 맞출 수 있으므로,
 * 체인 밖(불변 저장소)에 둔 헤드 해시로 고정해야 위변조를 잡을 수 있다.
 * <p>
 * 종료 코드: 0 정상, 10 체인 손상, 11 기대 헤드 해시가 체인에 없음. 기동 실패(DB 접속·스키마 불일치)는 1.
 */
@Component
@ConditionalOnProperty(name = "nova.cli.command", havingValue = "verify-chain")
public class ChainVerifyCommand implements ApplicationRunner {

    public static final int OK = 0;
    public static final int CHAIN_BROKEN = 10;
    public static final int EXPECTED_HEAD_MISSING = 11;

    private final ApprovalHistoryService history;
    private final ApprovalHistoryRepository repo;
    private final ObjectMapper json;
    private final ApplicationContext context;
    private final String expectedHead;

    public ChainVerifyCommand(ApprovalHistoryService history, ApprovalHistoryRepository repo, ObjectMapper json,
                              ApplicationContext context,
                              @Value("${nova.cli.expected-head:}") String expectedHead) {
        this.history = history;
        this.repo = repo;
        this.json = json;
        this.context = context;
        this.expectedHead = expectedHead;
    }

    @Override
    public void run(ApplicationArguments args) throws JsonProcessingException {
        Report report = verify(expectedHead);
        // 로캘이 없는 환경(distroless 등)에서도 한글 사유가 깨지지 않게 UTF-8로 고정한다
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(json.writeValueAsString(report));
        System.exit(SpringApplication.exit(context, report::exitCode));
    }

    public Report verify(String expected) {
        ApprovalHistoryService.IntegrityResult chain = history.verify();
        String wanted = (expected == null || expected.isBlank()) ? null : expected.trim().toLowerCase();
        if (!chain.valid()) {
            return new Report(CHAIN_BROKEN, chain, wanted, null);
        }
        if (wanted == null) {
            return new Report(OK, chain, null, null);
        }
        boolean found = wanted.equals(ApprovalHistoryService.GENESIS) || repo.existsByHash(wanted);
        return new Report(found ? OK : EXPECTED_HEAD_MISSING, chain, wanted, found);
    }

    public record Report(int exitCode, ApprovalHistoryService.IntegrityResult chain,
                         String expectedHead, Boolean expectedHeadFound) {
    }
}
