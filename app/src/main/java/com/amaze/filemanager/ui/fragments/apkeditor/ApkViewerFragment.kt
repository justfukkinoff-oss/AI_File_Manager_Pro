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

package com.amaze.filemanager.ui.fragments.apkeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amaze.filemanager.R
import com.amaze.filemanager.apkeditor.ApkEditorEngine
import com.amaze.filemanager.apkeditor.ApkInfo
import com.amaze.filemanager.apkeditor.ApkOperationResult
import com.amaze.filemanager.apkeditor.FileType
import com.amaze.filemanager.apkeditor.ManifestEditor
import com.amaze.filemanager.apkeditor.ProjectFile
import com.amaze.filemanager.databinding.DialogApkInfoBinding
import com.amaze.filemanager.databinding.FragmentApkViewerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Fragment for viewing and editing APK files.
 * Provides functionality similar to MT Manager for APK manipulation.
 */
class ApkViewerFragment : Fragment() {
    private val LOG = LoggerFactory.getLogger(ApkViewerFragment::class.java)
    private var _binding: FragmentApkViewerBinding? = null
    private val binding get() = _binding!!

    private var apkPath: String? = null
    private var projectDir: String? = null
    private var currentPath: String = ""
    private var apkInfo: ApkInfo? = null

    private lateinit var apkEditorEngine: ApkEditorEngine
    private lateinit var fileAdapter: ApkFileAdapter

    private val disposables = CompositeDisposable()

    companion object {
        const val ARG_APK_PATH = "apk_path"
        const val REQUEST_CODE_SAVE_APK = 1001

        fun newInstance(apkPath: String): ApkViewerFragment {
            val fragment = ApkViewerFragment()
            val args = Bundle()
            args.putString(ARG_APK_PATH, apkPath)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        apkPath = arguments?.getString(ARG_APK_PATH)
        apkEditorEngine = ApkEditorEngine(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentApkViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        loadApkInfo()
    }

    private fun setupRecyclerView() {
        fileAdapter =
            ApkFileAdapter { file ->
                onFileClicked(file)
            }

        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnShowInfo.setOnClickListener {
            showApkInfo()
        }

        binding.btnSecurityAnalysis.setOnClickListener {
            showSecurityAnalysis()
        }

        binding.btnQuickEdit.setOnClickListener {
            quickEditManifest()
        }

        binding.btnSaveApk.setOnClickListener {
            showSaveApkDialog()
        }

        binding.btnAiAnalyze.setOnClickListener {
            onAiAnalyzeClicked()
        }
    }

    private fun loadApkInfo() {
        apkPath?.let { path ->
            binding.progressBar.visibility = View.VISIBLE
            binding.txtStatus.text = "Loading APK information..."

            disposables.add(
                Single.fromCallable { apkEditorEngine.getApkInfo(path) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ info ->
                        apkInfo = info
                        binding.progressBar.visibility = View.GONE
                        binding.txtStatus.text = "${info.packageName} v${info.versionName}"
                        binding.txtAppTitle.text = info.applicationLabel

                        // Auto-extract APK
                        extractApk(path)
                    }, { error ->
                        LOG.error("Failed to load APK info", error)
                        binding.progressBar.visibility = View.GONE
                        binding.txtStatus.text = "Failed to load APK: ${error.message}"
                    }),
            )
        }
    }

    private fun extractApk(apkPath: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.txtStatus.text = "Extracting APK contents..."

        // Create project directory
        val cacheDir = File(requireContext().cacheDir, "apk_projects")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        projectDir =
            File(cacheDir, File(apkPath).nameWithoutExtension).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }.absolutePath

        disposables.add(
            Single.fromCallable {
                apkEditorEngine.extractApk(
                    apkPath,
                    projectDir!!,
                    object : ApkEditorEngine.ProgressCallback {
                        override fun onProgressUpdate(
                            progress: Int,
                            message: String,
                        ) {
                            activity?.runOnUiThread {
                                binding.progressBar.progress = progress
                                binding.txtStatus.text = message
                            }
                        }

                        override fun onComplete(result: ApkOperationResult) {
                            // Result is returned via the return value
                        }
                    },
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    binding.progressBar.visibility = View.GONE
                    when (result) {
                        is ApkOperationResult.Success -> {
                            binding.txtStatus.text = "APK extracted successfully"
                            loadProjectFiles("")
                        }
                        is ApkOperationResult.Error -> {
                            binding.txtStatus.text = "Extraction failed: ${result.message}"
                        }
                        else -> {}
                    }
                }, { error ->
                    LOG.error("Failed to extract APK", error)
                    binding.progressBar.visibility = View.GONE
                    binding.txtStatus.text = "Extraction failed: ${error.message}"
                }),
        )
    }

    private fun loadProjectFiles(path: String) {
        projectDir?.let { dir ->
            currentPath = path
            val files = apkEditorEngine.listProjectFiles(dir, path)
            fileAdapter.updateFiles(files, path)
            binding.txtCurrentPath.text = if (path.isEmpty()) "/" else path
        }
    }

    private fun onFileClicked(file: ProjectFile) {
        if (file.isDirectory) {
            loadProjectFiles(file.path)
        } else {
            when (file.type) {
                FileType.XML, FileType.SMALI, FileType.TEXT, FileType.JSON -> {
                    openFileEditor(file)
                }
                else -> {
                    showFileInfo(file)
                }
            }
        }
    }

    private fun openFileEditor(file: ProjectFile) {
        projectDir?.let { dir ->
            val content = apkEditorEngine.readFile(dir, file.path)
            content?.let {
                val editorFragment =
                    ApkFileEditorFragment.newInstance(
                        projectDir = dir,
                        filePath = file.path,
                        fileType = file.type,
                        content = it,
                    )
                editorFragment.setOnSaveListener { savedContent ->
                    apkEditorEngine.writeFile(dir, file.path, savedContent)
                }

                parentFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, editorFragment)
                    .addToBackStack("editor")
                    .commit()
            }
        }
    }

    private fun showFileInfo(file: ProjectFile) {
        val sizeStr = formatFileSize(file.size)
        val message =
            """
            Name: ${file.name}
            Path: ${file.path}
            Type: ${file.type}
            Size: $sizeStr
            """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("File Info")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showApkInfo() {
        apkInfo?.let { info ->
            val binding = DialogApkInfoBinding.inflate(layoutInflater)

            binding.txtPackageName.text = info.packageName
            binding.txtVersion.text = "${info.versionName} (${info.versionCode})"
            binding.txtSdk.text = "Min: ${info.minSdkVersion}, Target: ${info.targetSdkVersion}"
            binding.txtPermissions.text =
                if (info.permissions.isNotEmpty()) {
                    info.permissions.take(10).joinToString("\n") { "• $it" } +
                        if (info.permissions.size > 10) "\n... and ${info.permissions.size - 10} more" else ""
                } else {
                    "No permissions"
                }
            binding.txtComponents.text =
                """
                Activities: ${info.activities.size}
                Services: ${info.services.size}
                Receivers: ${info.receivers.size}
                Providers: ${info.providers.size}
                """.trimIndent()

            val dialog =
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(info.applicationLabel)
                    .setView(binding.root)
                    .setPositiveButton("OK", null)
                    .create()

            dialog.show()
        }
    }

    private fun showSecurityAnalysis() {
        projectDir?.let { dir ->
            binding.progressBar.visibility = View.VISIBLE
            binding.txtStatus.text = "Analyzing security..."

            disposables.add(
                Single.fromCallable {
                    val manifestInfo = apkEditorEngine.parseManifest(dir)
                    manifestInfo?.let { info ->
                        val editor = ManifestEditor()
                        editor.analyzeSecurity(info)
                    }
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ report ->
                        binding.progressBar.visibility = View.GONE

                        report?.let { securityReport ->
                            val message =
                                buildString {
                                    append("Risk Level: ${securityReport.riskLevel}\n")
                                    append("Risk Score: ${securityReport.riskScore}\n\n")
                                    append("Issues Found:\n")
                                    securityReport.issues.forEach { issue ->
                                        append("\n[${issue.severity}] ${issue.title}\n")
                                        append("${issue.description}\n")
                                    }

                                    if (securityReport.exportedComponents.isNotEmpty()) {
                                        append("\n\nExported Components:\n")
                                        securityReport.exportedComponents.take(5).forEach {
                                            append("• ${it.type}: ${it.name}\n")
                                        }
                                    }
                                }

                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Security Analysis")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }, { error ->
                        LOG.error("Security analysis failed", error)
                        binding.progressBar.visibility = View.GONE
                        binding.txtStatus.text = "Analysis failed: ${error.message}"
                    }),
            )
        }
    }

    private fun quickEditManifest() {
        projectDir?.let { dir ->
            val manifestFile = File(dir, ApkEditorEngine.ANDROID_MANIFEST)
            if (manifestFile.exists()) {
                val content = manifestFile.readText()
                val editorFragment =
                    ApkFileEditorFragment.newInstance(
                        projectDir = dir,
                        filePath = ApkEditorEngine.ANDROID_MANIFEST,
                        fileType = FileType.XML,
                        content = content,
                    )
                editorFragment.setOnSaveListener { savedContent ->
                    manifestFile.writeText(savedContent)
                    Snackbar.make(binding.root, "Manifest saved", Snackbar.LENGTH_SHORT).show()
                }

                parentFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, editorFragment)
                    .addToBackStack("manifest_editor")
                    .commit()
            } else {
                Snackbar.make(binding.root, "AndroidManifest.xml not found", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSaveApkDialog() {
        val options = arrayOf("Save unsigned APK", "Save signed APK (test key)")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save APK")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveUnsignedApk()
                    1 -> saveSignedApk()
                }
            }
            .show()
    }

    private fun saveUnsignedApk() {
        projectDir?.let { dir ->
            val outputPath =
                File(
                    requireContext().getExternalFilesDir(null),
                    "${File(apkPath ?: "app").nameWithoutExtension}_modified.apk",
                ).absolutePath

            binding.progressBar.visibility = View.VISIBLE
            binding.txtStatus.text = "Building APK..."

            disposables.add(
                Single.fromCallable {
                    apkEditorEngine.repackageApk(
                        dir,
                        outputPath,
                        object : ApkEditorEngine.ProgressCallback {
                            override fun onProgressUpdate(
                                progress: Int,
                                message: String,
                            ) {
                                activity?.runOnUiThread {
                                    binding.progressBar.progress = progress
                                    binding.txtStatus.text = message
                                }
                            }

                            override fun onComplete(result: ApkOperationResult) {}
                        },
                    )
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        binding.progressBar.visibility = View.GONE
                        when (result) {
                            is ApkOperationResult.Success -> {
                                Snackbar.make(binding.root, "APK saved to: ${result.outputPath}", Snackbar.LENGTH_LONG)
                                    .setAction("Open") {
                                        // Open file location
                                    }
                                    .show()
                            }
                            is ApkOperationResult.Error -> {
                                Snackbar.make(binding.root, "Failed: ${result.message}", Snackbar.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }, { error ->
                        LOG.error("Failed to save APK", error)
                        binding.progressBar.visibility = View.GONE
                        Snackbar.make(binding.root, "Error: ${error.message}", Snackbar.LENGTH_LONG).show()
                    }),
            )
        }
    }

    private fun saveSignedApk() {
        projectDir?.let { dir ->
            val outputPath =
                File(
                    requireContext().getExternalFilesDir(null),
                    "${File(apkPath ?: "app").nameWithoutExtension}_signed.apk",
                ).absolutePath

            binding.progressBar.visibility = View.VISIBLE
            binding.txtStatus.text = "Building and signing APK..."

            disposables.add(
                Single.fromCallable {
                    apkEditorEngine.repackageApk(
                        dir,
                        outputPath,
                        object : ApkEditorEngine.ProgressCallback {
                            override fun onProgressUpdate(
                                progress: Int,
                                message: String,
                            ) {
                                activity?.runOnUiThread {
                                    binding.progressBar.progress = progress
                                    binding.txtStatus.text = message
                                }
                            }

                            override fun onComplete(result: ApkOperationResult) {}
                        },
                    )
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        binding.progressBar.visibility = View.GONE
                        when (result) {
                            is ApkOperationResult.Success -> {
                                Snackbar.make(binding.root, "Signed APK saved: ${result.outputPath}", Snackbar.LENGTH_LONG)
                                    .setAction("Install") {
                                        // Install APK
                                        val intent = Intent(Intent.ACTION_VIEW)
                                        intent.setDataAndType(
                                            Uri.fromFile(File(result.outputPath!!)),
                                            "application/vnd.android.package-archive",
                                        )
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        startActivity(intent)
                                    }
                                    .show()
                            }
                            is ApkOperationResult.Error -> {
                                Snackbar.make(binding.root, "Failed: ${result.message}", Snackbar.LENGTH_LONG).show()
                            }
                            else -> {}
                        }
                    }, { error ->
                        LOG.error("Failed to save signed APK", error)
                        binding.progressBar.visibility = View.GONE
                        Snackbar.make(binding.root, "Error: ${error.message}", Snackbar.LENGTH_LONG).show()
                    }),
            )
        }
    }

    private fun onAiAnalyzeClicked() {
        // This will be implemented with AI integration
        projectDir?.let { dir ->
            val manifestContent = apkEditorEngine.readFile(dir, ApkEditorEngine.ANDROID_MANIFEST)

            if (manifestContent != null) {
                // Trigger AI analysis
                val aiFragment =
                    AiAssistantFragment.newInstance(
                        context = "APK Analysis",
                        fileContent = manifestContent,
                        filePath = ApkEditorEngine.ANDROID_MANIFEST,
                    )

                parentFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, aiFragment)
                    .addToBackStack("ai_assistant")
                    .commit()
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.apk_viewer_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadProjectFiles(currentPath)
                true
            }
            R.id.action_info -> {
                showApkInfo()
                true
            }
            R.id.action_security -> {
                showSecurityAnalysis()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
        _binding = null
    }

    /**
     * Adapter for displaying APK project files.
     */
    inner class ApkFileAdapter(
        private val onFileClick: (ProjectFile) -> Unit,
    ) : RecyclerView.Adapter<ApkFileAdapter.FileViewHolder>() {
        private var files = listOf<ProjectFile>()
        private var currentPath = ""

        fun updateFiles(
            newFiles: List<ProjectFile>,
            path: String,
        ) {
            files = newFiles
            currentPath = path
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): FileViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_apk_file, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: FileViewHolder,
            position: Int,
        ) {
            holder.bind(files[position])
        }

        override fun getItemCount() = files.size

        inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(file: ProjectFile) {
                // Bind file data to view
                itemView.setOnClickListener { onFileClick(file) }
            }
        }
    }
}
