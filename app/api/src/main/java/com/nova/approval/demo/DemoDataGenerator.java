package com.nova.approval.demo;

import com.nova.approval.domain.Document;
import com.nova.approval.domain.DocumentType;
import com.nova.approval.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * RPO 측정용 데이터 생성기. 주기마다 결재 1건을 만들고 상신·최종 승인까지 진행한다.
 * RPO = 사고 시각 - 복원된 DB에서 가장 늦은 approval_history.acted_at
 */
@Component
@Profile("demo")
@ConditionalOnProperty(name = "nova.demo.generator.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(DemoDataGenerator.class);

    private final DemoWorkflow workflow;
    private final Random rnd = new Random();

    public DemoDataGenerator(DemoWorkflow workflow) {
        this.workflow = workflow;
    }

    @Scheduled(fixedDelayString = "${nova.demo.generator.interval:PT10S}",
        initialDelayString = "${nova.demo.generator.interval:PT10S}")
    public void tick() {
        try {
            generateOne();
        } catch (RuntimeException e) {
            // 생성기 오류로 앱이 죽지 않게 하고, 다음 주기에 다시 시도한다
            log.warn("[demo] 데이터 생성 실패: {}", e.toString());
        }
    }

    /** 결재 1건을 만들어 최종 승인까지 진행한다. 기안자나 결재자가 아직 없으면 건너뛴다. */
    public Optional<Long> generateOne() {
        List<UserAccount> drafters = workflow.drafters();
        List<UserAccount> approvers = workflow.approvers();
        if (drafters.isEmpty() || approvers.isEmpty()) {
            return Optional.empty();
        }
        Long drafterId = drafters.get(rnd.nextInt(drafters.size())).getId();
        List<Long> approverIds = DemoWorkflow.pickApprovers(approvers, rnd);
        Instant now = Instant.now();
        Document doc = workflow.draft(drafterId, DocumentType.APPROVAL, "[GEN] 자동 생성 결재 " + now,
            "RPO 측정용 자동 생성 문서", null, approverIds);
        workflow.submit(doc);
        workflow.approve(doc, approverIds, approverIds.size());
        log.info("[demo] 생성: document={} at={}", doc.getId(), now);
        return Optional.of(doc.getId());
    }
}
