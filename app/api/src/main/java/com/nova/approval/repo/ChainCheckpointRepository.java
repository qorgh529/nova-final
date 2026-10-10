package com.nova.approval.repo;

import com.nova.approval.domain.ChainCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainCheckpointRepository extends JpaRepository<ChainCheckpoint, Long> {
}
