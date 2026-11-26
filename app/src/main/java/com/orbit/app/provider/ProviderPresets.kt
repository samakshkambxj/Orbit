package com.orbit.app.provider

import com.orbit.app.data.model.ApiFormat

enum class ProviderPreset(
    val label: String,
    val providerName: String,
    val baseUrl: String,
    val format: ApiFormat,
    val modelId: String,
    val modelDisplayName: String,
) {
    OPENAI(
        label = "OpenAI",
        providerName = "OpenAI",
        baseUrl = "https://api.openai.com",
        format = ApiFormat.OPENAI,
        modelId = "gpt-4o-mini",
        modelDisplayName = "GPT-4o mini",
    ),
    ANTHROPIC(
        label = "Anthropic",
        providerName = "Anthropic",
        baseUrl = "https://api.anthropic.com",
        format = ApiFormat.ANTHROPIC,
        modelId = "claude-sonnet-4-5",
        modelDisplayName = "Claude Sonnet 4.5",
    ),
    OPENROUTER(
        label = "OpenRouter",
        providerName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api",
        format = ApiFormat.OPENAI,
        modelId = "openai/gpt-4o-mini",
        modelDisplayName = "GPT-4o mini",
    ),
    GROQ(
        label = "Groq",
        providerName = "Groq",
        baseUrl = "https://api.groq.com/openai",
        format = ApiFormat.OPENAI,
        modelId = "llama-3.3-70b-versatile",
        modelDisplayName = "Llama 3.3 70B",
    ),
    OLLAMA(
        label = "Ollama",
        providerName = "Ollama",
        baseUrl = "http://localhost:11434",
        format = ApiFormat.OPENAI,
        modelId = "llama3.2",
        modelDisplayName = "Llama 3.2",
    ),
    LM_STUDIO(
        label = "LM Studio",
        providerName = "LM Studio",
        baseUrl = "http://localhost:1234",
        format = ApiFormat.OPENAI,
        modelId = "local-model",
        modelDisplayName = "Local model",
    ),
}
