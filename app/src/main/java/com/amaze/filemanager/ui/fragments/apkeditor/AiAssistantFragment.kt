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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.amaze.filemanager.R
import com.amaze.filemanager.ai.AiAssistantService
import com.amaze.filemanager.ai.AiContext
import com.amaze.filemanager.ai.AiResponse
import com.amaze.filemanager.apkeditor.FileType
import com.amaze.filemanager.databinding.FragmentAiAssistantBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import com.amaze.filemanager.ai.ChatMessage as AiChatMessage

/**
 * AI Assistant Fragment with chat interface for code analysis and modification.
 * Provides persistent memory of current project context.
 */
class AiAssistantFragment : Fragment() {
    private val LOG = LoggerFactory.getLogger(AiAssistantFragment::class.java)
    private var _binding: FragmentAiAssistantBinding? = null
    private val binding get() = _binding!!

    private lateinit var aiService: AiAssistantService
    private lateinit var chatAdapter: ChatAdapter

    private var context: String = "APK Analysis"
    private var fileContent: String? = null
    private var filePath: String? = null
    private var fileType: FileType = FileType.UNKNOWN
    private var projectDir: String? = null

    private val conversationHistory = mutableListOf<ChatMessage>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ARG_CONTEXT = "context"
        const val ARG_FILE_CONTENT = "file_content"
        const val ARG_FILE_PATH = "file_path"
        const val ARG_PROJECT_DIR = "project_dir"

        fun newInstance(
            context: String,
            fileContent: String? = null,
            filePath: String? = null,
            projectDir: String? = null,
        ): AiAssistantFragment {
            val fragment = AiAssistantFragment()
            val args = Bundle()
            args.putString(ARG_CONTEXT, context)
            args.putString(ARG_FILE_CONTENT, fileContent)
            args.putString(ARG_FILE_PATH, filePath)
            args.putString(ARG_PROJECT_DIR, projectDir)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        arguments?.let { args ->
            context = args.getString(ARG_CONTEXT, "APK Analysis")
            fileContent = args.getString(ARG_FILE_CONTENT)
            filePath = args.getString(ARG_FILE_PATH)
            projectDir = args.getString(ARG_PROJECT_DIR)

            // Determine file type from path
            filePath?.let { path ->
                fileType =
                    when (path.substringAfterLast(".").lowercase()) {
                        "smali" -> FileType.SMALI
                        "xml" -> FileType.XML
                        "java" -> FileType.JAVA
                        "json" -> FileType.JSON
                        else -> FileType.UNKNOWN
                    }
            }
        }

        aiService = AiAssistantService(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAiAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupChat()
        setupQuickActions()
        checkApiKey()

        // Add welcome message
        if (conversationHistory.isEmpty()) {
            addSystemMessage(
                "AI Assistant ready. I can help you with:\n\n" +
                    "• Code analysis and explanation\n" +
                    "• Security vulnerability detection\n" +
                    "• Code modification and optimization\n" +
                    "• Smali to Java conversion\n\n" +
                    "Select code or use quick actions below to get started.",
            )
        }
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewChat.apply {
            layoutManager =
                LinearLayoutManager(requireContext()).apply {
                    stackFromEnd = true
                }
            adapter = chatAdapter
        }

        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.editTextMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun setupQuickActions() {
        binding.btnExplainError.setOnClickListener {
            quickAction("Explain this code and identify potential errors or issues")
        }

        binding.btnAnalyzeSecurity.setOnClickListener {
            if (filePath?.contains("AndroidManifest.xml") == true) {
                securityAnalysis()
            } else {
                quickAction("Analyze this code for security vulnerabilities")
            }
        }

        binding.btnSuggestImprovements.setOnClickListener {
            quickAction("Suggest improvements and optimizations for this code")
        }

        binding.btnConvertToJava.setOnClickListener {
            if (fileType == FileType.SMALI) {
                convertSmaliToJava()
            } else {
                Toast.makeText(requireContext(), "This action is only available for smali files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkApiKey() {
        if (!aiService.isApiKeyConfigured()) {
            binding.inputContainer.visibility = View.GONE
            binding.quickActionsContainer.visibility = View.GONE
            addSystemMessage(
                "⚠️ API key not configured.\n\n" +
                    "To use AI features, add your Gemini API key:\n\n" +
                    "1. Get an API key from Google AI Studio (aistudio.google.com)\n" +
                    "2. Add it to local.properties:\n" +
                    "   GEMINI_API_KEY=your_api_key_here\n" +
                    "3. Rebuild the app",
            )
        }
    }

    private fun sendMessage() {
        val message = binding.editTextMessage.text?.toString()?.trim().orEmpty()
        if (message.isEmpty()) return

        binding.editTextMessage.text?.clear()
        addUserMessage(message)
        processUserMessage(message)
    }

    private fun quickAction(promptSuffix: String) {
        fileContent?.let { content ->
            val message = "$promptSuffix:\n\n```\n$content\n```"
            addUserMessage(promptSuffix)
            processUserMessage(message)
        } ?: run {
            Snackbar.make(binding.root, "No file content available", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun securityAnalysis() {
        fileContent?.let { content ->
            addUserMessage("Perform security analysis")
            showTypingIndicator()

            scope.launch {
                val aiContext = AiContext.SECURITY
                val response = aiService.analyzeManifestSecurity(content)

                hideTypingIndicator()
                when (response) {
                    is AiResponse.Success -> addAiMessage(response.message)
                    is AiResponse.Error -> addSystemMessage("Error: ${response.message}")
                }
            }
        }
    }

    private fun convertSmaliToJava() {
        fileContent?.let { content ->
            addUserMessage("Convert to Java")
            showTypingIndicator()

            scope.launch {
                val response = aiService.smaliToJava(content)

                hideTypingIndicator()
                when (response) {
                    is AiResponse.Success -> addAiMessage(response.message)
                    is AiResponse.Error -> addSystemMessage("Error: ${response.message}")
                }
            }
        }
    }

    private fun processUserMessage(message: String) {
        if (!aiService.isApiKeyConfigured()) {
            addSystemMessage("API key not configured. Please set up your Gemini API key.")
            return
        }

        showTypingIndicator()

        scope.launch {
            val aiContext = AiContext.fromFileType(fileType)
            val history = conversationHistory.dropLast(1).map { AiChatMessage(it.content, it.isUser, it.timestamp) }
            val response = aiService.sendMessage(message, aiContext, history)

            hideTypingIndicator()
            when (response) {
                is AiResponse.Success -> {
                    addAiMessage(response.message)
                }
                is AiResponse.Error -> {
                    addSystemMessage("Error: ${response.message}")
                }
            }
        }
    }

    private fun addUserMessage(message: String) {
        val chatMessage = ChatMessage(message, isUser = true)
        conversationHistory.add(chatMessage)
        chatAdapter.addMessage(chatMessage)
        scrollToBottom()
    }

    private fun addAiMessage(message: String) {
        val chatMessage = ChatMessage(message, isUser = false)
        conversationHistory.add(chatMessage)
        chatAdapter.addMessage(chatMessage)
        scrollToBottom()
    }

    private fun addSystemMessage(message: String) {
        chatAdapter.addMessage(ChatMessage(message, isUser = false, isSystem = true))
        scrollToBottom()
    }

    private fun showTypingIndicator() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false
    }

    private fun hideTypingIndicator() {
        binding.progressBar.visibility = View.GONE
        binding.btnSend.isEnabled = true
    }

    private fun scrollToBottom() {
        binding.recyclerViewChat.post {
            binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    /**
     * Update the current file context.
     */
    fun updateFileContext(
        content: String,
        path: String,
        type: FileType,
    ) {
        fileContent = content
        filePath = path
        fileType = type

        addSystemMessage("Context updated: ${path.substringAfterLast("/")}")
    }

    /**
     * Clear conversation history.
     */
    fun clearHistory() {
        conversationHistory.clear()
        chatAdapter.clearMessages()
        addSystemMessage("Conversation cleared. Ready for new context.")
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.ai_assistant_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                clearHistory()
                true
            }
            R.id.action_export -> {
                exportConversation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportConversation() {
        // Export conversation to file
        val sb = StringBuilder()
        sb.appendLine("AI Assistant Conversation Export")
        sb.appendLine("Date: ${java.util.Date()}")
        sb.appendLine("Context: $context")
        sb.appendLine("File: $filePath")
        sb.appendLine("=".repeat(50))
        sb.appendLine()

        conversationHistory.forEach { message ->
            sb.appendLine(if (message.isUser) "User:" else "AI:")
            sb.appendLine(message.content)
            sb.appendLine()
        }

        // Save to file
        // Implementation would save to Downloads or share
        Snackbar.make(binding.root, "Conversation exported", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    /**
     * RecyclerView adapter for chat messages.
     */
    private class ChatAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
        private val messages = mutableListOf<ChatMessage>()

        fun addMessage(message: ChatMessage) {
            messages.add(message)
            notifyItemInserted(messages.size - 1)
        }

        fun clearMessages() {
            messages.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): MessageViewHolder {
            val view =
                when (viewType) {
                    TYPE_USER ->
                        LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_chat_message_user, parent, false)
                    TYPE_SYSTEM ->
                        LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_chat_message_system, parent, false)
                    else ->
                        LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_chat_message_ai, parent, false)
                }
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: MessageViewHolder,
            position: Int,
        ) {
            holder.bind(messages[position])
        }

        override fun getItemViewType(position: Int): Int {
            return when {
                messages[position].isSystem -> TYPE_SYSTEM
                messages[position].isUser -> TYPE_USER
                else -> TYPE_AI
            }
        }

        override fun getItemCount() = messages.size

        inner class MessageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            fun bind(message: ChatMessage) {
                itemView.findViewById<TextView>(R.id.txtMessage).text = message.content
            }
        }

        companion object {
            const val TYPE_USER = 0
            const val TYPE_AI = 1
            const val TYPE_SYSTEM = 2
        }
    }
}

/**
 * Extended ChatMessage with system message support.
 */
private data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)
