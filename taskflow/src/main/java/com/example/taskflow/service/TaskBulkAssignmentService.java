package com.example.taskflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskScope;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.BulkAssignResponseDTO;
import com.example.taskflow.dto.TaskAssignmentCommand;
import com.example.taskflow.dto.TaskRequestDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.repository.TeamRepository;

@Service
public class TaskBulkAssignmentService {

    private final TaskAssignmentService taskAssignmentService;
    private final UserService userService;
    private final TeamRepository teamRepository;

    public TaskBulkAssignmentService(TaskAssignmentService taskAssignmentService,
                                     UserService userService,
                                     TeamRepository teamRepository) {
        this.taskAssignmentService = taskAssignmentService;
        this.userService = userService;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public BulkAssignResponseDTO bulkAssignTasks(String title, String description, List<String> assigneeUsernames, User creator, LocalDate dueDate, String tags) {
        return bulkAssignTasks(title, description, assigneeUsernames, creator, dueDate, tags, null);
    }

    @Transactional
    public BulkAssignResponseDTO bulkAssignTasks(String title, String description, List<String> assigneeUsernames, User creator, LocalDate dueDate, String tags, Long teamId) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title is required");
        }

        List<String> resolvedUsernames = assigneeUsernames;
        if (teamId != null && (assigneeUsernames == null || assigneeUsernames.isEmpty())) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
            resolvedUsernames = team.getMembers().stream()
                    .filter(member -> !member.getId().equals(creator.getId())) // exclude creator
                    .map(User::getUsername)
                    .collect(Collectors.toList());
            if (resolvedUsernames.isEmpty()) {
                throw new IllegalArgumentException("Team has no other members to assign tasks to");
            }
        }

        if (resolvedUsernames == null || resolvedUsernames.isEmpty()) {
            throw new IllegalArgumentException("No assignees resolved - provide teamId or assigneeUsernames");
        }

        List<TaskResponseDTO> successfulTasks = new ArrayList<>();
        Map<String, String> failedAssignees = new HashMap<>();

        for (String username : resolvedUsernames) {
            try {
                User assignee = userService.getCurrentUser(username);
                
                TaskRequestDTO reqDto = new TaskRequestDTO();
                reqDto.setTitle(title);
                reqDto.setDescription(description);
                reqDto.setPriority(TaskPriority.MEDIUM);
                reqDto.setDueDate(dueDate);
                reqDto.setTags(tags);
                reqDto.setPersonal(false);
                
                TaskScope scope = TaskScope.org(teamId);
                
                TaskAssignmentCommand cmd = TaskAssignmentCommand.builder()
                        .request(reqDto)
                        .assignor(creator)
                        .assignee(assignee)
                        .scope(scope)
                        .build();
                        
                TaskResponseDTO result = taskAssignmentService.assignTask(cmd);
                successfulTasks.add(result);
            } catch (Exception ex) {
                failedAssignees.put(username, ex.getMessage());
            }
        }
        
        if (successfulTasks.isEmpty() && !failedAssignees.isEmpty()) {
             throw new IllegalArgumentException("All assignments failed. Sample error: " + failedAssignees.values().iterator().next());
        }

        return new BulkAssignResponseDTO(successfulTasks, failedAssignees);
    }
}
