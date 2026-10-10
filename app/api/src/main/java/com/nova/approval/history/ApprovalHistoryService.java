package com.nova.approval.history;

import com.nova.approval.domain.ApprovalAction;
import com.nova.approval.domain.ApprovalHistory;
import com.nova.approval.repo.ApprovalHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * 결재 이력 해시 체인. 전역 단일 체인이며 append 만 한다.
 * hash = SHA-256(prev_hash | seq | document_id | actor_id | action | acted_at(micros, ISO-8601))
 */
@Service
public class ApprovalHistoryService {

    public static final String GENESIS = "0".repeat(64);

    private final ApprovalHistoryRepository repo;

    @PersistenceContext
    private EntityManager em;

    public ApprovalHistoryService(ApprovalHistoryRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ApprovalHistory append(Long documentId, Long actorId, ApprovalAction action) {
        // 전역 직렬화: 트랜잭션 범위 advisory lock.
        // pg_advisory_xact_lock은 void를 반환해서 Hibernate가 결과를 못 읽으므로 text로 캐스팅한다.
        em.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(4919) AS text)").getSingleResult();

        ApprovalHistory prev = repo.findTopByOrderBySeqDesc().orElse(null);
        long seq = (prev == null) ? 1L : prev.getSeq() + 1L;
        String prevHash = (prev == null) ? GENESIS : prev.getHash();
        Instant actedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        ApprovalHistory row = new ApprovalHistory(documentId, actorId, action, actedAt, prevHash);
        row.setSeq(seq);
        row.setHash(computeHash(prevHash, seq, documentId, actorId, action, actedAt));
        return repo.save(row);
    }

    public static String computeHash(String prevHash, long seq, long documentId, long actorId,
                                     ApprovalAction action, Instant actedAt) {
        String payload = String.join("|",
            prevHash,
            Long.toString(seq),
            Long.toString(documentId),
            Long.toString(actorId),
            action.name(),
            actedAt.truncatedTo(ChronoUnit.MICROS).toString());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 체인 전체를 재계산해 각 행의 저장된 해시, prev_hash 연결, seq 연속성을 검증한다.
     */
    @Transactional(readOnly = true)
    public IntegrityResult verify() {
        List<ApprovalHistory> rows = repo.findAllByOrderBySeqAsc();
        String expectedPrev = GENESIS;
        long expectedSeq = 1L;
        for (ApprovalHistory r : rows) {
            if (r.getSeq() != expectedSeq) {
                return IntegrityResult.broken(r.getSeq(), "seq가 연속이 아님 (기대 " + expectedSeq + ")");
            }
            if (!expectedPrev.equals(r.getPrevHash())) {
                return IntegrityResult.broken(r.getSeq(), "prev_hash 불일치");
            }
            String recomputed = computeHash(r.getPrevHash(), r.getSeq(), r.getDocumentId(),
                r.getActorId(), r.getAction(), r.getActedAt());
            if (!recomputed.equals(r.getHash())) {
                return IntegrityResult.broken(r.getSeq(), "hash 불일치 (위변조 의심)");
            }
            expectedPrev = r.getHash();
            expectedSeq++;
        }
        String head = rows.isEmpty() ? GENESIS : rows.get(rows.size() - 1).getHash();
        return IntegrityResult.ok(rows.size(), head);
    }

    public record IntegrityResult(boolean valid, long count, String headHash, Long brokenAtSeq, String reason) {
        static IntegrityResult ok(long count, String head) {
            return new IntegrityResult(true, count, head, null, null);
        }

        static IntegrityResult broken(long seq, String reason) {
            return new IntegrityResult(false, 0, null, seq, reason);
        }
    }
}
