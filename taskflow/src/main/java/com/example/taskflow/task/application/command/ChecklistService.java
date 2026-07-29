package com.example.taskflow.task.application.command;

import com.example.taskflow.task.domain.model.ChecklistItem;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.response.ChecklistItemDTO;
import com.example.taskflow.task.api.request.ChecklistItemRequestDTO;
import com.example.taskflow.task.mapper.TaskResponseMapper;
import com.example.taskflow.task.infrastructure.persistence.ChecklistItemRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.task.security.TaskPermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.taskflow.task.application.orchestration.TaskAuditService;

@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final TaskRepository taskRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final TaskAuditService taskAuditService;
    private final TaskResponseMapper taskResponseMapper;
    private final TaskPermissionHandler taskPermissionHandler;

    @Transactional
    public ChecklistItemDTO addChecklistItem(Long taskId, String text, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        ChecklistItem item = new ChecklistItem();
        item.setTask(task);
        item.setText(text);
        item.setIsCompleted(false);
        item.setCreatedBy(user);
        ChecklistItem saved = checklistItemRepository.save(item);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), 
                "CHECKLIST_ADDED", user, text, Map.of("checklistItem", text));
        return taskResponseMapper.mapToChecklistItemDTO(saved);
    }

    @Transactional
    public List<ChecklistItemDTO> addChecklistItems(Long taskId, User user, List<ChecklistItemRequestDTO> items) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        List<ChecklistItem> createdItems = java.util.stream.IntStream.range(0, items.size())
                .mapToObj(i -> {
                    ChecklistItemRequestDTO dto = items.get(i);
                    ChecklistItem item = new ChecklistItem();
                    item.setTask(task);
                    item.setText(dto.getText());
                    item.setDisplayOrder(i);
                    item.setCreatedBy(user);
                    item.setIsCompleted(false);
                    return item;
                }).collect(Collectors.toList());

        List<ChecklistItem> saved = checklistItemRepository.saveAll(createdItems);

        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(),
                "CHECKLIST_ADDED", user, "Added " + saved.size() + " checklist items", null);

        return saved.stream().map(taskResponseMapper::mapToChecklistItemDTO).collect(Collectors.toList());
    }

    @Transactional
    public ChecklistItemDTO toggleChecklistItem(Long taskId, Long itemId, User user) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist item not found"));

        if (!item.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Item does not belong to this task");
        }

        if (item.getTask().isLocked()) {
            throw new com.example.taskflow.shared.exception.UnauthorizedActionException("Task is locked awaiting reassignment.");
        }

        if (!item.getTask().isPersonal() && item.getTask().getCurrentStatus() != com.example.taskflow.task.domain.model.TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Checklist items can only be toggled when task is IN_PROGRESS.");
        }

        item.setIsCompleted(!item.getIsCompleted());
        ChecklistItem saved = checklistItemRepository.save(item);

        taskAuditService.recordStatus(item.getTask(), item.getTask().getCurrentStatus().name(), item.getTask().getCurrentStatus().name(),
                "CHECKLIST_TOGGLED", user, "Toggled item: " + item.getText() + " to " + item.getIsCompleted(), null);

        return taskResponseMapper.mapToChecklistItemDTO(saved);
    }

    @Transactional
    public void deleteChecklistItem(Long taskId, Long itemId, User user) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Checklist item not found"));
        
        if (!item.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Checklist item does not belong to this task");
        }

        if (item.getCreatedBy() != null && !item.getCreatedBy().getId().equals(user.getId())) {
            throw new com.example.taskflow.shared.exception.UnauthorizedActionException("Only the creator of the checklist item can delete it.");
        }

        checklistItemRepository.delete(item);
        taskAuditService.recordStatus(item.getTask(), item.getTask().getCurrentStatus().name(), 
                item.getTask().getCurrentStatus().name(), "CHECKLIST_REMOVED", user, item.getText(), 
                Map.of("itemId", item.getId(), "text", item.getText()));
    }

    @Transactional
    public void reorderChecklistItems(Long taskId, List<Long> itemIds, User user) {
        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            ChecklistItem item = checklistItemRepository.findById(itemId).orElse(null);
            if (item != null && item.getTask().getId().equals(taskId)) {
                boolean isCreator = item.getCreatedBy() != null && item.getCreatedBy().getId().equals(user.getId());
                if (!isCreator && !taskPermissionHandler.hasPermission(null, user, item.getTask(), "TASK_OVERRIDE")) {
                    throw new com.example.taskflow.shared.exception.UnauthorizedActionException(
                            "Only the creator of checklist item '" + item.getText() + "' can reorder it.");
                }
                item.setDisplayOrder(i);
                checklistItemRepository.save(item);
            }
        }
    }
}