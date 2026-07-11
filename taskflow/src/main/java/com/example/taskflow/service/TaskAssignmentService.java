package com.example.taskflow.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.TaskTemplate;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Project;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TaskTemplateRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.TeamRepository;
import com.example.taskflow.repository.ProjectRepository;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.transaction.Transactional;
import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TaskAssignmentService {

    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;
    private final TaskTemplateRepository taskTemplateRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;

    @Value("${app.reminders.timezone:Asia/Kolkata}")
    private String timezoneProperty;

    private ZoneId zoneId;

    public TaskAssignmentService(TaskRepository taskRepository,
                                 TaskAuditService taskAuditService,
                                 TaskTemplateRepository taskTemplateRepository,
                                 UserService userService,
                                 NotificationService notificationService,
                                 OrganizationRepository organizationRepository,
                                 TeamRepository teamRepository,
                                 OrganizationMembershipRepository membershipRepository,
                                 ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.taskTemplateRepository = taskTemplateRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
    }

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezoneProperty);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, LocalDateTime dueDate, String tags, boolean isPersonal) {
        return assignTask(title, description, assignee, creator, priority, dueDate, tags, isPersonal, null, null);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, LocalDateTime dueDate, String tags, boolean isPersonal, Long teamId) {
        return assignTask(title, description, assignee, creator, priority, dueDate, tags, isPersonal, teamId, null);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, LocalDateTime dueDate, String tags, boolean isPersonal, Long teamId, Long projectId) {

        // Personal tasks can be assigned to anyone (including self)
        if (!isPersonal) {
            boolean isSuperAdmin = creator.getRoles() != null && creator.getRoles().stream()
                    .anyMatch(r -> r.getName().contains("SUPER_ADMIN"));

            var creatorMembership = membershipRepository.findByUserId(creator.getId());
            var assigneeMembership = membershipRepository.findByUserId(assignee.getId());
            
            if (assigneeMembership.isEmpty()) {
                throw new IllegalArgumentException("Assignee is not a member of any organization");
            }

            if (!isSuperAdmin) {
                if (creatorMembership.isEmpty()) {
                    throw new IllegalStateException("You must belong to an organization to assign org tasks");
                }
                
                // Verify same org
                Long creatorOrgId = creatorMembership.get(0).getOrganization().getId();
                boolean sameOrg = assigneeMembership.stream()
                        .anyMatch(m -> m.getOrganization().getId().equals(creatorOrgId));
                if (!sameOrg) {
                    throw new IllegalArgumentException("Assignee must be a member of your organization");
                }
            }
            
            if (creator.getId().equals(assignee.getId())) {
                throw new IllegalArgumentException("Org tasks cannot be self-assigned. Use a personal task instead, or assign to another member.");
            }
        } else {
            if (assignee != null && !assignee.getId().equals(creator.getId())) {
                throw new IllegalArgumentException("Personal tasks must be assigned to the creator");
            }
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setAssignedTo(assignee);
        task.setCreatedBy(creator);

        // Team/Org scoping
        if (teamId != null) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
                    
            boolean isSuperAdmin = creator.getRoles() != null && creator.getRoles().stream()
                    .anyMatch(r -> r.getName().contains("SUPER_ADMIN"));
            if (isSuperAdmin) {
                var creatorMembership = membershipRepository.findByUserId(creator.getId());
                if (!creatorMembership.isEmpty()) {
                    Long creatorOrgId = creatorMembership.get(0).getOrganization().getId();
                    if (!team.getOrganization().getId().equals(creatorOrgId)) {
                        throw new IllegalArgumentException("Super Admins cannot assign tasks to teams outside their organization");
                    }
                }
            }
            
            // Validate assignee is a member of this team
            boolean isTeamMember = team.getMembers().stream()
                    .anyMatch(m -> m.getId().equals(assignee.getId()));
            if (!isTeamMember) {
                throw new IllegalArgumentException("Assignee is not a member of team: " + team.getName());
            }
            task.setTeam(team);
            task.setOrganization(team.getOrganization());
            isPersonal = false; // Team tasks cannot be personal
        } else if (!isPersonal) {
            // If no teamId and not personal, task must have org scope via creator's org membership
            var memberships = membershipRepository.findByUserId(creator.getId());
            if (!memberships.isEmpty()) {
                task.setOrganization(memberships.get(0).getOrganization());
            } else {
                // If creator has no org (Super Admin), use assignee's org!
                var assigneeMemberships = membershipRepository.findByUserId(assignee.getId());
                if (!assigneeMemberships.isEmpty()) {
                    task.setOrganization(assigneeMemberships.get(0).getOrganization());
                }
            }
        }
        
        if (dueDate != null && dueDate.isBefore(LocalDateTime.now(zoneId).minusMinutes(5))) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }

        // ✨ Set defaults if null
        task.setPriority(priority != null ? priority : TaskPriority.NORMAL);
        task.setDueDate(dueDate);
        task.setTags(tags);
        task.setPersonal(isPersonal);

        // B-16b: Wire projectId
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
            task.setProject(project);
        }

        // B-04b: Personal tasks start as To-Do, not Assigned
        String statusName = isPersonal ? "TODO" : "ASSIGNED";
        task.setCurrentStatus(isPersonal ? TaskStatus.TODO : TaskStatus.ASSIGNED);
        // CreatedAt/UpdatedAt are handled by @CreationTimestamp / @UpdateTimestamp

        Task savedTask = taskRepository.save(task);
        taskAuditService.recordStatus(savedTask, null, statusName, statusName, creator, null);

        notificationService.createAndSend(
            assignee,
            creator,
            com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
            "You have a new task: " + title,
            description,
            savedTask,
            null,
            creator
        );

        return mapToTaskResponseDTO(savedTask);
    }

    @Transactional
    public List<TaskResponseDTO> bulkAssignTasks(Long templateId, String title, String description, List<String> assigneeUsernames, User creator, LocalDateTime dueDate, String tags) {
        return bulkAssignTasks(templateId, title, description, assigneeUsernames, creator, dueDate, tags, null);
    }

    @Transactional
    public List<TaskResponseDTO> bulkAssignTasks(Long templateId, String title, String description, List<String> assigneeUsernames, User creator, LocalDateTime dueDate, String tags, Long teamId) {
        String finalTitle = title;
        String finalDescription = description;
        TaskPriority finalPriority = TaskPriority.NORMAL;

        if (templateId != null) {
            TaskTemplate template = taskTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
            finalTitle = template.getDefaultTitle();
            finalDescription = template.getDefaultDescription();
            finalPriority = template.getDefaultPriority();
        }

        if (finalTitle == null || finalTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title is required");
        }

        // Mode 1: teamId provided → auto-resolve all team members as assignees
        List<String> resolvedUsernames = assigneeUsernames;
        if (teamId != null && (assigneeUsernames == null || assigneeUsernames.isEmpty())) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
            resolvedUsernames = team.getMembers().stream()
                    .filter(member -> !member.getId().equals(creator.getId())) // exclude creator (can't self-assign org tasks)
                    .map(User::getUsername)
                    .collect(Collectors.toList());
            if (resolvedUsernames.isEmpty()) {
                throw new IllegalArgumentException("Team has no other members to assign tasks to");
            }
        }

        if (resolvedUsernames == null || resolvedUsernames.isEmpty()) {
            throw new IllegalArgumentException("No assignees resolved — provide teamId or assigneeUsernames");
        }

        final String t = finalTitle;
        final String d = finalDescription;
        final TaskPriority p = finalPriority;

        return resolvedUsernames.stream().map(username -> {
            User assignee = userService.getCurrentUser(username);

            return assignTask(
                    t,
                    d,
                    assignee,
                    creator,
                    p,
                    dueDate,
                    tags,
                    false, // bulk tasks are org tasks, not personal
                    teamId
            );
        }).collect(Collectors.toList());
    }

    private TaskResponseDTO mapToTaskResponseDTO(Task task) {
        return new TaskResponseDTO(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getAssignedTo() != null ? task.getAssignedTo().getUsername() : null,
            task.getCreatedBy() != null ? task.getCreatedBy().getUsername() : null,
            task.getReviewedBy() != null ? task.getReviewedBy().getUsername() : null,
            task.getCurrentStatus(),
            task.getPriority(),
            task.getDueDate(),
            task.getTags(),
            task.getChecklists() != null ? task.getChecklists().stream().map(item -> new com.example.taskflow.dto.ChecklistItemDTO(item.getId(), item.getText(), item.getIsCompleted(), item.getDisplayOrder(), item.getCreatedBy() != null ? item.getCreatedBy().getUsername() : null)).collect(Collectors.toList()) : java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            task.getCreatedAt(),
            task.getUpdatedAt(),
            task.getCoverImageUrl(),
            task.isPersonal(),
            task.isArchived(),
            task.getOrganization() != null ? task.getOrganization().getId() : null,
            task.getOrganization() != null ? task.getOrganization().getName() : null,
            task.getTeam() != null ? task.getTeam().getId() : null,
            task.getTeam() != null ? task.getTeam().getName() : null,
            task.getProject() != null ? task.getProject().getId() : null,
            task.getProject() != null ? task.getProject().getName() : null
        );
    }
}