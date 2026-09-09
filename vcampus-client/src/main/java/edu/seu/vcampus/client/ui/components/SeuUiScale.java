package edu.seu.vcampus.client.ui.components;

import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 按窗口大小缩放界面字号。设计稿基准为 {@code 1200×720}，
 * 拉伸主窗口时字号与表格行高会跟着变。
 */
public final class SeuUiScale {
    private static final float BASE_WIDTH = 1200f;
    private static final float BASE_HEIGHT = 720f;
    private static final float MIN_SCALE = 0.85f;
    private static final float MAX_SCALE = 1.65f;
    private static final Map<Window, Float> APPLIED = new WeakHashMap<Window, Float>();

    private SeuUiScale() {
    }

    /** 监听窗口尺寸，防抖后刷新缩放。 */
    public static void bind(final Window window) {
        if (window == null) {
            return;
        }
        final Timer timer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refresh(window);
            }
        });
        timer.setRepeats(false);
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                timer.restart();
            }

            @Override
            public void componentShown(ComponentEvent event) {
                refresh(window);
            }
        });
        refresh(window);
    }

    /** 按当前窗口尺寸立即重算并应用到组件树。 */
    public static void refresh(Window window) {
        if (window == null) {
            return;
        }
        int width = window.getWidth();
        int height = window.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        apply(window, compute(width, height));
    }

    static float compute(int width, int height) {
        float scale = Math.min(width / BASE_WIDTH, height / BASE_HEIGHT);
        if (scale < MIN_SCALE) {
            scale = MIN_SCALE;
        }
        if (scale > MAX_SCALE) {
            scale = MAX_SCALE;
        }
        return Math.round(scale * 20f) / 20f;
    }

    private static void apply(Window window, float nextScale) {
        Float previous = APPLIED.get(window);
        float prevScale = previous == null ? 1f : previous.floatValue();
        if (Math.abs(nextScale - prevScale) < 0.001f) {
            SeuTheme.setUiScale(nextScale);
            SeuTheme.applyUiScaleToLookAndFeel(nextScale);
            APPLIED.put(window, Float.valueOf(nextScale));
            return;
        }
        float ratio = nextScale / prevScale;
        SeuTheme.setUiScale(nextScale);
        SeuTheme.applyUiScaleToLookAndFeel(nextScale);
        APPLIED.put(window, Float.valueOf(nextScale));
        visit(window, ratio);
        window.invalidate();
        window.validate();
        window.repaint();
    }

    private static void visit(Component component, float ratio) {
        if (component == null) {
            return;
        }
        scaleFont(component, ratio);
        if (component instanceof JTable) {
            syncTableRowHeight((JTable) component);
        } else if (component instanceof JList) {
            syncListRowHeight((JList) component);
        }
        if (component instanceof Container) {
            Component[] children = ((Container) component).getComponents();
            for (int i = 0; i < children.length; i++) {
                visit(children[i], ratio);
            }
        }
        if (component instanceof SeuNavBar) {
            ((SeuNavBar) component).syncMetrics();
        } else if (component instanceof SeuAppTile) {
            ((SeuAppTile) component).syncMetrics();
        } else {
            scaleExplicitSize(component, ratio);
        }
    }

    private static void scaleFont(Component component, float ratio) {
        Font font = component.getFont();
        if (font == null || font.getSize2D() <= 0f) {
            return;
        }
        float next = font.getSize2D() * ratio;
        if (next < 9f) {
            next = 9f;
        }
        component.setFont(font.deriveFont(next));
    }

    private static void syncTableRowHeight(JTable table) {
        Font font = table.getFont();
        float size = font == null ? SeuTheme.FONT_BODY : font.getSize2D();
        table.setRowHeight(Math.max(18, Math.round(size * 2.2f)));
    }

    private static void syncListRowHeight(JList list) {
        if (list.getFixedCellHeight() <= 0) {
            return;
        }
        Font font = list.getFont();
        float size = font == null ? SeuTheme.FONT_BODY : font.getSize2D();
        list.setFixedCellHeight(Math.max(16, Math.round(size * 1.85f)));
    }

    private static void scaleExplicitSize(Component component, float ratio) {
        if (component instanceof Window) {
            return;
        }
        if (component.isPreferredSizeSet()) {
            component.setPreferredSize(scaleDimension(component.getPreferredSize(), ratio, false));
        }
        if (component.isMinimumSizeSet()) {
            component.setMinimumSize(scaleDimension(component.getMinimumSize(), ratio, false));
        }
        if (component.isMaximumSizeSet()) {
            component.setMaximumSize(scaleDimension(component.getMaximumSize(), ratio, true));
        }
    }

    private static Dimension scaleDimension(Dimension size, float ratio, boolean preserveUnbounded) {
        if (size == null) {
            return null;
        }
        int width = size.width;
        int height = size.height;
        if (!(preserveUnbounded && width >= Short.MAX_VALUE)) {
            width = Math.max(1, Math.round(width * ratio));
        }
        if (!(preserveUnbounded && height >= Short.MAX_VALUE)) {
            height = Math.max(1, Math.round(height * ratio));
        }
        return new Dimension(width, height);
    }
}
