package com.example.taskflow.organization.core.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.core.dto.CreateOrganizationRequestDTO;
import com.example.taskflow.organization.core.dto.OrganizationResponseDTO;
import com.example.taskflow.organization.core.application.OrganizationService;
import com.example.taskflow.user.application.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/organizations", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;

    public OrganizationController(OrganizationService organizationService,
                                  UserService userService) {
        this.organizationService = organizationService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponseDTO> createOrganization(
            @Valid @RequestBody CreateOrganizationRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        OrganizationResponseDTO response = organizationService.createOrganization(
                request.getName(), request.getDescription(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationResponseDTO>> listOrganizations(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.listUserOrganizations(user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ORG_PROFILE_UPDATE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationResponseDTO> updateOrganization(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody com.example.taskflow.organization.core.dto.UpdateOrganizationRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.updateOrganization(id, request.getName(), request.getDescription(), user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponseDTO> getOrganization(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.getOrganization(id, user));
    }
}
