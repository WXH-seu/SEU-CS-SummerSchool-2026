package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Calendar;

/** Small form dialogs used by the academic management page. */
final class AcademicEditors {
    private AcademicEditors() {
    }

    static StudentDto editStudent(Component parent, StudentDto value) {
        JTextField id = field(value == null ? "" : value.getStudentId(), value == null);
        JTextField userId = field(value == null ? "" : value.getUserId(), true);
        JTextField name = field(value == null ? "" : value.getFullName(), true);
        JComboBox<String> gender = new JComboBox<String>(new String[]{"男", "女", "其他"});
        JTextField birth = field(value == null ? "" : value.getBirthDate(), true);
        JTextField department = field(value == null ? "" : value.getDepartmentId(), true);
        JTextField classId = field(value == null ? "" : value.getClassId(), true);
        JTextField year = field(value == null
                ? String.valueOf(Calendar.getInstance().get(Calendar.YEAR))
                : String.valueOf(value.getEnrollmentYear()), true);
        JComboBox<String> status = new JComboBox<String>(
                new String[]{"在读", "休学", "毕业", "退学"});
        JTextField phone = field(value == null ? "" : value.getPhone(), true);
        JTextField email = field(value == null ? "" : value.getEmail(), true);
        if (value != null) {
            gender.setSelectedItem(value.getGender());
            status.setSelectedItem(value.getStatus());
        }
        JPanel form = form(new String[]{"学号*", "登录账号", "姓名*", "性别*", "出生日期",
                        "院系编号*", "班级编号*", "入学年份*", "学籍状态*", "电话", "邮箱"},
                new Component[]{id, userId, name, gender, birth, department, classId,
                        year, status, phone, email});
        if (!confirm(parent, form, value == null ? "新增学生" : "编辑学生")) {
            return null;
        }
        return new StudentDto(text(id), text(userId), text(name),
                String.valueOf(gender.getSelectedItem()), text(birth), text(department),
                text(classId), parseYear(year), String.valueOf(status.getSelectedItem()),
                text(phone), text(email));
    }

    static TeacherDto editTeacher(Component parent, TeacherDto value) {
        JTextField id = field(value == null ? "" : value.getTeacherId(), value == null);
        JTextField userId = field(value == null ? "" : value.getUserId(), true);
        JTextField name = field(value == null ? "" : value.getFullName(), true);
        JTextField department = field(value == null ? "" : value.getDepartmentId(), true);
        JTextField title = field(value == null ? "" : value.getTitle(), true);
        JTextField phone = field(value == null ? "" : value.getPhone(), true);
        JTextField email = field(value == null ? "" : value.getEmail(), true);
        JCheckBox active = new JCheckBox("在岗", value == null || value.isActive());
        JPanel form = form(new String[]{"工号*", "登录账号", "姓名*", "院系编号*",
                        "职称", "电话", "邮箱", "状态"},
                new Component[]{id, userId, name, department, title, phone, email, active});
        if (!confirm(parent, form, value == null ? "新增教师" : "编辑教师")) {
            return null;
        }
        return new TeacherDto(text(id), text(userId), text(name), text(department),
                text(title), text(phone), text(email), active.isSelected());
    }

    static DepartmentDto editDepartment(Component parent, DepartmentDto value) {
        JTextField id = field(value == null ? "" : value.getDepartmentId(), value == null);
        JTextField name = field(value == null ? "" : value.getDepartmentName(), true);
        JTextField description = field(value == null ? "" : value.getDescription(), true);
        JCheckBox active = new JCheckBox("启用", value == null || value.isActive());
        JPanel form = form(new String[]{"院系编号*", "院系名称*", "简介", "状态"},
                new Component[]{id, name, description, active});
        if (!confirm(parent, form, value == null ? "新增院系" : "编辑院系")) {
            return null;
        }
        return new DepartmentDto(text(id), text(name), text(description), active.isSelected());
    }

    static SchoolClassDto editClass(Component parent, SchoolClassDto value) {
        JTextField id = field(value == null ? "" : value.getClassId(), value == null);
        JTextField name = field(value == null ? "" : value.getClassName(), true);
        JTextField department = field(value == null ? "" : value.getDepartmentId(), true);
        JTextField year = field(value == null
                ? String.valueOf(Calendar.getInstance().get(Calendar.YEAR))
                : String.valueOf(value.getGradeYear()), true);
        JTextField counselor = field(value == null ? "" : value.getCounselor(), true);
        JCheckBox active = new JCheckBox("启用", value == null || value.isActive());
        JPanel form = form(new String[]{"班级编号*", "班级名称*", "院系编号*",
                        "年级*", "辅导员", "状态"},
                new Component[]{id, name, department, year, counselor, active});
        if (!confirm(parent, form, value == null ? "新增班级" : "编辑班级")) {
            return null;
        }
        return new SchoolClassDto(text(id), text(name), text(department), parseYear(year),
                text(counselor), active.isSelected());
    }

    private static JPanel form(String[] labels, Component[] components) {
        JPanel panel = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        for (int i = 0; i < labels.length; i++) {
            panel.add(new JLabel(labels[i]));
            panel.add(components[i]);
        }
        return panel;
    }

    private static boolean confirm(Component parent, JPanel form, String title) {
        return JOptionPane.showConfirmDialog(parent, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private static JTextField field(String value, boolean editable) {
        JTextField field = new JTextField(value == null ? "" : value, 18);
        field.setEditable(editable);
        return field;
    }

    private static String text(JTextField field) {
        return field.getText().trim();
    }

    private static int parseYear(JTextField field) {
        try {
            return Integer.parseInt(text(field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("年份必须是数字");
        }
    }
}
