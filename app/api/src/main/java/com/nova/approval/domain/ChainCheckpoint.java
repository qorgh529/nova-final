package com.nova.approval.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chain_checkpoints")
public class ChainCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(name = "head_hash", nullable = false, length = 64)
    private String headHash;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected ChainCheckpoint() {
    }

    public ChainCheckpoint(String headHash) {
        this.headHash = headHash;
    }

    public Long getSeq() {
        return seq;
    }

    public String getHeadHash() {
        return headHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
