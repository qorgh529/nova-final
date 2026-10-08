package com.nova.approval.history;

import com.nova.approval.domain.ApprovalAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalHistoryHashTest {

    @Test
    void sameInputProducesSameHash() {
        Instant t = Instant.parse("2026-10-08T00:00:00Z");
        String a = ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 10, 20, ApprovalAction.SUBMIT, t);
        String b = ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 10, 20, ApprovalAction.SUBMIT, t);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
    }

    @Test
    void changingAnyFieldChangesHash() {
        Instant t = Instant.parse("2026-10-08T00:00:00Z");
        String base = ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 10, 20, ApprovalAction.SUBMIT, t);
        assertThat(ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 2, 10, 20, ApprovalAction.SUBMIT, t)).isNotEqualTo(base);
        assertThat(ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 11, 20, ApprovalAction.SUBMIT, t)).isNotEqualTo(base);
        assertThat(ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 10, 21, ApprovalAction.SUBMIT, t)).isNotEqualTo(base);
        assertThat(ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 10, 20, ApprovalAction.APPROVE, t)).isNotEqualTo(base);
    }

    @Test
    void chainLinksThroughPrevHash() {
        Instant t = Instant.parse("2026-10-08T00:00:00Z");
        String h1 = ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 1, 10, 20, ApprovalAction.SUBMIT, t);
        String h2 = ApprovalHistoryService.computeHash(h1, 2, 10, 21, ApprovalAction.APPROVE, t);
        // h2는 h1에 의존한다: h1이 바뀌면 h2도 달라진다
        String tampered = ApprovalHistoryService.computeHash(ApprovalHistoryService.GENESIS, 2, 10, 21, ApprovalAction.APPROVE, t);
        assertThat(h2).isNotEqualTo(tampered);
    }
}
