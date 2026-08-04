package com.example.taskflow.organization.membership.infrastructure.persistence;

import com.example.taskflow.organization.membership.domain.ExitRequest;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExitRequestRepository extends JpaRepository<ExitRequest, Long> {
    List<ExitRequest> findByOrganizationId(Long orgId);
    List<ExitRequest> findByOrganizationIdAndStatusIn(Long orgId, List<ExitRequest.ExitRequestStatus> statuses);
    Optional<ExitRequest> findByUserAndOrganizationAndStatus(User user, Organization org, ExitRequest.ExitRequestStatus status);
    List<ExitRequest> findByUserAndOrganizationAndStatusIn(User user, Organization org, List<ExitRequest.ExitRequestStatus> statuses);
    boolean existsByUserAndOrganizationAndStatus(User user, Organization org, ExitRequest.ExitRequestStatus status);
    boolean existsByUserIdAndOrganizationIdAndStatusIn(Long userId, Long orgId, List<ExitRequest.ExitRequestStatus> statuses);
}
