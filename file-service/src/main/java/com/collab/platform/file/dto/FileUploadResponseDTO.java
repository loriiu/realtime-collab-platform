package com.collab.platform.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * File upload response DTO returned to the client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Public-facing file ID (UUID). */
    private String fileId;

    /** Original file name. */
    private String fileName;

    /** Pre-signed download URL (valid for 3600s). */
    private String url;

    /** File size in bytes. */
    private Long size;

    /** HTTP Content-Type. */
    private String contentType;
}
