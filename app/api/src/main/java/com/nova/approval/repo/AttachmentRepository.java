package com.nova.approval.repo;

import com.nova.approval.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByDocumentId(Long documentId);
}
