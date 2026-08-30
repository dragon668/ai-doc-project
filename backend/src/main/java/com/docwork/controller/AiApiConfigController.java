package com.docwork.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.Result;
import com.docwork.entity.AiApiConfig;
import com.docwork.interceptor.UserContext;
import com.docwork.mapper.AiApiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-config")
@RequiredArgsConstructor
public class AiApiConfigController {

    private final AiApiConfigMapper aiApiConfigMapper;

    @GetMapping("/list")
    public Result<List<AiApiConfig>> list() {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(aiApiConfigMapper.selectList(
                new LambdaQueryWrapper<AiApiConfig>()
                        .eq(AiApiConfig::getUserId, userId)
                        .orderByDesc(AiApiConfig::getIsDefault)
        ));
    }

    @PostMapping
    public Result<AiApiConfig> save(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        AiApiConfig config = new AiApiConfig();
        config.setUserId(userId);
        config.setProvider(String.valueOf(body.getOrDefault("provider", "openai")));
        config.setApiKey(String.valueOf(body.getOrDefault("apiKey", "")));
        config.setBaseUrl(String.valueOf(body.getOrDefault("baseUrl", "")));
        config.setModelName(String.valueOf(body.getOrDefault("modelName", "gpt-4o-mini")));
        config.setIsDefault(Integer.valueOf(body.getOrDefault("isDefault", 0).toString()));
        aiApiConfigMapper.insert(config);
        return Result.success(config);
    }
}
