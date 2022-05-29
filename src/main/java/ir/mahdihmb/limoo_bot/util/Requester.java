package ir.mahdihmb.limoo_bot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ir.limoo.driver.entity.User;
import ir.limoo.driver.entity.Workspace;
import ir.limoo.driver.exception.LimooException;
import ir.limoo.driver.util.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Requester {

    private static final Logger logger = LoggerFactory.getLogger(Requester.class);

    private static final String GET_USER_URI_TEMPLATE = "user/items/%s";
    private static final String CONVERSATIONS_ROOT_URI_TEMPLATE = "workspace/items/%s/conversation/items";
    private static final String THREAD_ROOT_URI_TEMPLATE = "workspace/items/%s/thread/items/%s";
    private static final String THREAD_VIEW_LOG_URI_TEMPLATE = THREAD_ROOT_URI_TEMPLATE + "/view_log";
    private static final String THREAD_FOLLOW_URI_TEMPLATE = THREAD_ROOT_URI_TEMPLATE + "/follow";

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
        body.put("is_viewed", true);
        workspace.getRequester().executeApiPost(uri, body, workspace.getWorker());
    }

    public static void followThread(Workspace workspace, String threadRootId) throws LimooException {
        String uri = String.format(THREAD_FOLLOW_URI_TEMPLATE, workspace.getId(), threadRootId);
        workspace.getRequester().executeApiPost(uri, JacksonUtils.createEmptyObjectNode(), workspace.getWorker());
    }

    public static JsonNode createDirect(Workspace workspace, String botId, String userId) throws LimooException {
        String uri = String.format(CONVERSATIONS_ROOT_URI_TEMPLATE, workspace.getId());
        ObjectNode body = JacksonUtils.createEmptyObjectNode();
        body.put("type", "direct");
        body.putArray("user_ids").add(botId).add(userId);
        return workspace.getRequester().executeApiPost(uri, body, workspace.getWorker());
    }
}
