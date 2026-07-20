package com.example.taskflow.service;

import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.mapper.TaskResponseMapper;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.security.RoleStrategy;
import com.example.taskflow.security.RoleStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final RoleStrategyFactory roleStrategyFactory;
    private final TaskResponseMapper taskResponseMapper;

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
            if (!isMember && !roleStrategyFactory.getStrategy(user).canOverride(user)) {
                throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view tasks for this crew.");
            }
            Page<Task> page = taskRepository.findByCrewId(crewId, pageable);
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
                throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view tasks for this project.");
            }
            
            Page<Task> page = taskRepository.findByProjectId(projectId, pageable);
            return batchMapTasks(page);
        }

        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);

        if (strategy.canOverride(user) || strategy.canViewAllTasks(user)) {
            boolean isSuperAdmin = user.isSuperAdmin();

            if (isSuperAdmin) {
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
                Long orgId = memberships.get(0).getOrganization().getId();
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
            Page<Task> page = taskRepository.findByAssigneeOrCreator(user, pageable);
            Page<TaskResponseDTO> fallbackResult = batchMapTasks(page);
            if (scope != null) {
                List<TaskResponseDTO> filtered = fallbackResult.getContent().stream()
                        .filter(dto -> scopeFilter(dto, user, scope))
                        .collect(Collectors.toList());
                return new PageImpl<>(filtered, pageable, filtered.size());
            }
            return fallbackResult;
        }

        if (strategy.canAssign(user)) {
            Page<Task> page = taskRepository.findVisibleForManager(user, user.getId(), pageable);
            List<Task> filteredTasks = page.stream()
                    .filter(task -> entityScopeFilter(task, user, scope))
                    .collect(Collectors.toList());
            return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
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
        
        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
        if (strategy.canOverride(user)) {
            return taskResponseMapper.mapToTaskResponseDTO(task);
        }

        if (task.getCrew() != null) {
            boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(task.getCrew().getId(), user.getId());
            if (isMember) {
                return taskResponseMapper.mapToTaskResponseDTO(task);
            }
        }

        boolean isPersonalAndMine = task.isPersonal() && task.getCreator() != null && task.getCreator().getId().equals(user.getId());
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(user.getId());
        
        if (!isPersonalAndMine && !isAssignee && !isCreator && !strategy.canViewAllTasks(user)) {
            if (task.getOrg() != null && strategy.canAssign(user)) {
                 boolean inOrg = membershipRepository.existsByUserAndOrganization(user, task.getOrg());
                 if (!inOrg) {
                     throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view this task.");
                 }
            } else {
                 throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view this task.");
            }
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
        }
        return true;
    }
    
    private boolean entityScopeFilter(Task task, User user, String scope) {
        if ("me".equalsIgnoreCase(scope)) {
            return task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        } else if ("created_by_me".equalsIgnoreCase(scope)) {
            return task.getCreator() != null && task.getCreator().getId().equals(user.getId());
        }
        return true;
    }
}
