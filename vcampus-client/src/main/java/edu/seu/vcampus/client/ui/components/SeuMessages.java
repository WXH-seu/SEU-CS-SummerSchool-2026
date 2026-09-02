package edu.seu.vcampus.client.ui.components;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * 统一提示框文案与按钮类型，避免各模块自拟标题不一致。
 */
public final class SeuMessages {
    public static final String TITLE_INFO = "提示";
    public static final String TITLE_ERROR = "操作失败";
    public static final String TITLE_CONFIRM = "确认";

    private SeuMessages() {
    }

    public static void info(Component parent, String message) {
        info(parent, TITLE_INFO, message);
    }

    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message,
                title == null || title.trim().isEmpty() ? TITLE_INFO : title,
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent,
                message == null || message.trim().isEmpty() ? "操作失败" : message,
                TITLE_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    /** @return {@code true} 用户选择「是」 */
    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, TITLE_CONFIRM,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION;
    }
}
