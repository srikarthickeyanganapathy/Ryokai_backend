package com.example.taskflow.task.domain.validation;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

import com.example.taskflow.organization.membership.domain.ExitRequest;
import com.example.taskflow.organization.membership.domain.LeaveRequest;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.ExitRequestRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.LeaveRequestRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamMemberRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;

@Component
public class TaskHierarchyValidator {

    private final CrewMemberRepository crewMemberRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectRepository projectRepository;
    private final ExitRequestRepository exitRequestRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public TaskHierarchyValidator(CrewMemberRepository crewMemberRepository,
                                  OrganizationMembershipRepository membershipRepository,
                                  TeamRepository teamRepository,
                                  TeamMemberRepository teamMemberRepository,
                                  ProjectRepository projectRepository,
                                  ExitRequestRepository exitRequestRepository,
                                  LeaveRequestRepository leaveRequestRepository) {
        this.crewMemberRepository = crewMemberRepository;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.projectRepository = projectRepository;
        this.exitRequestRepository = exitRequestRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public void validateAssigneeEligibility(User assignee, Long orgId, LocalDate taskDueDate) {
        if (assignee == null || orgId == null) return;

        List<OrganizationMembership> memberships = membershipRepository.findByUserId(assignee.getId())
                .stream()
                .filter(m -> m.getOrganization().getId().equals(orgId))
                .toList();

        if (memberships.isEmpty()) {
            throw new IllegalArgumentException("Assignee is not an active member of this organization.");
        }

        boolean hasExit = exitRequestRepository.existsByUserIdAndOrganizationIdAndStatusIn(
                assignee.getId(), orgId,
                List.of(ExitRequest.ExitRequestStatus.PENDING, ExitRequest.ExitRequestStatus.APPROVED));
        if (hasExit) {
            throw new IllegalStateException("Cannot assign task: Assignee has a pending or approved exit request (offboarding in progress).");
        }

        LocalDate checkDate = (taskDueDate != null) ? taskDueDate : LocalDate.now();
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findOverlappingLeaves(
                assignee.getId(), orgId,
                LeaveRequest.LeaveRequestStatus.APPROVED,
                checkDate, checkDate);

        if (!approvedLeaves.isEmpty()) {
            LeaveRequest leave = approvedLeaves.get(0);
            throw new IllegalStateException("Cannot assign task: Assignee is on approved " + leave.getLeaveType() +
                    " leave from " + leave.getStartDate() + " to " + leave.getEndDate() + ".");
        }
    }

    public void validateCrewTask(Long crewId, User creator, User assignee) {
        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, creator.getId())) {
            throw new IllegalStateException("You must be a member of the crew to create a task in it");
        }
        if (assignee != null && !crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, assignee.getId())) {
            throw new IllegalArgumentException("Assignee must be a member of the crew");
        }
    }

    public void validateOrgOrTeamTask(User creator, User assignee, Long teamId, boolean isSuperAdmin) {
        validateOrgOrTeamTask(creator, assignee, teamId, isSuperAdmin, null);
    }

    public void validateOrgOrTeamTask(User creator, User assignee, Long teamId, boolean isSuperAdmin, LocalDate dueDate) {
        var creatorMembership = membershipRepository.findByUserId(creator.getId());
        var assigneeMembership = membershipRepository.findByUserId(assignee.getId());

        if (assigneeMembership.isEmpty()) {
            throw new IllegalArgumentException("Assignee is not a member of any organization");
        }

        Long targetOrgId = null;

        if (!isSuperAdmin) {
            if (creatorMembership.isEmpty()) {
                throw new IllegalStateException("You must belong to an organization to assign org tasks");
            }

            Long creatorOrgId = creatorMembership.get(0).getOrganization().getId();
            OrganizationMembership creatorOrgMem = creatorMembership.get(0);
            targetOrgId = creatorOrgId;

            OrganizationMembership assigneeOrgMem = assigneeMembership.stream()
                    .filter(m -> m.getOrganization().getId().equals(creatorOrgId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Assignee must be a member of your organization"));

            if (creatorOrgMem.getOrgRole() != null && assigneeOrgMem.getOrgRole() != null) {
                Integer creatorPriority = creatorOrgMem.getOrgRole().getPriority();
                Integer assigneePriority = assigneeOrgMem.getOrgRole().getPriority();
                if (creatorPriority > assigneePriority) {
                    throw new IllegalArgumentException("You do not have enough power (role priority) to assign tasks to this user.");
                }
            }
        } else if (!assigneeMembership.isEmpty()) {
            targetOrgId = assigneeMembership.get(0).getOrganization().getId();
        }

        if (teamId != null) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
            targetOrgId = team.getOrganization().getId();

            if (isSuperAdmin) {
                var creatorMembershipCheck = membershipRepository.findByUserId(creator.getId());
                if (!creatorMembershipCheck.isEmpty()) {
                    if (!team.getOrganization().getId().equals(creatorMembershipCheck.get(0).getOrganization().getId())) {
                        throw new IllegalArgumentException("Team does not belong to your organization");
                    }
                }
            } else {
                if (!team.getOrganization().getId().equals(creatorMembership.get(0).getOrganization().getId())) {
                    throw new IllegalArgumentException("Team does not belong to your organization");
                }
            }

            boolean isCreatorInTeam = teamMemberRepository.existsByIdTeamIdAndIdUserId(teamId, creator.getId());
            boolean isAssigneeInTeam = teamMemberRepository.existsByIdTeamIdAndIdUserId(teamId, assignee.getId());

            if (!isSuperAdmin && !isCreatorInTeam) {
                throw new IllegalStateException("You must be a member of the team to assign tasks to it");
            }
            if (!isAssigneeInTeam) {
                throw new IllegalArgumentException("Assignee is not a member of the team");
            }
        }

        if (targetOrgId != null) {
            validateAssigneeEligibility(assignee, targetOrgId, dueDate);
        }
    }

    public void validatePersonalTask(User creator, User assignee) {
        if (assignee != null && !assignee.getId().equals(creator.getId())) {
            throw new IllegalArgumentException("Personal tasks must be assigned to the creator");
        }
    }

    public void validateProjectForTask(Long projectId, Task task, boolean isPersonal, User assignee) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (isPersonal && (project.getOrganization() != null || project.getTeam() != null)) {
            throw new IllegalArgumentException("Personal tasks cannot belong to team or organization scoped projects");
        }

        if (task.getOrg() != null && project.getOrganization() != null && !project.getOrganization().getId().equals(task.getOrg().getId())) {
            throw new IllegalArgumentException("Project does not belong to the same organization as the task");
        }

        if (project.getTeam() != null && assignee != null) {
            boolean isProjectTeamMember = project.getTeam().getMembers().stream()
                    .anyMatch(m -> m.getId().equals(assignee.getId()));
            if (!isProjectTeamMember) {
                throw new IllegalArgumentException("Assignee is not a member of the project's team");
            }
        }
        
        task.setProject(project);

        if (task.getTeam() == null && project.getTeam() != null) {
            task.setTeam(project.getTeam());
        }
        if (task.getOrg() == null && project.getOrganization() != null) {
            task.setOrg(project.getOrganization());
        }
    }
}