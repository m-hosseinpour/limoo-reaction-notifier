package ir.mahdihmb.limoo_bot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.entity.User;
import ir.limoo.driver.entity.Workspace;
import ir.limoo.driver.exception.LimooException;
import ir.limoo.driver.util.JacksonUtils;
import ir.limoo.driver.util.MessageUtils;
import ir.mahdihmb.limoo_bot.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class Requester {

    private static final Logger logger = LoggerFactory.getLogger(Requester.class);

    private static final String GET_USER_URI_TEMPLATE = "user/items/%s";
    private static final String CONVERSATIONS_ROOT_URI_TEMPLATE = "workspace/items/%s/conversation/items";
    private static final String THREAD_ROOT_URI_TEMPLATE = "workspace/items/%s/thread/items/%s";
    private static final String THREAD_VIEW_LOG_URI_TEMPLATE = THREAD_ROOT_URI_TEMPLATE + "/view_log";
    private static final String REACT_URI_TEMPLATE = MessageUtils.MESSAGES_ROOT_URI_TEMPLATE + "/%s/reaction/items/%s";
    private static final String GET_USERS_BY_IDS_URI_TEMPLATE = "user/ids";

    public static User getUser(Workspace workspace, String userId) throws LimooException {
        String uri = String.format(GET_USER_URI_TEMPLATE, userId);
        JsonNode userNode = workspace.getRequester().executeApiGet(uri, workspace.getWorker());
        try {
            return JacksonUtils.deserializeObject(userNode, User.class);
        } catch (IOException e) {
            logger.error("", e);
            return null;
        }
    }

    public static void viewLogThread(Workspace workspace, String threadRootId) throws LimooException {
        String uri = String.format(THREAD_VIEW_LOG_URI_TEMPLATE, workspace.getId(), threadRootId);
        ObjectNode body = JacksonUtils.createEmptyObjectNode();
        body.put("viewed", true);
        workspace.getRequester().executeApiPost(uri, body, workspace.getWorker());
    }

    public static JsonNode getOrCreateDirect(Workspace workspace, String botId, String userId) throws LimooException {
        String uri = String.format(CONVERSATIONS_ROOT_URI_TEMPLATE, workspace.getId());
        ObjectNode body = JacksonUtils.createEmptyObjectNode();
        body.put("type", ConversationType.DIRECT.label);
        body.putArray("user_ids").add(botId).add(userId);
        return workspace.getRequester().executeApiPost(uri, body, workspace.getWorker());
    }

    public static void reactMessage(Message message, String reaction) throws LimooException {
        Workspace workspace = message.getWorkspace();
        String uri = String.format(REACT_URI_TEMPLATE, workspace.getId(), message.getConversationId(), message.getId(), reaction);
        workspace.getRequester().executeApiPost(uri, JacksonUtils.createEmptyObjectNode(), workspace.getWorker());
    }

    public static void likeMessage(Message message) throws LimooException {
        reactMessage(message, Utils.LIKE_REACTION);
    }

    public static List<User> getUsersByIds(Workspace workspace, Set<String> userIds) throws LimooException {
        ArrayNode userIdsNode = JacksonUtils.getObjectMapper().createArrayNode();
        for (String userId : userIds)
            userIdsNode.add(userId);
        JsonNode usersNode = workspace.getRequester().executeApiPost(GET_USERS_BY_IDS_URI_TEMPLATE, userIdsNode, workspace.getWorker());
        return JacksonUtils.deserializeObjectToList(usersNode, User.class);
    }
}
