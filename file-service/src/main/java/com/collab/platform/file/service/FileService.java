package com.collab.platform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collab.platform.file.entity.FileRecord;
import com.collab.platform.file.mapper.FileRecordMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core file service logic: upload to MinIO, persist metadata, generate pre-signed URLs.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final long MAX_FILE_SIZE = 52_428_800L; // 50 MB

    /** P45: Allowed file extensions (lowercase). */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "zip"
    );

    /** P45: Allowed MIME types. */
    private static final java.util.Set<String> ALLOWED_MIME_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain", "application/zip",
            "application/x-zip-compressed"
    );

    private static final int PRE_SIGNED_URL_TTL = 3600; // seconds

    private final MinioClient minioClient;
    private final FileRecordMapper fileRecordMapper;

    @Value("${minio.bucket}")
    private String bucket;

    public FileService(MinioClient minioClient, FileRecordMapper fileRecordMapper) {
        this.minioClient = minioClient;
        this.fileRecordMapper = fileRecordMapper;
    }

    /**
     * Upload a file to MinIO and persist the metadata record.
     *
     * @param file     the multipart file from the client
     * @param userId   the authenticated uploader ID
     * @return the persisted FileRecord
     */
    public FileRecord upload(MultipartFile file, Long userId) throws Exception {
        // Size validation (P43)
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制 (最大 50MB)");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // P45: MIME type + extension whitelist validation
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }

        String extension = getExtension(originalName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("不支持的文件扩展名: " + extension);
        }

        // Generate object key: {userId}/{yyyy-MM-dd}/{uuid}.{ext}
        String datePath = java.time.LocalDate.now().toString(); // yyyy-MM-dd
        String fileUuid = UUID.randomUUID().toString();
        String objectKey = userId + "/" + datePath + "/" + fileUuid + "." + extension.toLowerCase();

        // Upload to MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        }
        log.info("File uploaded to MinIO: bucket={}, objectKey={}, size={}", bucket, objectKey, file.getSize());

        // Persist file record
        FileRecord record = new FileRecord();
        record.setFileId(fileUuid);
        record.setOriginalName(originalName);
        record.setObjectKey(objectKey);
        record.setBucket(bucket);
        record.setContentType(contentType);
        record.setSizeBytes(file.getSize());
        record.setUploaderId(userId);
        record.setIsDeleted(0);
        record.setCreateTime(LocalDateTime.now());
        fileRecordMapper.insert(record);

        log.info("File record persisted: fileId={}, id={}", fileUuid, record.getId());
        return record;
    }

    /**
     * Generate a pre-signed GET URL for downloading a file.
     *
     * <p>P42: Pre-signed URLs use localhost:9000 (configured via minio.endpoint).</p>
     *
     * @param objectKey the MinIO object key
     * @return pre-signed GET URL valid for PRE_SIGNED_URL_TTL seconds
     */
    public String getPresignedGetUrl(String objectKey) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(PRE_SIGNED_URL_TTL, TimeUnit.SECONDS)
                        .build()
        );
    }

    /**
     * Get a pre-signed URL for a file by its public fileId.
     * Checks that the file exists and is not logically deleted.
     *
     * @param fileId the public-facing file UUID
     * @return pre-signed GET URL
     */
    public String getPreSignedUrl(String fileId) throws Exception {
        FileRecord record = fileRecordMapper.selectOne(
                new LambdaQueryWrapper<FileRecord>()
                        .eq(FileRecord::getFileId, fileId)
                        .eq(FileRecord::getIsDeleted, 0)
        );
        if (record == null) {
            throw new IllegalArgumentException("文件不存在或已删除: " + fileId);
        }
        return getPresignedGetUrl(record.getObjectKey());
    }

    /**
     * Look up a file record by public fileId.
     *
     * @param fileId the public-facing file UUID
     * @return FileRecord or null
     */
    public FileRecord getByFileId(String fileId) {
        return fileRecordMapper.selectOne(
                new LambdaQueryWrapper<FileRecord>()
                        .eq(FileRecord::getFileId, fileId)
                        .eq(FileRecord::getIsDeleted, 0)
        );
    }

    /**
     * List files uploaded by a specific user, with pagination.
     *
     * @param uploaderId the user ID
     * @param page       1-based page number
     * @param size       page size (default 20, max 50)
     * @return paginated file records (is_deleted=0, ordered by create_time DESC)
     */
    public IPage<FileRecord> listByUploader(Long uploaderId, int page, int size) {
        Page<FileRecord> pageObj = new Page<>(page, Math.min(size, 50));
        return fileRecordMapper.selectPage(pageObj,
                new LambdaQueryWrapper<FileRecord>()
                        .eq(FileRecord::getUploaderId, uploaderId)
                        .eq(FileRecord::getIsDeleted, 0)
                        .orderByDesc(FileRecord::getCreateTime)
        );
    }

    /**
     * Extract the file extension from a filename.
     *
     * @param filename the original filename
     * @return lowercase extension without dot, or null if none
     */
    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(dotIndex + 1);
    }
}
