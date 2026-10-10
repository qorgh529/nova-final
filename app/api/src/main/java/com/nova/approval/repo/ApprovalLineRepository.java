package com.nova.approval.repo;

import com.nova.approval.domain.ApprovalLine;
import com.nova.approval.domain.LineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {
    List<ApprovalLine> findByDocumentIdOrderByStep(Long documentId);

    List<ApprovalLine> findByApproverIdAndStatus(Long approverId, LineStatus status);
}
