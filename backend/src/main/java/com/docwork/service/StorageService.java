package com.docwork.service;

import com.docwork.dto.ChunkUploadDTO;
import com.docwork.dto.ChunkUploadResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface StorageService {

    /** 初始化分片上传，支持MD5秒传检测 */
    ChunkUploadResultVO initChunkUpload(String uploadId, String fileName, String md5,
                                         int totalChunks, long totalSize, Long userId);

    /** 上传单个分片 */
    void uploadChunk(ChunkUploadDTO dto, MultipartFile file);

    /** 合并分片，返回完整文件的MinIO key */
    String mergeChunks(String uploadId, String fileName, String md5, Long userId);

    /** 直接上传小文件 */
    String uploadFile(String objectKey, InputStream inputStream, String contentType);

    /** 下载文件 */
    InputStream downloadFile(String objectKey);

    /** 删除文件 */
    void deleteFile(String objectKey);

    /** 获取文件预签名URL */
    String getPresignedUrl(String objectKey);

    /** 获取文件大小 */
    long getFileSize(String objectKey);

    /** 初始化MinIO Bucket */
    void initBuckets();
}
