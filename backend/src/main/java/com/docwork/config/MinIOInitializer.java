package com.docwork.config;

import com.docwork.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动初始化MinIO Bucket
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinIOInitializer implements ApplicationRunner {

    private final StorageService storageService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("正在初始化MinIO Bucket...");
        storageService.initBuckets();
        log.info("MinIO Bucket初始化完成");
    }
}
