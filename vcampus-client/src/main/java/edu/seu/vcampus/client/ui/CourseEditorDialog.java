package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.TeacherDto;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;

/** Small form dialog used by the course management page for administrators. */
final class CourseEditorDialog {
    private CourseEditorDialog() {
    }

    static CourseDto edit(Component parent, CourseDto value,
                          List<TeacherDto> teachers, List<DepartmentDto> departments) {
        JTextField id = field(value == null ? "" : value.getCourseId(), value == null);
        JTextField name = field(value == null ? "" : value.getCourseName(), true);
        JComboBox<Choice> teacher = new JComboBox<Choice>();
        for (TeacherDto teacherDto : teachers) {
            teacher.addItem(new Choice(teacherDto.getTeacherId(), teacherDto.getTeacherId()
                    + " - " + teacherDto.getFullName()));
        }
        JComboBox<Choice> department = new JComboBox<Choice>();
        for (DepartmentDto departmentDto : departments) {
            department.addItem(new Choice(departmentDto.getDepartmentId(),
                    departmentDto.getDepartmentId() + " - " + departmentDto.getDepartmentName()));
        }
        JTextField credit = field(value == null ? "3.0" : String.valueOf(value.getCredit()), true);
        JTextField capacity = field(value == null ? "30" : String.valueOf(value.getCapacity()),
                true);
        JTextField semester = field(value == null ? "" : value.getSemesterName(), true);
        JTextField classTime = field(value == null ? "" : value.getClassTime(), true);
        JTextField location = field(value == null ? "" : value.getLocation(), true);
        JTextField description = field(value == null ? "" : value.getDescription(), true);
        JCheckBox active = new JCheckBox("开放选课", value == null || value.isActive());
        if (value != null) {
            select(teacher, value.getTeacherId());
            select(department, value.getDepartmentId());
        }

        JPanel form = form(new String[]{"课程编号*", "课程名称*", "授课教师*", "开课院系*",
                        "学分*", "容量*", "学期*", "上课时间*", "上课地点", "课程简介", "状态"},
                new Component[]{id, name, teacher, department, credit, capacity,
                        semester, classTime, location, description, active});
        if (JOptionPane.showConfirmDialog(parent, form,
                value == null ? "新增课程" : "编辑课程",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return null;
        }
        return new CourseDto(text(id), text(name),
                selectedId(teacher), null, selectedId(department), null,
                parseCredit(credit), parseCapacity(capacity), 0,
                text(semester), text(classTime), text(location), text(description),
                active.isSelected());
    }

    private static void select(JComboBox<Choice> combo, String id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).id.equals(id)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String selectedId(JComboBox<Choice> combo) {
        Choice selected = (Choice) combo.getSelectedItem();
        return selected == null ? "" : selected.id;
    }

    private static JPanel form(String[] labels, Component[] components) {
        JPanel panel = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        for (int i = 0; i < labels.length; i++) {
            panel.add(new JLabel(labels[i]));
            panel.add(components[i]);
        }
        return panel;
    }

    private static JTextField field(String value, boolean editable) {
        JTextField field = new JTextField(value == null ? "" : value, 18);
        field.setEditable(editable);
        return field;
    }

    private static String text(JTextField field) {
        return field.getText().trim();
    }

    private static double parseCredit(JTextField field) {
        try {
            return Double.parseDouble(text(field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("学分必须是数字");
        }
    }

    private static int parseCapacity(JTextField field) {
        try {
            return Integer.parseInt(text(field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("容量必须是整数");
        }
    }

    /** Combo box item that keeps the real id and a readable label. */
    private static final class Choice {
        private final String id;
        private final String label;

        Choice(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
