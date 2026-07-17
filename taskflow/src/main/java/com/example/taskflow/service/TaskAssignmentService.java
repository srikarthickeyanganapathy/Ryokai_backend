package com.example.taskflow.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.repository.TaskRepository;
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
    private final UserService userService;
    private final NotificationService notificationService;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final com.example.taskflow.repository.CrewMemberRepository crewMemberRepository;
    private final com.example.taskflow.repository.CrewRepository crewRepository;

    @Value("${app.reminders.timezone:Asia/Kolkata}")
    private String timezoneProperty;

    private ZoneId zoneId;

    public TaskAssignmentService(TaskRepository taskRepository,
                                 TaskAuditService taskAuditService,
                                 UserService userService,
                                 NotificationService notificationService,
                                 OrganizationRepository organizationRepository,
                                 TeamRepository teamRepository,
                                 OrganizationMembershipRepository membershipRepository,
                                 ProjectRepository projectRepository,
                                 com.example.taskflow.repository.CrewMemberRepository crewMemberRepository,
                                 com.example.taskflow.repository.CrewRepository crewRepository) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.crewRepository = crewRepository;
    }

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezoneProperty);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, java.time.LocalDate dueDate, String tags, boolean isPersonal) {
        return assignTask(title, description, assignee, creator, priority, dueDate, tags, isPersonal, null, null, null);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, java.time.LocalDate dueDate, String tags, boolean isPersonal, Long teamId) {
        return assignTask(title, description, assignee, creator, priority, dueDate, tags, isPersonal, teamId, null, null);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, java.time.LocalDate dueDate, String tags, boolean isPersonal, Long teamId, Long projectId) {
        return assignTask(title, description, assignee, creator, priority, dueDate, tags, isPersonal, teamId, projectId, null);
    }

    @Transactional
    public TaskResponseDTO assignTask(String title, String description, User assignee, User creator, 
                           TaskPriority priority, java.time.LocalDate dueDate, String tags, boolean isPersonal, Long teamId, Long projectId, Long crewId) {

        // Crew tasks: assignee is optional (unclaimed = null, nudge = set)
        if (crewId != null) {
            if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, creator.getId())) {
                throw new IllegalStateException("You must be a member of the crew to create a task in it");
            }
            // If assignee provided, verify they are also a crew member
            if (assignee != null && !crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, assignee.getId())) {
                throw new IllegalArgumentException("Assignee must be a member of the crew");
            }
            // Spec: self-assign is allowed in crews (no check needed)
        }
        // Personal tasks can be assigned to anyone (including self)
        else if (!isPersonal) {
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
                OrganizationMembership creatorOrgMem = creatorMembership.get(0);

                OrganizationMembership assigneeOrgMem = assigneeMembership.stream()
                        .filter(m -> m.getOrganization().getId().equals(creatorOrgId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Assignee must be a member of your organization"));

                // Enforce Role Priority Assignment Guard (0 is top, lower priority value = higher power)
                if (creatorOrgMem.getOrgRole() != null && assigneeOrgMem.getOrgRole() != null) {
                    Integer creatorPriority = creatorOrgMem.getOrgRole().getPriority();
                    Integer assigneePriority = assigneeOrgMem.getOrgRole().getPriority();
                    if (creatorPriority >= assigneePriority) {
                        throw new IllegalArgumentException("You do not have enough power (role priority) to assign tasks to this user.");
                    }
                }
            }
            
            // Spec: org tasks cannot be self-assigned (crew check is above, not here)
            if (creator.getId().equals(assignee.getId())) {
                throw new IllegalArgumentException("Org tasks cannot be self-assigned. Use a personal task instead, or assign to another member.");
            }
        } else {
            if (assignee != null && !assignee.getId().equals(creator.getId())) {
                throw new IllegalArgumentException("Personal tasks must be assigned to the creator");
            }
            // RT-M02 fix: explicit mutual exclusivity - personal tasks have
            // no org, no team, no crew. Spec flowchart: organization_id=NULL,
            // team_id=NULL, crew_id=NULL, creator=assignee=user.
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setAssignee(assignee); // null for unclaimed crew tasks
        task.setCreator(creator);

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
            task.setOrg(team.getOrganization());
            isPersonal = false; // Team tasks cannot be personal
        } else if (!isPersonal && crewId == null) {
            // If no teamId, not personal, and not crew, task must have org scope
            var memberships = membershipRepository.findByUserId(creator.getId());
            if (!memberships.isEmpty()) {
                task.setOrg(memberships.get(0).getOrganization());
            } else {
                // If creator has no org (Super Admin), use assignee's org!
                if (assignee != null) {
                    var assigneeMemberships = membershipRepository.findByUserId(assignee.getId());
                    if (!assigneeMemberships.isEmpty()) {
                        task.setOrg(assigneeMemberships.get(0).getOrganization());
                    }
                }
            }
            // RT-M02 fix: org task - ensure crew is NULL (mutual exclusivity)
            task.setCrew(null);
        } else if (isPersonal) {
            // RT-M02 fix: personal task - ensure org/team/crew are all NULL
            task.setOrg(null);
            task.setTeam(null);
            task.setCrew(null);
        }
        
        if (dueDate != null && dueDate.isBefore(java.time.LocalDate.now(zoneId))) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }

        // Crew scoping + RT-M02 fix: make personal/crew/org mutually exclusive
        if (crewId != null) {
            com.example.taskflow.domain.Crew crew = crewRepository.findById(crewId)
                    .orElseThrow(() -> new IllegalArgumentException("Crew not found"));
            task.setCrew(crew);
            task.setOrg(null);   // RT-M02: crew tasks have no org
            task.setTeam(null);  // RT-M02: crew tasks have no team
            task.setPersonal(false);  // RT-M02: crew tasks are not personal
        }

        task.setPriority(priority != null ? priority : TaskPriority.MEDIUM);
        task.setDueDate(dueDate);
        task.setTags(tags);
        task.setPersonal(isPersonal);

        // Spec state machine:
        //   Personal tasks: TODO -> COMPLETED
        //   Crew tasks: TODO (unclaimed) -> ASSIGNED (claimed/nudged) -> COMPLETED
        //   Org tasks:  ASSIGNED -> SUBMITTED -> APPROVED/REJECTED
        if (isPersonal) {
            task.setCurrentStatus(TaskStatus.TODO);
        } else if (crewId != null) {
            // Crew: unclaimed (no assignee) = TODO, nudged (with assignee) = ASSIGNED
            task.setCurrentStatus(assignee != null ? TaskStatus.ASSIGNED : TaskStatus.TODO);
        } else {
            task.setCurrentStatus(TaskStatus.ASSIGNED);
        }

        // B-16b: Wire projectId
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
            task.setProject(project);
        }

        // CreatedAt/UpdatedAt are handled by @CreationTimestamp / @UpdateTimestamp

        Task savedTask = taskRepository.save(task);
        taskAuditService.recordStatus(savedTask, null, savedTask.getCurrentStatus().name(), savedTask.getCurrentStatus().name(), creator, null);

        // Only notify if there's an assignee (crew tasks may be unclaimed)
        if (assignee != null) {
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
        }

        return mapToTaskResponseDTO(savedTask);
    }

    @Transactional
    public List<TaskResponseDTO> bulkAssignTasks(String title, String description, List<String> assigneeUsernames, User creator, java.time.LocalDate dueDate, String tags) {
        return bulkAssignTasks(title, description, assigneeUsernames, creator, dueDate, tags, null);
    }

    @Transactional
    public List<TaskResponseDTO> bulkAssignTasks(String title, String description, List<String> assigneeUsernames, User creator, java.time.LocalDate dueDate, String tags, Long teamId) {
        String finalTitle = title;
        String finalDescription = description;
        TaskPriority finalPriority = TaskPriority.MEDIUM;

        if (finalTitle == null || finalTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title is required");
        }

        // Mode 1: teamId provided ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ auto-resolve all team members as assignees
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
            throw new IllegalArgumentException("No assignees resolved ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â provide teamId or assigneeUsernames");
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
            task.getAssignee() != null ? task.getAssignee().getUsername() : null,
            task.getCreator() != null ? task.getCreator().getUsername() : null,
            task.getReviewer() != null ? task.getReviewer().getUsername() : null,
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
            task.getRejectionReason(),
            task.isPersonal(),
            task.isArchived(),
            task.getOrg() != null ? task.getOrg().getId() : null,
            task.getOrg() != null ? task.getOrg().getName() : null,
            task.getTeam() != null ? task.getTeam().getId() : null,
            task.getTeam() != null ? task.getTeam().getName() : null,
            task.getProject() != null ? task.getProject().getId() : null,
            task.getProject() != null ? task.getProject().getName() : null,
            task.getCrew() != null ? task.getCrew().getId() : null,
            task.getCrew() != null ? task.getCrew().getName() : null
        );
    }
}