package com.example.taskflow.workspace.api;

import com.example.taskflow.workspace.application.ModeManagerService;
import com.example.taskflow.workspace.domain.Mode;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.user.application.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/workspace/mode", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WorkspaceModeController {

    private final ModeManagerService modeManagerService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getMode(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        Mode mode = modeManagerService.getMode(user.getId());
        return ResponseEntity.ok(Map.of("mode", mode.name()));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateMode(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        String modeStr = request.get("mode");
        if (modeStr == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Mode mode;
        try {
            mode = Mode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        
        modeManagerService.setMode(user.getId(), user.getUsername(), mode);
        return ResponseEntity.noContent().build();
    }
}
