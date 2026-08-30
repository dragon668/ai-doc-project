package com.docwork.mq;

import com.docwork.common.BusinessException;
import com.docwork.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class AiDocumentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final StorageService storageService;

    @Value("${ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public AiDocumentClient(StorageService storageService) {
        this.storageService = storageService;
    }

    public void vectorizeDocument(Long docId, Long workspaceId, String fileKey, String docType, String title) {
        try {
            String url = aiServiceUrl + "/api/document/vectorize";
            Map<String, Object> payload = new HashMap<>();
            payload.put("workspace_id", workspaceId);
            payload.put("doc_id", docId);
            payload.put("doc_title", title);
            payload.put("doc_type", docType);
            payload.put("minio_url", storageService.getPresignedUrl(fileKey));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || !"ok".equals(response.getBody().get("status"))) {
                throw new BusinessException("AI文档向量化失败: " + (response.getBody() == null ? "空响应" : response.getBody()));
            }
        } catch (RestClientException e) {
            throw new BusinessException("调用AI文档向量化服务失败: " + e.getMessage(), e);
        }
    }
}
