package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.StudentProfileUpdateRequest;
import edu.seu.vcampus.common.dto.TeacherDto;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Form dialogs used by the academic management page. */
final class AcademicEditors {
    private AcademicEditors() {
    }

    static StudentDto editStudent(Component parent, StudentDto value,
                                  List<DepartmentDto> departments,
                                  List<SchoolClassDto> classes) {
        JTextField id = field(value == null ? "" : value.getStudentId(), value == null);
        JTextField userId = field(value == null ? "" : value.getUserId(), true);
        JTextField name = field(value == null ? "" : value.getFullName(), true);
        JComboBox<String> gender = SeuFields.combo(new String[]{"男", "女", "其他"});
        JTextField birth = field(value == null ? "" : value.getBirthDate(), true);
        final JComboBox<Choice> department = departmentCombo(
                departments, value == null ? null : value.getDepartmentId(), false);
        final JComboBox<Choice> schoolClass = SeuFields.combo(new Choice[0]);
        JTextField year = field(value == null
                ? String.valueOf(Calendar.getInstance().get(Calendar.YEAR))
                : String.valueOf(value.getEnrollmentYear()), true);
        JComboBox<String> status = SeuFields.combo(
                new String[]{"在读", "休学", "毕业", "退学"});
        JTextField phone = field(value == null ? "" : value.getPhone(), true);
        JTextField email = field(value == null ? "" : value.getEmail(), true);
        if (value != null) {
            gender.setSelectedItem(value.getGender());
            status.setSelectedItem(value.getStatus());
        }
        Runnable refreshClasses = new Runnable() {
            @Override
            public void run() {
                String selectedDepartment = selectedId(department);
                String previous = value == null ? selectedId(schoolClass) : value.getClassId();
                schoolClass.removeAllItems();
                schoolClass.addItem(new Choice("", "自动分班（按学院、入学年份和容量）"));
                for (SchoolClassDto item : classes) {
                    if (item.isActive() && item.getDepartmentId().equals(selectedDepartment)) {
                        schoolClass.addItem(new Choice(item.getClassId(), item.getClassName()
                                + "（" + item.getGradeYear() + "级，容量 "
                                + item.getCapacity() + "）"));
                    }
                }
                select(schoolClass, previous);
            }
        };
        department.addActionListener(event -> refreshClasses.run());
        refreshClasses.run();
        JPanel form = form(new String[]{"学号*", "登录账号", "姓名*", "性别*", "出生日期",
                        "学院*", "班级", "入学年份*", "学籍状态*", "电话", "邮箱"},
                new Component[]{id, userId, name, gender, birth, department, schoolClass,
                        year, status, phone, email});
        if (!confirm(parent, form, value == null ? "新增学生" : "编辑学生")) {
            return null;
        }
        return new StudentDto(text(id), text(userId), text(name),
                String.valueOf(gender.getSelectedItem()), text(birth), selectedId(department),
                selectedId(schoolClass), parseYear(year), String.valueOf(status.getSelectedItem()),
                text(phone), text(email));
    }

    static StudentProfileUpdateRequest editOwnProfile(Component parent, StudentDto value) {
        JComboBox<String> gender = SeuFields.combo(new String[]{"男", "女", "其他"});
        gender.setSelectedItem(value.getGender());
        JTextField birth = field(value.getBirthDate(), true);
        JTextField phone = field(value.getPhone(), true);
        JTextField email = field(value.getEmail(), true);
        SeuFields.setPlaceholder(birth, "yyyy-MM-dd");
        JPanel form = form(new String[]{"性别*", "出生日期", "电话", "邮箱"},
                new Component[]{gender, birth, phone, email});
        if (!confirm(parent, form, "编辑我的学籍资料")) {
            return null;
        }
        return new StudentProfileUpdateRequest(String.valueOf(gender.getSelectedItem()),
                text(birth), text(phone), text(email));
    }

    static TeacherDto editTeacher(Component parent, TeacherDto value,
                                  List<DepartmentDto> departments) {
        JTextField id = field(value == null ? "" : value.getTeacherId(), value == null);
        JTextField userId = field(value == null ? "" : value.getUserId(), true);
        JTextField name = field(value == null ? "" : value.getFullName(), true);
        JComboBox<Choice> department = departmentCombo(
                departments, value == null ? null : value.getDepartmentId(), false);
        JTextField title = field(value == null ? "" : value.getTitle(), true);
        JTextField phone = field(value == null ? "" : value.getPhone(), true);
        JTextField email = field(value == null ? "" : value.getEmail(), true);
        JCheckBox active = new JCheckBox("在岗", value == null || value.isActive());
        JPanel form = form(new String[]{"工号*", "登录账号", "姓名*", "学院*",
                        "职称", "电话", "邮箱", "状态"},
                new Component[]{id, userId, name, department, title, phone, email, active});
        if (!confirm(parent, form, value == null ? "新增教师" : "编辑教师")) {
            return null;
        }
        return new TeacherDto(text(id), text(userId), text(name), selectedId(department),
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

    static SchoolClassDto editClass(Component parent, SchoolClassDto value,
                                    List<DepartmentDto> departments) {
        JTextField id = field(value == null ? "" : value.getClassId(), value == null);
        JTextField name = field(value == null ? "" : value.getClassName(), true);
        JComboBox<Choice> department = departmentCombo(
                departments, value == null ? null : value.getDepartmentId(), false);
        JTextField year = field(value == null
                ? String.valueOf(Calendar.getInstance().get(Calendar.YEAR))
                : String.valueOf(value.getGradeYear()), true);
        JTextField counselor = field(value == null ? "" : value.getCounselor(), true);
        JTextField capacity = field(value == null ? "50" : String.valueOf(value.getCapacity()), true);
        JCheckBox active = new JCheckBox("启用", value == null || value.isActive());
        JPanel form = form(new String[]{"班级编号*", "班级名称*", "学院*",
                        "年级*", "辅导员", "班级容量*", "状态"},
                new Component[]{id, name, department, year, counselor, capacity, active});
        if (!confirm(parent, form, value == null ? "新增班级" : "编辑班级")) {
            return null;
        }
        return new SchoolClassDto(text(id), text(name), selectedId(department), parseYear(year),
                text(counselor), parsePositive(capacity, "班级容量"), active.isSelected());
    }

    private static JComboBox<Choice> departmentCombo(
            List<DepartmentDto> departments, String selected, boolean includeAll) {
        List<Choice> choices = new ArrayList<Choice>();
        if (includeAll) {
            choices.add(new Choice("", "全部学院"));
        }
        for (DepartmentDto department : departments) {
            if (department.isActive() || department.getDepartmentId().equals(selected)) {
                choices.add(new Choice(department.getDepartmentId(),
                        department.getDepartmentName() + "（" + department.getDepartmentId() + "）"));
            }
        }
        JComboBox<Choice> combo = SeuFields.combo(choices.toArray(new Choice[choices.size()]));
        select(combo, selected);
        return combo;
    }

    private static void select(JComboBox<Choice> combo, String id) {
        String wanted = id == null ? "" : id;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (wanted.equals(combo.getItemAt(index).id)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private static String selectedId(JComboBox<Choice> combo) {
        Choice selected = (Choice) combo.getSelectedItem();
        return selected == null ? "" : selected.id;
    }

    private static JPanel form(String[] labels, Component[] components) {
        JPanel panel = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        for (int i = 0; i < labels.length; i++) {
            panel.add(SeuLabels.field(labels[i]));
            panel.add(components[i]);
        }
        return panel;
    }

    private static boolean confirm(Component parent, JPanel form, String title) {
        return JOptionPane.showConfirmDialog(parent, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private static JTextField field(String value, boolean editable) {
        JTextField field = SeuFields.text(value == null ? "" : value, 18);
        field.setEditable(editable);
        return field;
    }

    private static String text(JTextField field) {
        return field.getText().trim();
    }

    private static int parseYear(JTextField field) {
        return parsePositive(field, "年份");
    }

    private static int parsePositive(JTextField field, String name) {
        try {
            int value = Integer.parseInt(text(field));
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + "必须是正整数");
        }
    }

    private static final class Choice {
        private final String id;
        private final String label;

        private Choice(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
