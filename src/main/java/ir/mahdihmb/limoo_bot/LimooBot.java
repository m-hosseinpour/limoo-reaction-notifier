package ir.mahdihmb.limoo_bot;

import com.fasterxml.jackson.databind.JsonNode;
import ir.limoo.driver.LimooDriver;
import ir.limoo.driver.entity.Conversation;
import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.entity.User;
import ir.limoo.driver.entity.Workspace;
import ir.limoo.driver.exception.LimooException;
import ir.limoo.driver.util.JacksonUtils;
import ir.mahdihmb.limoo_bot.core.ConfigService;
import ir.mahdihmb.limoo_bot.entity.MessageWithReactions;
import ir.mahdihmb.limoo_bot.entity.Reaction;
import ir.mahdihmb.limoo_bot.event.MessageCreatedEventListener;
import ir.mahdihmb.limoo_bot.event.MessageEditedEventListener;
import ir.mahdihmb.limoo_bot.util.Requester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static ir.mahdihmb.limoo_bot.util.GeneralUtils.*;

public class LimooBot {

    private static final Logger logger = LoggerFactory.getLogger(LimooBot.class);

    private static final String REACTIONS_STORE_FILE = "reactions.data";
    private static final String USERS_STORE_FILE = "users.data";

    private static final String PEYGIR_BOT_ID = "6a52edf5-31b4-44be-88ad-b221974596de";
    private static final String SHAEDAEI_MENTION = "@29481cc3-1ad7-4bd5-8254-28d4e00d6970";

    private static final String START_COMMAND = "/start";
    private static final String STOP_COMMAND = "/stop";
    private static final String EMOJI_WRAPPER = ":";
    private static final int TEXT_PREVIEW_LEN = 500;

    private final String limooUrl;
    private final LimooDriver limooDriver;
    private final String storePath;
    private final String adminUserId;

    private Map<String, List<Reaction>> msgToReactions;
    private Set<String> activeUsers;

    public LimooBot(String limooUrl, String botUsername, String botPassword) throws LimooException, IOException {
        this.limooUrl = limooUrl;
        limooDriver = new LimooDriver(limooUrl, botUsername, botPassword);
        storePath = ConfigService.get("store.path");
        adminUserId = ConfigService.get("admin.userId");
        loadOrCreateStoredData();
    }

    public void run() {
        limooDriver.addEventListener((MessageCreatedEventListener) this::onMessageCreated);
        limooDriver.addEventListener((MessageEditedEventListener) this::onMessageEdited);
    }

    private void onMessageCreated(MessageWithReactions message, Conversation conversation) {
        String threadRootId = message.getThreadRootId();
        try {
            if (message.getUserId().equals(limooDriver.getBot().getId()))
                return;
            if (ConversationType.DIRECT.equals(conversation.getConversationType())) {
                handleDirectMessage(message, conversation);
            } else {
                saveMessageReactions(message);
            }

            if (SHAEDAEI_MENTION.equals(message.getText().trim())) {
                Requester.poopMessage(message);
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

    private void handleDirectMessage(MessageWithReactions message, Conversation conversation) throws LimooException {
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
            saveDataFileAsync(storePath, USERS_STORE_FILE, activeUsers);
            Requester.likeMessage(message);
        } else if (message.getText().startsWith(STOP_COMMAND) && activeUsers.contains(userId)) {
            activeUsers.remove(userId);
            saveDataFileAsync(storePath, USERS_STORE_FILE, activeUsers);
            Requester.likeMessage(message);
        } else {
            String msgLastPart = String.format("To start: [%1$s](%1$s)", START_COMMAND);
            if (activeUsers.contains(userId)) {
                msgLastPart = String.format("You are currently a member of bot. To stop: [%1$s](%1$s)", STOP_COMMAND);
            }
            conversation.send(
                    "This bot notifies you of reactions to your messages (in groups where bot is added).\n" +
                    "Does not work on messages that created before the bot was added (unless they are edited).\n" +
                    "***\n" +
                    msgLastPart
            );
        }
    }

    private void onMessageEdited(MessageWithReactions message, Conversation conversation) {
        try {
            if (ConversationType.DIRECT.equals(conversation.getConversationType()))
                return;

            String id = message.getId();
            List<Reaction> preReactions = new ArrayList<>(Optional.ofNullable(msgToReactions.get(id)).orElse(new ArrayList<>()));

            saveMessageReactions(message);

            String userId = message.getUserId();
            if (!msgToReactions.containsKey(id) || !activeUsers.contains(userId))
                return;

            List<Reaction> reactions = Optional.ofNullable(message.getReactions()).orElse(new ArrayList<>());
            fixReactionEmojis(reactions);

            List<Reaction> addedReactions = new ArrayList<>(reactions);
            addedReactions.removeAll(preReactions);
            if (!addedReactions.isEmpty()) {
                for (Reaction addedReaction : addedReactions) {
                    if (addedReaction.getUserId().equals(userId))
                        continue;

                    Workspace workspace = message.getWorkspace();
                    JsonNode directNode = Requester.getOrCreateDirect(workspace, limooDriver.getBot().getId(), userId);
                    Conversation direct = new Conversation(workspace);
                    JacksonUtils.deserializeIntoObject(directNode, direct);

                    User user = Requester.getUser(workspace, addedReaction.getUserId());
                    if (user != null && user.getBot())
                        continue;

                    String userDisplayName = user != null ? user.getDisplayName() : "Someone";

                    String textPreview = message.getText();
                    if (message.getText().length() > TEXT_PREVIEW_LEN)
                        textPreview = message.getText().substring(0, TEXT_PREVIEW_LEN);
                    textPreview = textPreview.replaceAll("`", "");
                    if (message.getText().length() > textPreview.length())
                        textPreview += "...";

                    direct.send(userDisplayName + ": " + getFixedEmoji(addedReaction.getEmojiName()) + "\n" +
                            "```\n" +
                            textPreview + "\n" +
                            "```\n" +
                            generateDirectLink(message, limooUrl));
                }
            }
        } catch (Throwable e) {
            logger.error("", e);
        }
    }

    private void loadOrCreateStoredData() throws IOException {
        msgToReactions = new HashMap<>();
        loadOrCreateDataFile(storePath, REACTIONS_STORE_FILE, msgToReactions);

        activeUsers = new HashSet<>();
        loadOrCreateDataFile(storePath, USERS_STORE_FILE, activeUsers);
    }

    private void saveMessageReactions(MessageWithReactions message) {
        List<Reaction> reactions = Optional.ofNullable(message.getReactions()).orElse(new ArrayList<>());
        fixReactionEmojis(reactions);
        msgToReactions.put(message.getId(), reactions);
        saveDataFileAsync(storePath, REACTIONS_STORE_FILE, msgToReactions);
    }

    private void fixReactionEmojis(List<Reaction> reactions) {
        for (Reaction reaction : reactions) {
            reaction.setEmojiName(getFixedEmoji(reaction.getEmojiName()));
        }
    }

    private String getFixedEmoji(String emoji) {
        return emoji.startsWith(EMOJI_WRAPPER) ? emoji : (EMOJI_WRAPPER + emoji + EMOJI_WRAPPER);
    }

}
