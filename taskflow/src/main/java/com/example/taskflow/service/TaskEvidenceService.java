package com.example.taskflow.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.EvidenceType;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskEvidence;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskEvidenceDTO;
import com.example.taskflow.dto.TaskEvidenceRequestDTO;
import com.example.taskflow.exception.TaskNotFoundException;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.TaskEvidenceRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.security.RoleStrategyFactory;

@Service
public class TaskEvidenceService {

    private final TaskEvidenceRepository evidenceRepository;
    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;
    private final RoleStrategyFactory roleStrategyFactory;

    public TaskEvidenceService(TaskEvidenceRepository evidenceRepository,
                               TaskRepository taskRepository,
                               TaskAuditService taskAuditService,
                               RoleStrategyFactory roleStrategyFactory) {
        this.evidenceRepository = evidenceRepository;
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.roleStrategyFactory = roleStrategyFactory;
    }

    @Transactional(readOnly = true)
    public List<TaskEvidenceDTO> listEvidence(Long taskId, User user) {
        Task task = getTask(taskId);
        assertCanView(user, task);
        return evidenceRepository.findByTask_Id(taskId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TaskEvidenceDTO addEvidence(Long taskId, TaskEvidenceRequestDTO request, User user) {
        Task task = getTask(taskId);
        assertCanEdit(user, task);
        validateByType(request);

        TaskEvidence evidence = new TaskEvidence();
        evidence.setTask(task);
        evidence.setType(request.getType());
        evidence.setAddedBy(user);
        evidence.setTitle(request.getTitle());
        evidence.setUrl(request.getUrl());
        evidence.setUnfurlJson(request.getUnfurlJson());
        evidence.setGhRepo(request.getGhRepo());
        evidence.setGhPrNo(request.getGhPrNo());
        evidence.setGhCommit(request.getGhCommit());
        evidence.setGhState(request.getGhState());
        evidence.setImageKey(request.getImageKey());
        evidence.setImageW(request.getImageW());
        evidence.setImageH(request.getImageH());
        evidence.setVideoUrl(request.getVideoUrl());
        evidence.setDurationS(request.getDurationS());
        evidence.setCodeLang(request.getCodeLang());
        evidence.setCodeBody(request.getCodeBody());
        evidence.setNoteMd(request.getNoteMd());

        TaskEvidence saved = evidenceRepository.save(evidence);
        taskAuditService.recordStatus(
                task,
                task.getCurrentStatus().name(),
                task.getCurrentStatus().name(),
                "EVIDENCE_ADDED",
                user,
                request.getTitle() != null ? request.getTitle() : request.getType().name(),
                Map.of(
                        "evidenceId", saved.getId(),
                        "type", request.getType().name()
                )
        );
        return toDto(saved);
    }

    @Transactional
    public void deleteEvidence(Long taskId, Long evidenceId, User user) {
        Task task = getTask(taskId);
        TaskEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new TaskNotFoundException("Evidence not found: " + evidenceId));

        if (!evidence.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Evidence does not belong to this task");
        }

        boolean isAdder = evidence.getAddedBy() != null && evidence.getAddedBy().getId().equals(user.getId());
        boolean canEdit = roleStrategyFactory.getStrategy(user).canEdit(user, task);
        if (!isAdder && !canEdit) {
            throw new UnauthorizedActionException("Only the adder or a task editor can delete evidence.");
        }

        evidenceRepository.delete(evidence);
        taskAuditService.recordStatus(
                task,
                task.getCurrentStatus().name(),
                task.getCurrentStatus().name(),
                "EVIDENCE_REMOVED",
                user,
                "Removed evidence " + evidenceId,
                Map.of("evidenceId", evidenceId, "type", evidence.getType().name())
        );
    }

    private void validateByType(TaskEvidenceRequestDTO request) {
        EvidenceType type = request.getType();
        switch (type) {
            case LINK, GITHUB -> {
                if (request.getUrl() == null || request.getUrl().isBlank()) {
                    throw new IllegalArgumentException(type + " evidence requires a url");
                }
            }
            case SCREENSHOT -> {
                if (request.getImageKey() == null || request.getImageKey().isBlank()) {
                    throw new IllegalArgumentException("SCREENSHOT evidence requires imageKey");
                }
            }
            case RECORDING -> {
                if ((request.getVideoUrl() == null || request.getVideoUrl().isBlank())
                        && (request.getUrl() == null || request.getUrl().isBlank())) {
                    throw new IllegalArgumentException("RECORDING evidence requires videoUrl or url");
                }
            }
            case SNIPPET -> {
                if (request.getCodeBody() == null || request.getCodeBody().isBlank()) {
                    throw new IllegalArgumentException("SNIPPET evidence requires codeBody");
                }
            }
            case NOTE -> {
                if (request.getNoteMd() == null || request.getNoteMd().isBlank()) {
                    throw new IllegalArgumentException("NOTE evidence requires noteMd");
                }
            }
        }
    }

    private Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with ID: " + taskId));
    }

    private void assertCanView(User user, Task task) {
        if (!roleStrategyFactory.getStrategy(user).canViewTask(user, task)) {
            throw new UnauthorizedActionException("You are not authorized to view this task.");
        }
    }

    private void assertCanEdit(User user, Task task) {
        if (task.isLocked()) {
            throw new UnauthorizedActionException("Task is currently locked awaiting reassignment.");
        }
        if (!task.isPersonal() && task.getCurrentStatus() != com.example.taskflow.domain.TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Evidence can only be attached when task is IN_PROGRESS.");
        }
        boolean isAssignee = task.isPersonal()
                ? (task.getCreator() != null && task.getCreator().getId().equals(user.getId()))
                : (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId()));
        if (!isAssignee) {
            throw new UnauthorizedActionException("Only the task assignee can add evidence to this task.");
        }
    }

    private TaskEvidenceDTO toDto(TaskEvidence e) {
        return new TaskEvidenceDTO(
                e.getId(),
                e.getTask() != null ? e.getTask().getId() : null,
                e.getType(),
                e.getAddedBy() != null ? e.getAddedBy().getUsername() : null,
                e.getTitle(),
                e.getUrl(),
                e.getUnfurlJson(),
                e.getGhRepo(),
                e.getGhPrNo(),
                e.getGhCommit(),
                e.getGhState(),
                e.getImageKey(),
                e.getImageW(),
                e.getImageH(),
                e.getVideoUrl(),
                e.getDurationS(),
                e.getCodeLang(),
                e.getCodeBody(),
                e.getNoteMd(),
                e.getCreatedAt()
        );
    }
}
