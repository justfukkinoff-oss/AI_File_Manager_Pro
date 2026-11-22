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
import java.io.File

/**
 * Handles parsing and editing of Smali files.
 * Provides functionality to analyze and modify Dalvik bytecode in smali format.
 */
class SmaliEditor {
    private val LOG = LoggerFactory.getLogger(SmaliEditor::class.java)

    companion object {
        const val CLASS_DIRECTIVE = ".class"
        const val SUPER_DIRECTIVE = ".super"
        const val SOURCE_DIRECTIVE = ".source"
        const val METHOD_DIRECTIVE = ".method"
        const val END_METHOD_DIRECTIVE = ".end method"
        const val FIELD_DIRECTIVE = ".field"
        const val ANNOTATION_DIRECTIVE = ".annotation"
        const val END_ANNOTATION_DIRECTIVE = ".end annotation"

        // Common access modifiers
        const val MODIFIER_PUBLIC = "public"
        const val MODIFIER_PRIVATE = "private"
        const val MODIFIER_PROTECTED = "protected"
        const val MODIFIER_STATIC = "static"
        const val MODIFIER_FINAL = "final"
        const val MODIFIER_SYNCHRONIZED = "synchronized"
        const val MODIFIER_NATIVE = "native"
        const val MODIFIER_ABSTRACT = "abstract"
    }

    /**
     * Parse a smali file and extract its structure.
     */
    fun parseSmali(content: String): SmaliFile {
        var className = ""
        var superClass = ""
        var sourceFile = ""
        val methods = mutableListOf<SmaliMethod>()
        val fields = mutableListOf<SmaliField>()
        val annotations = mutableListOf<SmaliAnnotation>()

        try {
            val lines = content.lines()
            var i = 0

            while (i < lines.size) {
                val line = lines[i].trim()

                when {
                    line.startsWith(CLASS_DIRECTIVE) -> {
                        className = parseClassDirective(line)
                    }
                    line.startsWith(SUPER_DIRECTIVE) -> {
                        superClass = parseSuperDirective(line)
                    }
                    line.startsWith(SOURCE_DIRECTIVE) -> {
                        sourceFile = parseSourceDirective(line)
                    }
                    line.startsWith(FIELD_DIRECTIVE) -> {
                        fields.add(parseFieldDirective(line))
                    }
                    line.startsWith(METHOD_DIRECTIVE) -> {
                        val methodResult = parseMethod(lines, i)
                        methods.add(methodResult.first)
                        i = methodResult.second
                    }
                    line.startsWith(ANNOTATION_DIRECTIVE) -> {
                        val annotationResult = parseAnnotation(lines, i)
                        annotations.add(annotationResult.first)
                        i = annotationResult.second
                    }
                }
                i++
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse smali file", e)
        }

        return SmaliFile(
            className = className,
            superClass = superClass,
            sourceFile = sourceFile,
            methods = methods,
            fields = fields,
            annotations = annotations,
            content = content,
        )
    }

    /**
     * Parse a smali file from path.
     */
    fun parseSmaliFile(filePath: String): SmaliFile? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.extension == "smali") {
                parseSmali(file.readText())
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse smali file: $filePath", e)
            null
        }
    }

    /**
     * Get all method names in a smali file.
     */
    fun getMethodNames(content: String): List<String> {
        val methods = mutableListOf<String>()
        content.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith(METHOD_DIRECTIVE)) {
                // Extract method name and signature
                val methodSignature = trimmedLine.removePrefix(METHOD_DIRECTIVE).trim()
                val nameStart = methodSignature.indexOfLast { it == ' ' } + 1
                if (nameStart > 0 && nameStart < methodSignature.length) {
                    val signature = methodSignature.substring(nameStart)
                    val name = signature.substringBefore("(")
                    methods.add(name)
                }
            }
        }
        return methods
    }

    /**
     * Get all field names in a smali file.
     */
    fun getFieldNames(content: String): List<String> {
        val fields = mutableListOf<String>()
        content.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith(FIELD_DIRECTIVE)) {
                val fieldDef = trimmedLine.removePrefix(FIELD_DIRECTIVE).trim()
                // Field format: [modifiers] name:Type
                val nameWithType = fieldDef.substringAfterLast(' ')
                val name = nameWithType.substringBefore(":")
                fields.add(name)
            }
        }
        return fields
    }

    /**
     * Find all string literals in the smali code.
     */
    fun findStringLiterals(content: String): List<StringLiteral> {
        val literals = mutableListOf<StringLiteral>()
        val stringPattern = """const-string(?:/jumbo)?\s+\w+,\s+"(.*)"""".toRegex()

        content.lines().forEachIndexed { index, line ->
            stringPattern.findAll(line).forEach { match ->
                val stringValue = match.groupValues[1]
                // Unescape common escape sequences
                val unescaped =
                    stringValue
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")

                literals.add(
                    StringLiteral(
                        value = unescaped,
                        lineNumber = index + 1,
                        rawLine = line.trim(),
                    ),
                )
            }
        }

        return literals
    }

    /**
     * Find all method invocations in the smali code.
     */
    fun findMethodInvocations(content: String): List<MethodInvocation> {
        val invocations = mutableListOf<MethodInvocation>()
        val invokePatterns =
            listOf(
                "invoke-virtual",
                "invoke-static",
                "invoke-direct",
                "invoke-interface",
                "invoke-super",
                "invoke-virtual/range",
                "invoke-static/range",
                "invoke-direct/range",
                "invoke-interface/range",
            )

        content.lines().forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            invokePatterns.forEach { pattern ->
                if (trimmedLine.startsWith(pattern)) {
                    // Extract method signature
                    // Format: invoke-type {regs}, Class->method(Args)Ret
                    val methodSignature = trimmedLine.substringAfter("}").trim()
                    val classAndMethod = methodSignature.substringBefore("(")
                    val className = classAndMethod.substringBefore("->")
                    val methodName = classAndMethod.substringAfter("->")

                    invocations.add(
                        MethodInvocation(
                            invokeType = pattern,
                            className = className,
                            methodName = methodName,
                            lineNumber = index + 1,
                            rawLine = trimmedLine,
                        ),
                    )
                }
            }
        }

        return invocations
    }

    /**
     * Find class references in the smali code.
     */
    fun findClassReferences(content: String): List<String> {
        val references = mutableSetOf<String>()
        val classPattern = """L[\w/$]+;""".toRegex()

        classPattern.findAll(content).forEach { match ->
            references.add(match.value)
        }

        return references.toList().sorted()
    }

    /**
     * Modify a string literal in the smali code.
     */
    fun modifyStringLiteral(
        content: String,
        oldString: String,
        newString: String,
    ): String {
        // Escape special characters for smali
        val escapedOld =
            oldString
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")

        val escapedNew =
            newString
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")

        return content.replace("\"$escapedOld\"", "\"$escapedNew\"")
    }

    /**
     * Add a new field to the class.
     */
    fun addField(
        content: String,
        fieldName: String,
        fieldType: String,
        modifiers: String = "public",
    ): String {
        val fieldDirective = ".field $modifiers $fieldName:$fieldType\n"

        // Find a good position to insert (after existing fields or before methods)
        val methodIndex = content.indexOf(METHOD_DIRECTIVE)
        val fieldIndex = content.lastIndexOf(FIELD_DIRECTIVE)

        val insertIndex =
            if (fieldIndex >= 0) {
                // Insert after the last field
                val nextLineIndex = content.indexOf('\n', fieldIndex)
                nextLineIndex + 1
            } else if (methodIndex >= 0) {
                // Insert before first method
                methodIndex
            } else {
                // Insert at the end
                content.length
            }

        return buildString {
            append(content.substring(0, insertIndex))
            append(fieldDirective)
            append(content.substring(insertIndex))
        }
    }

    /**
     * Get a summary of the smali file.
     */
    fun getSmaliSummary(content: String): SmaliSummary {
        val smaliFile = parseSmali(content)
        val stringLiterals = findStringLiterals(content)
        val methodInvocations = findMethodInvocations(content)
        val classReferences = findClassReferences(content)

        return SmaliSummary(
            className = smaliFile.className,
            superClass = smaliFile.superClass,
            methodCount = smaliFile.methods.size,
            fieldCount = smaliFile.fields.size,
            stringLiteralCount = stringLiterals.size,
            methodInvocationCount = methodInvocations.size,
            classReferenceCount = classReferences.size,
            methods = smaliFile.methods.map { it.name },
            fields = smaliFile.fields.map { it.name },
        )
    }

    // Private helper methods

    private fun parseClassDirective(line: String): String {
        // .class [modifiers] LclassName;
        return line.removePrefix(CLASS_DIRECTIVE).trim()
            .substringAfterLast(' ')
            .removeSurrounding("L", ";")
            .replace("/", ".")
    }

    private fun parseSuperDirective(line: String): String {
        // .super LclassName;
        return line.removePrefix(SUPER_DIRECTIVE).trim()
            .removeSurrounding("L", ";")
            .replace("/", ".")
    }

    private fun parseSourceDirective(line: String): String {
        // .source "SourceFile.java"
        return line.removePrefix(SOURCE_DIRECTIVE).trim()
            .removeSurrounding("\"")
    }

    private fun parseFieldDirective(line: String): SmaliField {
        // .field [modifiers] name:Type [ = value ]
        val fieldDef = line.removePrefix(FIELD_DIRECTIVE).trim()

        val parts = fieldDef.split(" ")
        val nameWithType = parts.last()
        val colonIndex = nameWithType.indexOf(':')

        val name = nameWithType.substring(0, colonIndex)
        val type = nameWithType.substring(colonIndex + 1)

        val modifiers =
            if (parts.size > 1) {
                parts.dropLast(1).joinToString(" ")
            } else {
                ""
            }

        val initialValue =
            if (nameWithType.contains("=")) {
                nameWithType.substringAfter("=").trim()
            } else {
                null
            }

        return SmaliField(
            name = name,
            type = type,
            accessFlags = modifiers,
            initialValue = initialValue,
        )
    }

    private fun parseMethod(
        lines: List<String>,
        startIndex: Int,
    ): Pair<SmaliMethod, Int> {
        val methodLine = lines[startIndex].trim()
        val methodDef = methodLine.removePrefix(METHOD_DIRECTIVE).trim()

        // Parse method signature
        // format: [modifiers] name(Args)Ret
        val signatureMatch = """(\S+)\s+(\w+)\(([^)]*)\)(.+)""".toRegex().find(methodDef)

        val (modifiers, name, params, returnType) =
            if (signatureMatch != null) {
                val values = signatureMatch.destructured
                Tuple4(values.component1(), values.component2(), values.component3(), values.component4())
            } else {
                // No modifiers
                val nameStart = methodDef.indexOf("(")
                val name = methodDef.substring(0, nameStart)
                val paramsEnd = methodDef.indexOf(")")
                val params = methodDef.substring(nameStart + 1, paramsEnd)
                val returnType = methodDef.substring(paramsEnd + 1)
                Tuple4("", name, params, returnType)
            }

        // Find method body
        val bodyBuilder = StringBuilder()
        var endIndex = startIndex + 1
        while (endIndex < lines.size && !lines[endIndex].trim().startsWith(END_METHOD_DIRECTIVE)) {
            bodyBuilder.appendLine(lines[endIndex])
            endIndex++
        }

        return Pair(
            SmaliMethod(
                name = name,
                returnType = returnType,
                parameters = parseParameterTypes(params),
                accessFlags = modifiers,
                body = bodyBuilder.toString(),
            ),
            endIndex,
        )
    }

    private fun parseAnnotation(
        lines: List<String>,
        startIndex: Int,
    ): Pair<SmaliAnnotation, Int> {
        val annotationLine = lines[startIndex].trim()
        val annotationType = annotationLine.removePrefix(ANNOTATION_DIRECTIVE).trim()

        val values = mutableMapOf<String, String>()
        var endIndex = startIndex + 1

        while (endIndex < lines.size && !lines[endIndex].trim().startsWith(END_ANNOTATION_DIRECTIVE)) {
            val valueLine = lines[endIndex].trim()
            val equalsIndex = valueLine.indexOf('=')
            if (equalsIndex > 0) {
                val key = valueLine.substring(0, equalsIndex).trim()
                val value = valueLine.substring(equalsIndex + 1).trim()
                values[key] = value
            }
            endIndex++
        }

        return Pair(
            SmaliAnnotation(type = annotationType, values = values),
            endIndex,
        )
    }

    private fun parseParameterTypes(params: String): List<String> {
        if (params.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var i = 0
        while (i < params.length) {
            when (val c = params[i]) {
                'V', 'Z', 'B', 'S', 'C', 'I', 'J', 'F', 'D' -> {
                    result.add(c.toString())
                    i++
                }
                'L' -> {
                    val end = params.indexOf(';', i)
                    if (end > i) {
                        result.add(params.substring(i, end + 1))
                        i = end + 1
                    } else {
                        i++
                    }
                }
                '[' -> {
                    // Array type, continue to next char
                    i++
                }
                else -> i++
            }
        }
        return result
    }

    // Helper data class for tuples
    private data class Tuple4<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)
}

/**
 * Represents a string literal found in smali code.
 */
data class StringLiteral(
    val value: String,
    val lineNumber: Int,
    val rawLine: String,
)

/**
 * Represents a method invocation in smali code.
 */
data class MethodInvocation(
    val invokeType: String,
    val className: String,
    val methodName: String,
    val lineNumber: Int,
    val rawLine: String,
)

/**
 * Summary of a smali file.
 */
data class SmaliSummary(
    val className: String,
    val superClass: String,
    val methodCount: Int,
    val fieldCount: Int,
    val stringLiteralCount: Int,
    val methodInvocationCount: Int,
    val classReferenceCount: Int,
    val methods: List<String>,
    val fields: List<String>,
)
