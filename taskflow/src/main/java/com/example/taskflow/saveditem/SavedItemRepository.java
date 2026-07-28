package com.example.taskflow.saveditem;

import com.example.taskflow.saveditem.SavedEntityType;
import com.example.taskflow.saveditem.SavedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {
    List<SavedItem> findByUserIdOrderBySavedAtDesc(Long userId);
    Optional<SavedItem> findByUserIdAndEntityTypeAndEntityId(Long userId, SavedEntityType entityType, Long entityId);
}
