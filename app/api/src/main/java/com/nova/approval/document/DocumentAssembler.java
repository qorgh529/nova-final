package com.nova.approval.document;

import com.nova.approval.domain.*;
import com.nova.approval.repo.*;
import com.nova.approval.web.dto.Dtos;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DocumentAssembler {

    private final LeaveDetailRepository leaves;
    private final ApprovalLineRepository lines;
    private final AttachmentRepository attachments;
    private final ApprovalHistoryRepository history;

    public DocumentAssembler(LeaveDetailRepository leaves, ApprovalLineRepository lines,
                             AttachmentRepository attachments, ApprovalHistoryRepository history) {
        this.leaves = leaves;
        this.lines = lines;
        this.attachments = attachments;
        this.history = history;
    }

    public Dtos.DocumentSummary summary(Document d) {
        return new Dtos.DocumentSummary(d.getId(), d.getType().name(), d.getTitle(),
            d.getStatus().name(), d.getDrafterId(), d.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Dtos.DocumentDetail detail(Document d) {
        Dtos.LeaveRequest leave = null;
        if (d.getType() == DocumentType.LEAVE) {
            leave = leaves.findById(d.getId())
                .map(l -> new Dtos.LeaveRequest(l.getLeaveType(), l.getStartDate(), l.getEndDate()))
                .orElse(null);
        }
        List<Dtos.ApprovalLineView> lineViews = lines.findByDocumentIdOrderByStep(d.getId()).stream()
            .map(l -> new Dtos.ApprovalLineView(l.getStep(), l.getApproverId(), l.getStatus().name(), l.getActedAt()))
            .toList();
        List<Dtos.HistoryView> historyViews = history.findAllByOrderBySeqAsc().stream()
            .filter(h -> h.getDocumentId().equals(d.getId()))
            .map(h -> new Dtos.HistoryView(h.getSeq(), h.getDocumentId(), h.getActorId(),
                h.getAction().name(), h.getActedAt(), h.getHash()))
            .toList();
        List<Dtos.AttachmentView> attachmentViews = attachments.findByDocumentId(d.getId()).stream()
            .map(a -> new Dtos.AttachmentView(a.getId(), a.getFileName(), a.getContentType(),
                a.getSize(), a.getSha256(), a.getUploadedAt()))
            .toList();
        return new Dtos.DocumentDetail(d.getId(), d.getType().name(), d.getTitle(), d.getBody(),
            d.getStatus().name(), d.getDrafterId(), d.getCreatedAt(), d.getUpdatedAt(),
            leave, lineViews, historyViews, attachmentViews);
    }
}
