package com.example.taskflow.note.application;

import com.example.taskflow.crew.domain.Crew;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewRepository;
import com.example.taskflow.note.infrastructure.NoteRepository;
import com.example.taskflow.note.domain.Note;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.note.api.NoteDTOs.NoteRequestDTO;
import com.example.taskflow.note.api.NoteDTOs.NoteResponseDTO;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.user.domain.User;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoteService {

    private static final int MAX_TAGS = 12;
    private static final int MAX_TAG_LENGTH = 50;

    private final NoteRepository noteRepository;
    private final OrganizationRepository organizationRepository;
    private final CrewRepository crewRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final CrewMemberRepository crewMemberRepository;

    /**
     * Personal notes (no workspace scope).
     */
    public List<NoteResponseDTO> getNotes(User user) {
        return noteRepository.findByUserIdOrderByIsPinnedDescUpdatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    /**
     * Organization-scoped notes. Requires org membership.
     */
    public List<NoteResponseDTO> getOrganizationNotes(User user, Long orgId) {
        requireOrgMember(user, orgId);
        return noteRepository.findByOrganizationIdOrderByIsPinnedDescUpdatedAtDesc(orgId)
                .stream().map(this::toDto).toList();
    }

    /**
     * Crew-scoped notes. Requires crew membership.
     */
    public List<NoteResponseDTO> getCrewNotes(User user, Long crewId) {
        requireCrewMember(user, crewId);
        return noteRepository.findByCrewIdOrderByIsPinnedDescUpdatedAtDesc(crewId)
                .stream().map(this::toDto).toList();
    }

    /**
     * Resolve the requested workspace scope and return its notes.
     * No scope params = personal workspace.
     */
    public List<NoteResponseDTO> getNotesInScope(User user, Long orgId, Long crewId) {
        assertSingleScope(orgId, crewId);
        if (orgId != null) return getOrganizationNotes(user, orgId);
        if (crewId != null) return getCrewNotes(user, crewId);
        return getNotes(user);
    }

    @Transactional
    public NoteResponseDTO create(User user, NoteRequestDTO req, Long orgId, Long crewId) {
        assertSingleScope(orgId, crewId);
        Note note = new Note();
        applyRequest(note, req);
        note.setUser(user);
        if (orgId != null) {
            requireOrgMember(user, orgId);
            note.setOrganization(organizationRepository.getReferenceById(orgId));
        } else if (crewId != null) {
            requireCrewMember(user, crewId);
            note.setCrew(crewRepository.getReferenceById(crewId));
        }
        return toDto(noteRepository.save(note));
    }

    @Transactional
    public NoteResponseDTO update(User user, Long id, NoteRequestDTO req) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + id));
        assertAccess(user, note);
        applyRequest(note, req);
        return toDto(noteRepository.save(note));
    }

    @Transactional
    public void delete(User user, Long id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + id));
        assertAccess(user, note);
        noteRepository.delete(note);
    }

    /**
     * A user may touch a note when they own it (personal),
     * or are a member of the org/crew the note belongs to.
     */
    private void assertAccess(User user, Note note) {
        if (note.getUser() != null && note.getUser().getId().equals(user.getId())) return;
        if (note.getOrganization() != null && organizationMembershipRepository
                .existsByUserIdAndOrganizationId(user.getId(), note.getOrganization().getId())) return;
        if (note.getCrew() != null && crewMemberRepository
                .existsByIdCrewIdAndIdUserId(note.getCrew().getId(), user.getId())) return;
        throw new SecurityException("Cannot modify another user's note");
    }

    private void requireOrgMember(User user, Long orgId) {
        if (!organizationMembershipRepository.existsByUserIdAndOrganizationId(user.getId(), orgId)) {
            throw new SecurityException("You are not a member of this organization");
        }
    }

    private void requireCrewMember(User user, Long crewId) {
        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId())) {
            throw new SecurityException("You are not a member of this crew");
        }
    }

    private void assertSingleScope(Long orgId, Long crewId) {
        if (orgId != null && crewId != null) {
            throw new IllegalArgumentException("Cannot scope a note to both an organization and a crew");
        }
    }

    private void applyRequest(Note note, NoteRequestDTO req) {
        note.setTitle(req.getTitle());
        note.setContent(req.getContent());
        note.setIsPinned(Boolean.TRUE.equals(req.getIsPinned()));
        note.setColor(req.getColor());
        note.setTags(normalizeTags(req.getTags()));
    }

    /** Trim, lowercase, de-duplicate, cap count & length. */
    private Set<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) return new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (raw == null) continue;
            String tag = raw.trim().toLowerCase().replaceAll("\\s+", "-");
            if (tag.isEmpty()) continue;
            if (tag.length() > MAX_TAG_LENGTH) tag = tag.substring(0, MAX_TAG_LENGTH);
            tags.add(tag);
            if (tags.size() >= MAX_TAGS) break;
        }
        return tags;
    }

    private NoteResponseDTO toDto(Note n) {
        return new NoteResponseDTO(n.getId(), n.getTitle(), n.getContent(),
                n.getIsPinned(), n.getColor(),
                n.getTags() == null ? List.of() : n.getTags().stream().sorted().toList(),
                n.getOrganization() == null ? null : n.getOrganization().getId(),
                n.getCrew() == null ? null : n.getCrew().getId(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}
