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

    private static final String REACTIONS_STORE_FILE = "reactions.data";
    private static final String USERS_STORE_FILE = "users.data";

    private static final String START_COMMAND = "/start";
    private static final String STOP_COMMAND = "/stop";
    private static final int TEXT_PREVIEW_LEN = 500;

    private final String limooUrl;
    private final LimooDriver limooDriver;
    private final String botId;
    private final String storePath;
    private final String adminUserId;

    private Map<String, List<Reaction>> msgToReactions;
    private Set<String> activeUsers;

    public LimooBot(String limooUrl, String botUsername, String botPassword) throws LimooException, IOException {
        this.limooUrl = limooUrl;
        limooDriver = new LimooDriver(limooUrl, botUsername, botPassword);
        botId = limooDriver.getBot().getId();
        storePath = ConfigService.get("store.path");
        adminUserId = ConfigService.get("admin.userId");
        loadOrCreateStoredData();
    }

    public void run() {
        limooDriver.addEventListener((MessageCreatedEventListener) this::onMessageCreated);
        limooDriver.addEventListener((MessageEditedEventListener) this::onMessageEdited);
    }

    private void onMessageCreated(Message message, Conversation conversation) {
        try {
            if (message.getUserId().equals(botId))
                return;

            if (ConversationType.DIRECT.equals(conversation.getConversationType())) {
                handleDirectMessage(message, conversation);
            } else {
                fixMessageReactions(message);
                msgToReactions.put(message.getId(), message.getReactions());
                saveMsgToReactions();
            }

            doAutoReactions(message);
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
                List<User> users = Requester.getUsersByIds(message.getWorkspace(), activeUsers);
                String usersDisplayNameText = users.stream().map(User::getDisplayName).collect(Collectors.joining("\n- ", "- ", "\n"));
                conversation.send("Active users:\n" + usersDisplayNameText + "Cached items: " + msgToReactions.size());
                return;
            }
        }

        if (message.getText().startsWith(START_COMMAND) && !activeUsers.contains(userId)) {
            activeUsers.add(userId);
            saveActiveUsers();
            Requester.likeMessage(message);
        } else if (message.getText().startsWith(STOP_COMMAND) && activeUsers.contains(userId)) {
            activeUsers.remove(userId);
            saveActiveUsers();
            Requester.likeMessage(message);
        } else {
            String msgLastPart;
            if (activeUsers.contains(userId)) {
                msgLastPart = String.format("You are currently a member of bot. To stop: [%1$s](%1$s)", STOP_COMMAND);
            } else {
                msgLastPart = String.format("To start: [%1$s](%1$s)", START_COMMAND);
            }
            conversation.send(
                    "This bot notifies you of reactions to your messages (in groups where bot is added).\n" +
                    "Does not work on messages that created before the bot was added (unless they are edited).\n" +
                    "***\n" +
                    msgLastPart
            );
        }
    }

    private void onMessageEdited(Message message, Conversation conversation) {
        try {
            if (ConversationType.DIRECT.equals(conversation.getConversationType()))
                return;

            List<Reaction> preReactions = msgToReactions.get(message.getId());

            fixMessageReactions(message);
            msgToReactions.put(message.getId(), message.getReactions());
            saveMsgToReactions();

            String ownerUserId = message.getUserId();
            if (preReactions == null || !activeUsers.contains(ownerUserId))
                return;

            List<Reaction> newReactions = new ArrayList<>(message.getReactions());
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

    private void saveActiveUsers() {
        saveDataFileAsync(storePath, USERS_STORE_FILE, activeUsers);
    }

    private void saveMsgToReactions() {
        saveDataFileAsync(storePath, REACTIONS_STORE_FILE, msgToReactions);
    }

    private void loadOrCreateStoredData() throws IOException {
        msgToReactions = new HashMap<>();
        loadOrCreateDataFile(storePath, REACTIONS_STORE_FILE, msgToReactions);

        activeUsers = new HashSet<>();
        loadOrCreateDataFile(storePath, USERS_STORE_FILE, activeUsers);
    }

    private void doAutoReactions(Message message) throws LimooException {
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
    }

}
