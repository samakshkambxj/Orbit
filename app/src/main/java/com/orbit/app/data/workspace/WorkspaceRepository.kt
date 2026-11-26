package com.orbit.app.data.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.orbit.app.data.prefs.SettingsRepository
import com.orbit.app.data.prefs.WorkspaceRef
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class WorkspaceEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)

/**
 * A folder the user granted through the Storage Access Framework. All paths are
 * relative to the chosen folder and never escape it.
 */
class WorkspaceRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    val workspace: Flow<WorkspaceRef?> = settings.workspace

    suspend fun adopt(uri: Uri): Result<WorkspaceRef> = withContext(Dispatchers.IO) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val root = DocumentFile.fromTreeUri(context, uri)
                ?: error("That folder could not be opened.")
            require(root.canRead()) { "ORBIT does not have read access to that folder." }
            require(root.canWrite()) { "ORBIT needs write access to edit files in that folder." }
            val name = root.name?.takeIf { it.isNotBlank() } ?: "Workspace"
            settings.setWorkspace(uri.toString(), name)
            WorkspaceRef(uri.toString(), name)
        }
    }

    suspend fun forget() = withContext(Dispatchers.IO) {
        val current = settings.workspace.first()
        if (current != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(current.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settings.setWorkspace(null, null)
    }

    suspend fun list(path: String): Result<List<WorkspaceEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = resolve(path) ?: error("That folder no longer exists.")
            require(folder.isDirectory) { "That path is not a folder." }
            folder.listFiles()
                .filter { it.name != null }
                .map { file ->
                    WorkspaceEntry(
                        name = file.name.orEmpty(),
                        path = join(path, file.name.orEmpty()),
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified(),
                    )
                }
                .sortedWith(
                    compareByDescending<WorkspaceEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() },
                )
        }
    }

    suspend fun read(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolve(path) ?: error("That file no longer exists.")
            require(file.isFile) { "That path is not a file." }
            require(file.length() <= MAX_READ_BYTES) {
                "That file is larger than ${MAX_READ_BYTES / 1024} KB."
            }
            val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: error("The file could not be opened.")
            require(bytes.size <= MAX_READ_BYTES) {
                "That file is larger than ${MAX_READ_BYTES / 1024} KB."
            }
            require(!looksBinary(bytes)) { "That file does not look like text." }
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    suspend fun write(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(content.length <= MAX_WRITE_CHARS) {
                "That file is larger than ${MAX_WRITE_CHARS / 1024} KB."
            }
            val file = resolve(path) ?: error("That file no longer exists.")
            require(file.isFile) { "That path is not a file." }
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray(StandardCharsets.UTF_8))
            } ?: error("The file could not be written.")
        }
    }

    suspend fun createFile(
        parentPath: String,
        name: String,
        content: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireValidName(name)
            require(content.length <= MAX_WRITE_CHARS) { "That file would be too large." }
            val parent = resolve(parentPath) ?: error("That folder no longer exists.")
            require(parent.isDirectory) { "That path is not a folder." }
            require(parent.findFile(name) == null) { "A file named “$name” already exists." }
            val created = parent.createFile("text/plain", name)
                ?: error("The file could not be created.")
            if (content.isNotEmpty()) {
                context.contentResolver.openOutputStream(created.uri, "wt")?.use { stream ->
                    stream.write(content.toByteArray(StandardCharsets.UTF_8))
                } ?: error("The file could not be written.")
            }
            join(parentPath, created.name ?: name)
        }
    }

    suspend fun createFolder(parentPath: String, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireValidName(name)
                val parent = resolve(parentPath) ?: error("That folder no longer exists.")
                require(parent.isDirectory) { "That path is not a folder." }
                require(parent.findFile(name) == null) { "A folder named “$name” already exists." }
                val created = parent.createDirectory(name)
                    ?: error("The folder could not be created.")
                join(parentPath, created.name ?: name)
            }
        }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(segments(path).isNotEmpty()) { "The workspace root cannot be deleted." }
            val target = resolve(path) ?: error("That item no longer exists.")
            require(target.delete()) { "That item could not be deleted." }
        }
    }

    suspend fun rename(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireValidName(newName)
            require(segments(path).isNotEmpty()) { "The workspace root cannot be renamed." }
            val target = resolve(path) ?: error("That item no longer exists.")
            val parentPath = segments(path).dropLast(1).joinToString("/")
            val parent = resolve(parentPath) ?: error("That folder no longer exists.")
            require(parent.findFile(newName) == null) {
                "An item named “$newName” already exists."
            }
            require(target.renameTo(newName)) { "That item could not be renamed." }
            join(parentPath, newName)
        }
    }

    private suspend fun resolve(path: String): DocumentFile? {
        val root = root() ?: return null
        var current = root
        segments(path).forEach { name ->
            current = current.findFile(name) ?: return null
        }
        return current
    }

    private suspend fun root(): DocumentFile? {
        val ref = settings.workspace.first() ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(ref.uri))
    }

    private fun segments(path: String): List<String> {
        val parts = path.split('/').filter { it.isNotBlank() }
        require(parts.none { it == "." || it == ".." }) { "Invalid path." }
        return parts
    }

    private fun join(parent: String, name: String): String =
        listOf(parent, name).filter { it.isNotBlank() }.joinToString("/")

    private fun requireValidName(name: String) {
        require(name.isNotBlank()) { "Enter a name." }
        require(name.length <= 128) { "That name is too long." }
        require('/' !in name && '\\' !in name) { "Names cannot contain slashes." }
        require(name != "." && name != "..") { "That name is not allowed." }
    }

    private fun looksBinary(bytes: ByteArray): Boolean =
        bytes.take(8_192).any { it == 0.toByte() }

    companion object {
        const val MAX_READ_BYTES = 2 * 1024 * 1024
        const val MAX_WRITE_CHARS = 1024 * 1024
    }
}
