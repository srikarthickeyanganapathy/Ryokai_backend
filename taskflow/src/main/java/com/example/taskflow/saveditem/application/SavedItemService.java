package com.example.taskflow.saveditem.application;
import com.example.taskflow.saveditem.infrastructure.SavedItemRepository;
import com.example.taskflow.saveditem.domain.SavedEntityType;
import com.example.taskflow.saveditem.domain.SavedItem;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.saveditem.api.SavedItemDTOs.SavedItemRequestDTO;
import com.example.taskflow.saveditem.api.SavedItemDTOs.SavedItemResponseDTO;
import com.example.taskflow.note.infrastructure.NoteRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedItemService {

    private final SavedItemRepository savedItemRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final NoteRepository noteRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;

    public List<SavedItemResponseDTO> getSavedItems(User user) {
        return savedItemRepository.findByUserIdOrderBySavedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public SavedItemResponseDTO saveItem(User user, SavedItemRequestDTO req) {
        return savedItemRepository
                .findByUserIdAndEntityTypeAndEntityId(user.getId(), req.getEntityType(), req.getEntityId())
                .map(this::toDto) // already saved ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â idempotent
                .orElseGet(() -> {
                    SavedItem item = new SavedItem();
                    item.setUser(user);
                    item.setEntityType(req.getEntityType());
                    item.setEntityId(req.getEntityId());
                    return toDto(savedItemRepository.save(item));
                });
    }

    @Transactional
    public void unsaveItem(User user, SavedEntityType entityType, Long entityId) {
        savedItemRepository.findByUserIdAndEntityTypeAndEntityId(user.getId(), entityType, entityId)
                .ifPresent(savedItemRepository::delete);
    }

    /**
     * Resolves title/subtitle per entity type. If the referenced entity no
     * longer exists (deleted task/project/etc.), returns a "(deleted)"
     * placeholder instead of throwing, so one stale bookmark can't break
     * the whole saved-items list.
     */
    private SavedItemResponseDTO toDto(SavedItem item) {
        String title = "(deleted)";
        String subtitle = null;

        try {
            switch (item.getEntityType()) {
                case TASK -> {
                    var t = taskRepository.findById(item.getEntityId()).orElse(null);
                    if (t != null) { title = t.getTitle(); subtitle = "Task"; }
                }
                case PROJECT -> {
                    var p = projectRepository.findById(item.getEntityId()).orElse(null);
                    if (p != null) { title = p.getName(); subtitle = "Project"; }
                }
                case NOTE -> {
                    var n = noteRepository.findById(item.getEntityId()).orElse(null);
                    if (n != null) { title = n.getTitle(); subtitle = "Note"; }
                }
                case ORGANIZATION -> {
                    var o = organizationRepository.findById(item.getEntityId()).orElse(null);
                    if (o != null) { title = o.getName(); subtitle = "Organization"; }
                }
                case TEAM -> {
                    var tm = teamRepository.findById(item.getEntityId()).orElse(null);
                    if (tm != null) { title = tm.getName(); subtitle = "Team"; }
                }
            }
        } catch (Exception ignored) {
            // Leave as "(deleted)" placeholder on any lookup failure.
        }

        return new SavedItemResponseDTO(item.getId(), item.getEntityType(), item.getEntityId(),
                title, subtitle, item.getSavedAt());
    }
}
