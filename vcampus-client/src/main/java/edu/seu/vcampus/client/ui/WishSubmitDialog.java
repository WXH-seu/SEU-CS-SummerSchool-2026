package edu.seu.vcampus.client.ui;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;

/** Small form used by patrons to submit a book recommendation. */
final class WishSubmitDialog {
    private WishSubmitDialog() {
    }

    static String[] prompt(Component parent) {
        JTextField title = new JTextField(18);
        JTextField author = new JTextField(18);
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("书名*"));
        form.add(title);
        form.add(new JLabel("作者*"));
        form.add(author);
        if (JOptionPane.showConfirmDialog(parent, form, "提交推荐",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return null;
        }
        return new String[]{title.getText().trim(), author.getText().trim()};
    }
}
