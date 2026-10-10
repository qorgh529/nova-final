package com.nova.approval.web.dto;

import com.nova.approval.domain.DocumentType;
import com.nova.approval.domain.LeaveType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 요청/응답 DTO 모음.
 */
public final class Dtos {

    private Dtos() {
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    public record LoginResponse(String token, long userId, String name, List<String> roles) {
    }

    public record MeResponse(long id, String loginId, String name, String department, List<String> roles) {
    }

    public record LeaveRequest(
        @NotNull LeaveType leaveType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
    }

    public record CreateDocumentRequest(
        @NotNull DocumentType type,
        @NotBlank String title,
        String body,
        @Valid LeaveRequest leave,
        @NotEmpty List<Long> approverIds) {
    }

    public record ApprovalLineView(int step, long approverId, String status, Instant actedAt) {
    }

    public record HistoryView(long seq, long documentId, long actorId, String action, Instant actedAt, String hash) {
    }

    public record AttachmentView(long id, String fileName, String contentType, long size, String sha256, Instant uploadedAt) {
    }

    public record DocumentSummary(long id, String type, String title, String status, long drafterId, Instant createdAt) {
    }

    public record DocumentDetail(
        long id, String type, String title, String body, String status, long drafterId,
        Instant createdAt, Instant updatedAt,
        LeaveRequest leave,
        List<ApprovalLineView> lines,
        List<HistoryView> history,
        List<AttachmentView> attachments) {
    }
}
