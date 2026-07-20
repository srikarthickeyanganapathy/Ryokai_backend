package com.example.taskflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ConvertToTaskRequestDTO;
import com.example.taskflow.dto.ProjectResponseDTO;
import com.example.taskflow.dto.CrewChannelDTO;
import com.example.taskflow.dto.CrewChannelRequestDTO;
import com.example.taskflow.dto.CrewInviteDTO;
import com.example.taskflow.dto.CrewMemberDTO;
import com.example.taskflow.dto.CrewMessageDTO;
import com.example.taskflow.dto.CrewMessageRequestDTO;
import com.example.taskflow.dto.CrewRequestDTO;
import com.example.taskflow.dto.CrewResponseDTO;
import com.example.taskflow.dto.ProjectSummaryDTO;
import com.example.taskflow.dto.CrewTaskRequestDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.service.CrewChannelService;
import com.example.taskflow.service.CrewService;
import com.example.taskflow.service.CrewMembershipService;
import com.example.taskflow.service.CrewProjectService;
import com.example.taskflow.service.TaskAssignmentService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crews")
public class CrewController {

    private final CrewService crewService;
    private final CrewMembershipService crewMembershipService;
    private final CrewProjectService crewProjectService;
    private final CrewChannelService crewChannelService;
    private final TaskAssignmentService taskAssignmentService;
    private final UserService userService;

    public CrewController(CrewService crewService, CrewMembershipService crewMembershipService,
                          CrewProjectService crewProjectService, CrewChannelService crewChannelService,
                          TaskAssignmentService taskAssignmentService, UserService userService) {
        this.crewService = crewService;
        this.crewMembershipService = crewMembershipService;
        this.crewProjectService = crewProjectService;
        this.crewChannelService = crewChannelService;
        this.taskAssignmentService = taskAssignmentService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        return userService.getCurrentUser(userDetails.getUsername());
    }

    // --- Crew CRUD ---

    @PostMapping
    public ResponseEntity<CrewResponseDTO> createCrew(@Valid @RequestBody CrewRequestDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(crewService.createCrew(getCurrentUser(userDetails), dto));
    }

    @GetMapping
    public ResponseEntity<List<CrewResponseDTO>> getMyCrews(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewService.getMyCrews(getCurrentUser(userDetails)));
    }

    @GetMapping("/{crewId}")
    public ResponseEntity<CrewResponseDTO> getCrew(@PathVariable Long crewId,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewService.getCrew(crewId, getCurrentUser(userDetails)));
    }

    @PutMapping("/{crewId}")
    public ResponseEntity<CrewResponseDTO> updateCrew(@PathVariable Long crewId,
                                                      @Valid @RequestBody CrewRequestDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewService.updateCrew(crewId, getCurrentUser(userDetails), dto));
    }

    @DeleteMapping("/{crewId}")
    public ResponseEntity<Void> deleteCrew(@PathVariable Long crewId,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        crewService.deleteCrew(crewId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/discover")
    public ResponseEntity<Page<CrewResponseDTO>> discoverCrews(
            @RequestParam(required = false) String keyword,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(crewService.discoverCrews(keyword, pageable, user));
    }

    @PostMapping("/{crewId}/join")
    public ResponseEntity<CrewResponseDTO> joinPublicCrew(
            @PathVariable Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(crewMembershipService.joinPublicCrew(crewId, user));
    }

    // --- Members & Invites ---

    @GetMapping("/{crewId}/members")
    public ResponseEntity<List<CrewMemberDTO>> getMembers(@PathVariable Long crewId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewMembershipService.getMembers(crewId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{crewId}/invite")
    public ResponseEntity<CrewInviteDTO> inviteMember(@PathVariable Long crewId,
                                                      @RequestBody java.util.Map<String, String> payload,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        String email = payload.get("email");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        return ResponseEntity.ok(crewMembershipService.inviteMember(crewId, getCurrentUser(userDetails), email));
    }

    /**
     * P2: PUBLIC_LINK invite  -  creates an invite with email=null.
     * Anyone authenticated who has the invite UUID can accept it.
     * Allowed for PUBLIC_LINK crews (any member) or INVITE_ONLY (creator only).
     */
    @PostMapping("/{crewId}/invite/link")
    public ResponseEntity<CrewInviteDTO> createPublicLinkInvite(@PathVariable Long crewId,
                                                                @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(crewMembershipService.createPublicLinkInvite(crewId, getCurrentUser(userDetails)));
    }

    @PostMapping("/invites/{inviteId}/accept")
    public ResponseEntity<CrewResponseDTO> acceptInvite(@PathVariable UUID inviteId,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewMembershipService.acceptInvite(inviteId, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{crewId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long crewId,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        crewMembershipService.removeMember(crewId, userId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{crewId}/transfer-ownership/{newOwnerId}")
    public ResponseEntity<Void> transferOwnership(@PathVariable Long crewId,
                                                  @PathVariable Long newOwnerId,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        crewMembershipService.transferOwnership(crewId, newOwnerId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{crewId}/leave")
    public ResponseEntity<Void> leaveCrew(@PathVariable Long crewId,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        crewMembershipService.leaveCrew(crewId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    // --- Channels ---

    @GetMapping("/{crewId}/channels")
    public ResponseEntity<List<CrewChannelDTO>> getChannels(@PathVariable Long crewId,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewChannelService.getChannels(crewId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{crewId}/channels")
    public ResponseEntity<CrewChannelDTO> createChannel(@PathVariable Long crewId,
                                                        @Valid @RequestBody CrewChannelRequestDTO dto,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(crewChannelService.createChannel(crewId, getCurrentUser(userDetails), dto));
    }

    @DeleteMapping("/{crewId}/channels/{channelId}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long crewId,
                                              @PathVariable Long channelId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        crewChannelService.deleteChannel(crewId, channelId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    // --- Messages ---

    @GetMapping("/{crewId}/channels/{channelId}/messages")
    public ResponseEntity<List<CrewMessageDTO>> getMessages(@PathVariable Long crewId,
                                                            @PathVariable Long channelId,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewChannelService.getAllMessages(crewId, channelId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{crewId}/channels/{channelId}/messages")
    public ResponseEntity<CrewMessageDTO> sendMessage(@PathVariable Long crewId,
                                                      @PathVariable Long channelId,
                                                      @Valid @RequestBody CrewMessageRequestDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(crewChannelService.sendMessage(crewId, channelId, getCurrentUser(userDetails), dto));
    }

    @PutMapping("/{crewId}/channels/{channelId}/messages/{messageId}")
    public ResponseEntity<CrewMessageDTO> editMessage(@PathVariable Long crewId,
                                                      @PathVariable Long channelId,
                                                      @PathVariable Long messageId,
                                                      @Valid @RequestBody CrewMessageRequestDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewChannelService.editMessage(crewId, channelId, messageId, getCurrentUser(userDetails), dto));
    }

    @DeleteMapping("/{crewId}/channels/{channelId}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long crewId,
                                              @PathVariable Long channelId,
                                              @PathVariable Long messageId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        crewChannelService.deleteMessage(crewId, channelId, messageId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{crewId}/channels/{channelId}/messages/{messageId}/convert-to-task")
    public ResponseEntity<TaskResponseDTO> convertMessageToTask(@PathVariable Long crewId,
                                                                @PathVariable Long channelId,
                                                                @PathVariable Long messageId,
                                                                @Valid @RequestBody ConvertToTaskRequestDTO dto,
                                                                @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(crewChannelService.convertMessageToTask(crewId, channelId, messageId, getCurrentUser(userDetails), dto));
    }

    // --- Crew Tasks ---

    /**
     * Create a crew task. Crew tasks have no assignee (claim-based model).
     * The task starts as TODO (unclaimed). Any crew member can later claim it
     * via POST /api/tasks/{taskId}/claim.
     */
    @PostMapping("/{crewId}/tasks")
    public ResponseEntity<TaskResponseDTO> createCrewTask(@PathVariable Long crewId,
                                                          @Valid @RequestBody CrewTaskRequestDTO dto,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        com.example.taskflow.dto.TaskRequestDTO req = new com.example.taskflow.dto.TaskRequestDTO();
        req.setTitle(dto.getTitle());
        req.setDescription(dto.getDescription());
        req.setPriority(dto.getPriority());
        req.setDueDate(dto.getDueDate());
        req.setTags(dto.getTags());
        req.setPersonal(false);
        req.setProjectId(dto.getProjectId());
        
        com.example.taskflow.dto.TaskAssignmentCommand cmd = com.example.taskflow.dto.TaskAssignmentCommand.builder()
                .request(req)
                .assignor(creator)
                .assignee(null)
                .scope(com.example.taskflow.domain.TaskScope.crew(crewId))
                .build();
                
        TaskResponseDTO response = taskAssignmentService.assignTask(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // --- Project Sharing Endpoints ---

    @GetMapping("/{crewId}/projects")
    public ResponseEntity<List<ProjectResponseDTO>> getCrewProjects(
            @PathVariable Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        List<ProjectResponseDTO> projects = crewProjectService.getCrewProjects(crewId, user);
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/{crewId}/projects/{projectId}")
    public ResponseEntity<ProjectResponseDTO> shareProject(
            @PathVariable Long crewId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        ProjectResponseDTO response = crewProjectService.shareProject(crewId, projectId, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{crewId}/projects/{projectId}")
    public ResponseEntity<Void> unshareProject(
            @PathVariable Long crewId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        crewProjectService.unshareProject(crewId, projectId, user);
        return ResponseEntity.noContent().build();
    }
}
