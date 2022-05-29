package ir.mahdihmb.limoo_bot;

import com.fasterxml.jackson.databind.JsonNode;
import ir.limoo.driver.LimooDriver;
import ir.limoo.driver.entity.Conversation;
import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.entity.User;
import ir.limoo.driver.entity.Workspace;
import ir.limoo.driver.event.LimooEvent;
import ir.limoo.driver.event.LimooEventListener;
import ir.limoo.driver.exception.LimooException;
import ir.limoo.driver.util.JacksonUtils;
import ir.mahdihmb.limoo_bot.entity.MessageWithReactions;
import ir.mahdihmb.limoo_bot.entity.Reaction;
import ir.mahdihmb.limoo_bot.util.GeneralUtils;
import ir.mahdihmb.limoo_bot.util.Requester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static ir.mahdihmb.limoo_bot.util.GeneralUtils.empty;

public class LimooBot {

    private static final Logger logger = LoggerFactory.getLogger(LimooBot.class);

    private final String limooUrl;
    private final LimooDriver limooDriver;
    private final Map<String, List<Reaction>> msgToReactions = new HashMap<>();

    public LimooBot(String limooUrl, String botUsername, String botPassword) throws LimooException {
        this.limooUrl = limooUrl;
        limooDriver = new LimooDriver(limooUrl, botUsername, botPassword);
    }

    public void run() {
        limooDriver.addEventListener(new LimooEventListener() {
            @Override
            public boolean canHandle(LimooEvent event) {
                return "message_created".equals(event.getType()) && event.getEventData().has("message");
            }

            @Override
            public void handleEvent(LimooEvent event) throws IOException {
                JsonNode dataNode = event.getEventData();
                JsonNode messageNode = dataNode.get("message");
                MessageWithReactions message = new MessageWithReactions(event.getWorkspace());
                JacksonUtils.deserializeIntoObject(messageNode, message);
                ConversationType type = ConversationType.valueOfLabel(dataNode.get("conversation_type").asText());
                Conversation conversation = new Conversation(message.getConversationId(), type, event.getWorkspace());
                onNewMessage(message, conversation);
                conversation.onNewMessage();
            }
        });

        limooDriver.addEventListener(new LimooEventListener() {
            @Override
            public boolean canHandle(LimooEvent event) {
                return "message_edited".equals(event.getType()) && event.getEventData().has("message");
            }

            @Override
            public void handleEvent(LimooEvent event) throws IOException {
                JsonNode dataNode = event.getEventData();
                JsonNode messageNode = dataNode.get("message");
                MessageWithReactions message = new MessageWithReactions(event.getWorkspace());
                JacksonUtils.deserializeIntoObject(messageNode, message);
                onEditMessage(message);
            }
        });
    }

    public void onNewMessage(MessageWithReactions message, Conversation conversation) {
        String threadRootId = message.getThreadRootId();
        try {
            msgToReactions.put(message.getId(), Optional.ofNullable(message.getReactions()).orElse(new ArrayList<>()));
            if (message.getThreadRootId() == null && !limooDriver.getBot().getId().equals(message.getUserId())) {
                Requester.followThread(message.getWorkspace(), threadRootId);
            }
        } catch (Throwable e) {
            logger.error("", e);
        } finally {
            if (empty(threadRootId)) {
                conversation.viewLog();
            } else {
                try {
                    Requester.viewLogThread(message.getWorkspace(), threadRootId);
                } catch (LimooException e) {
                    logger.info("Can't send viewLog for a thread: ", e);
                }
            }
        }
    }

    public void onEditMessage(MessageWithReactions message) {
        try {
            String id = message.getId();
            List<Reaction> messageReactions = Optional.ofNullable(message.getReactions()).orElse(new ArrayList<>());
            if (!msgToReactions.containsKey(id)) {
                msgToReactions.put(id, messageReactions);
            } else {
                if (Arrays.deepEquals(msgToReactions.get(id).toArray(new Reaction[0]), messageReactions.toArray(new Reaction[0])))
                    return;
                List<Reaction> addedReactions = new ArrayList<>(messageReactions);
                addedReactions.removeAll(msgToReactions.get(id));
                if (!addedReactions.isEmpty()) {
                    for (Reaction addedReaction : addedReactions) {
                        if (addedReaction.getUserId().equals(message.getUserId()))
                            continue;
                        Workspace workspace = message.getWorkspace();
                        JsonNode conversationNode = Requester.createDirect(workspace, limooDriver.getBot().getId(), message.getUserId());
                        Conversation direct = new Conversation(workspace);
                        JacksonUtils.deserializeIntoObject(conversationNode, direct);

                        User user = Requester.getUser(workspace, addedReaction.getUserId());
                        String userDisplayName = "Someone";
                        if (user != null)
                            userDisplayName = user.getDisplayName();

                        int TEXT_PREVIEW_LEN = 200;
                        String textPreview = message.getText();
                        if (message.getText().length() > TEXT_PREVIEW_LEN)
                            textPreview = message.getText().substring(0, TEXT_PREVIEW_LEN);
                        textPreview = textPreview.replaceAll("[\r\n]", " ");
                        textPreview = textPreview.replaceAll("`", "");
                        if (message.getText().length() > textPreview.length())
                            textPreview += "...";

                        direct.send(userDisplayName + ": :" + addedReaction.getEmojiName() + ":\n" +
                                "```\n" +
                                textPreview + "\n" +
                                "```\n" +
                                GeneralUtils.generateDirectLink(message, limooUrl));
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

}
