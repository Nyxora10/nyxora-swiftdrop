package com.nyxora.swiftdrop.auth.repository;

import com.nyxora.swiftdrop.auth.entity.ProcessedWorkloadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWorkloadEventRepository extends JpaRepository<ProcessedWorkloadEvent, String> {
}
