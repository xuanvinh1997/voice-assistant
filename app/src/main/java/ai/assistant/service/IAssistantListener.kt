package ai.assistant.service

import ai.assistant.Message

interface IAssistantListener {
    fun onNewMessageSent(message: Message)
}