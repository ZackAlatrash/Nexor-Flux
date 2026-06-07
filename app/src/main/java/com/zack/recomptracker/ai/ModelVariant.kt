package com.zack.recomptracker.ai

enum class ModelVariant(
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sha256: String,
    val displayName: String,
    val displaySizeGb: String,
    val displaySizeGbFloat: Float,
) {
    GEMMA_2B(
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        expectedBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        displayName = "Gemma 4 2B",
        displaySizeGb = "2.6 GB",
        displaySizeGbFloat = 2.6f,
    ),
    GEMMA_4B(
        fileName = "gemma-4-E4B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        expectedBytes = 3_659_530_240L,
        // Pinned from litert-community/gemma-4-E4B-it-litert-lm blob page, verified 2026-06-07.
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        displayName = "Gemma 4 4B",
        displaySizeGb = "3.4 GB",
        displaySizeGbFloat = 3.4f,
    ),
}
