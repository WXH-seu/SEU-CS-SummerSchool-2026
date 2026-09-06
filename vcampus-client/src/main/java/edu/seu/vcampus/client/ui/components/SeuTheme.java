package edu.seu.vcampus.client.ui.components;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

/**
 * 东南大学信息服务门户风格的客户端主题常量与 FlatLaf 安装入口。
 *
 * <p>颜色取自门户截图：顶栏深绿、导航金黄强调、主按钮绿、白底内容区。
 * 各模块应优先引用本类常量，避免散落魔法色值。
 */
public final class SeuTheme {
    /** 顶栏 / 主品牌绿（门户 Header）。 */
    public static final Color PRIMARY = new Color(0x3F6B3A);
    /** 主按钮悬停与稍亮的搜索绿。 */
    public static final Color PRIMARY_HOVER = new Color(0x4A7C43);
    /** 图标与次级强调绿。 */
    public static final Color PRIMARY_SOFT = new Color(0x5C7E4E);
    /** 身份认证页大号登录按钮绿。 */
    public static final Color LOGIN_GREEN = new Color(0x4FAE45);
    /** 登录按钮悬停。 */
    public static final Color LOGIN_GREEN_HOVER = new Color(0x449B3B);
    /** 认证页输入框浅边框。 */
    public static final Color FIELD_BORDER = new Color(0xE8E8E8);
    /** 导航激活块金黄（门户「应用中心」）。 */
    public static final Color ACCENT = new Color(0xE8C84A);
    /** 金黄悬停。 */
    public static final Color ACCENT_HOVER = new Color(0xF0D15C);
    /** 危险操作（删除等）。 */
    public static final Color DANGER = new Color(0xC0392B);
    /** 页面浅灰底。 */
    public static final Color PAGE_BG = new Color(0xF3F4F6);
    /** 卡片 / 面板白底。 */
    public static final Color SURFACE = Color.WHITE;
    /** 分割线与输入框边框。 */
    public static final Color BORDER = new Color(0xE5E7EB);
    /** 主文字。 */
    public static final Color TEXT = new Color(0x1F2937);
    /** 次要文字 / 状态提示。 */
    public static final Color TEXT_MUTED = new Color(0x6B7280);
    /** 表格斑马纹。 */
    public static final Color TABLE_STRIPE = new Color(0xF8FAF8);
    /** 表格选中行浅绿。 */
    public static final Color TABLE_SELECTION = new Color(0xDCE8D8);

    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 16;
    public static final int SPACE_LG = 24;
    public static final int SPACE_XL = 36;

    public static final int RADIUS = 8;
    public static final int PAGE_PADDING = 24;

    public static final float FONT_TITLE = 24f;
    public static final float FONT_SUBTITLE = 16f;
    public static final float FONT_BODY = 13f;
    public static final float FONT_SMALL = 12f;

    private static boolean installed;

    private SeuTheme() {
    }

    /**
     * 安装 FlatLaf 并写入门户配色。应在创建任何窗口之前调用一次。
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        FlatLightLaf.setup();
        applyUiDefaults();
        installed = true;
    }

    private static void applyUiDefaults() {
        UIManager.put("Component.accentColor", PRIMARY);
        UIManager.put("Component.focusColor",
                new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 120));
        UIManager.put("Component.arc", Integer.valueOf(RADIUS));
        UIManager.put("Button.arc", Integer.valueOf(RADIUS));
        UIManager.put("TextComponent.arc", Integer.valueOf(RADIUS));
        UIManager.put("ScrollBar.thumbArc", Integer.valueOf(999));
        UIManager.put("ScrollBar.trackArc", Integer.valueOf(999));
        UIManager.put("ScrollBar.width", Integer.valueOf(10));

        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.margin", new Insets(6, 14, 6, 14));
        UIManager.put("Button.default.background", PRIMARY);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.hoverBackground", PRIMARY_HOVER);
        UIManager.put("Button.default.focusedBackground", PRIMARY_HOVER);

        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", PRIMARY);
        UIManager.put("TextField.selectionBackground", TABLE_SELECTION);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("PasswordField.caretForeground", PRIMARY);

        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", TABLE_SELECTION);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.background", new Color(0xEEF2EE));
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("Table.alternateRowColor", TABLE_STRIPE);

        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Panel.background", PAGE_BG);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ScrollPane.background", SURFACE);
        UIManager.put("Viewport.background", SURFACE);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("TabbedPane.selectedBackground", SURFACE);
        UIManager.put("TabbedPane.underlineColor", PRIMARY);
        UIManager.put("TabbedPane.focusColor", PRIMARY);
    }

    /** 基于当前 LookAndFeel 派生指定样式与字号的字体。 */
    public static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(style, size);
    }

    public static Font titleFont() {
        return font(Font.BOLD, FONT_TITLE);
    }

    public static Font subtitleFont() {
        return font(Font.BOLD, FONT_SUBTITLE);
    }

    public static Font bodyFont() {
        return font(Font.PLAIN, FONT_BODY);
    }

    public static Font smallFont() {
        return font(Font.PLAIN, FONT_SMALL);
    }

    /** 统一空白边距。 */
    public static Border empty(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** 页面外边距（约 24px）。 */
    public static Border pageBorder() {
        return empty(PAGE_PADDING, PAGE_PADDING, PAGE_PADDING, PAGE_PADDING);
    }

    /** 白底卡片：细边框 + 内边距。 */
    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                empty(SPACE_MD, SPACE_MD, SPACE_MD, SPACE_MD));
    }

    /** 底部分割线边框，适合顶栏下方。 */
    public static Border bottomDividerBorder(int paddingY, int paddingX) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                empty(paddingY, paddingX, paddingY, paddingX));
    }
}
