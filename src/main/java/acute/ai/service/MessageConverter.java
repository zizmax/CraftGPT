package acute.ai.service;

import acute.ai.service.ChatMessage;
import acute.ai.service.ChatMessageRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting between different message formats
 */
public class MessageConverter {
    
    /**
     * Convert from theokanning ChatMessage to our Message format
     */
    public static Message fromOpenAIMessage(ChatMessage openAIMessage) {
        return new Message(openAIMessage.getRole(), openAIMessage.getContent());
    }
    
    /**
     * Convert from our Message to theokanning ChatMessage format
     */
    public static ChatMessage toOpenAIMessage(Message message) {
        return new ChatMessage(message.getRole(), message.getContent());
    }
    
    /**
     * Convert a list of theokanning ChatMessage to our Message format
     */
    public static List<Message> fromOpenAIMessages(List<ChatMessage> openAIMessages) {
        List<Message> messages = new ArrayList<>(openAIMessages.size());
        for (ChatMessage openAIMessage : openAIMessages) {
            messages.add(fromOpenAIMessage(openAIMessage));
        }
        return messages;
    }
    
    /**
     * Convert a list of our Message to theokanning ChatMessage format
     */
    public static List<ChatMessage> toOpenAIMessages(List<Message> messages) {
        List<ChatMessage> openAIMessages = new ArrayList<>(messages.size());
        for (Message message : messages) {
            openAIMessages.add(toOpenAIMessage(message));
        }
        return openAIMessages;
    }
    
    /**
     * Create system message in our format
     */
    public static Message createSystemMessage(String content) {
        return new Message(ChatMessageRole.SYSTEM.value(), content);
    }
    
    /**
     * Create user message in our format
     */
    public static Message createUserMessage(String content) {
        return new Message(ChatMessageRole.USER.value(), content);
    }
    
    /**
     * Create assistant message in our format
     */
    public static Message createAssistantMessage(String content) {
        return new Message(ChatMessageRole.ASSISTANT.value(), content);
    }
}