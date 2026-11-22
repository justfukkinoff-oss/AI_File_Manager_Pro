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

import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Handles parsing and modification of AndroidManifest.xml files.
 * Provides functionality to modify package name, permissions, components, etc.
 */
class ManifestEditor {
    private val LOG = LoggerFactory.getLogger(ManifestEditor::class.java)

    companion object {
        const val TAG_MANIFEST = "manifest"
        const val TAG_APPLICATION = "application"
        const val TAG_ACTIVITY = "activity"
        const val TAG_SERVICE = "service"
        const val TAG_RECEIVER = "receiver"
        const val TAG_PROVIDER = "provider"
        const val TAG_USES_PERMISSION = "uses-permission"
        const val TAG_USES_SDK = "uses-sdk"
        const val TAG_INTENT_FILTER = "intent-filter"

        const val ATTR_PACKAGE = "package"
        const val ATTR_VERSION_CODE = "android:versionCode"
        const val ATTR_VERSION_NAME = "android:versionName"
        const val ATTR_NAME = "android:name"
        const val ATTR_EXPORTED = "android:exported"
        const val ATTR_DEBUGGABLE = "android:debuggable"
        const val ATTR_ALLOW_BACKUP = "android:allowBackup"
        const val ATTR_MIN_SDK = "android:minSdkVersion"
        const val ATTR_TARGET_SDK = "android:targetSdkVersion"
        const val ATTR_PERMISSION = "android:permission"
    }

    /**
     * Parse AndroidManifest.xml and extract information.
     */
    fun parseManifest(xmlContent: String): ManifestInfo {
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        val receivers = mutableListOf<String>()
        val providers = mutableListOf<String>()

        var packageName = ""
        var versionCode = ""
        var versionName = ""
        var minSdkVersion = ""
        var targetSdkVersion = ""

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            TAG_MANIFEST -> {
                                packageName = parser.getAttributeValue(null, ATTR_PACKAGE) ?: ""
                                versionCode = parser.getAttributeValue(null, ATTR_VERSION_CODE) ?: ""
                                versionName = parser.getAttributeValue(null, ATTR_VERSION_NAME) ?: ""
                            }
                            TAG_USES_SDK -> {
                                minSdkVersion = parser.getAttributeValue(null, ATTR_MIN_SDK) ?: ""
                                targetSdkVersion = parser.getAttributeValue(null, ATTR_TARGET_SDK) ?: ""
                            }
                            TAG_USES_PERMISSION -> {
                                val permission = parser.getAttributeValue(null, ATTR_NAME) ?: ""
                                if (permission.isNotEmpty()) {
                                    permissions.add(permission)
                                }
                            }
                            TAG_ACTIVITY -> {
                                val name = parser.getAttributeValue(null, ATTR_NAME) ?: ""
                                if (name.isNotEmpty()) {
                                    activities.add(name)
                                }
                            }
                            TAG_SERVICE -> {
                                val name = parser.getAttributeValue(null, ATTR_NAME) ?: ""
                                if (name.isNotEmpty()) {
                                    services.add(name)
                                }
                            }
                            TAG_RECEIVER -> {
                                val name = parser.getAttributeValue(null, ATTR_NAME) ?: ""
                                if (name.isNotEmpty()) {
                                    receivers.add(name)
                                }
                            }
                            TAG_PROVIDER -> {
                                val name = parser.getAttributeValue(null, ATTR_NAME) ?: ""
                                if (name.isNotEmpty()) {
                                    providers.add(name)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse manifest", e)
        }

        return ManifestInfo(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdkVersion = minSdkVersion,
            targetSdkVersion = targetSdkVersion,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            rawXml = xmlContent,
        )
    }

    /**
     * Modify the package name in the manifest.
     */
    fun modifyPackageName(
        xmlContent: String,
        newPackageName: String,
    ): String {
        return try {
            // Simple string replacement for package attribute
            // A more robust implementation would parse and rebuild the XML
            val packagePattern = """package\s*=\s*"[^"]*"""".toRegex()
            xmlContent.replaceFirst(packagePattern, """package="$newPackageName"""")
        } catch (e: Exception) {
            LOG.error("Failed to modify package name", e)
            xmlContent
        }
    }

    /**
     * Add a permission to the manifest.
     */
    fun addPermission(
        xmlContent: String,
        permission: String,
    ): String {
        return try {
            val permissionTag = """<uses-permission android:name="$permission"/>"""
            // Insert after the manifest tag
            val manifestEndPattern = """<manifest[^>]*>""".toRegex()
            val match = manifestEndPattern.find(xmlContent)
            if (match != null) {
                val insertPosition = match.range.last + 1
                buildString {
                    append(xmlContent.substring(0, insertPosition))
                    append("\n    ")
                    append(permissionTag)
                    append(xmlContent.substring(insertPosition))
                }
            } else {
                xmlContent
            }
        } catch (e: Exception) {
            LOG.error("Failed to add permission", e)
            xmlContent
        }
    }

    /**
     * Remove a permission from the manifest.
     */
    fun removePermission(
        xmlContent: String,
        permission: String,
    ): String {
        return try {
            val permissionPattern = """<uses-permission[^>]*android:name\s*=\s*"$permission"[^>]*/>""".toRegex()
            xmlContent.replace(permissionPattern, "")
        } catch (e: Exception) {
            LOG.error("Failed to remove permission", e)
            xmlContent
        }
    }

    /**
     * Set the debuggable attribute.
     */
    fun setDebuggable(
        xmlContent: String,
        debuggable: Boolean,
    ): String {
        return try {
            val applicationPattern = """<application[^>]*>""".toRegex()
            val match = applicationPattern.find(xmlContent)
            if (match != null) {
                var applicationTag = match.value
                val debuggableAttr = """android:debuggable\s*=\s*"[^"]*"""".toRegex()

                applicationTag =
                    if (debuggableAttr.containsMatchIn(applicationTag)) {
                        applicationTag.replace(debuggableAttr, """android:debuggable="$debuggable"""")
                    } else {
                        // Add the attribute before the closing >
                        applicationTag.replace(">", """ android:debuggable="$debuggable">""")
                    }

                xmlContent.replaceRange(match.range, applicationTag)
            } else {
                xmlContent
            }
        } catch (e: Exception) {
            LOG.error("Failed to set debuggable", e)
            xmlContent
        }
    }

    /**
     * Set the allowBackup attribute.
     */
    fun setAllowBackup(
        xmlContent: String,
        allowBackup: Boolean,
    ): String {
        return try {
            val applicationPattern = """<application[^>]*>""".toRegex()
            val match = applicationPattern.find(xmlContent)
            if (match != null) {
                var applicationTag = match.value
                val backupAttr = """android:allowBackup\s*=\s*"[^"]*"""".toRegex()

                applicationTag =
                    if (backupAttr.containsMatchIn(applicationTag)) {
                        applicationTag.replace(backupAttr, """android:allowBackup="$allowBackup"""")
                    } else {
                        applicationTag.replace(">", """ android:allowBackup="$allowBackup">""")
                    }

                xmlContent.replaceRange(match.range, applicationTag)
            } else {
                xmlContent
            }
        } catch (e: Exception) {
            LOG.error("Failed to set allowBackup", e)
            xmlContent
        }
    }

    /**
     * Change the version code.
     */
    fun setVersionCode(
        xmlContent: String,
        versionCode: Int,
    ): String {
        return try {
            val versionCodePattern = """android:versionCode\s*=\s*"[^"]*"""".toRegex()
            if (versionCodePattern.containsMatchIn(xmlContent)) {
                xmlContent.replace(versionCodePattern, """android:versionCode="$versionCode"""")
            } else {
                // Add to manifest tag
                val manifestPattern = """<manifest[^>]*>""".toRegex()
                val match = manifestPattern.find(xmlContent)
                if (match != null) {
                    xmlContent.replaceRange(match.range, match.value.replace(">", """ android:versionCode="$versionCode">"""))
                } else {
                    xmlContent
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to set version code", e)
            xmlContent
        }
    }

    /**
     * Change the version name.
     */
    fun setVersionName(
        xmlContent: String,
        versionName: String,
    ): String {
        return try {
            val versionNamePattern = """android:versionName\s*=\s*"[^"]*"""".toRegex()
            if (versionNamePattern.containsMatchIn(xmlContent)) {
                xmlContent.replace(versionNamePattern, """android:versionName="$versionName"""")
            } else {
                // Add to manifest tag
                val manifestPattern = """<manifest[^>]*>""".toRegex()
                val match = manifestPattern.find(xmlContent)
                if (match != null) {
                    xmlContent.replaceRange(match.range, match.value.replace(">", """ android:versionName="$versionName">"""))
                } else {
                    xmlContent
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to set version name", e)
            xmlContent
        }
    }

    /**
     * Change the target SDK version.
     */
    fun setTargetSdkVersion(
        xmlContent: String,
        targetSdk: Int,
    ): String {
        return try {
            val targetSdkPattern = """android:targetSdkVersion\s*=\s*"[^"]*"""".toRegex()
            if (targetSdkPattern.containsMatchIn(xmlContent)) {
                xmlContent.replace(targetSdkPattern, """android:targetSdkVersion="$targetSdk"""")
            } else {
                xmlContent
            }
        } catch (e: Exception) {
            LOG.error("Failed to set target SDK", e)
            xmlContent
        }
    }

    /**
     * Find all exported components.
     */
    fun findExportedComponents(xmlContent: String): List<ExportedComponent> {
        val exportedComponents = mutableListOf<ExportedComponent>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var currentComponent: ExportedComponent? = null
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            TAG_ACTIVITY, TAG_SERVICE, TAG_RECEIVER, TAG_PROVIDER -> {
                                val name = parser.getAttributeValue(null, ATTR_NAME) ?: ""
                                val exported = parser.getAttributeValue(null, ATTR_EXPORTED)?.toBoolean() ?: false
                                val hasIntentFilter = hasIntentFilterAhead(parser)

                                // Component is considered exported if:
                                // 1. android:exported="true" is explicitly set
                                // 2. It has an intent-filter and exported is not explicitly set to false
                                val isExported = exported || (hasIntentFilter && parser.getAttributeValue(null, ATTR_EXPORTED) == null)

                                if (isExported) {
                                    currentComponent =
                                        ExportedComponent(
                                            type = parser.name,
                                            name = name,
                                            hasIntentFilter = hasIntentFilter,
                                            explicitExport = exported,
                                        )
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (currentComponent != null && parser.name in listOf(TAG_ACTIVITY, TAG_SERVICE, TAG_RECEIVER, TAG_PROVIDER)) {
                            exportedComponents.add(currentComponent!!)
                            currentComponent = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            LOG.error("Failed to find exported components", e)
        }

        return exportedComponents
    }

    /**
     * Check if there's an intent-filter ahead in the current component.
     */
    private fun hasIntentFilterAhead(parser: XmlPullParser): Boolean {
        try {
            var depth = 1
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT && depth > 0) {
                eventType = parser.next()
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == TAG_INTENT_FILTER) {
                            return true
                        }
                        depth++
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return false
    }

    /**
     * Generate a security analysis report of the manifest.
     */
    fun analyzeSecurity(manifestInfo: ManifestInfo): ManifestSecurityReport {
        val issues = mutableListOf<SecurityIssue>()
        var riskScore = 0

        // Check for debuggable
        if (manifestInfo.rawXml.contains("android:debuggable=\"true\"")) {
            issues.add(
                SecurityIssue(
                    severity = Severity.HIGH,
                    title = "Debuggable Application",
                    description = "The application is marked as debuggable, which allows debugging and potential security bypass.",
                    recommendation = "Remove android:debuggable=\"true\" from the manifest for production builds.",
                ),
            )
            riskScore += 20
        }

        // Check for backup
        if (manifestInfo.rawXml.contains("android:allowBackup=\"true\"")) {
            issues.add(
                SecurityIssue(
                    severity = Severity.MEDIUM,
                    title = "Backup Enabled",
                    description = "Application data can be backed up, potentially exposing sensitive data.",
                    recommendation = "Disable allowBackup or use android:fullBackupContent to control backup behavior.",
                ),
            )
            riskScore += 10
        }

        // Check for cleartext traffic
        if (manifestInfo.rawXml.contains("android:usesCleartextTraffic=\"true\"")) {
            issues.add(
                SecurityIssue(
                    severity = Severity.MEDIUM,
                    title = "Cleartext Traffic Allowed",
                    description = "The application may transmit data over unencrypted connections.",
                    recommendation = "Use HTTPS for all network communications.",
                ),
            )
            riskScore += 15
        }

        // Check for dangerous permissions
        val dangerousPermissions = manifestInfo.permissions.filter { isDangerousPermission(it) }
        if (dangerousPermissions.isNotEmpty()) {
            issues.add(
                SecurityIssue(
                    severity = Severity.MEDIUM,
                    title = "Dangerous Permissions",
                    description =
                        "Application requests ${dangerousPermissions.size} dangerous permission(s): " +
                            "${dangerousPermissions.take(3).joinToString()}...",
                    recommendation = "Review if all permissions are necessary for the app's functionality.",
                ),
            )
            riskScore += dangerousPermissions.size * 5
        }

        // Check for exported components
        val exportedComponents = findExportedComponents(manifestInfo.rawXml)
        if (exportedComponents.isNotEmpty()) {
            issues.add(
                SecurityIssue(
                    severity = Severity.LOW,
                    title = "Exported Components",
                    description = "Found ${exportedComponents.size} exported component(s) that could be accessed by other apps.",
                    recommendation = "Review exported components and add permission restrictions if needed.",
                ),
            )
            riskScore += exportedComponents.size * 3
        }

        val riskLevel =
            when {
                riskScore >= 50 -> RiskLevel.HIGH
                riskScore >= 25 -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

        return ManifestSecurityReport(
            riskScore = riskScore,
            riskLevel = riskLevel,
            issues = issues,
            exportedComponents = exportedComponents,
        )
    }

    /**
     * Check if permission is dangerous.
     */
    private fun isDangerousPermission(permission: String): Boolean {
        val dangerousPermissions =
            listOf(
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.READ_CALENDAR",
                "android.permission.WRITE_CALENDAR",
                "android.permission.READ_CALL_LOG",
                "android.permission.WRITE_CALL_LOG",
                "android.permission.READ_PHONE_STATE",
                "android.permission.CALL_PHONE",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_SMS",
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS",
            )
        return permission in dangerousPermissions
    }
}

/**
 * Represents an exported component found in the manifest.
 */
data class ExportedComponent(
    val type: String,
    val name: String,
    val hasIntentFilter: Boolean,
    val explicitExport: Boolean,
)

/**
 * Represents a security issue in the manifest.
 */
data class SecurityIssue(
    val severity: Severity,
    val title: String,
    val description: String,
    val recommendation: String,
)

/**
 * Severity level for security issues.
 */
enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * Security analysis report for a manifest.
 */
data class ManifestSecurityReport(
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val issues: List<SecurityIssue>,
    val exportedComponents: List<ExportedComponent>,
)
