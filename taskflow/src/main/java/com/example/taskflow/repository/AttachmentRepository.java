package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.domain.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTask_Id(Long taskId);
    void deleteByTaskId(Long taskId);
}
