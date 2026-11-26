package com.orbit.app.chat

import com.orbit.app.data.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallFormatTest {
    @Test
    fun readsStringArguments() {
        val call = ToolCall(
            id = "1",
            name = "write_file",
            arguments = """{"path":"src/Main.kt","content":"fun main() {}"}""",
        )
        assertEquals("src/Main.kt", toolArgument(call, "path"))
        assertEquals("fun main() {}", toolArgument(call, "content"))
        assertNull(toolArgument(call, "missing"))
    }

    @Test
    fun invalidJsonHasNoArguments() {
        val call = ToolCall(id = "1", name = "read_file", arguments = "not json")
        assertNull(toolArgument(call, "path"))
        assertEquals("", describeToolCall(call))
    }

    @Test
    fun describesSearchCalls() {
        val call = ToolCall(
            id = "1",
            name = "search_files",
            arguments = """{"query":"TODO","path":"app"}""",
        )
        assertEquals("“TODO” in app", describeToolCall(call))
    }

    @Test
    fun describesRenameCalls() {
        val call = ToolCall(
            id = "1",
            name = "rename_path",
            arguments = """{"path":"old.txt","new_name":"new.txt"}""",
        )
        assertEquals("old.txt → new.txt", describeToolCall(call))
    }
}
