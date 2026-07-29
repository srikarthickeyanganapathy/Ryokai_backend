package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.organization.rbac.domain.Scope;

public interface ScopeRepository extends JpaRepository<Scope, Long> {
    Optional<Scope> findByCode(String code);
}
