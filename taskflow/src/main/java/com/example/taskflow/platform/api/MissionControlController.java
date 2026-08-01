package com.example.taskflow.platform.api;

import com.example.taskflow.platform.api.dto.MissionControlDTO;
import com.example.taskflow.platform.application.MissionControlService;
import com.example.taskflow.user.application.UserService;
import com.example.taskflow.user.domain.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/mission-control", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class MissionControlController {

    private final MissionControlService missionControlService;
    private final UserService userService;

    public MissionControlController(MissionControlService missionControlService, UserService userService) {
        this.missionControlService = missionControlService;
        this.userService = userService;
    }

    @GetMapping("/personal/context")
    public ResponseEntity<MissionControlDTO> getPersonalContext(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(missionControlService.getPersonalContext(user));
    }

    @GetMapping("/crews/{crewId}/context")
    public ResponseEntity<MissionControlDTO> getCrewContext(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long crewId) {
        
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(missionControlService.getCrewContext(user, crewId));
    }

    @GetMapping("/organizations/{orgId}/context")
    public ResponseEntity<MissionControlDTO> getOrganizationContext(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orgId) {
        
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(missionControlService.getOrganizationContext(user, orgId));
    }
}
