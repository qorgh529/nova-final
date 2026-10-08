package com.nova.approval.repo;

import com.nova.approval.domain.LeaveDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveDetailRepository extends JpaRepository<LeaveDetail, Long> {
}
