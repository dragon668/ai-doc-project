package com.docwork.service;

import com.docwork.dto.DashboardVO;

public interface DashboardService {
    DashboardVO getDashboardData(Long userId);
}
