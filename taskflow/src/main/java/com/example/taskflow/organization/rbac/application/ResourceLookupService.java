package com.example.taskflow.organization.rbac.application;

import com.example.taskflow.organization.rbac.dto.ResourceLookupDTO;
import java.util.List;

public interface ResourceLookupService {
    List<ResourceLookupDTO> lookupResources(Long organizationId, String resourceType);
}
