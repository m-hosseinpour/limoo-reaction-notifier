package ir.mahdihmb.limoo_bot;

import com.fasterxml.jackson.databind.JsonNode;
import ir.limoo.driver.LimooDriver;
import ir.limoo.driver.entity.Conversation;
import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.exception.LimooException;
import ir.limoo.driver.util.JacksonUtils;
import ir.mahdihmb.limoo_bot.core.ConfigService;
import ir.mahdihmb.limoo_bot.entity.Message;
import ir.mahdihmb.limoo_bot.entity.Reaction;
import ir.mahdihmb.limoo_bot.entity.StoreDTO;
import ir.mahdihmb.limoo_bot.entity.User;
import ir.mahdihmb.limoo_bot.event.MessageCreatedEventListener;
import ir.mahdihmb.limoo_bot.event.MessageEditedEventListener;
import ir.mahdihmb.limoo_bot.util.Requester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ir.mahdihmb.limoo_bot.util.Utils.*;

public class LimooBot {

    private static final Logger logger = LoggerFactory.getLogger(LimooBot.class);

    private static final String STORE_FILE = "reaction_notifier.data";

    private static final String START_COMMAND = "/start";
    private static final String STOP_COMMAND = "/stop";
    private static final int TEXT_PREVIEW_LEN = 500;

    private final String limooUrl;
    private final LimooDriver limooDriver;
    private final String botId;
    private final String storePath;
    private final String adminUserId;

    private StoreDTO store = new StoreDTO();

    public LimooBot(String limooUrl, String botUsername, String botPassword) throws LimooException, IOException {
        this.limooUrl = limooUrl;
        limooDriver = new LimooDriver(limooUrl, botUsername, botPassword);
        botId = limooDriver.getBot().getId();
        storePath = ConfigService.get("store.path");
        adminUserId = ConfigService.get("admin.userId");
        loadStoreData();
    }

    public void run() {
        limooDriver.addEventListener((MessageCreatedEventListener) this::onMessageCreated);
        limooDriver.addEventListener((MessageEditedEventListener) this::onMessageEdited);
    }

    private void onMessageCreated(Message message, Conversation conversation) {
        try {
            if (message.getUserId().equals(botId))
                return;

            doAutoReactions(message);

            if (ConversationType.DIRECT.equals(conversation.getConversationType())) {
                handleDirectMessage(message, conversation);
            } else {
                fixMessageReactions(message);
                store.msgToReactions.put(message.getId(), message.getReactions());
                saveStoreData();
            }
        } catch (Throwable e) {
            logger.error("", e);
        } finally {
            String threadRootId = message.getThreadRootId();
            if (isEmpty(threadRootId)) {
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

    private void handleDirectMessage(Message message, Conversation conversation) throws LimooException {
        String userId = message.getUserId();
        if (userId.equals(adminUserId)) {
            if ("/report".equals(message.getText())) {
                List<User> users = Requester.getUsersByIds(message.getWorkspace(), store.activeUsers);
                String usersDisplayNameText = users.stream().map(User::getDisplayName).collect(Collectors.joining("\n- ", "- ", "\n"));
                conversation.send("Active users:\n" + usersDisplayNameText + "Cached items: " + store.msgToReactions.size());
                return;
            }
        }

        if (message.getText().startsWith(START_COMMAND) && !store.activeUsers.contains(userId)) {
            store.activeUsers.add(userId);
            try {
                saveStoreData();
                Requester.reactMessage(message, LIKE_REACTION);
            } catch (IOException e) {
                Requester.reactMessage(message, WARNING_REACTION);
            }
        } else if (message.getText().startsWith(STOP_COMMAND) && store.activeUsers.contains(userId)) {
            store.activeUsers.remove(userId);
            try {
                saveStoreData();
                Requester.reactMessage(message, LIKE_REACTION);
            } catch (IOException e) {
                Requester.reactMessage(message, WARNING_REACTION);
            }
        } else {
            String msgLastPart;
            if (store.activeUsers.contains(userId)) {
                msgLastPart = String.format("You are currently a member of bot. To stop: [%1$s](%1$s)", STOP_COMMAND);
            } else {
                msgLastPart = String.format("To start: [%1$s](%1$s)", START_COMMAND);
            }
            conversation.send(
                    "This bot notifies you of reactions to your messages (in groups where bot is added).\n" +
                    "***\n" +
                    msgLastPart
            );
        }
    }

    private void onMessageEdited(Message message, Conversation conversation) {
        try {
            if (ConversationType.DIRECT.equals(conversation.getConversationType()))
                return;

            List<Reaction> preReactions = store.msgToReactions.get(message.getId());

            fixMessageReactions(message);
            store.msgToReactions.put(message.getId(), message.getReactions());
            saveStoreData();

            String ownerUserId = message.getUserId();
            if (!store.activeUsers.contains(ownerUserId))
                return;

            List<Reaction> newReactions = new ArrayList<>(message.getReactions());
            if (preReactions != null)
                newReactions.removeAll(preReactions);

            Set<String> userIds = newReactions.stream().map(Reaction::getUserId).collect(Collectors.toSet());
            List<User> users = Requester.getUsersByIds(message.getWorkspace(), userIds);
            Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));

            for (Reaction newReaction : newReactions) {
                User user = userMap.get(newReaction.getUserId());
                if (user != null && user.isBot() || newReaction.getUserId().equals(ownerUserId))
                    continue;

                String userDisplayName = user != null ? user.getDisplayName() : "Someone";

                String textPreview = message.getText().replaceAll("`", "");
                if (textPreview.length() > TEXT_PREVIEW_LEN) {
                    textPreview = textPreview.substring(0, TEXT_PREVIEW_LEN) + "...";
                }

                JsonNode directNode = Requester.getOrCreateDirect(message.getWorkspace(), botId, ownerUserId);
                Conversation direct = new Conversation(message.getWorkspace());
                JacksonUtils.deserializeIntoObject(directNode, direct);

                direct.send(userDisplayName + ": " + newReaction.getEmojiName() + "\n" +
                        "```\n" +
                        textPreview + "\n" +
                        "```\n" +
                        generateDirectLink(message, limooUrl));
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

    private void saveStoreData() throws IOException {
        saveDataFile(storePath, STORE_FILE, store);
    }

    private void loadStoreData() throws IOException {
        JsonNode storeNode = loadDataFile(storePath, STORE_FILE);
        if (storeNode != null) {
            store = JacksonUtils.deserializeObject(storeNode, StoreDTO.class);
            store.initNullProps();
        }
    }

    private void doAutoReactions(Message message) {
        try {
            String trimmedText = message.getText().trim();
            if (YEKKEKHANI_MENTION.equals(trimmedText)) {
                Requester.reactMessage(message, TROPHY_REACTION);
                return;
            }

            if (HOSSEINPOUR_MENTION.equals(trimmedText)) {
                Requester.reactMessage(message, GHOST_REACTION);
                return;
            }

            Set<String> mentionSet = new HashSet<>(message.getMentions());
            if (mentionSet.size() == 1 && TAVASSOLIAN_UID.equals(mentionSet.iterator().next())) {
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                boolean isNotWorking = hour < 8 || hour >= 16 || Calendar.FRIDAY == calendar.get(Calendar.DAY_OF_WEEK);
                Requester.reactMessage(message, isNotWorking ? SLEEPING_REACTION : HUGGING_FACE_REACTION);
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

    private void fixMessageReactions(Message message) {
        if (message.getReactions() == null) {
            message.setReactions(Collections.emptyList());
        }
        for (Reaction reaction : message.getReactions()) {
            String emojiName = reaction.getEmojiName();
            if (!emojiName.startsWith(EMOJI_WRAPPER))
                emojiName = EMOJI_WRAPPER + emojiName;
            if (!emojiName.endsWith(EMOJI_WRAPPER))
                emojiName = emojiName + EMOJI_WRAPPER;
            reaction.setEmojiName(emojiName);
        }
    }

}
