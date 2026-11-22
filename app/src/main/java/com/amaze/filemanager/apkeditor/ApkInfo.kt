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

package com.amaze.filemanager.apkeditor

/**
 * Data class representing information extracted from an APK file.
 * Contains all relevant metadata about an Android application package.
 */
data class ApkInfo(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val permissions: List<String> = emptyList(),
    val activities: List<ComponentInfo> = emptyList(),
    val services: List<ComponentInfo> = emptyList(),
    val receivers: List<ComponentInfo> = emptyList(),
    val providers: List<ComponentInfo> = emptyList(),
    val applicationLabel: String = "",
    val applicationIcon: String = "",
    val isDebuggable: Boolean = false,
    val allowBackup: Boolean = false,
    val usesCleartextTraffic: Boolean = false,
    val exportedComponents: List<ComponentInfo> = emptyList(),
    val dangerousPermissions: List<String> = emptyList(),
    val filePath: String = "",
    val fileSize: Long = 0,
    val lastModified: Long = 0,
) {
    /**
     * Returns true if this APK has any dangerous permissions
     */
    fun hasDangerousPermissions(): Boolean = dangerousPermissions.isNotEmpty()

    /**
     * Returns true if this APK has any exported components
     */
    fun hasExportedComponents(): Boolean = exportedComponents.isNotEmpty()

    /**
     * Returns a summary of security concerns
     */
    fun getSecuritySummary(): SecuritySummary {
        val concerns = mutableListOf<String>()

        if (isDebuggable) {
            concerns.add("Application is debuggable")
        }
        if (usesCleartextTraffic) {
            concerns.add("Uses cleartext (HTTP) network traffic")
        }
        if (hasDangerousPermissions()) {
            concerns.add("Requests ${dangerousPermissions.size} dangerous permission(s)")
        }
        if (hasExportedComponents()) {
            concerns.add("Has ${exportedComponents.size} exported component(s)")
        }
        if (allowBackup) {
            concerns.add("Allows backup (potential data leakage)")
        }

        val riskLevel =
            when {
                concerns.isEmpty() -> RiskLevel.LOW
                concerns.size <= 2 -> RiskLevel.MEDIUM
                else -> RiskLevel.HIGH
            }

        return SecuritySummary(riskLevel, concerns)
    }
}

/**
 * Represents an Android component (Activity, Service, Receiver, Provider)
 */
data class ComponentInfo(
    val name: String,
    val packageName: String,
    val isExported: Boolean = false,
    val hasIntentFilters: Boolean = false,
    val permission: String? = null,
    val enabled: Boolean = true,
) {
    val fullName: String
        get() = if (name.startsWith(packageName)) name else "$packageName.$name"
}

/**
 * Risk level enumeration for security assessment
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * Security summary containing risk assessment
 */
data class SecuritySummary(
    val riskLevel: RiskLevel,
    val concerns: List<String>,
)

/**
 * Represents a Smali file structure
 */
data class SmaliFile(
    val className: String,
    val superClass: String = "",
    val sourceFile: String = "",
    val methods: List<SmaliMethod> = emptyList(),
    val fields: List<SmaliField> = emptyList(),
    val annotations: List<SmaliAnnotation> = emptyList(),
    val content: String = "",
)

/**
 * Represents a Smali method
 */
data class SmaliMethod(
    val name: String,
    val returnType: String,
    val parameters: List<String>,
    val accessFlags: String = "",
    val body: String = "",
)

/**
 * Represents a Smali field
 */
data class SmaliField(
    val name: String,
    val type: String,
    val accessFlags: String = "",
    val initialValue: String? = null,
)

/**
 * Represents a Smali annotation
 */
data class SmaliAnnotation(
    val type: String,
    val values: Map<String, String> = emptyMap(),
)

/**
 * Represents parsed AndroidManifest.xml information
 */
data class ManifestInfo(
    val packageName: String,
    val versionCode: String,
    val versionName: String,
    val minSdkVersion: String,
    val targetSdkVersion: String,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val rawXml: String,
)

/**
 * Result of an APK operation
 */
sealed class ApkOperationResult {
    data class Success(val message: String, val outputPath: String? = null) : ApkOperationResult()

    data class Error(val message: String, val exception: Throwable? = null) : ApkOperationResult()

    data class InProgress(val progress: Int, val message: String) : ApkOperationResult()
}
