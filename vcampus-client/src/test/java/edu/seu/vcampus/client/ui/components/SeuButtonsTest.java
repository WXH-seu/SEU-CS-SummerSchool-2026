package edu.seu.vcampus.client.ui.components;

import org.junit.Test;

import javax.swing.JButton;
import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Guards the action-button shape and semantic colors used by the library baseline. */
public class SeuButtonsTest {
    @Test
    public void actionButtonsShareLibraryDimensionsAndInteractionStyle() {
        JButton[] buttons = new JButton[]{
                SeuButtons.primary("查询"),
                SeuButtons.secondary("编辑"),
                SeuButtons.accent("借阅"),
                SeuButtons.danger("删除")
        };
        for (JButton button : buttons) {
            assertEquals(34, button.getPreferredSize().height);
            assertTrue(button.getPreferredSize().width >= 72);
            assertFalse(button.isFocusPainted());
            assertTrue(button.getMargin().left >= 16);
            assertTrue(button.getMargin().right >= 16);
        }
    }

    @Test
    public void actionTypesKeepTheLibrarySemanticColors() {
        JButton primary = SeuButtons.primary("查询");
        JButton secondary = SeuButtons.secondary("编辑");
        JButton danger = SeuButtons.danger("删除");

        assertEquals(SeuTheme.PRIMARY, primary.getBackground());
        assertEquals(Color.WHITE, primary.getForeground());
        assertEquals(SeuTheme.SURFACE, secondary.getBackground());
        assertEquals(SeuTheme.TEXT, secondary.getForeground());
        assertEquals(SeuTheme.DANGER, danger.getBackground());
        assertEquals(Color.WHITE, danger.getForeground());
    }
}
