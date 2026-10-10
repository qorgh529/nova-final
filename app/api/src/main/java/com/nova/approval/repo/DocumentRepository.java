package com.nova.approval.repo;

import com.nova.approval.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByDrafterIdOrderByIdDesc(Long drafterId);

    Optional<Document> findFirstByTitleOrderByIdAsc(String title);
}
