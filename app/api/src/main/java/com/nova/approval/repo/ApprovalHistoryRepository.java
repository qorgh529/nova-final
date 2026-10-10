package com.nova.approval.repo;

import com.nova.approval.domain.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

    Optional<ApprovalHistory> findTopByOrderBySeqDesc();

    List<ApprovalHistory> findAllByOrderBySeqAsc();

    boolean existsByHash(String hash);
}
