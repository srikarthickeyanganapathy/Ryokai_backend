package com.example.taskflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
    
    Optional<Role> findByNameAndOrganizationIdIsNull(String name);
    
    Optional<Role> findByNameAndOrganizationId(String name, Long organizationId);
    
    List<Role> findByOrganizationId(Long organizationId);
    
    List<Role> findAllByOrderByNameAsc();

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name")
    Optional<Role> findByNameWithPermissions(String name);
}
