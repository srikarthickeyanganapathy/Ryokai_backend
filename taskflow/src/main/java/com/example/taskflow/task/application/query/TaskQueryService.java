package com.example.taskflow.task.application.query;

import com.example.taskflow.project.domain.Project;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.response.TaskResponseDTO;
import com.example.taskflow.task.mapper.TaskResponseMapper;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final AuthorizationEngine authorizationEngine;

    public java.util.List<Task> getRawTasksForUser(com.example.taskflow.user.domain.User user, String scope, Long projectId, Long crewId) {
        return java.util.Collections.emptyList();
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable) {
        return getTasksForUser(user, pageable, null, null, null, null, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope) {
        return getTasksForUser(user, pageable, scope, null, null, null, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId) {
        return getTasksForUser(user, pageable, scope, projectId, null, null, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId, Long crewId) {
        return getTasksForUser(user, pageable, scope, projectId, crewId, null, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId, Long crewId, Long orgId) {
        return getTasksForUser(user, pageable, scope, projectId, crewId, orgId, null);
    }

    /**
     * STRICT workspace isolation — one dimension per call, exactly like Projects:
     *   crewId   -> only tasks of that crew + tasks of its shared/owned projects (membership required)
     *   projectId -> only tasks of that project (access required)
     *   teamId   -> only tasks of that team + tasks of its projects (membership required)
     *   orgId    -> ONLY tasks of that organization (membership required) — no
     *              personal/crew/created-by mixing ever
     *   none     -> ONLY the user's own personal tasks
     * A user can never see tasks from a workspace they are not in, and org views
     * never leak personal or other-org data.
     */
    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId, Long crewId, Long orgId, Long teamId) {
        // Crew workspace: strict (direct crew tasks + tasks of shared/owned projects)
        if (crewId != null) {
            boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId());
            if (!isMember && !user.isSuperAdmin()) {
                throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view tasks for this crew.");
            }
            return batchMapTasks(taskRepository.findByCrewIdWithBridge(crewId, pageable));
        }

        // Project workspace: strict (existing access rule)
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

            boolean isCreator = project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId());
            boolean inOrg = project.getOrganization() != null &&
                    membershipRepository.existsByUserAndOrganization(user, project.getOrganization());
            boolean inTeam = project.getTeam() != null &&
                    teamMemberRepository.existsByIdTeamIdAndIdUserId(project.getTeam().getId(), user.getId());

            if (!isCreator && !inOrg && !inTeam && !user.isSuperAdmin()) {
                throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view tasks for this project.");
            }

            return batchMapTasks(taskRepository.findByProjectId(projectId, pageable));
        }

        // Team workspace: strict (direct team tasks + tasks of the team's projects)
        if (teamId != null) {
            boolean isMember = teamMemberRepository.existsByIdTeamIdAndIdUserId(teamId, user.getId());
            if (!isMember && !user.isSuperAdmin()) {
                throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view tasks for this team.");
            }
            return batchMapTasks(taskRepository.findByTeamIdWithProjectBridge(teamId, pageable));
        }

        // Org workspace: STRICT — only this org's tasks, nothing else
        if (orgId != null) {
            boolean isMember = membershipRepository.existsByUserIdAndOrganizationId(user.getId(), orgId);
            if (!isMember && !user.isSuperAdmin()) {
                throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view tasks for this organization.");
            }
            return batchMapTasks(taskRepository.findByOrgIdStrict(orgId, pageable));
        }

        // Personal workspace (default): STRICT — only own personal tasks
        Page<Task> page = taskRepository.findPersonalTasksStrict(user, pageable);
        if (scope != null) {
            List<Task> filtered = page.getContent().stream()
                    .filter(task -> entityScopeFilter(task, user, scope))
                    .collect(Collectors.toList());
            return batchMapList(filtered, pageable, filtered.size());
        }
        return batchMapTasks(page);
    }

    public TaskResponseDTO getTaskForUser(Long taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));
        
        // Removed legacy programmatic check

        return taskResponseMapper.mapToTaskResponseDTO(task);
    }

    private Page<TaskResponseDTO> batchMapTasks(Page<Task> page) {
        if (page.isEmpty()) return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        List<TaskResponseDTO> dtos = page.stream()
                .map(taskResponseMapper::mapToTaskResponseDTO)
                .collect(Collectors.toList());
        return new PageImpl<>(dtos, page.getPageable(), page.getTotalElements());
    }

    private Page<TaskResponseDTO> batchMapList(List<Task> tasks, Pageable pageable, long total) {
        if (tasks.isEmpty()) return new PageImpl<>(List.of(), pageable, total);
        List<TaskResponseDTO> dtos = tasks.stream()
                .map(taskResponseMapper::mapToTaskResponseDTO)
                .collect(Collectors.toList());
        return new PageImpl<>(dtos, pageable, total);
    }

    private boolean entityScopeFilter(Task task, User user, String scope) {
        if ("me".equalsIgnoreCase(scope)) {
            return task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        } else if ("created_by_me".equalsIgnoreCase(scope)) {
            return task.getCreator() != null && task.getCreator().getId().equals(user.getId());
        } else if ("PERSONAL".equalsIgnoreCase(scope)) {
            return task.getOrg() == null && task.getCrew() == null && task.getTeam() == null;
        } else if ("ORG".equalsIgnoreCase(scope) || "ORGANIZATION".equalsIgnoreCase(scope)) {
            return task.getOrg() != null;
        } else if ("CREWS".equalsIgnoreCase(scope) || "CREW".equalsIgnoreCase(scope)) {
            return task.getCrew() != null || (task.getProject() != null && !task.getProject().getSharedCrews().isEmpty() && task.getProject().getOrganization() == null);
        }
        return true;
    }
}