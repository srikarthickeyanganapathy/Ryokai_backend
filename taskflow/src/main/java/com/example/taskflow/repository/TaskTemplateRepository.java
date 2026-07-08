package com.example.taskflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.TaskTemplate;

@Repository
public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {
}
