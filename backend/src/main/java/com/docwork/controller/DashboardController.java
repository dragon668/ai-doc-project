package com.docwork.controller;

import com.docwork.common.Result;
import com.docwork.dto.DashboardVO;
import com.docwork.interceptor.UserContext;
import com.docwork.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardVO> getDashboard() {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(dashboardService.getDashboardData(userId));
    }
}
