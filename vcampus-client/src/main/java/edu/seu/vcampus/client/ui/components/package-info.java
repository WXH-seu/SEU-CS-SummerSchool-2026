/**
 * 可复用的公共 Swing 组件与视觉规范，供各业务模块统一界面风格。
 *
 * <p>视觉参考东南大学信息服务门户：绿色顶栏、金黄强调色、白底圆角卡片与绿色主按钮。
 * 业务页面逐步改用本包工厂方法即可，无需各自硬编码颜色与字号。
 *
 * <p>典型用法：
 * <pre>
 * SeuTheme.install();                 // 仅在 ClientApplication 调用一次
 * JButton search = SeuButtons.primary("搜索");
 * JTextField keyword = SeuFields.text(18);
 * JTable table = SeuTables.create(model);
 * JPanel page = SeuPanels.page();
 * </pre>
 */
package edu.seu.vcampus.client.ui.components;
