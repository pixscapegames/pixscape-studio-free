package games.pixscape.studio.ui.crash;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class CrashDialog {

    private CrashDialog() {
    }

    public static void show(String title, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String stacktrace = sw.toString();

        SwingUtilities.invokeLater(() -> {
            JTextArea area = new JTextArea(stacktrace, 24, 100);
            area.setEditable(false);
            area.setCaretPosition(0);

            JScrollPane scrollPane = new JScrollPane(area);

            JOptionPane optionPane = new JOptionPane(
                    scrollPane,
                    JOptionPane.ERROR_MESSAGE,
                    JOptionPane.YES_NO_OPTION,
                    null,
                    new Object[]{"Send", "Close"},
                    "Send"
            );

            JDialog dialog = optionPane.createDialog(null, title);
            dialog.setModal(true);

            if (dialog.isAlwaysOnTopSupported()) {
                dialog.setAlwaysOnTop(true);
            }

            dialog.toFront();
            dialog.requestFocus();
            dialog.setVisible(true);

            Object value = optionPane.getValue();
            if ("Send".equals(value)) {
                sendCrashReport(optionPane, dialog, stacktrace, throwable);
            }

            System.exit(1);
        });
    }

    private static void sendCrashReport(JOptionPane optionPane, JDialog dialog, String stacktrace, Throwable throwable) {
        Object value = optionPane.getValue();

        if ("Send".equals(value)) {
            try {
                CrashMailSender.openCrashMail(
                        "info@pixscape.games",
                        throwable,
                        stacktrace
                );
            } catch (Exception mailError) {
                JOptionPane.showMessageDialog(
                        dialog,
                        "Unable to open the email client.\n\n" + mailError.getMessage(),
                        "Mail error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
}
