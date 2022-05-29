package ir.mahdihmb.limoo_bot.util;

import ir.limoo.driver.entity.Message;

public class GeneralUtils {

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
}
