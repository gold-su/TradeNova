package com.tradenova.community.repository;

import com.tradenova.community.entity.CommunityReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, CommunityReport.TargetType type, Long targetId);
    void deleteAllByTargetTypeAndTargetId(CommunityReport.TargetType type, Long targetId);
}
