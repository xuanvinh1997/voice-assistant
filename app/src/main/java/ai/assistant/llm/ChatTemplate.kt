object ChatTemplate {

    data class Message(val role: String, val content: String)
    data class Tool(val name: String, val description: String)

    private val lama3Template = """
        {{IF_SYSTEM}}
        {{TOOLS}}
        {{FOR_MESSAGES}}
        {{IF_TOOL_CALL}}
    """.trimIndent()

    @JvmStatic
    fun render(messages: List<Message>, tools: List<Tool>, toolCall: Boolean = false): String {
        // Ensure there is a system message at the beginning
        val updatedMessages = if (messages.first().role != "system") {
            listOf(Message("system", "")) + messages
        } else {
            messages
        }

        // Convert tools to a JSON string
        val toolsJson = tools.joinToString("\n") { tool ->
            """{
                "name": "${tool.name}",
                "description": "${tool.description}"
            }"""
        }

        // Create the template parts
        val ifSystem = if (updatedMessages[0].role == "system") {
            "system\n\n${updatedMessages[0].content}\n"
        } else {
            ""
        }

        val forMessages = updatedMessages.joinToString("\n") { message ->
            when (message.role) {
                "user" -> "user\n\n${message.content}\n"
                "tool" -> "assistant\n\n<functioncall> ${message.content}\n"
                "tool_response", "assistant" -> "assistant\n\n${message.content}\n"
                else -> ""
            }
        }

        val ifToolCall = if (toolCall) "assistant\n\n<functioncall>" else ""

        // Replace the placeholders with the actual content
        return lama3Template
            .replace("{{IF_SYSTEM}}", ifSystem)
            .replace("{{TOOLS}}", toolsJson)
            .replace("{{FOR_MESSAGES}}", forMessages)
            .replace("{{IF_TOOL_CALL}}", ifToolCall)
    }

    @JvmStatic
    fun getFuncCall(text: String, prompt: String): String {
        return text.split(prompt)[1].split(" ")[0]
    }
}
