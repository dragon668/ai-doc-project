package com.docwork.dto;

import lombok.Data;

@Data
public class DashboardVO {
    private long totalDocs;
    private long totalSize;
    private long usedStorage;
    private long totalStorage;
    private long vectorizedDocs;
    private long parsingDocs;
    private long sharedLinks;
    private long totalConversations;
}
