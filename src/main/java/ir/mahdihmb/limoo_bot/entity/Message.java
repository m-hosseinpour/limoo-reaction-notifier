package ir.mahdihmb.limoo_bot.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import ir.limoo.driver.entity.Workspace;

import java.util.List;

public class Message extends ir.limoo.driver.entity.Message {

    @JsonProperty("reactions")
    private List<Reaction> reactions;

    @JsonProperty("props")
    private MessageProps props;

    public Message() {
        // empty constructor needed
    }

    public Message(Workspace workspace) {
        super(workspace);
    }

    public List<Reaction> getReactions() {
        return reactions;
    }

    public void setReactions(List<Reaction> reactions) {
        this.reactions = reactions;
    }

    public MessageProps getProps() {
        return props;
    }

    public void setProps(MessageProps props) {
        this.props = props;
    }

    public List<String> getMentions() {
        if (props == null)
            return null;
        return props.getMentions();
    }

    public static class MessageProps {

        @JsonProperty("mentions")
        private List<String> mentions;

        public List<String> getMentions() {
            return mentions;
        }

        public void setMentions(List<String> mentions) {
            this.mentions = mentions;
        }
    }
}
