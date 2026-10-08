package com.nova.approval.document;

import com.nova.approval.domain.*;
import com.nova.approval.history.ApprovalHistoryService;
import com.nova.approval.repo.*;
import com.nova.approval.web.ApiException;
import com.nova.approval.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final LeaveDetailRepository leaves;
    private final ApprovalLineRepository lines;
    private final UserRepository users;
    private final ApprovalHistoryService history;

    public DocumentService(DocumentRepository documents, LeaveDetailRepository leaves,
                           ApprovalLineRepository lines, UserRepository users,
                           ApprovalHistoryService history) {
        this.documents = documents;
        this.leaves = leaves;
        this.lines = lines;
        this.users = users;
        this.history = history;
    }

    @Transactional
    public Document create(Long drafterId, Dtos.CreateDocumentRequest req) {
        if (req.type() == DocumentType.LEAVE && req.leave() == null) {
            throw ApiException.badRequest("휴가 문서에는 leave 정보가 필요합니다");
        }
        Document doc = documents.save(new Document(req.type(), req.title(), req.body(), drafterId));

        if (req.type() == DocumentType.LEAVE) {
            Dtos.LeaveRequest lv = req.leave();
            if (lv.endDate().isBefore(lv.startDate())) {
                throw ApiException.badRequest("휴가 종료일이 시작일보다 빠릅니다");
            }
            long days = ChronoUnit.DAYS.between(lv.startDate(), lv.endDate()) + 1;
            leaves.save(new LeaveDetail(doc.getId(), lv.leaveType(), lv.startDate(), lv.endDate(),
                BigDecimal.valueOf(days)));
        }

        int step = 1;
        for (Long approverId : req.approverIds()) {
            if (users.findById(approverId).isEmpty()) {
                throw ApiException.badRequest("결재자 없음: " + approverId);
            }
            lines.save(new ApprovalLine(doc.getId(), step++, approverId));
        }
        return doc;
    }

    @Transactional
    public void submit(Long documentId, Long actorId) {
        Document doc = requireDocument(documentId);
        if (!doc.getDrafterId().equals(actorId)) {
            throw ApiException.forbidden("기안자만 상신할 수 있습니다");
        }
        if (doc.getStatus() != DocumentStatus.DRAFT) {
            throw ApiException.conflict("DRAFT 상태만 상신할 수 있습니다");
        }
        doc.setStatus(DocumentStatus.SUBMITTED);
        history.append(documentId, actorId, ApprovalAction.SUBMIT);
    }

    @Transactional
    public void approve(Long documentId, Long actorId) {
        Document doc = requireDocument(documentId);
        ApprovalLine line = currentPendingLine(doc, actorId);
        line.act(LineStatus.APPROVED);

        List<ApprovalLine> all = lines.findByDocumentIdOrderByStep(documentId);
        boolean allApproved = all.stream().allMatch(l -> l.getStatus() == LineStatus.APPROVED);
        if (allApproved) {
            doc.setStatus(DocumentStatus.APPROVED);
        }
        history.append(documentId, actorId, ApprovalAction.APPROVE);
    }

    @Transactional
    public void reject(Long documentId, Long actorId) {
        Document doc = requireDocument(documentId);
        ApprovalLine line = currentPendingLine(doc, actorId);
        line.act(LineStatus.REJECTED);
        doc.setStatus(DocumentStatus.REJECTED);
        history.append(documentId, actorId, ApprovalAction.REJECT);
    }

    private ApprovalLine currentPendingLine(Document doc, Long actorId) {
        if (doc.getStatus() != DocumentStatus.SUBMITTED) {
            throw ApiException.conflict("상신(SUBMITTED) 상태에서만 결재할 수 있습니다");
        }
        ApprovalLine current = lines.findByDocumentIdOrderByStep(doc.getId()).stream()
            .filter(l -> l.getStatus() == LineStatus.PENDING)
            .findFirst()
            .orElseThrow(() -> ApiException.conflict("대기 중인 결재 단계가 없습니다"));
        if (!current.getApproverId().equals(actorId)) {
            throw ApiException.forbidden("현재 단계의 결재자가 아닙니다");
        }
        return current;
    }

    @Transactional(readOnly = true)
    public Document requireDocument(Long id) {
        return documents.findById(id).orElseThrow(() -> ApiException.notFound("문서 없음: " + id));
    }

    @Transactional(readOnly = true)
    public List<Document> myDrafts(Long drafterId) {
        return documents.findByDrafterIdOrderByIdDesc(drafterId);
    }
}
