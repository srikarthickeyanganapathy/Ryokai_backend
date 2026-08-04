package com.example.taskflow.organization.membership.infrastructure.persistence;

import com.example.taskflow.organization.membership.domain.LeaveRequest;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByOrganizationId(Long orgId);
    List<LeaveRequest> findByOrganizationIdAndStatus(Long orgId, LeaveRequest.LeaveRequestStatus status);
    List<LeaveRequest> findByOrganizationIdAndStatusIn(Long orgId, List<LeaveRequest.LeaveRequestStatus> statuses);
    Optional<LeaveRequest> findByUserAndOrganizationAndStatus(User user, Organization org, LeaveRequest.LeaveRequestStatus status);
    List<LeaveRequest> findByUserAndOrganizationAndStatusIn(User user, Organization org, List<LeaveRequest.LeaveRequestStatus> statuses);
    List<LeaveRequest> findByUserIdAndOrganizationIdAndStatus(Long userId, Long orgId, LeaveRequest.LeaveRequestStatus status);
    boolean existsByUserAndOrganizationAndStatus(User user, Organization org, LeaveRequest.LeaveRequestStatus status);

    @Query("SELECT r FROM LeaveRequest r WHERE r.user.id = :userId AND r.organization.id = :orgId AND r.status = :status AND r.startDate <= :end AND r.endDate >= :start")
    List<LeaveRequest> findOverlappingLeaves(
            @Param("userId") Long userId,
            @Param("orgId") Long orgId,
            @Param("status") LeaveRequest.LeaveRequestStatus status,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
