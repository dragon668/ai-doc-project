package com.docwork.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docwork.common.Result;
import com.docwork.common.SecretCryptoService;
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
    private final SecretCryptoService secretCryptoService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        Long userId = UserContext.getCurrentUserId();
        List<AiApiConfig> configs = aiApiConfigMapper.selectList(
                new LambdaQueryWrapper<AiApiConfig>()
                        .eq(AiApiConfig::getUserId, userId)
                        .orderByDesc(AiApiConfig::getIsDefault));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (AiApiConfig config : configs) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", config.getId());
            item.put("provider", config.getProvider());
            item.put("baseUrl", config.getBaseUrl());
            item.put("modelName", config.getModelName());
            item.put("isDefault", config.getIsDefault());
            String key = secretCryptoService.decrypt(config.getApiKey());
            item.put("apiKey", key.isBlank() ? "" : key.substring(0, Math.min(4, key.length())) + "****");
            result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping
    public Result<AiApiConfig> save(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getCurrentUserId();
        Long configId = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        AiApiConfig config = configId == null ? null : aiApiConfigMapper.selectById(configId);
        if (config == null || !userId.equals(config.getUserId())) {
            config = new AiApiConfig();
            config.setUserId(userId);
        }
        config.setUserId(userId);
        config.setProvider(String.valueOf(body.getOrDefault("provider", "openai")));
        String apiKey = String.valueOf(body.getOrDefault("apiKey", ""));
        if (!apiKey.endsWith("****") && !apiKey.isBlank()) {
            config.setApiKey(secretCryptoService.encrypt(apiKey));
        }
        config.setBaseUrl(String.valueOf(body.getOrDefault("baseUrl", "")));
        config.setModelName(String.valueOf(body.getOrDefault("modelName", "gpt-4o-mini")));
        config.setIsDefault(Integer.valueOf(body.getOrDefault("isDefault", 0).toString()));
        if (config.getId() == null) aiApiConfigMapper.insert(config);
        else aiApiConfigMapper.updateById(config);
        config.setApiKey("");
        return Result.success(config);
    }
}
