package com.collab.platform.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * File record entity mapped to the {@code file_records} table.
 */
@Data
@TableName("file_records")
public class FileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Auto-increment primary key. */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Public-facing unique file ID (UUID). */
    private String fileId;

    /** Original file name from upload. */
    private String originalName;

    /** MinIO object key (path within bucket). */
    private String objectKey;

    /** MinIO bucket name. */
    private String bucket;

    /** HTTP Content-Type. */
    private String contentType;

    /** File size in bytes. */
    private Long sizeBytes;

    /** Uploader user ID. */
    private Long uploaderId;

    /** Logical delete flag: 0=normal, 1=deleted. */
    private Integer isDeleted;

    /** Creation timestamp. */
    private LocalDateTime createTime;
}
