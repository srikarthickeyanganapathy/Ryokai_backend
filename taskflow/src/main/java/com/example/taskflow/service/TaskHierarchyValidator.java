package com.example.taskflow.service;

import org.springframework.stereotype.Component;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.repository.TeamRepository;

@Component
public class TaskHierarchyValidator {

    private final CrewMemberRepository crewMemberRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectRepository projectRepository;

    public TaskHierarchyValidator(CrewMemberRepository crewMemberRepository,
                                  OrganizationMembershipRepository membershipRepository,
                                  TeamRepository teamRepository,
                                  TeamMemberRepository teamMemberRepository,
                                  ProjectRepository projectRepository) {
        this.crewMemberRepository = crewMemberRepository;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.projectRepository = projectRepository;
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
        var creatorMembership = membershipRepository.findByUserId(creator.getId());
        var assigneeMembership = membershipRepository.findByUserId(assignee.getId());

        if (assigneeMembership.isEmpty()) {
            throw new IllegalArgumentException("Assignee is not a member of any organization");
        }

        if (!isSuperAdmin) {
            if (creatorMembership.isEmpty()) {
                throw new IllegalStateException("You must belong to an organization to assign org tasks");
            }

            Long creatorOrgId = creatorMembership.get(0).getOrganization().getId();
            OrganizationMembership creatorOrgMem = creatorMembership.get(0);

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
        }

        if (teamId != null) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

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
        } else {
            if (creator.getId().equals(assignee.getId()) && !isSuperAdmin) {
                throw new IllegalArgumentException("You cannot assign org tasks to yourself (unless via a crew or team)");
            }
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
