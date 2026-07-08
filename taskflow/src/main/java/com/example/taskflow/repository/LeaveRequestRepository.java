package com.example.taskflow.repository;

import com.example.taskflow.domain.LeaveRequest;
import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByOrganizationId(Long orgId);
    List<LeaveRequest> findByOrganizationIdAndStatus(Long orgId, LeaveRequest.LeaveRequestStatus status);
    Optional<LeaveRequest> findByUserAndOrganizationAndStatus(User user, Organization org, LeaveRequest.LeaveRequestStatus status);
    boolean existsByUserAndOrganizationAndStatus(User user, Organization org, LeaveRequest.LeaveRequestStatus status);
}
