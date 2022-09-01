package ir.mahdihmb.limoo_bot.util;

import ir.limoo.driver.entity.Message;
import ir.limoo.driver.util.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.CompletableFuture;

public class GeneralUtils {

    private static final Logger logger = LoggerFactory.getLogger(GeneralUtils.class);

    public static final String LIKE_REACTION = "+1";
    public static final String POOP_REACTION = "hankey";
    public static final String LINK_EMOJI = ":link:";
    public static final String MARKDOWN_LINK_TEMPLATE = "[%s](%s)";
    public static final String DIRECT_LINK_URI_TEMPLATE = "workspace/%s/conversation/%s/message/%s";
    public static final String THREAD_DIRECT_LINK_URI_TEMPLATE = "workspace/%s/conversation/%s/thread/%s/message/%s";

    public static boolean empty(String text) {
        return text == null || text.isEmpty();
    }

    public static boolean notEmpty(String text) {
        return text != null && !text.isEmpty();
    }

    public static String concatUris(String first, String second) {
        return first + (first.endsWith("/") || second.startsWith("/") ? "" : "/") + second;
    }

    public static String generateDirectLink(Message msg, String limooUrl) {
        if (notEmpty(msg.getWorkspaceKey()) && notEmpty(msg.getConversationId()) && notEmpty(msg.getId())) {
            String directLinkUri;
            if (notEmpty(msg.getThreadRootId())) {
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

    public static void loadOrCreateDataFile(String storePath, String fileName, Object obj) throws IOException {
        File usersFile = new File(concatUris(storePath, fileName));
        if (!usersFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(usersFile))) {
                writer.write(JacksonUtils.serializeObjectAsString(obj));
            }
        } else {
            StringBuilder fileContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fileContent.append(line).append("\n");
                }
            }
            JacksonUtils.deserializeIntoObject(JacksonUtils.convertStringToJsonNode(fileContent.toString()), obj);
        }
    }

    public static void saveDataFileAsync(String storePath, String fileName, Object obj) {
        CompletableFuture.runAsync(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(concatUris(storePath, fileName)))) {
                writer.write(JacksonUtils.serializeObjectAsString(obj));
            } catch (IOException e) {
                logger.error("Can't store msgToReactions cache", e);
            }
        });
    }
}
