package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookSummary;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;

/** Small form dialog used by the library page for administrators. */
final class BookEditorDialog {
    private BookEditorDialog() {
    }

    static BookDto edit(Component parent, BookSummary value) {
        JTextField isbn = field(value == null ? "" : value.getIsbn(), value == null);
        JTextField title = field(value == null ? "" : value.getTitle(), true);
        JTextField author = field(value == null ? "" : value.getAuthor(), true);
        JTextField publisher = field(value == null ? "" : value.getPublisher(), true);
        JTextField category = field(value == null ? "" : value.getCategory(), true);
        JTextField copies = field(value == null ? "1" : String.valueOf(value.getTotalCopies()),
                true);
        JCheckBox active = new JCheckBox("在架", value == null || value.isActive());

        JPanel form = new JPanel(new GridLayout(7, 2, 8, 8));
        form.add(new JLabel("ISBN*"));
        form.add(isbn);
        form.add(new JLabel("书名*"));
        form.add(title);
        form.add(new JLabel("作者*"));
        form.add(author);
        form.add(new JLabel("出版社"));
        form.add(publisher);
        form.add(new JLabel("分类"));
        form.add(category);
        form.add(new JLabel("馆藏册数*"));
        form.add(copies);
        form.add(new JLabel("状态"));
        form.add(active);

        if (JOptionPane.showConfirmDialog(parent, form,
                value == null ? "新增图书" : "编辑图书",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return null;
        }
        return new BookDto(text(isbn), text(title), text(author), text(publisher),
                text(category), parseCopies(copies), active.isSelected());
    }

    private static JTextField field(String value, boolean editable) {
        JTextField field = new JTextField(value == null ? "" : value, 18);
        field.setEditable(editable);
        return field;
    }

    private static String text(JTextField field) {
        return field.getText().trim();
    }

    private static int parseCopies(JTextField field) {
        try {
            return Integer.parseInt(text(field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("馆藏册数必须是整数");
        }
    }
}
