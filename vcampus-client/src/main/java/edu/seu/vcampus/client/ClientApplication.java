package edu.seu.vcampus.client;

import com.formdev.flatlaf.FlatLightLaf;
import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.ui.LoginFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Swing client entry point. */
public final class ClientApplication {
    private ClientApplication() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    ClientConfig config = ClientConfig.load();
                    new LoginFrame(config).setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                            "客户端配置加载失败：" + e.getMessage(),
                            "启动失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}
