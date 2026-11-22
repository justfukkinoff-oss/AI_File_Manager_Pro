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

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.amaze.filemanager.R
import com.amaze.filemanager.ai.AiAssistantService
import com.amaze.filemanager.ai.AiResponse
import com.amaze.filemanager.apkeditor.FileType
import com.amaze.filemanager.databinding.DialogAiEditBinding
import com.amaze.filemanager.databinding.FragmentApkFileEditorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Fragment for editing individual files within an APK project.
 * Supports XML, Smali, and text file editing with AI assistance.
 */
class ApkFileEditorFragment : Fragment() {
    private val LOG = LoggerFactory.getLogger(ApkFileEditorFragment::class.java)
    private var _binding: FragmentApkFileEditorBinding? = null
    private val binding get() = _binding!!

    private var projectDir: String? = null
    private var filePath: String? = null
    private var fileType: FileType = FileType.UNKNOWN
    private var originalContent: String = ""
    private var hasUnsavedChanges = false

    private var onSaveListener: ((String) -> Unit)? = null

    private lateinit var aiService: AiAssistantService
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ARG_PROJECT_DIR = "project_dir"
        const val ARG_FILE_PATH = "file_path"
        const val ARG_FILE_TYPE = "file_type"
        const val ARG_CONTENT = "content"

        fun newInstance(
            projectDir: String,
            filePath: String,
            fileType: FileType,
            content: String,
        ): ApkFileEditorFragment {
            val fragment = ApkFileEditorFragment()
            val args = Bundle()
            args.putString(ARG_PROJECT_DIR, projectDir)
            args.putString(ARG_FILE_PATH, filePath)
            args.putString(ARG_FILE_TYPE, fileType.name)
            args.putString(ARG_CONTENT, content)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        arguments?.let { args ->
            projectDir = args.getString(ARG_PROJECT_DIR)
            filePath = args.getString(ARG_FILE_PATH)
            fileType = FileType.valueOf(args.getString(ARG_FILE_TYPE, "UNKNOWN"))
            originalContent = args.getString(ARG_CONTENT, "")
        }

        aiService = AiAssistantService(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentApkFileEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupEditor()
        setupAiButtons()
        loadContent()
    }

    private fun setupEditor() {
        // Set up the code editor
        binding.editTextContent.setText(originalContent)

        binding.editTextContent.doAfterTextChanged { text ->
            hasUnsavedChanges = text.toString() != originalContent
            updateSaveButton()
        }

        binding.btnSave.setOnClickListener {
            saveFile()
        }

        binding.btnRevert.setOnClickListener {
            revertChanges()
        }
    }

    private fun setupAiButtons() {
        binding.btnAiExplain.setOnClickListener {
            aiExplainSelection()
        }

        binding.btnAiEdit.setOnClickListener {
            showAiEditDialog()
        }

        binding.btnAiAnalyze.setOnClickListener {
            aiAnalyzeFile()
        }

        // Show/hide AI buttons based on file type
        when (fileType) {
            FileType.SMALI -> {
                binding.btnAiConvert.visibility = View.VISIBLE
                binding.btnAiConvert.text = "Convert to Java"
                binding.btnAiConvert.setOnClickListener {
                    aiConvertSmaliToJava()
                }
            }
            FileType.XML -> {
                if (filePath?.contains("AndroidManifest.xml") == true) {
                    binding.btnAiAnalyze.text = "Security Analysis"
                }
                binding.btnAiConvert.visibility = View.GONE
            }
            else -> {
                binding.btnAiConvert.visibility = View.GONE
            }
        }
    }

    private fun loadContent() {
        binding.editTextContent.setText(originalContent)
        binding.txtFilePath.text = filePath
        binding.txtFileType.text = fileType.name

        // Apply syntax highlighting based on file type
        applySyntaxHighlighting()
    }

    private fun applySyntaxHighlighting() {
        // Basic syntax highlighting would be implemented here
        // For now, just set the text
        when (fileType) {
            FileType.SMALI -> {
                // Apply smali syntax highlighting
            }
            FileType.XML -> {
                // Apply XML syntax highlighting
            }
            FileType.JAVA -> {
                // Apply Java syntax highlighting
            }
            else -> {}
        }
    }

    private fun updateSaveButton() {
        binding.btnSave.isEnabled = hasUnsavedChanges
    }

    private fun saveFile() {
        val content = binding.editTextContent.text.toString()
        onSaveListener?.invoke(content)
        originalContent = content
        hasUnsavedChanges = false
        updateSaveButton()
        Snackbar.make(binding.root, "File saved", Snackbar.LENGTH_SHORT).show()
    }

    private fun revertChanges() {
        if (hasUnsavedChanges) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Revert Changes")
                .setMessage("Are you sure you want to discard your changes?")
                .setPositiveButton("Revert") { _, _ ->
                    binding.editTextContent.setText(originalContent)
                    hasUnsavedChanges = false
                    updateSaveButton()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun aiExplainSelection() {
        val editor = binding.editTextContent
        val start = minOf(editor.selectionStart, editor.selectionEnd)
        val end = maxOf(editor.selectionStart, editor.selectionEnd)
        val selection = editor.text?.takeIf { start >= 0 && end <= it.length }?.substring(start, end)
        if (selection.isNullOrEmpty()) {
            Snackbar.make(binding.root, "Please select some text first", Snackbar.LENGTH_SHORT).show()
            return
        }

        showAiProgress("Analyzing selection...")

        scope.launch {
            val response = aiService.explainError(selection, null, fileType)
            hideAiProgress()

            when (response) {
                is AiResponse.Success -> {
                    showAiResult("Explanation", response.message)
                }
                is AiResponse.Error -> {
                    Snackbar.make(binding.root, response.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAiEditDialog() {
        val dialogBinding = DialogAiEditBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("AI Edit")
            .setMessage("Describe the changes you want to make:")
            .setView(dialogBinding.root)
            .setPositiveButton("Apply") { _, _ ->
                val instruction = dialogBinding.editTextInstruction.text.toString()
                if (instruction.isNotEmpty()) {
                    aiEditFile(instruction)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun aiEditFile(instruction: String) {
        val content = binding.editTextContent.text.toString()

        showAiProgress("Processing AI edit...")

        scope.launch {
            val response = aiService.modifyCode(content, instruction, fileType)
            hideAiProgress()

            when (response) {
                is AiResponse.Success -> {
                    // Extract code from response and apply
                    val modifiedCode = extractCodeFromResponse(response.message)
                    if (modifiedCode != null) {
                        binding.editTextContent.setText(modifiedCode)
                        showAiResult("AI Edit Result", response.message)
                    } else {
                        showAiResult("AI Edit Result", response.message)
                    }
                }
                is AiResponse.Error -> {
                    Snackbar.make(binding.root, response.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun aiAnalyzeFile() {
        val content = binding.editTextContent.text.toString()

        showAiProgress("Analyzing file...")

        scope.launch {
            val response =
                when {
                    filePath?.contains("AndroidManifest.xml") == true -> {
                        aiService.analyzeManifestSecurity(content)
                    }
                    else -> {
                        aiService.suggestImprovements(content, fileType)
                    }
                }

            hideAiProgress()

            when (response) {
                is AiResponse.Success -> {
                    showAiResult("Analysis Result", response.message)
                }
                is AiResponse.Error -> {
                    Snackbar.make(binding.root, response.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun aiConvertSmaliToJava() {
        val content = binding.editTextContent.text.toString()

        showAiProgress("Converting to Java...")

        scope.launch {
            val response = aiService.smaliToJava(content)
            hideAiProgress()

            when (response) {
                is AiResponse.Success -> {
                    showAiResult("Java Conversion", response.message)
                }
                is AiResponse.Error -> {
                    Snackbar.make(binding.root, response.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAiProgress(message: String) {
        binding.aiProgressContainer.visibility = View.VISIBLE
        binding.txtAiProgress.text = message
    }

    private fun hideAiProgress() {
        binding.aiProgressContainer.visibility = View.GONE
    }

    private fun showAiResult(
        title: String,
        message: String,
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                // Copy to clipboard
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText(title, message)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(binding.root, "Copied to clipboard", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun extractCodeFromResponse(response: String): String? {
        // Try to extract code from markdown code blocks
        val codeBlockPattern = """```(?:\w+)?\n([\s\S]*?)```""".toRegex()
        val match = codeBlockPattern.find(response)
        return match?.groupValues?.get(1)?.trim()
    }

    fun setOnSaveListener(listener: (String) -> Unit) {
        onSaveListener = listener
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.file_editor_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveFile()
                true
            }
            R.id.action_revert -> {
                revertChanges()
                true
            }
            R.id.action_ai_assistant -> {
                openFullAiAssistant()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openFullAiAssistant() {
        val aiFragment =
            AiAssistantFragment.newInstance(
                context = "File Editor: ${filePath?.substringAfterLast("/")}",
                fileContent = binding.editTextContent.text.toString(),
                filePath = filePath,
                projectDir = projectDir,
            )

        parentFragmentManager.beginTransaction()
            .replace(R.id.content_frame, aiFragment)
            .addToBackStack("ai_full")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}
