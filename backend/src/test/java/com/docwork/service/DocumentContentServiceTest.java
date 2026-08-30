package com.docwork.service;

import com.docwork.entity.Document;
import com.docwork.mapper.DocumentMapper;
import com.docwork.mapper.DocumentVersionMapper;
import com.docwork.mapper.WorkspaceMemberMapper;
import com.docwork.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentContentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentVersionMapper versionMapper;

    @Mock
    private WorkspaceMemberMapper memberMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    void shouldReadAndPersistTextContent() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setFileKey("docs/abc.md");
        doc.setType("md");
        doc.setVersion(1);
        doc.setCreatorId(7L);
        doc.setWorkspaceId(9L);
        doc.setPermission(1);
        doc.setDeleted(0);

        when(documentMapper.selectById(1L)).thenReturn(doc);
        when(storageService.downloadFile("docs/abc.md")).thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        String content = documentService.getDocumentContent(1L);
        assertThat(content).isEqualTo("hello");

        documentService.updateDocumentContent(1L, "world", 7L);

        verify(storageService).uploadFile(eq("docs/abc.md"), any(), eq("text/markdown; charset=utf-8"));
        verify(versionMapper).insert(any());
    }
}
