package com.example.taskflow.service;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.taskflow.domain.Attachment;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.exception.TaskNotFoundException;
import com.example.taskflow.repository.AttachmentRepository;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "doc", "docx", "xls", "xlsx", "txt", "zip", "md");

    // Simple MIME type map to cross check tika detected types.
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "application/zip", "text/markdown");

    private final AttachmentRepository attachmentRepository;
    private final String uploadDir;
    private final Tika tika;

    private final long maxFileSize;

    public FileStorageService(AttachmentRepository attachmentRepository,
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            @Value("${app.upload.max-file-size:10485760}") long maxFileSize) {
        this.attachmentRepository = attachmentRepository;
        this.uploadDir = uploadDir;
        this.maxFileSize = maxFileSize;
        this.tika = new Tika();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (Exception e) {
            throw new RuntimeException("Could not create upload directory!");
        }
    }

    @Transactional
    public Attachment store(MultipartFile file, Task task, User user) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File too large");
        }

        // 1. Sanitize original filename and check extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null)
            originalFilename = "unknown";
        String safeName = sanitizeFilename(originalFilename);
        String extension = extractExtension(safeName).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Extension not allowed: " + extension);
        }

        // 2. Magic bytes sniffing
        String detectedType;
        try (InputStream is = file.getInputStream()) {
            detectedType = tika.detect(is, safeName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not verify file content");
        }

        if (!isAllowedDetectedType(detectedType)) {
            throw new IllegalArgumentException("File content not allowed: " + detectedType);
        }

        // 3. Generate UUID filename and save
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path target = Paths.get(uploadDir).resolve(storedFilename);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Could not store file", e);
        }

        // 4. Save metadata
        Attachment attachment = new Attachment();
        attachment.setTask(task);
        attachment.setUploadedBy(user);
        attachment.setFilename(storedFilename);
        attachment.setOriginalFilename(safeName);
        attachment.setContentType(detectedType);
        attachment.setFileSize(file.getSize());
        attachment.setStoragePath(target.toString());
        attachment.setCreatedAt(LocalDateTime.now());

        return attachmentRepository.save(attachment);
    }

    public String storeAvatar(MultipartFile file, User user) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Avatar too large");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null)
            originalFilename = "unknown";
        String safeName = sanitizeFilename(originalFilename);
        String extension = extractExtension(safeName).toLowerCase();

        Set<String> imageExts = Set.of("png", "jpg", "jpeg", "gif", "webp");
        if (!imageExts.contains(extension)) {
            throw new IllegalArgumentException("Extension not allowed for avatar: " + extension);
        }

        String detectedType;
        try (InputStream is = file.getInputStream()) {
            detectedType = tika.detect(is, safeName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not verify file content");
        }

        if (!detectedType.startsWith("image/")) {
            throw new IllegalArgumentException("File content not an image: " + detectedType);
        }

        String storedFilename = UUID.randomUUID() + "." + extension;
        Path target = Paths.get(uploadDir).resolve("avatars").resolve(user.getId().toString()).resolve(storedFilename);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Could not store avatar", e);
        }

        return "/uploads/avatars/" + user.getId() + "/" + storedFilename;
    }

    @Transactional(readOnly = true)
    public Attachment getById(Long id) {
        return attachmentRepository.findById(id).orElseThrow(() -> new TaskNotFoundException("Attachment not found"));
    }

    public Resource load(Attachment attachment) {
        try {
            Path file = Paths.get(attachment.getStoragePath());
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + attachment.getFilename());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read file: " + attachment.getFilename(), e);
        }
    }

    @Transactional
    public void delete(Long attachmentId) {
        Attachment attachment = getById(attachmentId);
        try {
            Files.deleteIfExists(Paths.get(attachment.getStoragePath()));
        } catch (Exception e) {
            log.warn("Failed to delete file {} for attachment {}", attachment.getStoragePath(), attachmentId, e);
        }
        attachmentRepository.delete(attachment);
    }

    private String sanitizeFilename(String filename) {
        String safeName = filename.replace("\\", "/");
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        if (safeName.contains("..") || safeName.contains("/") || safeName.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        if (safeName.length() > 255) {
            safeName = safeName.substring(0, 255);
        }
        return safeName;
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? "" : filename.substring(dotIndex + 1);
    }

    private boolean isAllowedDetectedType(String detectedType) {
        // Tika usually detects correct mime types like application/pdf or image/png
        return ALLOWED_MIME_TYPES.contains(detectedType);
    }
}
