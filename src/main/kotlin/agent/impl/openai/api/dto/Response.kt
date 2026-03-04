package org.example.agent.impl.openai.api.dto

data class Response(
    val status: Int,
    val statusText: String,
    val body: String
)