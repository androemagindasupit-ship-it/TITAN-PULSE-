package com.titanpulse.app;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native allowlist for providers that are permitted to use a securely stored API key.
 * Custom providers are intentionally excluded from native secret-backed requests.
 */
public final class AiProviderRegistry {
    public static final class ProviderSpec {
        public final String id;
        public final String adapter;
        public final String baseUrl;
        ProviderSpec(String id, String adapter, String baseUrl) {
            this.id = id;
            this.adapter = adapter;
            this.baseUrl = baseUrl;
        }
    }

    private static final Map<String, ProviderSpec> PROVIDERS;
    static {
        Map<String, ProviderSpec> m = new HashMap<>();
        add(m, "gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta");
        add(m, "openai", "openai", "https://api.openai.com/v1");
        add(m, "openrouter", "openai", "https://openrouter.ai/api/v1");
        add(m, "anthropic", "anthropic", "https://api.anthropic.com/v1");
        add(m, "groq", "openai", "https://api.groq.com/openai/v1");
        add(m, "mistral", "openai", "https://api.mistral.ai/v1");
        add(m, "deepseek", "openai", "https://api.deepseek.com/v1");
        add(m, "together", "openai", "https://api.together.xyz/v1");
        add(m, "fireworks", "openai", "https://api.fireworks.ai/inference/v1");
        add(m, "perplexity", "openai", "https://api.perplexity.ai");
        add(m, "xai", "openai", "https://api.x.ai/v1");
        add(m, "moonshot", "openai", "https://api.moonshot.cn/v1");
        add(m, "deepinfra", "openai", "https://api.deepinfra.com/v1/openai");
        add(m, "cerebras", "openai", "https://api.cerebras.ai/v1");
        add(m, "sambanova", "openai", "https://api.sambanova.ai/v1");
        add(m, "novita", "openai", "https://api.novita.ai/v3/openai");
        add(m, "hyperbolic", "openai", "https://api.hyperbolic.xyz/v1");
        add(m, "nebius", "openai", "https://api.studio.nebius.ai/v1");
        add(m, "github-models", "openai", "https://models.inference.ai.azure.com");
        add(m, "zhipu", "openai", "https://open.bigmodel.cn/api/paas/v4");
        add(m, "volcengine", "openai", "https://ark.cn-beijing.volces.com/api/v3");
        add(m, "dashscope", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        add(m, "stepfun", "openai", "https://api.stepfun.com/v1");
        add(m, "lingyi", "openai", "https://api.lingyiwanwu.com/v1");
        add(m, "baichuan", "openai", "https://api.baichuan-ai.com/v1");
        add(m, "cohere", "openai", "https://api.cohere.com/compatibility/v1");
        add(m, "tencent-hunyuan", "openai", "https://api.hunyuan.cloud.tencent.com/v1");
        add(m, "reka", "openai", "https://api.reka.ai/v1");
        PROVIDERS = Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, ProviderSpec> map, String id, String adapter, String baseUrl) {
        map.put(id, new ProviderSpec(id, adapter, baseUrl));
    }

    public static ProviderSpec get(String providerId) {
        if (providerId == null) return null;
        return PROVIDERS.get(providerId.toLowerCase(Locale.ROOT));
    }

    public static boolean isSupported(String providerId) {
        return get(providerId) != null;
    }

    private AiProviderRegistry() {}
}
