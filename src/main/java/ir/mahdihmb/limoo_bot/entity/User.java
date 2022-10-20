package ir.mahdihmb.limoo_bot.entity;

public class User extends ir.limoo.driver.entity.User {

    public boolean isBot() {
        return getBot() != null && getBot();
    }
}
