package ir.mahdihmb.limoo_bot.event;

import com.fasterxml.jackson.databind.JsonNode;
import ir.limoo.driver.entity.Conversation;
import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.event.LimooEvent;
import ir.limoo.driver.event.LimooEventListener;
import ir.limoo.driver.util.JacksonUtils;
import ir.mahdihmb.limoo_bot.entity.Message;

import java.io.IOException;

@FunctionalInterface
public interface MessageCreatedEventListener extends LimooEventListener {

    @Override
    default boolean canHandle(LimooEvent event) {
        return "message_created".equals(event.getType()) && event.getEventData().has("message");
    }

    @Override
    default void handleEvent(LimooEvent event) throws IOException {
        JsonNode dataNode = event.getEventData();
        JsonNode messageNode = dataNode.get("message");
        Message message = new Message(event.getWorkspace());
        JacksonUtils.deserializeIntoObject(messageNode, message);
        ConversationType type = ConversationType.valueOfLabel(dataNode.get("conversation_type").asText());
        Conversation conversation = new Conversation(message.getConversationId(), type, event.getWorkspace());
        onMessageCreated(message, conversation);
        conversation.onNewMessage();
    }

    void onMessageCreated(Message message, Conversation conversation);
}
