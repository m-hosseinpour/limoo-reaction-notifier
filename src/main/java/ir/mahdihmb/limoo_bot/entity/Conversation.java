package ir.mahdihmb.limoo_bot.entity;

import ir.limoo.driver.entity.ConversationType;
import ir.limoo.driver.entity.Workspace;

public class Conversation extends ir.limoo.driver.entity.Conversation {

    private String displayName;

    public Conversation() {
    }

    public Conversation(Workspace workspace) {
        super(workspace);
    }

    public Conversation(String id, ConversationType conversationType, Workspace workspace) {
        super(id, conversationType, workspace);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
