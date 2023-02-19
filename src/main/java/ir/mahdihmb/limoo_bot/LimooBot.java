package ir.mahdihmb.limoo_bot;

import com.fasterxml.jackson.databind.JsonNode;
import ir.limoo.driver.LimooDriver;
import ir.limoo.driver.entity.Conversation;
import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.entity.User;
import ir.limoo.driver.exception.LimooException;
import ir.limoo.driver.util.JacksonUtils;
import ir.mahdihmb.limoo_bot.core.ConfigService;
import ir.mahdihmb.limoo_bot.core.MessageService;
import ir.mahdihmb.limoo_bot.entity.*;
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
    public static final String REPORT_COMMAND = "/report";

    private static final int TEXT_PREVIEW_LEN = 300;

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
            if (handleAdminCommand(message, conversation)) {
                return;
            }
        }

        if (message.getText().startsWith(START_COMMAND) && !store.activeUsers.contains(userId)) {
            startCommand(message, userId);
        } else if (message.getText().startsWith(STOP_COMMAND) && store.activeUsers.contains(userId)) {
            stopCommand(message, userId);
        } else {
            sendHelp(conversation, userId);
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

            for (Reaction reaction : newReactions) {
                User user = userMap.get(reaction.getUserId());
                if (user != null && user.isBot() || reaction.getUserId().equals(ownerUserId))
                    continue;

                sendReactionNotif(message, conversation, user, reaction, ownerUserId);
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

    private boolean handleAdminCommand(Message message, Conversation conversation) throws LimooException {
        if (REPORT_COMMAND.equals(message.getText())) {
            List<User> users = Requester.getUsersByIds(message.getWorkspace(), store.activeUsers);
            String usersDisplayNameText = users.stream().map(User::getDisplayName).collect(Collectors.joining("\n- ", "- ", "\n"));
            conversation.send("Cached items: " + store.msgToReactions.size() + "\nActive users:\n" + usersDisplayNameText);
            return true;
        }
        return false;
    }

    private void startCommand(Message message, String userId) throws LimooException {
        store.activeUsers.add(userId);
        try {
            saveStoreData();
            Requester.reactMessage(message, LIKE_REACTION);
        } catch (IOException e) {
            Requester.reactMessage(message, WARNING_REACTION);
        }
    }

    private void stopCommand(Message message, String userId) throws LimooException {
        store.activeUsers.remove(userId);
        try {
            saveStoreData();
            Requester.reactMessage(message, LIKE_REACTION);
        } catch (IOException e) {
            Requester.reactMessage(message, WARNING_REACTION);
        }
    }

    private void sendHelp(Conversation conversation, String userId) throws LimooException {
        String msgLastPart;
        if (store.activeUsers.contains(userId)) {
            msgLastPart = String.format(MessageService.get("help.activeUser.stopNotif"), STOP_COMMAND);
        } else {
            msgLastPart = String.format(MessageService.get("help.notActiveUser.startNotif"), START_COMMAND);
        }
        conversation.send(MessageService.get("help.description") + "\n***\n" + msgLastPart);
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

    private void sendReactionNotif(Message message, Conversation conversation,
                                   User user, Reaction reaction, String ownerUserId)
            throws LimooException, IOException {
        String userDisplayName = user != null ? user.getDisplayName() : MessageService.get("reactionNotif.someone");

        String msgPreview = message.getText()
                .replaceAll("`", "")
                .replaceAll("#", "");
        if (msgPreview.length() > TEXT_PREVIEW_LEN) {
            msgPreview = msgPreview.substring(0, TEXT_PREVIEW_LEN) + "...";
        }

        String text = bold(userDisplayName) + ": " + reaction.getEmojiName() + "\n" +
                codeBlock(msgPreview) + "\n" +
                SPEECH_BALLOON_EMOJI + " " + italic(MessageService.get("reactionNotif.group") + ": ") + conversation.getDisplayName() + "\n" +
                LINK_EMOJI + " " + italic(MessageService.get("reactionNotif.directLink") + ": ") + generateDirectLink(message, limooUrl);

        JsonNode directNode = Requester.getOrCreateDirect(message.getWorkspace(), botId, ownerUserId);
        Conversation direct = new Conversation(message.getWorkspace());
        JacksonUtils.deserializeIntoObject(directNode, direct);
        direct.send(text);
    }

}
