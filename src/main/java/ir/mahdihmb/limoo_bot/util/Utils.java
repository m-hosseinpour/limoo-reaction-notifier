package ir.mahdihmb.limoo_bot.util;

import com.fasterxml.jackson.databind.JsonNode;
import ir.limoo.driver.util.JacksonUtils;
import ir.mahdihmb.limoo_bot.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static final String YEKKEKHANI_MENTION = "@59a404e1-da30-45ce-bf7f-8099e1ef9273";
    public static final String HOSSEINPOUR_MENTION = "@e8f42839-6b9e-4e72-bd0f-d70804c0b50e";

    public static final String TAVASSOLIAN_UID = "5b64a372-d3c6-4933-9245-9464af0d3800";

    public static final String EMOJI_WRAPPER = ":";
    public static final String LIKE_REACTION = "+1";
    public static final String WARNING_REACTION = "warning";
    public static final String TROPHY_REACTION = "trophy";
    public static final String GHOST_REACTION = "ghost";
    public static final String SLEEPING_REACTION = "sleeping";
    public static final String HUGGING_FACE_REACTION = "hugging_face";
    public static final String LINK_EMOJI = ":link:";
    public static final String MARKDOWN_LINK_TEMPLATE = "[%s](%s)";
    public static final String DIRECT_LINK_URI_TEMPLATE = "workspace/%s/conversation/%s/message/%s";
    public static final String THREAD_DIRECT_LINK_URI_TEMPLATE = "workspace/%s/conversation/%s/thread/%s/message/%s";

    public static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    public static boolean isNotEmpty(String text) {
        return text != null && !text.isEmpty();
    }

    public static String concatUris(String first, String second) {
        return first + (first.endsWith("/") || second.startsWith("/") ? "" : "/") + second;
    }

    public static String generateDirectLink(Message msg, String limooUrl) {
        if (isNotEmpty(msg.getWorkspaceKey()) && isNotEmpty(msg.getConversationId()) && isNotEmpty(msg.getId())) {
            String directLinkUri;
            if (isNotEmpty(msg.getThreadRootId())) {
                directLinkUri = String.format(THREAD_DIRECT_LINK_URI_TEMPLATE,
                        msg.getWorkspaceKey(), msg.getConversationId(), msg.getThreadRootId(), msg.getId());
            } else {
                directLinkUri = String.format(DIRECT_LINK_URI_TEMPLATE,
                        msg.getWorkspaceKey(), msg.getConversationId(), msg.getId());
            }
            return String.format(MARKDOWN_LINK_TEMPLATE, LINK_EMOJI, concatUris(limooUrl, directLinkUri));
        }
        return null;
    }

    public static JsonNode loadDataFile(String storePath, String fileName) throws IOException {
        File usersFile = new File(concatUris(storePath, fileName));
        if (!usersFile.exists())
            return null;

        StringBuilder fileContent = new StringBuilder();
        try (FileReader fileReader = new FileReader(usersFile);
             BufferedReader reader = new BufferedReader(fileReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
        }
        return JacksonUtils.convertStringToJsonNode(fileContent.toString());
    }

    public static void saveDataFile(String storePath, String fileName, Object obj) throws IOException {
        try (FileWriter fileWriter = new FileWriter(concatUris(storePath, fileName));
             BufferedWriter writer = new BufferedWriter(fileWriter)) {
            writer.write(JacksonUtils.serializeObjectAsString(obj));
        } catch (IOException e) {
            logger.error("Can't store " + fileName + " cache", e);
            throw e;
        }
    }

}
