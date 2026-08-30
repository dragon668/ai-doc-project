package com.docwork.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.common.Constants;
import com.docwork.config.MinioConfig;
import com.docwork.dto.ChunkUploadDTO;
import com.docwork.dto.ChunkUploadResultVO;
import com.docwork.entity.UploadChunk;
import com.docwork.mapper.UploadChunkMapper;
import com.docwork.service.StorageService;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final UploadChunkMapper chunkMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void initBuckets() {
        try {
            createBucketIfNotExists(minioConfig.getBucketName());
            createBucketIfNotExists(minioConfig.getChunkBucket());
            log.info("MinIO buckets initialized successfully");
        } catch (Exception e) {
            log.error("Failed to init MinIO buckets", e);
        }
    }

    private void createBucketIfNotExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    @Override
    public ChunkUploadResultVO initChunkUpload(String uploadId, String fileName, String md5,
                                                int totalChunks, long totalSize, Long userId) {
        ChunkUploadResultVO result = new ChunkUploadResultVO();
        result.setUploadId(uploadId);

        // MD5秒传检测：检查是否有相同MD5的完整文件
        String cachedFileKey = redisTemplate.opsForValue().get(Constants.REDIS_DOC_MD5 + md5);
        if (cachedFileKey != null) {
            result.setQuickUpload(true);
            result.setFileKey(cachedFileKey);
            result.setUploaded(true);
            return result;
        }

        // 检查已上传的分片(断点续传)
        List<UploadChunk> existingChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                        .eq(UploadChunk::getUploadId, uploadId)
                        .eq(UploadChunk::getStatus, Constants.UPLOAD_IN_PROGRESS)
        );

        int[] uploadedIndices = existingChunks.stream()
                .mapToInt(UploadChunk::getChunkIndex)
                .toArray();
        result.setUploadedChunks(uploadedIndices);
        result.setUploaded(false);
        result.setQuickUpload(false);

        return result;
    }

    @Override
    public void uploadChunk(ChunkUploadDTO dto, MultipartFile file) {
        try {
            String chunkKey = minioConfig.getChunkBucket() + "/" + dto.getUploadId() + "/" + dto.getChunkIndex();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getChunkBucket())
                    .object(dto.getUploadId() + "/" + dto.getChunkIndex())
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType("application/octet-stream")
                    .build());

            // 记录分片信息到数据库
            UploadChunk chunk = new UploadChunk();
            chunk.setUploadId(dto.getUploadId());
            chunk.setFileName(dto.getFileName());
            chunk.setMd5(dto.getMd5());
            chunk.setChunkIndex(dto.getChunkIndex());
            chunk.setChunkMd5(dto.getChunkMd5() != null ? dto.getChunkMd5() : "");
            chunk.setChunkSize(file.getSize());
            chunk.setTotalChunks(dto.getTotalChunks());
            chunk.setFileKey(chunkKey);
            chunk.setUserId(0L);
            chunk.setStatus(Constants.UPLOAD_IN_PROGRESS);
            chunkMapper.insert(chunk);

            // Redis记录进度
            redisTemplate.opsForSet().add(Constants.REDIS_UPLOAD_CHUNKS + dto.getUploadId(),
                    String.valueOf(dto.getChunkIndex()));
            redisTemplate.expire(Constants.REDIS_UPLOAD_CHUNKS + dto.getUploadId(), 24, TimeUnit.HOURS);

        } catch (Exception e) {
            log.error("上传分片失败: uploadId={}, chunk={}", dto.getUploadId(), dto.getChunkIndex(), e);
            throw new BusinessException("上传分片失败: " + e.getMessage());
        }
    }

    @Override
    public String mergeChunks(String uploadId, String fileName, String md5, Long userId) {
        try {
            List<UploadChunk> chunks = chunkMapper.selectList(
                    new LambdaQueryWrapper<UploadChunk>()
                            .eq(UploadChunk::getUploadId, uploadId)
                            .orderByAsc(UploadChunk::getChunkIndex)
            );

            if (chunks.isEmpty()) {
                throw new BusinessException("未找到分片记录");
            }

            int totalChunks = chunks.get(0).getTotalChunks();
            if (chunks.size() != totalChunks) {
                throw new BusinessException("分片不完整，期望" + totalChunks + "个，实际" + chunks.size() + "个");
            }

            // 生成文件存储key
            String fileKey = "docs/" + UUID.randomUUID().toString().replace("-", "") + "/" + fileName;

            // 合并分片：依次读取每个分片并写入完整文件
            List<InputStream> streams = new ArrayList<>();
            List<ComposeSource> sources = new ArrayList<>();

            for (UploadChunk chunk : chunks) {
                sources.add(ComposeSource.builder()
                        .bucket(minioConfig.getChunkBucket())
                        .object(uploadId + "/" + chunk.getChunkIndex())
                        .build());
            }

            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(fileKey)
                    .sources(sources)
                    .build());

            // 缓存MD5 -> fileKey映射(用于秒传)
            redisTemplate.opsForValue().set(Constants.REDIS_DOC_MD5 + md5, fileKey, 30, TimeUnit.DAYS);

            // 更新分片状态
            for (UploadChunk chunk : chunks) {
                chunk.setStatus(Constants.UPLOAD_MERGED);
                chunkMapper.updateById(chunk);
            }

            // 清理Redis中的上传进度
            redisTemplate.delete(Constants.REDIS_UPLOAD_CHUNKS + uploadId);

            // 异步清理分片文件
            cleanupChunks(uploadId, totalChunks);

            log.info("分片合并成功: uploadId={}, fileKey={}", uploadId, fileKey);
            return fileKey;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("合并分片失败: uploadId={}", uploadId, e);
            throw new BusinessException("合并分片失败: " + e.getMessage());
        }
    }

    private void cleanupChunks(String uploadId, int totalChunks) {
        try {
            for (int i = 0; i < totalChunks; i++) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getChunkBucket())
                        .object(uploadId + "/" + i)
                        .build());
            }
        } catch (Exception e) {
            log.warn("清理分片文件失败，不影响主流程: uploadId={}", uploadId);
        }
    }

    @Override
    public String uploadFile(String objectKey, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(inputStream, -1, 10485760) // 10MB part size
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream downloadFile(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("删除文件失败: {}", objectKey, e);
        }
    }

    @Override
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .method(io.minio.http.Method.GET)
                    .expiry(3600)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("获取下载链接失败: " + e.getMessage());
        }
    }

    @Override
    public long getFileSize(String objectKey) {
        try {
            var stat = minioClient.statObject(io.minio.StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
            return stat.size();
        } catch (Exception e) {
            log.warn("获取文件大小失败: {}", objectKey, e);
            return 0;
        }
    }
}
