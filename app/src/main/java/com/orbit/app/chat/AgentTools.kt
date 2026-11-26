package com.orbit.app.chat

import com.orbit.app.data.model.ToolCall
import com.orbit.app.data.model.ToolSpec
import com.orbit.app.data.workspace.WorkspaceEntry
import com.orbit.app.data.workspace.WorkspaceRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class ToolOutcome(
    val content: String,
    val isError: Boolean = false,
)

/**
 * The workspace tools offered to the model. Read-only tools run immediately;
 * every mutating tool must be approved by the user first.
 */
class AgentTools(
    private val workspace: WorkspaceRepository,
    private val json: Json,
) {
    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = LIST_FILES,
            description = "List the files and folders inside a workspace directory. " +
                "Use an empty path for the workspace root.",
            parameters = schema(
                "path" to stringProperty("Directory path relative to the workspace root."),
            ),
        ),
        ToolSpec(
            name = READ_FILE,
            description = "Read a UTF-8 text file from the workspace. " +
                "Output is truncated when the file is very large.",
            parameters = schema(
                "path" to stringProperty("File path relative to the workspace root."),
            ),
            required = listOf("path"),
        ),
        ToolSpec(
            name = SEARCH_FILES,
            description = "Search text files in the workspace for a literal string. " +
                "Returns matching paths with line numbers.",
            parameters = schema(
                "query" to stringProperty("Text to search for."),
                "path" to stringProperty("Directory to start from. Empty means the root."),
            ),
            required = listOf("query"),
        ),
        ToolSpec(
            name = WRITE_FILE,
            description = "Replace the contents of an existing text file. Requires approval.",
            parameters = schema(
                "path" to stringProperty("File path relative to the workspace root."),
                "content" to stringProperty("New file contents."),
            ),
            required = listOf("path", "content"),
        ),
        ToolSpec(
            name = CREATE_FILE,
            description = "Create a new text file. Requires approval.",
            parameters = schema(
                "path" to stringProperty("Path for the new file, including its name."),
                "content" to stringProperty("Initial file contents. Defaults to empty."),
            ),
            required = listOf("path"),
        ),
        ToolSpec(
            name = CREATE_FOLDER,
            description = "Create a new folder. Requires approval.",
            parameters = schema(
                "path" to stringProperty("Path for the new folder, including its name."),
            ),
            required = listOf("path"),
        ),
        ToolSpec(
            name = DELETE_PATH,
            description = "Delete a file or folder and everything inside it. Requires approval.",
            parameters = schema(
                "path" to stringProperty("Path to delete, relative to the workspace root."),
            ),
            required = listOf("path"),
        ),
        ToolSpec(
            name = RENAME_PATH,
            description = "Rename a file or folder inside its current parent. Requires approval.",
            parameters = schema(
                "path" to stringProperty("Current path of the item."),
                "new_name" to stringProperty("New name, without slashes."),
            ),
            required = listOf("path", "new_name"),
        ),
    )

    fun isMutation(name: String): Boolean = name in MUTATING_TOOLS

    suspend fun execute(call: ToolCall): ToolOutcome {
        val args = runCatching { json.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse {
                return ToolOutcome("Invalid arguments: expected a JSON object.", isError = true)
            }
        return runCatching {
            when (call.name) {
                LIST_FILES -> listFiles(args.string("path").orEmpty())
                READ_FILE -> readFile(requirePath(args))
                SEARCH_FILES -> searchFiles(
                    root = args.string("path").orEmpty(),
                    query = args.string("query")
                        ?: error("The query argument is required."),
                )
                WRITE_FILE -> writeFile(
                    path = requirePath(args),
                    content = args.string("content").orEmpty(),
                )
                CREATE_FILE -> createFile(
                    path = requirePath(args),
                    content = args.string("content").orEmpty(),
                )
                CREATE_FOLDER -> createFolder(requirePath(args))
                DELETE_PATH -> deletePath(requirePath(args))
                RENAME_PATH -> renamePath(
                    path = requirePath(args),
                    newName = args.string("new_name")
                        ?: error("The new_name argument is required."),
                )
                else -> error("Unknown tool: ${call.name}")
            }
        }.getOrElse { error ->
            ToolOutcome(
                content = error.message ?: "The tool failed.",
                isError = true,
            )
        }
    }

    private suspend fun listFiles(path: String): ToolOutcome {
        val entries = workspace.list(path).getOrThrow()
        if (entries.isEmpty()) return ToolOutcome("The folder is empty.")
        val text = entries.take(MAX_LIST_ENTRIES).joinToString("\n") { entry ->
            if (entry.isDirectory) "${entry.name}/" else "${entry.name} (${formatSize(entry.size)})"
        }
        return ToolOutcome(text)
    }

    private suspend fun readFile(path: String): ToolOutcome {
        val text = workspace.read(path).getOrThrow()
        if (text.length <= MAX_OUTPUT_CHARS) return ToolOutcome(text)
        return ToolOutcome(
            text.take(MAX_OUTPUT_CHARS) +
                "\n\n[Truncated after $MAX_OUTPUT_CHARS characters. Read a smaller file or search instead.]",
        )
    }

    private suspend fun searchFiles(root: String, query: String): ToolOutcome {
        val matches = mutableListOf<String>()
        val budget = SearchBudget(files = MAX_SEARCH_FILES)
        search(root, query, matches, budget, depth = 0)
        if (matches.isEmpty()) return ToolOutcome("No matches for “$query”.")
        return ToolOutcome(matches.joinToString("\n"))
    }

    private suspend fun search(
        path: String,
        query: String,
        matches: MutableList<String>,
        budget: SearchBudget,
        depth: Int,
    ) {
        if (depth > MAX_SEARCH_DEPTH || budget.files <= 0 || matches.size >= MAX_MATCHES) return
        val entries: List<WorkspaceEntry> = workspace.list(path).getOrElse { return }
        for (entry in entries) {
            if (budget.files <= 0 || matches.size >= MAX_MATCHES) return
            if (entry.isDirectory) {
                search(entry.path, query, matches, budget, depth + 1)
                continue
            }
            if (entry.size > MAX_SEARCH_FILE_BYTES) continue
            budget.files -= 1
            val text = workspace.read(entry.path).getOrNull() ?: continue
            text.lineSequence().forEachIndexed { index, line ->
                if (matches.size < MAX_MATCHES && line.contains(query, ignoreCase = true)) {
                    matches += "${entry.path}:${index + 1}: ${line.trim().take(180)}"
                }
            }
        }
    }

    private suspend fun writeFile(path: String, content: String): ToolOutcome {
        val parent = parentOf(path)
        val name = nameOf(path)
        val existing = workspace.list(parent).getOrThrow().firstOrNull { it.name == name }
        if (existing == null) {
            workspace.createFile(parent, name, content).getOrThrow()
            return ToolOutcome("Created $path.")
        }
        if (existing.isDirectory) error("That path is a folder.")
        workspace.write(path, content).getOrThrow()
        return ToolOutcome("Wrote ${content.length} characters to $path.")
    }

    private suspend fun createFile(path: String, content: String): ToolOutcome {
        workspace.createFile(parentOf(path), nameOf(path), content).getOrThrow()
        return ToolOutcome("Created $path.")
    }

    private suspend fun createFolder(path: String): ToolOutcome {
        workspace.createFolder(parentOf(path), nameOf(path)).getOrThrow()
        return ToolOutcome("Created folder $path.")
    }

    private suspend fun deletePath(path: String): ToolOutcome {
        workspace.delete(path).getOrThrow()
        return ToolOutcome("Deleted $path.")
    }

    private suspend fun renamePath(path: String, newName: String): ToolOutcome {
        val renamed = workspace.rename(path, newName).getOrThrow()
        return ToolOutcome("Renamed to $renamed.")
    }

    private fun requirePath(args: JsonObject): String =
        args.string("path")?.takeIf { it.isNotBlank() }
            ?: error("The path argument is required.")

    private fun parentOf(path: String): String =
        path.split('/').filter { it.isNotBlank() }.dropLast(1).joinToString("/")

    private fun nameOf(path: String): String =
        path.split('/').lastOrNull { it.isNotBlank() } ?: error("The path needs a file name.")

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun schema(vararg properties: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (name, value) -> put(name, value) }
        }
        put("additionalProperties", false)
    }

    private fun stringProperty(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private class SearchBudget(var files: Int)

    companion object {
        const val LIST_FILES = "list_files"
        const val READ_FILE = "read_file"
        const val SEARCH_FILES = "search_files"
        const val WRITE_FILE = "write_file"
        const val CREATE_FILE = "create_file"
        const val CREATE_FOLDER = "create_folder"
        const val DELETE_PATH = "delete_path"
        const val RENAME_PATH = "rename_path"

        private val MUTATING_TOOLS = setOf(
            WRITE_FILE,
            CREATE_FILE,
            CREATE_FOLDER,
            DELETE_PATH,
            RENAME_PATH,
        )

        private const val MAX_OUTPUT_CHARS = 40_000
        private const val MAX_LIST_ENTRIES = 500
        private const val MAX_MATCHES = 40
        private const val MAX_SEARCH_FILES = 120
        private const val MAX_SEARCH_DEPTH = 6
        private const val MAX_SEARCH_FILE_BYTES = 512 * 1024
    }
}
