package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.CrewChannel;
import com.example.taskflow.domain.CrewMessage;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ConvertToTaskRequestDTO;
import com.example.taskflow.dto.CrewChannelDTO;
import com.example.taskflow.dto.CrewChannelRequestDTO;
import com.example.taskflow.dto.CrewMessageDTO;
import com.example.taskflow.dto.CrewMessageRequestDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.exception.CrewNotFoundException;
import com.example.taskflow.exception.ResourceNotFoundException;
import com.example.taskflow.repository.CrewChannelRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.CrewMessageRepository;
import com.example.taskflow.repository.CrewRepository;
import com.example.taskflow.repository.TaskRepository;

@Service
public class CrewChannelService {

    private final CrewChannelRepository channelRepository;
    private final CrewMessageRepository messageRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CrewRepository crewRepository;
    private final TaskAssignmentService taskAssignmentService;
    private final TaskRepository taskRepository;

    public CrewChannelService(CrewChannelRepository channelRepository,
                              CrewMessageRepository messageRepository,
                              CrewMemberRepository crewMemberRepository,
                              CrewRepository crewRepository,
                              TaskAssignmentService taskAssignmentService,
                              TaskRepository taskRepository) {
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.crewRepository = crewRepository;
        this.taskAssignmentService = taskAssignmentService;
        this.taskRepository = taskRepository;
    }

    // --- Channels ---

    @Transactional(readOnly = true)
    public List<CrewChannelDTO> getChannels(Long crewId, User user) {
        validateMembership(crewId, user);
        return channelRepository.findByCrew_IdOrderByPositionAsc(crewId).stream()
                .map(c -> new CrewChannelDTO(c.getId(), c.getName(), c.getType(), c.getPosition(), c.getMessages().size()))
                .collect(Collectors.toList());
    }

    @Transactional
    public CrewChannelDTO createChannel(Long crewId, User user, CrewChannelRequestDTO dto) {
        Crew crew = getCrewEntity(crewId);
        validateCreator(crew, user);

        CrewChannel channel = new CrewChannel();
        channel.setCrew(crew);
        channel.setName(dto.getName());
        channel.setType(dto.getType());
        
        long count = channelRepository.findByCrew_IdOrderByPositionAsc(crewId).size();
        channel.setPosition((int) count);
        
        CrewChannel saved = channelRepository.save(channel);
        return new CrewChannelDTO(saved.getId(), saved.getName(), saved.getType(), saved.getPosition(), 0);
    }

    @Transactional
    public void deleteChannel(Long crewId, Long channelId, User user) {
        Crew crew = getCrewEntity(crewId);
        validateCreator(crew, user);

        List<CrewChannel> channels = channelRepository.findByCrew_IdOrderByPositionAsc(crewId);
        if (channels.size() <= 1) {
            throw new IllegalStateException("Cannot delete the last channel in a crew.");
        }

        CrewChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));

        if (!channel.getCrew().getId().equals(crewId)) {
            throw new IllegalStateException("Channel does not belong to this crew.");
        }

        channelRepository.delete(channel);
    }

    // --- Messages ---


    
    @Transactional(readOnly = true)
    public List<CrewMessageDTO> getAllMessages(Long crewId, Long channelId, User user) {
        validateMembership(crewId, user);
        CrewChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));
                
        if (!channel.getCrew().getId().equals(crewId)) {
            throw new IllegalStateException("Channel does not belong to this crew.");
        }

        return messageRepository.findByChannel_IdOrderByCreatedAtAsc(channelId).stream()
                .map(m -> new CrewMessageDTO(m.getId(), m.getAuthor().getUsername(), m.getContent(), m.getTask() != null ? m.getTask().getId() : null, m.getCreatedAt(), m.getEditedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public CrewMessageDTO sendMessage(Long crewId, Long channelId, User user, CrewMessageRequestDTO dto) {
        validateMembership(crewId, user);
        CrewChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));

        if (!channel.getCrew().getId().equals(crewId)) {
            throw new IllegalStateException("Channel does not belong to this crew.");
        }

        CrewMessage msg = new CrewMessage();
        msg.setChannel(channel);
        msg.setAuthor(user);
        msg.setContent(dto.getContent());
        
        if (dto.getTaskId() != null) {
            Task task = taskRepository.findById(dto.getTaskId()).orElse(null);
            msg.setTask(task);
        }

        CrewMessage saved = messageRepository.save(msg);
        return new CrewMessageDTO(saved.getId(), saved.getAuthor().getUsername(), saved.getContent(), saved.getTask() != null ? saved.getTask().getId() : null, saved.getCreatedAt(), saved.getEditedAt());
    }

    @Transactional
    public CrewMessageDTO editMessage(Long crewId, Long channelId, Long messageId, User user, CrewMessageRequestDTO dto) {
        validateMembership(crewId, user);
        CrewMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!msg.getChannel().getId().equals(channelId) || !msg.getChannel().getCrew().getId().equals(crewId)) {
            throw new IllegalStateException("Message does not belong to the specified crew/channel.");
        }

        if (!msg.getAuthor().getId().equals(user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the author can edit this message.");
        }

        msg.setContent(dto.getContent());
        msg.setEditedAt(LocalDateTime.now());
        CrewMessage saved = messageRepository.save(msg);
        return new CrewMessageDTO(saved.getId(), saved.getAuthor().getUsername(), saved.getContent(), saved.getTask() != null ? saved.getTask().getId() : null, saved.getCreatedAt(), saved.getEditedAt());
    }

    @Transactional
    public void deleteMessage(Long crewId, Long channelId, Long messageId, User user) {
        validateMembership(crewId, user);
        CrewMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!msg.getChannel().getId().equals(channelId) || !msg.getChannel().getCrew().getId().equals(crewId)) {
            throw new IllegalStateException("Message does not belong to the specified crew/channel.");
        }

        boolean isAuthor = msg.getAuthor().getId().equals(user.getId());
        boolean isCreator = msg.getChannel().getCrew().getCreator().getId().equals(user.getId());

        if (!isAuthor && !isCreator) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Not authorized to delete this message.");
        }

        messageRepository.delete(msg);
    }

    @Transactional
    public TaskResponseDTO convertMessageToTask(Long crewId, Long channelId, Long messageId, User user, ConvertToTaskRequestDTO dto) {
        validateMembership(crewId, user);
        CrewMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (!msg.getChannel().getId().equals(channelId) || !msg.getChannel().getCrew().getId().equals(crewId)) {
            throw new IllegalStateException("Message does not belong to the specified crew/channel.");
        }

        // Create a new task in the crew
        // isPersonal=false, crewId=crewId
        TaskResponseDTO taskDTO = taskAssignmentService.assignTask(
            dto.getTitle(),
            dto.getDescription() != null ? dto.getDescription() : "Converted from message: " + msg.getContent(),
            null, // assignee initially null
            user, // creator
            null, // no project
            null, // no due date
            null, // tags
            false, // not personal
            null, // no teamId
            crewId
        );

        Task task = taskRepository.findById(taskDTO.getId()).orElseThrow();
        msg.setTask(task);
        messageRepository.save(msg);

        return taskDTO;
    }

    // --- Internal Helpers ---

    private void validateMembership(Long crewId, User user) {
        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not a member of this crew.");
        }
    }

    private Crew getCrewEntity(Long crewId) {
        return crewRepository.findById(crewId)
                .orElseThrow(() -> new CrewNotFoundException("Crew not found with id " + crewId));
    }

    private void validateCreator(Crew crew, User user) {
        if (!crew.getCreator().getId().equals(user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the crew creator can perform this action.");
        }
    }
}
