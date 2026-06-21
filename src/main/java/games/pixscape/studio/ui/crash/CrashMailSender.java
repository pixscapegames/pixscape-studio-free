package games.pixscape.studio.ui.crash;

import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class CrashMailSender {

    private static final int MAX_BODY_CHARS = 6000;

    private CrashMailSender() {
    }

    public static void openCrashMail(String to, Throwable throwable, String stacktrace) {
        String subject = "[Pixscape Crash] " + throwable.getClass().getSimpleName();

        String body = buildBody(throwable, stacktrace);

        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop API not supported");
            }

            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.MAIL)) {
                throw new IllegalStateException("Mail action not supported");
            }

            String uri = "mailto:" + to
                    + "?subject=" + encode(subject)
                    + "&body=" + encode(body);

            desktop.mail(URI.create(uri));
        } catch (Exception e) {
            throw new RuntimeException("Unable to open mail client", e);
        }
    }

    private static String buildBody(Throwable throwable, String stacktrace) {

        String sb = "Hello,\n\n" +
                "Pixscape Studio encountered a crash.\n\n" +
                "Exception: " + throwable.getClass().getName() + '\n' +
                "Message: " + throwable.getMessage() + '\n' +
                "OS: " + System.getProperty("os.name") + " " +
                System.getProperty("os.version") + '\n' +
                "Java: " + System.getProperty("java.version") + '\n' +
                '\n' +
                "Stacktrace:\n" +
                truncate(stacktrace, MAX_BODY_CHARS) +
                "\n\nMerci.";

        return sb;
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n\n[stacktrace truncated]";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}