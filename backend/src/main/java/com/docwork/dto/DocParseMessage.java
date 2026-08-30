package com.docwork.dto;

import lombok.Data;

@Data
public class DocParseMessage {
    private Long documentId;
    private Long workspaceId;
    private String fileKey;
    private String docType;
    private String action; // VECTORIZE or REPARSE
}
