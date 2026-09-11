package edu.seu.vcampus.client.ui.components;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 身份认证页品牌区：校徽图片 +「东南大学 / SOUTHEAST UNIVERSITY」。
 * 使用高分辨率资源并分步缩小，绘制时开启双三次插值，避免发糊。
 */
public final class SeuBrandHeader extends JPanel {
    private static final String EMBLEM_RESOURCE = "/images/seu-emblem.jpg";
    private static final int EMBLEM_SIZE = 104;

    public SeuBrandHeader() {
        super(new FlowLayout(FlowLayout.CENTER, 16, 0));
        setOpaque(false);

        JLabel emblem = new JLabel(loadEmblemIcon(EMBLEM_SIZE));
        emblem.setPreferredSize(new Dimension(EMBLEM_SIZE, EMBLEM_SIZE));

        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        JLabel cn = new JLabel("东南大学");
        cn.setFont(SeuTheme.font(Font.BOLD, 28f));
        cn.setForeground(SeuTheme.TEXT);
        JLabel en = new JLabel("SOUTHEAST UNIVERSITY");
        en.setFont(SeuTheme.font(Font.PLAIN, 11f));
        en.setForeground(SeuTheme.TEXT_MUTED);
        titles.add(cn, BorderLayout.NORTH);
        titles.add(en, BorderLayout.SOUTH);

        add(emblem);
        add(titles);
    }

    /** 居中大标题「身份认证中心」。 */
    public static JLabel authTitle() {
        JLabel title = new JLabel("身份认证中心", SwingConstants.CENTER);
        title.setFont(SeuTheme.font(Font.BOLD, 30f));
        title.setForeground(SeuTheme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        return title;
    }

    private static Icon loadEmblemIcon(int size) {
        InputStream stream = SeuBrandHeader.class.getResourceAsStream(EMBLEM_RESOURCE);
        if (stream == null) {
            return new EmptyIcon(size);
        }
        try {
            BufferedImage source = ImageIO.read(stream);
            if (source == null) {
                return new EmptyIcon(size);
            }
            // 预生成 2× 位图，绘制时缩到逻辑尺寸，高分屏更清晰
            BufferedImage hiRes = scaleHighQuality(source, size * 2, size * 2);
            return new CrispIcon(hiRes, size);
        } catch (IOException e) {
            return new EmptyIcon(size);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Resource stream close is best-effort.
            }
        }
    }

    /** 分步缩小大图，比一次性缩放更清晰。 */
    private static BufferedImage scaleHighQuality(BufferedImage source, int width, int height) {
        BufferedImage current = source;
        int currentWidth = current.getWidth();
        int currentHeight = current.getHeight();
        while (currentWidth / 2 >= width && currentHeight / 2 >= height) {
            currentWidth = Math.max(width, currentWidth / 2);
            currentHeight = Math.max(height, currentHeight / 2);
            current = drawScaled(current, currentWidth, currentHeight);
        }
        if (current.getWidth() != width || current.getHeight() != height) {
            current = drawScaled(current, width, height);
        }
        return current;
    }

    private static BufferedImage drawScaled(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private static final class CrispIcon implements Icon {
        private final BufferedImage image;
        private final int size;

        private CrispIcon(BufferedImage image, int size) {
            this.image = image;
            this.size = size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(image, x, y, size, size, null);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static final class EmptyIcon implements Icon {
        private final int size;

        private EmptyIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            // Intentionally blank when the resource is missing.
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
