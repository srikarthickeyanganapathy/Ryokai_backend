package com.example.taskflow.saveditem;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.saveditem.SavedItemDTOs.SavedItemRequestDTO;
import com.example.taskflow.saveditem.SavedItemDTOs.SavedItemResponseDTO;
import com.example.taskflow.user.application.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/saved-items", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SavedItemController {

    private final SavedItemService savedItemService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SavedItemResponseDTO>> list(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(savedItemService.getSavedItems(user));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SavedItemResponseDTO> save(@RequestBody SavedItemRequestDTO req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(savedItemService.saveItem(user, req));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unsave(
            @RequestParam SavedEntityType entityType,
            @RequestParam Long entityId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        savedItemService.unsaveItem(user, entityType, entityId);
        return ResponseEntity.noContent().build();
    }
}
