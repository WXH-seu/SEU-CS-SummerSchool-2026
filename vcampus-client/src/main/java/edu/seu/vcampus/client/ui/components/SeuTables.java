package edu.seu.vcampus.client.ui.components;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.Component;

/**
 * 统一表格外观：斑马纹、浅绿选中、不可编辑模型助手。
 */
public final class SeuTables {
    private SeuTables() {
    }

    /** 创建只读表格模型（单元格不可编辑）。 */
    public static DefaultTableModel readOnlyModel(String[] columns) {
        DefaultTableModel model = new DefaultTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        if (columns != null) {
            model.setColumnIdentifiers(columns);
        }
        return model;
    }

    /** 用已有模型创建并套用门户表格样式。 */
    public static JTable create(DefaultTableModel model) {
        JTable table = new JTable(model);
        apply(table);
        return table;
    }

    /** 对现有表格套用统一样式（可在迁移旧页面时调用）。 */
    public static void apply(JTable table) {
        table.setFont(SeuTheme.bodyFont());
        table.setForeground(SeuTheme.TEXT);
        table.setBackground(SeuTheme.SURFACE);
        table.setSelectionBackground(SeuTheme.TABLE_SELECTION);
        table.setSelectionForeground(SeuTheme.TEXT);
        table.setGridColor(SeuTheme.BORDER);
        table.setRowHeight(32);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setIntercellSpacing(new java.awt.Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        header.setFont(SeuTheme.font(java.awt.Font.BOLD, SeuTheme.FONT_BODY));
        header.setBackground(new java.awt.Color(0xEEF2EE));
        header.setForeground(SeuTheme.TEXT);
        header.setReorderingAllowed(false);

        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    component.setBackground(row % 2 == 0
                            ? SeuTheme.SURFACE
                            : SeuTheme.TABLE_STRIPE);
                }
                return component;
            }
        };
        table.setDefaultRenderer(Object.class, cellRenderer);
    }

    /** 包装为带细边框的滚动面板。 */
    public static JScrollPane scroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(javax.swing.BorderFactory.createLineBorder(SeuTheme.BORDER, 1));
        scroll.getViewport().setBackground(SeuTheme.SURFACE);
        return scroll;
    }
}
