package edu.seu.vcampus.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 服务端控制台日志配置。
 *
 * <p><strong>为什么需要本类：</strong>UCanAccess/HSQLDB 在首次连接 Access 数据库文件时，
 * 会把根 {@link Logger} 上 {@code ConsoleHandler} 的级别改写为 {@code WARNING}
 * （HSQLDB 自身降噪的行为）。其后果是**此后所有 INFO 级日志被静默丢弃**——
 * 服务端启动信息、连接/断开日志等全部不可见，只剩下 SEVERE/WARNING。
 *
 * <p>因此这里统一安装一行式控制台处理器，并把级别显式固定为 INFO；
 * 在数据库初始化完成之后再调用一次 {@link #configure()}，即可把被依赖改写的级别恢复。
 *
 * <p>可重复调用：首次调用安装处理器，之后每次调用都会把级别修正回 INFO。
 */
public final class ServerLogging {
    private static volatile boolean installed;

    private ServerLogging() {
    }

    /** 安装（首次）或修正（后续）控制台日志级别，保证 INFO 日志可见。 */
    public static synchronized void configure() {
        Logger root = Logger.getLogger("");
        if (!installed) {
            for (Handler handler : root.getHandlers()) {
                root.removeHandler(handler);
            }
            ConsoleHandler console = new ConsoleHandler();
            console.setFormatter(new OneLineFormatter());
            console.setLevel(Level.INFO);
            root.addHandler(console);
            installed = true;
        }
        forceInfo(root);
    }

    /** 把根日志与其处理器的级别下限恢复到 INFO（依赖可能把它们抬高）。 */
    private static void forceInfo(Logger root) {
        if (root.getLevel() == null || root.getLevel().intValue() > Level.INFO.intValue()) {
            root.setLevel(Level.INFO);
        }
        for (Handler handler : root.getHandlers()) {
            if (handler.getLevel() == null
                    || handler.getLevel().intValue() > Level.INFO.intValue()) {
                handler.setLevel(Level.INFO);
            }
        }
    }

    /** 一行式日志格式：时间 级别 类名 - 内容，附带异常堆栈。 */
    private static final class OneLineFormatter extends Formatter {
        private final SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public synchronized String format(LogRecord record) {
            StringBuilder builder = new StringBuilder(160);
            builder.append(stamp.format(new Date(record.getMillis())))
                    .append(' ').append(record.getLevel().getName())
                    .append(' ').append(simpleName(record.getLoggerName()))
                    .append(" - ").append(formatMessage(record))
                    .append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter writer = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(writer));
                builder.append(writer);
            }
            return builder.toString();
        }

        private String simpleName(String loggerName) {
            if (loggerName == null || loggerName.isEmpty()) {
                return "-";
            }
            int dot = loggerName.lastIndexOf('.');
            return dot < 0 ? loggerName : loggerName.substring(dot + 1);
        }
    }
}
