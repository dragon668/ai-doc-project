package com.docwork.mq;

import com.docwork.common.Constants;
import com.docwork.dto.DocParseMessage;
import com.docwork.entity.Document;
import com.docwork.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocParseConsumerTest {

    private static class TestStringRedisTemplate extends StringRedisTemplate {
        private final ValueOperations<String, String> valueOperations;

        TestStringRedisTemplate(ValueOperations<String, String> valueOperations) {
            this.valueOperations = valueOperations;
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }
    }

    private DocumentService documentService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private AiDocumentClient aiDocumentClient;
    private DocParseConsumer consumer;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        valueOperations = mock(ValueOperations.class);
        redisTemplate = new TestStringRedisTemplate(valueOperations);
        objectMapper = new ObjectMapper();
        aiDocumentClient = mock(AiDocumentClient.class);

        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(true);

        consumer = new DocParseConsumer(documentService, redisTemplate, objectMapper, aiDocumentClient);
    }

    private Document mockDocument(Long docId, String title) {
        Document doc = new Document();
        doc.setId(docId);
        doc.setTitle(title);
        return doc;
    }

    @Test
    void onMessage_shouldCallAiVectorizationService() throws Exception {
        DocParseMessage parseMsg = new DocParseMessage();
        parseMsg.setDocumentId(42L);
        parseMsg.setWorkspaceId(7L);
        parseMsg.setFileKey("docs/123/a.pdf");
        parseMsg.setDocType("pdf");
        parseMsg.setAction("VECTORIZE");

        String message = objectMapper.writeValueAsString(parseMsg);
        when(documentService.getDocument(42L)).thenReturn(mockDocument(42L, "a.pdf"));

        consumer.onMessage(message);

        verify(documentService).updateDocStatus(42L, Constants.DOC_PARSING);
        verify(aiDocumentClient).vectorizeDocument(42L, 7L, "docs/123/a.pdf", "pdf", "a.pdf");
        verify(documentService).updateDocStatus(42L, Constants.DOC_VECTORIZED);
    }

    @Test
    void onMessage_shouldSkipWhenLockAlreadyExists() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).thenReturn(false);

        DocParseMessage parseMsg = new DocParseMessage();
        parseMsg.setDocumentId(99L);
        parseMsg.setWorkspaceId(5L);
        parseMsg.setFileKey("docs/999/b.txt");
        parseMsg.setDocType("txt");
        parseMsg.setAction("VECTORIZE");

        consumer.onMessage(objectMapper.writeValueAsString(parseMsg));

        verify(aiDocumentClient, never()).vectorizeDocument(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }
}
