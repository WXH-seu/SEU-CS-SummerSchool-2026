package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SectionAudienceDto;
import edu.seu.vcampus.common.dto.TeacherDto;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员维护教学班的表单：课程目录信息 + 开课信息 + 选课时间窗口 + 受众规则。
 * 受众支持「全校 / 指定院系」并叠加入学年份区间，多条规则并集生效。
 */
final class CourseEditorDialog {
    private static final String[] NATURE_ITEMS = {"必修", "限选", "任选", "通选"};
    private static final String SCOPE_ALL_LABEL = "全校";
    private static final String SCOPE_DEPARTMENT_LABEL = "指定院系";

    private CourseEditorDialog() {
    }

    static CourseDto edit(Component parent, CourseDto value,
                          List<TeacherDto> teachers, List<DepartmentDto> departments) {
        JTextField id = field(value == null ? "" : value.getCourseId(), value == null);
        JTextField name = field(value == null ? "" : value.getCourseName(), true);
        JTextField credit = field(value == null ? "3.0" : String.valueOf(value.getCredit()), true);
        JTextField capacity = field(value == null ? "30" : String.valueOf(value.getCapacity()),
                true);
        JTextField semester = field(value == null ? "2026-2027-1" : value.getSemesterName(),
                true);
        JTextField classTime = field(value == null ? "" : value.getClassTime(), true);
        JTextField location = field(value == null ? "" : value.getLocation(), true);
        JTextField description = field(value == null ? "" : value.getDescription(), true);
        JTextField start = field(value == null ? "" : value.getSelectionStartTime(), true);
        JTextField end = field(value == null ? "" : value.getSelectionEndTime(), true);
        JCheckBox active = new JCheckBox("开放选课", value == null || value.isActive());
        active.setFont(SeuTheme.bodyFont());
        active.setForeground(SeuTheme.TEXT);
        active.setOpaque(false);

        JComboBox<Choice> teacher = teacherCombo(teachers, value);
        JComboBox<Choice> department = departmentCombo(departments, value);
        JComboBox<String> nature = SeuFields.combo(NATURE_ITEMS);
        nature.setSelectedItem(value == null ? "必修" : value.getCourseNature());

        DefaultListModel<AudienceItem> audienceModel = new DefaultListModel<AudienceItem>();
        List<SectionAudienceDto> existing = value == null
                ? new ArrayList<SectionAudienceDto>() : value.getAudiences();
        if (existing.isEmpty()) {
            audienceModel.addElement(new AudienceItem(new SectionAudienceDto(null,
                    SectionAudienceDto.SCOPE_ALL, null, null, null), departments));
        } else {
            for (SectionAudienceDto audience : existing) {
                audienceModel.addElement(new AudienceItem(audience, departments));
            }
        }
        JList<AudienceItem> audienceList = new JList<AudienceItem>(audienceModel);
        audienceList.setFont(SeuTheme.bodyFont());
        audienceList.setFixedCellHeight(24);

        JComboBox<String> scope = SeuFields.combo(
                new String[]{SCOPE_ALL_LABEL, SCOPE_DEPARTMENT_LABEL});
        JComboBox<Choice> scopeDepartment = departmentCombo(departments, null);
        scopeDepartment.setEnabled(false);
        JTextField yearMin = SeuFields.text(4);
        JTextField yearMax = SeuFields.text(4);
        scope.addActionListener(event -> scopeDepartment.setEnabled(
                SCOPE_DEPARTMENT_LABEL.equals(scope.getSelectedItem())));
        JButton addAudience = SeuButtons.secondary("添加规则");
        JButton removeAudience = SeuButtons.danger("移除选中");
        addAudience.addActionListener(event -> {
            try {
                audienceModel.addElement(new AudienceItem(
                        buildAudience(scope, scopeDepartment, yearMin, yearMax),
                        departments));
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(parent, e.getMessage(), "受众规则有误",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        removeAudience.addActionListener(event -> {
            int index = audienceList.getSelectedIndex();
            if (index >= 0) {
                audienceModel.remove(index);
            }
        });

        JPanel audienceBar = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, SeuTheme.SPACE_SM, 0));
        audienceBar.setOpaque(false);
        audienceBar.add(SeuLabels.field("范围"));
        audienceBar.add(scope);
        audienceBar.add(scopeDepartment);
        audienceBar.add(SeuLabels.field("入学年份"));
        audienceBar.add(yearMin);
        audienceBar.add(new javax.swing.JLabel("—"));
        audienceBar.add(yearMax);
        audienceBar.add(addAudience);
        audienceBar.add(removeAudience);

        JScrollPane audienceScroll = new JScrollPane(audienceList);
        audienceScroll.setPreferredSize(new java.awt.Dimension(420, 90));
        audienceScroll.setBorder(javax.swing.BorderFactory.createLineBorder(
                SeuTheme.BORDER, 1));

        JPanel form = new JPanel(new GridLayout(0, 2, SeuTheme.SPACE_SM, SeuTheme.SPACE_SM));
        addRow(form, "课程编号*", id);
        addRow(form, "课程名称*", name);
        addRow(form, "授课教师*", teacher);
        addRow(form, "开课院系*", department);
        addRow(form, "学分*", credit);
        addRow(form, "课程性质*", nature);
        addRow(form, "容量*", capacity);
        addRow(form, "学期*", semester);
        addRow(form, "上课时间*", classTime);
        addRow(form, "上课地点", location);
        addRow(form, "选课开始(yyyy-MM-dd HH:mm)", start);
        addRow(form, "选课结束(yyyy-MM-dd HH:mm)", end);
        addRow(form, "状态", active);
        addRow(form, "课程简介", description);

        JPanel body = new JPanel(new java.awt.BorderLayout(
                SeuTheme.SPACE_SM, SeuTheme.SPACE_MD));
        body.setOpaque(false);
        body.add(form, java.awt.BorderLayout.NORTH);

        JPanel audiencePanel = new JPanel(new java.awt.BorderLayout(
                0, SeuTheme.SPACE_SM));
        audiencePanel.setOpaque(false);
        audiencePanel.add(SeuLabels.field("选课受众规则（并集，至少一条）"),
                java.awt.BorderLayout.NORTH);
        audiencePanel.add(audienceBar, java.awt.BorderLayout.CENTER);
        audiencePanel.add(audienceScroll, java.awt.BorderLayout.SOUTH);
        body.add(audiencePanel, java.awt.BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(parent, body,
                value == null ? "新增教学班" : "编辑教学班",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return null;
        }
        List<SectionAudienceDto> audiences = new ArrayList<SectionAudienceDto>();
        for (int i = 0; i < audienceModel.size(); i++) {
            audiences.add(audienceModel.get(i).dto);
        }
        return new CourseDto(value == null ? null : value.getSectionId(),
                text(id), text(name), text(description),
                selectedId(teacher), null, selectedId(department), null,
                parseCredit(credit), String.valueOf(nature.getSelectedItem()),
                parseCapacity(capacity), value == null ? 0 : value.getEnrolledCount(),
                text(semester), text(classTime), text(location),
                text(start), text(end), active.isSelected(), false, null, audiences);
    }

    private static void addRow(JPanel form, String label, Component component) {
        form.add(SeuLabels.field(label));
        form.add(component);
    }

    private static SectionAudienceDto buildAudience(
            JComboBox<String> scope, JComboBox<Choice> scopeDepartment,
            JTextField yearMin, JTextField yearMax) {
        String scopeValue = null;
        String scopeType = SectionAudienceDto.SCOPE_ALL;
        if (SCOPE_DEPARTMENT_LABEL.equals(scope.getSelectedItem())) {
            scopeType = SectionAudienceDto.SCOPE_DEPARTMENT;
            scopeValue = selectedId(scopeDepartment);
            if (scopeValue.isEmpty()) {
                throw new IllegalArgumentException("请选择受众院系");
            }
        }
        return new SectionAudienceDto(null, scopeType, scopeValue,
                parseNullableYear(yearMin, "起始"), parseNullableYear(yearMax, "结束"));
    }

    private static Integer parseNullableYear(JTextField field, String label) {
        String value = text(field);
        if (value.isEmpty()) {
            return null;
        }
        try {
            int year = Integer.parseInt(value);
            if (year < 1900 || year > 2100) {
                throw new IllegalArgumentException("入学年份" + label + "需在 1900-2100");
            }
            return Integer.valueOf(year);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("入学年份" + label + "必须是整数");
        }
    }

    /** 受众规则的显示包装：把规则格式化成“全校 / 院系名 + 年份区间”。 */
    private static final class AudienceItem {
        private final SectionAudienceDto dto;
        private final String label;

        AudienceItem(SectionAudienceDto dto, List<DepartmentDto> departments) {
            this.dto = dto;
            StringBuilder text = new StringBuilder();
            if (SectionAudienceDto.SCOPE_ALL.equals(dto.getScopeType())) {
                text.append("全校");
            } else {
                text.append(departmentLabel(dto.getScopeValue(), departments));
            }
            text.append(yearLabel(dto));
            this.label = text.toString();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static String departmentLabel(String departmentId,
                                          List<DepartmentDto> departments) {
        if (departmentId != null) {
            for (DepartmentDto department : departments) {
                if (departmentId.equals(department.getDepartmentId())) {
                    return department.getDepartmentName() + "（" + departmentId + "）";
                }
            }
        }
        return departmentId == null || departmentId.isEmpty() ? "指定院系" : departmentId;
    }

    private static String yearLabel(SectionAudienceDto audience) {
        Integer min = audience.getYearMin();
        Integer max = audience.getYearMax();
        if (min == null && max == null) {
            return " · 不限入学年份";
        }
        if (min == null) {
            return " · ≤" + max + " 级入学";
        }
        if (max == null) {
            return " · ≥" + min + " 级入学";
        }
        return " · " + min + "-" + max + " 级入学";
    }

    private static JComboBox<Choice> teacherCombo(List<TeacherDto> teachers, CourseDto value) {
        JComboBox<Choice> combo = new JComboBox<Choice>();
        for (TeacherDto teacher : teachers) {
            combo.addItem(new Choice(teacher.getTeacherId(),
                    teacher.getTeacherId() + " - " + teacher.getFullName()));
        }
        if (value != null) {
            select(combo, value.getTeacherId());
        }
        return combo;
    }

    private static JComboBox<Choice> departmentCombo(List<DepartmentDto> departments,
                                                     CourseDto value) {
        JComboBox<Choice> combo = new JComboBox<Choice>();
        for (DepartmentDto department : departments) {
            combo.addItem(new Choice(department.getDepartmentId(),
                    department.getDepartmentId() + " - " + department.getDepartmentName()));
        }
        if (value != null) {
            select(combo, value.getDepartmentId());
        }
        return combo;
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

    private static JTextField field(String value, boolean editable) {
        JTextField field = SeuFields.text(value, 16);
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

    /** Combo item that keeps the real id and a readable label. */
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
