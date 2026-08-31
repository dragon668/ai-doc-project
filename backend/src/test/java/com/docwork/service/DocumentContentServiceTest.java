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
import static org.mockito.ArgumentMatchers.argThat;
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

        // 每次在线编辑应生成新的不可变版本文件对象，保证历史版本可回滚
        verify(storageService).uploadFile(eq("docs/1/v2.md"), any(), eq("text/markdown; charset=utf-8"));
        verify(versionMapper).insert(any());
    }

    @Test
    void shouldCreateBlankMarkdownDocument() {
        Document savedDoc = new Document();
        savedDoc.setId(11L);
        savedDoc.setTitle("会议纪要.md");
        savedDoc.setType("md");
        savedDoc.setVersion(1);
        savedDoc.setWorkspaceId(9L);
        savedDoc.setCreatorId(7L);
        savedDoc.setPermission(1);
        savedDoc.setStatus(1);
        savedDoc.setFileKey("docs/meeting.md");

        when(documentMapper.insert(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(11L);
            doc.setFileKey("docs/meeting.md");
            return 1;
        });

        Document doc = documentService.createTextDocument("会议纪要.md", "# 会议纪要\n\n", 9L, null, 7L);

        assertThat(doc.getId()).isEqualTo(11L);
        assertThat(doc.getType()).isEqualTo("md");
        assertThat(doc.getVersion()).isEqualTo(1);
        verify(storageService).uploadFile(argThat(fileKey -> fileKey != null && fileKey.startsWith("docs/") && fileKey.endsWith(".md")), any(), eq("text/markdown; charset=utf-8"));
        verify(versionMapper).insert(any());
    }
}
