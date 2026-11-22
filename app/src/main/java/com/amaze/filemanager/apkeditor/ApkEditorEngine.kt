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

import android.content.Context
import android.content.pm.PackageManager
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Main engine for APK decompilation, editing, and recompilation operations.
 * Provides functionality similar to MT Manager for APK manipulation.
 */
class ApkEditorEngine(private val context: Context) {
    private val LOG = LoggerFactory.getLogger(ApkEditorEngine::class.java)

    companion object {
        const val ANDROID_MANIFEST = "AndroidManifest.xml"
        const val CLASSES_DEX = "classes.dex"
        const val RESOURCES_ARSC = "resources.arsc"
        const val APK_EXTENSION = ".apk"
        const val SMALI_DIR = "smali"
        const val BUILD_DIR = "build"
        const val DIST_DIR = "dist"
    }

    // Callback interface for progress updates
    interface ProgressCallback {
        fun onProgressUpdate(
            progress: Int,
            message: String,
        )

        fun onComplete(result: ApkOperationResult)
    }

    /**
     * Extract APK contents to a directory for editing.
     * This is a simplified extraction that doesn't fully decompile.
     */
    fun extractApk(
        apkPath: String,
        outputDir: String,
        callback: ProgressCallback? = null,
    ): ApkOperationResult {
        return try {
            callback?.onProgressUpdate(0, "Starting APK extraction...")

            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return ApkOperationResult.Error("APK file not found: $apkPath")
            }

            val outputDirectory = File(outputDir)
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs()
            }

            callback?.onProgressUpdate(10, "Reading APK structure...")

            ZipFile(apkFile).use { zipFile ->
                val entries = zipFile.entries()
                val totalEntries = zipFile.size()
                var processedEntries = 0

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryFile = File(outputDir, entry.name)

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        zipFile.getInputStream(entry).use { input ->
                            FileOutputStream(entryFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    processedEntries++
                    if (processedEntries % 50 == 0) {
                        val progress = 10 + (processedEntries.toFloat() / totalEntries * 80).toInt()
                        callback?.onProgressUpdate(progress, "Extracting: ${entry.name}")
                    }
                }
            }

            callback?.onProgressUpdate(100, "Extraction complete!")
            ApkOperationResult.Success("APK extracted successfully", outputDir)
        } catch (e: Exception) {
            LOG.error("Failed to extract APK", e)
            ApkOperationResult.Error("Failed to extract APK: ${e.message}", e)
        }
    }

    /**
     * Repackage extracted APK contents back into a signed APK.
     */
    fun repackageApk(
        projectDir: String,
        outputPath: String,
        callback: ProgressCallback? = null,
    ): ApkOperationResult {
        return try {
            callback?.onProgressUpdate(0, "Starting APK repackaging...")

            val projectDirectory = File(projectDir)
            if (!projectDirectory.exists()) {
                return ApkOperationResult.Error("Project directory not found: $projectDir")
            }

            callback?.onProgressUpdate(10, "Creating APK archive...")

            val unsignedApk = File(outputPath.replace(".apk", "_unsigned.apk"))
            ZipOutputStream(FileOutputStream(unsignedApk)).use { zipOut ->
                val files = projectDirectory.listFiles()
                if (files != null) {
                    var processedFiles = 0
                    val totalFiles = countFiles(projectDirectory)

                    for (file in files) {
                        addFileToZip(file, "", zipOut)
                        processedFiles++
                        if (processedFiles % 50 == 0) {
                            val progress = 10 + (processedFiles.toFloat() / totalFiles * 60).toInt()
                            callback?.onProgressUpdate(progress, "Adding: ${file.name}")
                        }
                    }
                }
            }

            callback?.onProgressUpdate(75, "Signing APK...")

            // Sign the APK
            val signer = ApkSigner(context)
            val signResult = signer.signApk(unsignedApk.absolutePath, outputPath)

            if (signResult is ApkOperationResult.Success) {
                // Delete unsigned APK
                unsignedApk.delete()
                callback?.onProgressUpdate(100, "APK repackaged and signed successfully!")
                ApkOperationResult.Success("APK repackaged successfully", outputPath)
            } else {
                ApkOperationResult.Error("Failed to sign APK: ${(signResult as ApkOperationResult.Error).message}")
            }
        } catch (e: Exception) {
            LOG.error("Failed to repackage APK", e)
            ApkOperationResult.Error("Failed to repackage APK: ${e.message}", e)
        }
    }

    /**
     * Get basic APK information without full extraction.
     */
    fun getApkInfo(apkPath: String): ApkInfo {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return ApkInfo(filePath = apkPath)
            }

            // Use PackageManager to get basic info
            val pm = context.packageManager
            val flags =
                PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            val packageInfo = pm.getPackageArchiveInfo(apkPath, flags)

            packageInfo?.let { info ->
                info.applicationInfo.sourceDir = apkPath
                info.applicationInfo.publicSourceDir = apkPath

                val permissions = info.requestedPermissions?.toList() ?: emptyList()
                val dangerousPermissions = permissions.filter { isDangerousPermission(it) }

                val activities =
                    info.activities?.map { activityInfo ->
                        ComponentInfo(
                            name = activityInfo.name,
                            packageName = info.packageName,
                            isExported = activityInfo.exported,
                            enabled = activityInfo.enabled,
                        )
                    } ?: emptyList()

                val services =
                    info.services?.map { serviceInfo ->
                        ComponentInfo(
                            name = serviceInfo.name,
                            packageName = info.packageName,
                            isExported = serviceInfo.exported,
                            enabled = serviceInfo.enabled,
                        )
                    } ?: emptyList()

                val receivers =
                    info.receivers?.map { receiverInfo ->
                        ComponentInfo(
                            name = receiverInfo.name,
                            packageName = info.packageName,
                            isExported = receiverInfo.exported,
                            enabled = receiverInfo.enabled,
                        )
                    } ?: emptyList()

                val providers =
                    info.providers?.map { providerInfo ->
                        ComponentInfo(
                            name = providerInfo.name,
                            packageName = info.packageName,
                            isExported = providerInfo.exported,
                            permission = providerInfo.readPermission ?: providerInfo.writePermission,
                            enabled = providerInfo.enabled,
                        )
                    } ?: emptyList()

                val exportedComponents =
                    (activities + services + receivers + providers)
                        .filter { it.isExported }

                ApkInfo(
                    packageName = info.packageName,
                    versionName = info.versionName ?: "",
                    versionCode =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            info.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            info.versionCode.toLong()
                        },
                    minSdkVersion = info.applicationInfo.minSdkVersion,
                    targetSdkVersion = info.applicationInfo.targetSdkVersion,
                    permissions = permissions,
                    activities = activities,
                    services = services,
                    receivers = receivers,
                    providers = providers,
                    applicationLabel = info.applicationInfo.loadLabel(pm).toString(),
                    isDebuggable = (info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    allowBackup = (info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0,
                    exportedComponents = exportedComponents,
                    dangerousPermissions = dangerousPermissions,
                    filePath = apkPath,
                    fileSize = apkFile.length(),
                    lastModified = apkFile.lastModified(),
                )
            } ?: ApkInfo(filePath = apkPath, fileSize = apkFile.length(), lastModified = apkFile.lastModified())
        } catch (e: Exception) {
            LOG.error("Failed to get APK info", e)
            ApkInfo(filePath = apkPath)
        }
    }

    /**
     * Parse AndroidManifest.xml from extracted APK directory.
     */
    fun parseManifest(projectDir: String): ManifestInfo? {
        return try {
            val manifestFile = File(projectDir, ANDROID_MANIFEST)
            if (!manifestFile.exists()) {
                LOG.warn("AndroidManifest.xml not found in $projectDir")
                return null
            }

            val content = manifestFile.readText()
            val editor = ManifestEditor()
            editor.parseManifest(content)
        } catch (e: Exception) {
            LOG.error("Failed to parse manifest", e)
            null
        }
    }

    /**
     * List files in extracted APK directory.
     */
    fun listProjectFiles(
        projectDir: String,
        path: String = "",
    ): List<ProjectFile> {
        val result = mutableListOf<ProjectFile>()
        val baseDir = File(projectDir, path)

        if (!baseDir.exists() || !baseDir.isDirectory) {
            return result
        }

        baseDir.listFiles()?.forEach { file ->
            result.add(
                ProjectFile(
                    name = file.name,
                    path = if (path.isEmpty()) file.name else "$path/${file.name}",
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    type = getFileType(file),
                ),
            )
        }

        return result.sortedWith(compareBy<ProjectFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Read file content from project.
     */
    fun readFile(
        projectDir: String,
        filePath: String,
    ): String? {
        return try {
            val file = File(projectDir, filePath)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to read file", e)
            null
        }
    }

    /**
     * Write content to file in project.
     */
    fun writeFile(
        projectDir: String,
        filePath: String,
        content: String,
    ): Boolean {
        return try {
            val file = File(projectDir, filePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            LOG.error("Failed to write file", e)
            false
        }
    }

    /**
     * Check if a permission is considered dangerous.
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
                "android.permission.READ_PHONE_NUMBERS",
                "android.permission.ANSWER_PHONE_CALLS",
                "android.permission.CALL_PHONE",
                "android.permission.PROCESS_OUTGOING_CALLS",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.READ_SMS",
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.READ_MMS",
                "android.permission.BODY_SENSORS",
                "android.permission.ACTIVITY_RECOGNITION",
            )
        return permission in dangerousPermissions
    }

    /**
     * Helper function to add files to ZIP archive recursively.
     */
    private fun addFileToZip(
        file: File,
        parentPath: String,
        zipOut: ZipOutputStream,
    ) {
        val entryPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"

        if (file.isDirectory) {
            val files = file.listFiles()
            files?.forEach { childFile ->
                addFileToZip(childFile, entryPath, zipOut)
            }
        } else {
            val entry = ZipEntry(entryPath)
            zipOut.putNextEntry(entry)
            FileInputStream(file).use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()
        }
    }

    /**
     * Count files in directory recursively.
     */
    private fun countFiles(directory: File): Int {
        var count = 0
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                count += countFiles(file)
            } else {
                count++
            }
        }
        return count
    }

    /**
     * Determine file type based on extension.
     */
    private fun getFileType(file: File): FileType {
        return when (file.extension.lowercase()) {
            "xml" -> FileType.XML
            "smali" -> FileType.SMALI
            "java" -> FileType.JAVA
            "dex" -> FileType.DEX
            "arsc" -> FileType.ARSC
            "png", "jpg", "jpeg", "webp", "gif" -> FileType.IMAGE
            "json" -> FileType.JSON
            "txt", "md" -> FileType.TEXT
            "so" -> FileType.NATIVE_LIB
            else -> if (file.isDirectory) FileType.DIRECTORY else FileType.UNKNOWN
        }
    }
}

/**
 * Represents a file in the APK project.
 */
data class ProjectFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val type: FileType,
)

/**
 * File type enumeration for APK contents.
 */
enum class FileType {
    DIRECTORY,
    XML,
    SMALI,
    JAVA,
    DEX,
    ARSC,
    IMAGE,
    JSON,
    TEXT,
    NATIVE_LIB,
    UNKNOWN,
}
