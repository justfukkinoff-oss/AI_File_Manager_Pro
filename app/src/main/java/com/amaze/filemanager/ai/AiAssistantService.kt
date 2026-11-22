/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ai

import android.content.Context
import com.amaze.filemanager.BuildConfig
import com.amaze.filemanager.apkeditor.FileType
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * AI Assistant Service powered by Google Gemini API.
 * Provides intelligent code analysis, error explanation, and code modification capabilities.
 */
class AiAssistantService(private val context: Context) {
    private val LOG = LoggerFactory.getLogger(AiAssistantService::class.java)

    companion object {
        const val GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        const val GEMINI_MODEL = "gemini-1.5-flash"
        const val MAX_TOKENS = 8192

        // System prompts for different contexts
        const val SYSTEM_PROMPT_GENERAL = """
            You are an expert Android developer and security analyst assistant.
            You help with Android app development, APK analysis, smali code, and security audits.
            Provide clear, accurate, and helpful responses.
            When analyzing code, look for potential security issues, bugs, and optimization opportunities.
        """

        const val SYSTEM_PROMPT_SMALI = """
            You are an expert in Android Dalvik bytecode and smali language.
            Help analyze and modify smali code safely.
            Explain smali instructions and their Java equivalents.
            Identify potential issues in bytecode manipulation.
        """

        const val SYSTEM_PROMPT_MANIFEST = """
            You are an expert in Android Manifest configuration and security.
            Analyze AndroidManifest.xml for security vulnerabilities.
            Provide recommendations for manifest optimization and security hardening.
            Help with permission management and component configuration.
        """

        const val SYSTEM_PROMPT_SECURITY = """
            You are a security expert specializing in Android application security.
            Perform thorough security analysis of Android applications.
            Identify vulnerabilities, insecure configurations, and potential attack vectors.
            Provide actionable recommendations for security improvements.
        """
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()

    /**
     * Check if API key is configured.
     */
    fun isApiKeyConfigured(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return !apiKey.isNullOrEmpty()
    }

    /**
     * Send a chat message to the AI assistant.
     */
    suspend fun sendMessage(
        message: String,
        context: AiContext,
        conversationHistory: List<ChatMessage> = emptyList(),
    ): AiResponse {
        if (!isApiKeyConfigured()) {
            return AiResponse.Error("API key not configured. Please set GEMINI_API_KEY in local.properties")
        }

        return try {
            val systemPrompt = getSystemPrompt(context)
            val contents = buildContents(systemPrompt, message, conversationHistory)

            val requestBody =
                GeminiRequest(
                    contents = contents,
                    generationConfig =
                        GenerationConfig(
                            maxOutputTokens = MAX_TOKENS,
                            temperature = 0.7,
                        ),
                )

            val jsonBody = gson.toJson(requestBody)
            val url = "${GEMINI_API_BASE_URL}${GEMINI_MODEL}:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

            val request =
                Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    parseGeminiResponse(responseBody)
                } else {
                    AiResponse.Error("Empty response from API")
                }
            } else {
                AiResponse.Error("API error: ${response.code} - ${response.message}")
            }
        } catch (e: Exception) {
            LOG.error("Failed to send message to AI", e)
            AiResponse.Error("Failed to communicate with AI: ${e.message}")
        }
    }

    /**
     * Explain an error or code segment.
     */
    suspend fun explainError(
        code: String,
        error: String? = null,
        fileType: FileType,
    ): AiResponse {
        val prompt =
            buildString {
                append("Please explain this ${fileType.name} code segment")
                if (error != null) {
                    append(" and the following error:\n$error")
                }
                append(":\n\n```\n$code\n```\n\n")
                append("Provide a clear explanation and identify any potential issues or improvements.")
            }

        return sendMessage(prompt, AiContext.fromFileType(fileType))
    }

    /**
     * Analyze AndroidManifest.xml for security vulnerabilities.
     */
    suspend fun analyzeManifestSecurity(manifestContent: String): AiResponse {
        val prompt =
            buildString {
                append("Perform a comprehensive security analysis of this AndroidManifest.xml:\n\n")
                append("```xml\n$manifestContent\n```\n\n")
                append("Analyze for:\n")
                append("1. Exported components and their risks\n")
                append("2. Dangerous permission usage\n")
                append("3. Debuggable and backup settings\n")
                append("4. Network security configuration\n")
                append("5. Potential attack vectors\n\n")
                append("Provide specific recommendations for each finding.")
            }

        return sendMessage(prompt, AiContext.SECURITY)
    }

    /**
     * Modify code based on user instruction.
     */
    suspend fun modifyCode(
        code: String,
        instruction: String,
        fileType: FileType,
    ): AiResponse {
        val prompt =
            buildString {
                append("Modify the following ${fileType.name} code according to this instruction: $instruction\n\n")
                append("Original code:\n```\n$code\n```\n\n")
                append("Provide the modified code and explain the changes made.")
            }

        return sendMessage(prompt, AiContext.fromFileType(fileType))
    }

    /**
     * Suggest code improvements.
     */
    suspend fun suggestImprovements(
        code: String,
        fileType: FileType,
    ): AiResponse {
        val prompt =
            buildString {
                append("Analyze this ${fileType.name} code and suggest improvements:\n\n")
                append("```\n$code\n```\n\n")
                append("Focus on:\n")
                append("1. Performance optimizations\n")
                append("2. Security improvements\n")
                append("3. Code quality and best practices\n")
                append("4. Potential bugs or issues\n")
            }

        return sendMessage(prompt, AiContext.fromFileType(fileType))
    }

    /**
     * Convert smali to pseudo-Java code.
     */
    suspend fun smaliToJava(smaliCode: String): AiResponse {
        val prompt =
            buildString {
                append("Convert the following smali code to equivalent Java code:\n\n")
                append("```smali\n$smaliCode\n```\n\n")
                append("Provide the Java equivalent and explain the conversion.")
            }

        return sendMessage(prompt, AiContext.SMALI)
    }

    /**
     * Get package name from smali code context.
     */
    suspend fun analyzeSmaliClass(smaliCode: String): AiResponse {
        val prompt =
            buildString {
                append("Analyze this smali class and provide:\n")
                append("1. Class name and package\n")
                append("2. Superclass and interfaces\n")
                append("3. Methods and their signatures\n")
                append("4. Fields\n")
                append("5. Purpose of the class\n\n")
                append("```smali\n$smaliCode\n```")
            }

        return sendMessage(prompt, AiContext.SMALI)
    }

    private fun getSystemPrompt(context: AiContext): String {
        return when (context) {
            AiContext.SMALI -> SYSTEM_PROMPT_SMALI
            AiContext.MANIFEST -> SYSTEM_PROMPT_MANIFEST
            AiContext.SECURITY -> SYSTEM_PROMPT_SECURITY
            else -> SYSTEM_PROMPT_GENERAL
        }
    }

    private fun buildContents(
        systemPrompt: String,
        message: String,
        history: List<ChatMessage>,
    ): List<Content> {
        val contents = mutableListOf<Content>()

        // Add system prompt as first user message (Gemini doesn't have system role)
        contents.add(
            Content(
                role = "user",
                parts = listOf(Part(text = systemPrompt)),
            ),
        )
        contents.add(
            Content(
                role = "model",
                parts = listOf(Part(text = "I understand and will follow these guidelines.")),
            ),
        )

        // Add conversation history
        history.forEach { chatMessage ->
            contents.add(
                Content(
                    role = if (chatMessage.isUser) "user" else "model",
                    parts = listOf(Part(text = chatMessage.content)),
                ),
            )
        }

        // Add current message
        contents.add(
            Content(
                role = "user",
                parts = listOf(Part(text = message)),
            ),
        )

        return contents
    }

    private fun parseGeminiResponse(responseBody: String): AiResponse {
        return try {
            val response = gson.fromJson(responseBody, GeminiResponse::class.java)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (text != null) {
                AiResponse.Success(text)
            } else {
                AiResponse.Error("No response generated")
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse Gemini response", e)
            AiResponse.Error("Failed to parse response: ${e.message}")
        }
    }
}

/**
 * Context for AI operations.
 */
enum class AiContext {
    GENERAL,
    SMALI,
    MANIFEST,
    SECURITY,
    JAVA,
    ;

    companion object {
        fun fromFileType(fileType: FileType): AiContext {
            return when (fileType) {
                FileType.SMALI -> SMALI
                FileType.XML -> MANIFEST
                FileType.JAVA -> JAVA
                else -> GENERAL
            }
        }
    }
}

/**
 * Chat message for conversation history.
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Response from AI service.
 */
sealed class AiResponse {
    data class Success(val message: String) : AiResponse()

    data class Error(val message: String) : AiResponse()
}

// Data classes for Gemini API request/response
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
)

data class Content(
    val role: String,
    val parts: List<Part>,
)

data class Part(
    val text: String,
)

data class GenerationConfig(
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int,
    val temperature: Double,
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
)

data class GeminiCandidate(
    val content: GeminiContent,
)

data class GeminiContent(
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String,
)
