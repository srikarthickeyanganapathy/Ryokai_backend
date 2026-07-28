package com.example.taskflow.controller;

import com.example.taskflow.domain.Permission;
import com.example.taskflow.dto.PermissionModuleDTO;
import com.example.taskflow.dto.PermissionResponseDTO;
import com.example.taskflow.repository.PermissionRepository;
import com.example.taskflow.security.PermissionModule;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionCatalogController {

    private final PermissionRepository permissionRepository;

    public PermissionCatalogController(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @GetMapping("/catalog")
    public ResponseEntity<com.example.taskflow.dto.PermissionCatalogResponseDTO> getPermissionCatalog() {
        List<Permission> allPermissions = permissionRepository.findAll();

        Map<String, List<Permission>> byModule = allPermissions.stream()
                .collect(Collectors.groupingBy(Permission::getModule));

        List<PermissionModuleDTO> catalog = new ArrayList<>();

        for (PermissionModule moduleEnum : PermissionModule.values()) {
            String moduleCode = moduleEnum.name();
            List<Permission> permsInModule = byModule.getOrDefault(moduleCode, new ArrayList<>());

            if (permsInModule.isEmpty()) {
                continue;
            }

            // Map and sort permissions using metadata order
            List<PermissionResponseDTO> permissionDTOs = permsInModule.stream()
                    .map(p -> new PermissionResponseDTO(
                            p.getId(),
                            p.getName(),
                            p.getCode(),
                            p.getModule(),
                            p.getCategory(),
                            p.getDescription(),
                            p.isSystem()))
                    .sorted(Comparator.comparingInt(PermissionResponseDTO::getOrder).thenComparing(PermissionResponseDTO::getCode))
                    .collect(Collectors.toList());

            PermissionModuleDTO moduleDTO = new PermissionModuleDTO(
                    moduleCode,
                    moduleEnum.getDisplayName(),
                    com.example.taskflow.util.PermissionMetadataRegistry.getModuleOrder(moduleCode),
                    moduleEnum.getDisplayName() + " permissions", // Generic description
                    permissionDTOs
            );

            catalog.add(moduleDTO);
        }

        // Sort modules using metadata order
        catalog.sort(Comparator.comparingInt(PermissionModuleDTO::getOrder));

        String now = java.time.Instant.now().toString();
        com.example.taskflow.dto.PermissionCatalogResponseDTO response = new com.example.taskflow.dto.PermissionCatalogResponseDTO(1, now, catalog);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(response);
    }
}
