package ir.mahdihmb.limoo_bot.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Reaction {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("emoji_name")
    private String emojiName;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmojiName() {
        return emojiName;
    }

    public void setEmojiName(String emojiName) {
        this.emojiName = emojiName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Reaction))
            return false;
        Reaction other = (Reaction) obj;
        return userId.equals(other.getUserId()) && emojiName.equals(other.getEmojiName());
    }
}
