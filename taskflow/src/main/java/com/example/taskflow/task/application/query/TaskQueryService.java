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
import com.example.taskflow.task.security.TaskPermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.example.taskflow.organization.rbac.application.PermissionService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final TaskPermissionHandler taskPermissionHandler;
    private final TaskResponseMapper taskResponseMapper;
    private final PermissionService permissionService;

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable) {
        return getTasksForUser(user, pageable, null, null, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope) {
        return getTasksForUser(user, pageable, scope, null, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId) {
        return getTasksForUser(user, pageable, scope, projectId, null);
    }

    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId, Long crewId) {
        if (crewId != null) {
            boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId());
            if (!isMember) {
                throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view tasks for this crew.");
            }
            Page<Task> page = taskRepository.findByCrewIdWithBridge(crewId, pageable);
            return batchMapTasks(page);
        }

        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
            
            boolean isCreator = project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId());
            boolean inOrg = project.getOrganization() != null && 
                    membershipRepository.existsByUserAndOrganization(user, project.getOrganization());
            boolean inTeam = project.getTeam() != null &&
                    teamMemberRepository.existsByIdTeamIdAndIdUserId(project.getTeam().getId(), user.getId());
            
            if (!isCreator && !inOrg && !inTeam) {
                throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view tasks for this project.");
            }
            
            Page<Task> page = taskRepository.findByProjectId(projectId, pageable);
            return batchMapTasks(page);
        }

        if (user.isSuperAdmin()) {
            Page<Task> page = taskRepository.findVisibleForEmployee(user, user.getId(), pageable);
            List<Task> personalOnly = page.stream()
                    .filter(task -> (task.isPersonal() && task.getCreator() != null
                            && task.getCreator().getId().equals(user.getId()))
                            || (task.getCrew() != null))
                    .collect(Collectors.toList());
            return batchMapList(personalOnly, pageable, personalOnly.size());
        }

        var memberships = membershipRepository.findByUserId(user.getId());
        if (!memberships.isEmpty()) {
            var membership = memberships.get(0);
            Long orgId = membership.getOrganization().getId();
            boolean isDirectorOrAdmin = permissionService.isAuthorized(user, com.example.taskflow.security.PermissionCode.TASK_OVERRIDE, orgId) ||
                                        permissionService.isAuthorized(user, com.example.taskflow.security.PermissionCode.TASK_VIEW, orgId);
            boolean isManager = permissionService.isAuthorized(user, com.example.taskflow.security.PermissionCode.TASK_ASSIGN, orgId);

            if (isDirectorOrAdmin) {
                Page<Task> page = taskRepository.findByOrganizationIdOrCreatedBy(orgId, user, user.getId(), pageable);
                Page<TaskResponseDTO> result = batchMapTasks(page);
                if (scope != null) {
                    List<TaskResponseDTO> filtered = result.getContent().stream()
                            .filter(dto -> scopeFilter(dto, user, scope))
                            .collect(Collectors.toList());
                    return new PageImpl<>(filtered, pageable, filtered.size());
                }
                return result;
            }

            if (isManager) {
                Page<Task> page = taskRepository.findVisibleForManager(user, user.getId(), pageable);
                List<Task> filteredTasks = page.stream()
                        .filter(task -> entityScopeFilter(task, user, scope))
                        .collect(Collectors.toList());
                return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
            }
        }

        Page<Task> page = taskRepository.findVisibleForEmployee(user, user.getId(), pageable);
        List<Task> filteredTasks = page.stream()
                .filter(task -> entityScopeFilter(task, user, scope))
                .collect(Collectors.toList());
        return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
    }

    public TaskResponseDTO getTaskForUser(Long taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));
        
        if (!taskPermissionHandler.hasPermission(null, user, task, "VIEW")) {
            throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view this task.");
        }

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

    private boolean scopeFilter(TaskResponseDTO dto, User user, String scope) {
        if ("me".equalsIgnoreCase(scope)) {
            return dto.getAssignee() != null && dto.getAssignee().equals(user.getUsername());
        } else if ("created_by_me".equalsIgnoreCase(scope)) {
            return dto.getCreator() != null && dto.getCreator().equals(user.getUsername());
        } else if ("PERSONAL".equalsIgnoreCase(scope)) {
            return dto.getOrgId() == null && dto.getCrewId() == null && dto.getTeamId() == null;
        } else if ("ORG".equalsIgnoreCase(scope) || "ORGANIZATION".equalsIgnoreCase(scope)) {
            return dto.getOrgId() != null;
        } else if ("CREWS".equalsIgnoreCase(scope) || "CREW".equalsIgnoreCase(scope)) {
            return dto.getCrewId() != null || (dto.getProjectId() != null && dto.getOrgId() == null);
        }
        return true;
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