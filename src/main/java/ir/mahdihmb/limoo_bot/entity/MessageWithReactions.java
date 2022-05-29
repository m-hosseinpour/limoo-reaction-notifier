package ir.mahdihmb.limoo_bot.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import ir.limoo.driver.entity.Workspace;

import java.util.List;

public class MessageWithReactions extends ir.limoo.driver.entity.Message {

    @JsonProperty("reactions")
    private List<Reaction> reactions;

    public MessageWithReactions() {
    }

    public MessageWithReactions(Workspace workspace) {
        super(workspace);
    }

    public List<Reaction> getReactions() {
        return reactions;
    }

    public void setReactions(List<Reaction> reactions) {
        this.reactions = reactions;
    }
}
