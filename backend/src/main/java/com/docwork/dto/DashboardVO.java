package com.docwork.dto;

import lombok.Data;

import java.util.List;

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
    private long editCount;
    private long contributionCount;
    private long activeDays;
    private List<Integer> activity;
}
