package ir.mahdihmb.limoo_bot.core;

public class CoreManager {

    public static void initApp() {
        try {
            ConfigService.init();
            MessageService.init();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

}
