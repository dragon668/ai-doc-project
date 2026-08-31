package com.docwork.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.BusinessException;
import com.docwork.entity.Document;
import com.docwork.entity.ShareLink;
import com.docwork.mapper.DocumentMapper;
import com.docwork.mapper.ShareLinkMapper;
import com.docwork.service.impl.ShareServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareLinkMapper shareLinkMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ShareServiceImpl shareService;

    private ShareLink buildLink(String code, String password) {
        ShareLink link = new ShareLink();
        link.setCode(code);
        link.setPassword(password);
        link.setExpireTime(LocalDateTime.now().plusHours(24));
        link.setViewCount(0);
        link.setMaxViews(-1);
        link.setDeleted(0);
        link.setDocumentId(99L);
        return link;
    }

    @Test
    void shouldRejectWrongPassword() {
        ShareLink link = buildLink("code1", "secret");
        when(shareLinkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(link);

        assertThatThrownBy(() -> shareService.getSharedDocument("code1", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提取码错误");
    }

    @Test
    void shouldAcceptCorrectPassword() {
        ShareLink link = buildLink("code1", "secret");
        when(shareLinkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(link);

        Document doc = new Document();
        doc.setId(99L);
        doc.setTitle("doc.md");
        doc.setType("md");
        doc.setFileKey("docs/abc.md");
        doc.setDeleted(0);

        when(documentMapper.selectById(99L)).thenReturn(doc);

        Document result = shareService.getSharedDocument("code1", "secret");

        assertThat(result.getId()).isEqualTo(99L);
        verify(shareLinkMapper).updateById(any(ShareLink.class));
    }

    @Test
    void shouldAccessSharedContentForMarkdown() {
        ShareLink link = buildLink("code1", "");
        when(shareLinkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(link);

        Document doc = new Document();
        doc.setId(99L);
        doc.setTitle("doc.md");
        doc.setType("md");
        doc.setFileKey("docs/abc.md");
        doc.setDeleted(0);

        when(documentMapper.selectById(99L)).thenReturn(doc);
        when(storageService.downloadFile("docs/abc.md"))
                .thenReturn(new ByteArrayInputStream("# hello".getBytes(StandardCharsets.UTF_8)));

        String content = shareService.getSharedDocumentContent("code1", null);

        assertThat(content).isEqualTo("# hello");
    }

    @Test
    void shouldRejectSharedContentForNonTextDoc() {
        ShareLink link = buildLink("code1", "");
        when(shareLinkMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(link);

        Document doc = new Document();
        doc.setId(99L);
        doc.setTitle("doc.pdf");
        doc.setType("pdf");
        doc.setFileKey("docs/abc.pdf");
        doc.setDeleted(0);

        when(documentMapper.selectById(99L)).thenReturn(doc);

        assertThatThrownBy(() -> shareService.getSharedDocumentContent("code1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持");
    }
}

