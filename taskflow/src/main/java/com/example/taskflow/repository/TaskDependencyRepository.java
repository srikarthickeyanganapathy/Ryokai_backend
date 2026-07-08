package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.TaskDependency;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    // Find all dependencies where this task is blocked BY other tasks
    List<TaskDependency> findByTask_Id(Long taskId);

    // Find all dependencies where this task is BLOCKING other tasks
    List<TaskDependency> findByBlocksTask_Id(Long blocksTaskId);

    // Batch-fetch to fix N+1 in mapToTaskResponseDTO
    @Query("SELECT d FROM TaskDependency d WHERE d.task.id IN :taskIds")
    List<TaskDependency> findAllByTaskIdIn(@Param("taskIds") List<Long> taskIds);

    @Query("SELECT d FROM TaskDependency d WHERE d.blocksTask.id IN :taskIds")
    List<TaskDependency> findAllByBlocksTaskIdIn(@Param("taskIds") List<Long> taskIds);
    
    // Check if a specific dependency exists
    boolean existsByTask_IdAndBlocksTask_Id(Long taskId, Long blocksTaskId);
    
    void deleteByTaskId(Long taskId);
    void deleteByBlocksTaskId(Long blocksTaskId);
}
