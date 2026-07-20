package com.example.taskflow.service;

import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Whiteboard;
import com.example.taskflow.dto.WhiteboardDTOs.WhiteboardRequestDTO;
import com.example.taskflow.dto.WhiteboardDTOs.WhiteboardResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.CrewRepository;
import com.example.taskflow.repository.WhiteboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhiteboardService {

    private final WhiteboardRepository whiteboardRepository;
    private final CrewRepository crewRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<WhiteboardResponseDTO> list(User user, Long crewId) {
        assertMember(user, crewId);
        return whiteboardRepository.findByCrewIdOrderByUpdatedAtDesc(crewId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public WhiteboardResponseDTO create(User user, Long crewId, WhiteboardRequestDTO req) {
        assertMember(user, crewId);
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new IllegalArgumentException("Crew not found: " + crewId));

        Whiteboard board = new Whiteboard();
        board.setCrew(crew);
        board.setTitle(req.getTitle());
        board.setCreatedBy(user);
        return toDto(whiteboardRepository.save(board));
    }

    @Transactional
    public WhiteboardResponseDTO saveSnapshot(User user, Long crewId, Long boardId, String dataUrl) {
        assertMember(user, crewId);
        Whiteboard board = whiteboardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Whiteboard not found: " + boardId));
        if (!board.getCrew().getId().equals(crewId)) {
            throw new UnauthorizedActionException("Whiteboard does not belong to this crew.");
        }
        board.setSnapshotDataUrl(dataUrl);
        return toDto(whiteboardRepository.save(board));
    }

    @Transactional
    public void delete(User user, Long crewId, Long boardId) {
        assertMember(user, crewId);
        Whiteboard board = whiteboardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Whiteboard not found: " + boardId));
        whiteboardRepository.delete(board);
    }

    /** Same crew-membership gate WhiteboardSocketController re-checks on every draw event. */
    void assertMember(User user, Long crewId) {
        boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId());
        if (!isMember) {
            throw new UnauthorizedActionException("You are not a member of this crew.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canDraw(Long boardId, String username) {
        User user = userService.findByUsername(username).orElse(null);
        if (user == null) return false;

        Whiteboard board = whiteboardRepository.findById(boardId).orElse(null);
        if (board == null) return false;

        return crewMemberRepository.existsByIdCrewIdAndIdUserId(board.getCrew().getId(), user.getId());
    }

    private WhiteboardResponseDTO toDto(Whiteboard b) {
        return new WhiteboardResponseDTO(b.getId(), b.getTitle(), b.getSnapshotDataUrl(),
                b.getCreatedBy().getUsername(), b.getUpdatedAt());
    }
}
