package ir.mahdihmb.limoo_bot.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class StoreDTO {

    @JsonProperty
    public Set<String> activeUsers = new HashSet<>();

    @JsonProperty
    public Map<String, List<Reaction>> msgToReactions = new HashMap<>();

    public Set<String> getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Set<String> activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Map<String, List<Reaction>> getMsgToReactions() {
        return msgToReactions;
    }

    public void setMsgToReactions(Map<String, List<Reaction>> msgToReactions) {
        this.msgToReactions = msgToReactions;
    }

    public void initNullProps() {
        if (activeUsers == null)
            activeUsers = new HashSet<>();
        if (msgToReactions == null)
            msgToReactions = new HashMap<>();
    }
}
