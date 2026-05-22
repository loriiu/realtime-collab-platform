package com.collab.platform.file.controller;

import com.collab.platform.common.core.result.Result;
import com.collab.platform.file.dto.FileUploadResponseDTO;
import com.collab.platform.file.entity.FileRecord;
import com.collab.platform.file.service.FileEventPublisher;
import com.collab.platform.file.service.FileService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for file upload and pre-signed URL generation.
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;
    private final FileEventPublisher fileEventPublisher;

    public FileController(FileService fileService, FileEventPublisher fileEventPublisher) {
        this.fileService = fileService;
        this.fileEventPublisher = fileEventPublisher;
    }

    /**
     * Upload a file to MinIO.
     *
     * <p>Validates file size (≤50MB), MIME type, and extension whitelist,
     * uploads to MinIO, persists metadata, publishes a file.uploaded domain event,
     * and returns a pre-signed download URL.</p>
     *
     * @param userId the authenticated user ID (from gateway header)
     * @param file   the multipart file
     * @return FileUploadResponseDTO with pre-signed URL
     */
    @PostMapping("/upload")
    public Result<FileUploadResponseDTO> upload(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("file") MultipartFile file) {
        try {
            FileRecord record = fileService.upload(file, userId);

            // Publish file.uploaded domain event
            fileEventPublisher.publishFileUploaded(record);

            // Generate pre-signed GET URL (TTL=3600s)
            String preSignedUrl = fileService.getPresignedGetUrl(record.getObjectKey());

            FileUploadResponseDTO response = FileUploadResponseDTO.builder()
                    .fileId(record.getFileId())
                    .fileName(record.getOriginalName())
                    .url(preSignedUrl)
                    .size(record.getSizeBytes())
                    .contentType(record.getContentType())
                    .build();

            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("File upload validation failed: {}", e.getMessage());
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("File upload failed for userId={}", userId, e);
            return Result.fail(500, "文件上传失败，请稍后重试");
        }
    }

    /**
     * Get a pre-signed download URL for a file by its public fileId.
     *
     * @param fileId the public-facing file UUID
     * @return pre-signed download URL (valid for 3600s)
     */
    @GetMapping("/url/{fileId}")
    public Result<String> getFileUrl(@PathVariable("fileId") String fileId) {
        try {
            String url = fileService.getPreSignedUrl(fileId);
            return Result.success(url);
        } catch (IllegalArgumentException e) {
            log.warn("File not found: fileId={}", fileId);
            return Result.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL for fileId={}", fileId, e);
            return Result.fail(500, "获取文件URL失败");
        }
    }

    /**
     * List files uploaded by the current user, with pagination.
     *
     * @param userId the authenticated user ID
     * @param page   1-based page number (default 1)
     * @param size   page size (default 20, max 50)
     * @return paginated file list with total count
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> listFiles(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<FileRecord> result = fileService.listByUploader(userId, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return Result.success(data);
    }
}
