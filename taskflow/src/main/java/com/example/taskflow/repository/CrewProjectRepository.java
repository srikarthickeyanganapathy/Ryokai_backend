package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.CrewProject;
import com.example.taskflow.domain.CrewProjectId;

@Repository
public interface CrewProjectRepository extends JpaRepository<CrewProject, CrewProjectId> {

    List<CrewProject> findByIdCrewId(Long crewId);

    List<CrewProject> findByIdProjectId(Long projectId);
}
