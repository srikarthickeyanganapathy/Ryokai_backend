package com.example.taskflow.service;

import com.example.taskflow.domain.*;
import com.example.taskflow.dto.GoalDTOs.*;
import com.example.taskflow.exception.OrganizationSuspendedException;
import com.example.taskflow.repository.GoalRepository;
import com.example.taskflow.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final OrganizationRepository organizationRepository;
    private final PermissionService permissionService;

    public List<GoalResponseDTO> getGoals(Long orgId) {
        return goalRepository.findByOrganizationIdOrderByEndDateAsc(orgId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public GoalResponseDTO create(User user, Long orgId, GoalRequestDTO req) {
        requireManagePermission(user);
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        if (org.getStatus() != Organization.OrgStatus.ACTIVE) {
            throw new OrganizationSuspendedException("Organization is not active.");
        }
        Goal goal = new Goal();
        goal.setOrganization(org);
        goal.setOwner(user);
        applyRequest(goal, req);
        return toDto(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponseDTO update(User user, Long goalId, GoalRequestDTO req) {
        requireManagePermission(user);
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        applyRequest(goal, req);
        return toDto(goalRepository.save(goal));
    }

    @Transactional
    public void delete(User user, Long goalId) {
        requireManagePermission(user);
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        goalRepository.delete(goal);
    }

    private void requireManagePermission(User user) {
        if (!permissionService.getPermissionsForUser(user).contains("GOAL_MANAGE")) {
            throw new com.example.taskflow.exception.UnauthorizedActionException(
                    "You are not authorized to manage goals.");
        }
    }

    private void applyRequest(Goal goal, GoalRequestDTO req) {
        goal.setTitle(req.getTitle());
        goal.setDescription(req.getDescription());
        goal.setPeriod(req.getPeriod());
        if (req.getStatus() != null) goal.setStatus(req.getStatus());
        goal.setStartDate(req.getStartDate());
        goal.setEndDate(req.getEndDate());

        goal.getKeyResults().clear();
        if (req.getKeyResults() != null) {
            req.getKeyResults().forEach(krDto -> {
                KeyResult kr = new KeyResult();
                kr.setGoal(goal);
                kr.setTitle(krDto.getTitle());
                kr.setCurrentValue(krDto.getCurrentValue() != null ? krDto.getCurrentValue() : 0.0);
                kr.setTargetValue(krDto.getTargetValue());
                kr.setUnit(krDto.getUnit());
                goal.getKeyResults().add(kr);
            });
        }

        // Progress is server-computed as the average of each key result's
        // completion fraction, not client-submitted
        goal.setProgress(computeProgress(goal.getKeyResults()));
    }

    private int computeProgress(List<KeyResult> keyResults) {
        if (keyResults.isEmpty()) return 0;
        double avg = keyResults.stream()
                .mapToDouble(kr -> {
                    if (kr.getTargetValue() == null || kr.getTargetValue() == 0) return 0;
                    return Math.min(1.0, kr.getCurrentValue() / kr.getTargetValue());
                })
                .average().orElse(0);
        return (int) Math.round(avg * 100);
    }

    private GoalResponseDTO toDto(Goal g) {
        List<KeyResultDTO> krDtos = g.getKeyResults().stream().map(kr -> {
            KeyResultDTO dto = new KeyResultDTO();
            dto.setId(kr.getId());
            dto.setTitle(kr.getTitle());
            dto.setCurrentValue(kr.getCurrentValue());
            dto.setTargetValue(kr.getTargetValue());
            dto.setUnit(kr.getUnit());
            return dto;
        }).toList();

        return new GoalResponseDTO(g.getId(), g.getTitle(), g.getDescription(), g.getPeriod(),
                g.getOwner().getUsername(), g.getStatus(), g.getProgress(),
                g.getStartDate(), g.getEndDate(), krDtos);
    }
}
