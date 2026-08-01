package com.example.taskflow.saveditem.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.saveditem.domain.SavedItem;
import com.example.taskflow.saveditem.domain.SavedEntityType;
import java.util.List;
import java.util.Optional;

public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {
    List<SavedItem> findByUserIdOrderBySavedAtDesc(Long userId);
    Optional<SavedItem> findByUserIdAndEntityTypeAndEntityId(Long userId, SavedEntityType entityType, Long entityId);
}
