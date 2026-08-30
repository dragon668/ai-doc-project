package com.docwork.dto;

import lombok.Data;

@Data
public class ChunkUploadResultVO {
    private String uploadId;
    private boolean uploaded;
    private int[] uploadedChunks;
    private String fileKey;
    private boolean quickUpload;
}
