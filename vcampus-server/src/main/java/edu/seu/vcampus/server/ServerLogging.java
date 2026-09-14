package edu.seu.vcampus.server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 服务端日志配置：控制台 + 按天滚动的文件日志。
 *
 * <p><strong>为什么需要本类：</strong>UCanAccess/HSQLDB 在首次连接 Access 数据库文件时，
 * 会把根 {@link Logger} 上处理器的级别改写为 {@code WARNING}（HSQLDB 自身降噪行为）。
 * 后果是**此后所有 INFO 级日志被静默丢弃**——服务端启动信息、连接/断开日志全部不可见，
 * 只剩 SEVERE/WARNING。注意过滤发生在 Handler 层，只设置 Logger 自身级别无效。
 *
 * <p>因此本类统一安装自己的处理器并把级别固定为 INFO；在数据库初始化完成之后再调用一次
 * {@link #configure()}，即可把被依赖改写/替换的处理器恢复。
 *
 * <p>日志文件固定写入工作目录下的 {@code log/} 目录，文件名 {@code vcampus-server-yyyy-MM-dd.log}，
 * 按天滚动，默认保留最近 {@value #KEEP_DAYS} 天；目录可用系统属性
 * {@code -Dvcampus.log.dir=...} 覆盖。文件一律使用 UTF-8 编码，便于编辑器直接查看。
 */
public final class ServerLogging {
    /** 覆盖日志目录的系统属性名。 */
    public static final String LOG_DIR_PROPERTY = "vcampus.log.dir";

    private static final String DEFAULT_LOG_DIR = "log";
    private static final String FILE_PREFIX = "vcampus-server-";
    private static final int KEEP_DAYS = 14;

    private static ConsoleHandler consoleHandler;
    private static DailyFileHandler fileHandler;
    private static Path logDirectory;

    private ServerLogging() {
    }

    /** 安装（首次）或修正（后续）控制台与文件日志，保证 INFO 日志可见。 */
    public static synchronized void configure() {
        if (consoleHandler == null) {
            consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new OneLineFormatter());
            consoleHandler.setLevel(Level.INFO);
        }
        if (fileHandler == null) {
            logDirectory = resolveLogDirectory();
            fileHandler = DailyFileHandler.tryCreate(logDirectory, KEEP_DAYS, new OneLineFormatter());
        }

        Logger root = Logger.getLogger("");
        // 移除不属于本类的处理器：依赖可能替换或新增默认处理器，统一收敛为我们的格式与级别。
        for (Handler handler : root.getHandlers()) {
            if (handler != consoleHandler && handler != fileHandler) {
                root.removeHandler(handler);
            }
        }
        if (!contains(root, consoleHandler)) {
            root.addHandler(consoleHandler);
        }
        if (fileHandler != null && !contains(root, fileHandler)) {
            root.addHandler(fileHandler);
        }
        forceInfo(root);
    }

    /** 返回当前使用的日志目录；未成功初始化文件日志时可能为 {@code null}。 */
    public static Path getLogDirectory() {
        return logDirectory;
    }

    /** 返回当前实际写入的日志文件；未成功初始化时可能为 {@code null}。 */
    public static Path getLogFile() {
        DailyFileHandler handler = fileHandler;
        return handler == null ? null : handler.currentFile();
    }

    private static boolean contains(Logger logger, Handler handler) {
        for (Handler existing : logger.getHandlers()) {
            if (existing == handler) {
                return true;
            }
        }
        return false;
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

    private static Path resolveLogDirectory() {
        String configured = System.getProperty(LOG_DIR_PROPERTY);
        String value = configured == null || configured.trim().isEmpty()
                ? DEFAULT_LOG_DIR : configured.trim();
        return Paths.get(value).toAbsolutePath().normalize();
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
                java.io.StringWriter writer = new java.io.StringWriter();
                record.getThrown().printStackTrace(new java.io.PrintWriter(writer));
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

    /**
     * 按天滚动的文件处理器：文件名形如 {@code vcampus-server-2026-09-14.log}，
     * 跨天时自动切换新文件，并清理超过保留天数的旧文件。
     */
    private static final class DailyFileHandler extends Handler {
        private final Path directory;
        private final int keepDays;
        private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");

        private String currentDay;
        private Path currentFile;
        private Writer writer;

        private DailyFileHandler(Path directory, int keepDays) {
            this.directory = directory;
            this.keepDays = keepDays;
            setLevel(Level.INFO);
        }

        static DailyFileHandler tryCreate(Path directory, int keepDays, Formatter formatter) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                return null;
            }
            DailyFileHandler handler = new DailyFileHandler(directory, keepDays);
            handler.setFormatter(formatter);
            return handler;
        }

        @Override
        public synchronized void publish(LogRecord record) {
            if (record == null || !isLoggable(record)) {
                return;
            }
            try {
                rollIfNeeded();
                if (writer == null) {
                    return;
                }
                writer.write(getFormatter().format(record));
                writer.flush();
            } catch (IOException e) {
                // 文件日志失败不应影响业务，降级为仅控制台输出。
                reportError("写日志文件失败: " + e.getMessage(), e, java.util.logging.ErrorManager.WRITE_FAILURE);
            }
        }

        @Override
        public synchronized void flush() {
            try {
                if (writer != null) {
                    writer.flush();
                }
            } catch (IOException ignored) {
                // 忽略刷新失败。
            }
        }

        @Override
        public synchronized void close() {
            closeWriter();
        }

        synchronized Path currentFile() {
            return currentFile;
        }

        /** 跨天时切换到新文件，并清理过期文件。 */
        private void rollIfNeeded() throws IOException {
            String today = dayFormat.format(new Date());
            if (writer != null && today.equals(currentDay)) {
                return;
            }
            closeWriter();
            Path target = directory.resolve(FILE_PREFIX + today + ".log");
            writer = new OutputStreamWriter(
                    new FileOutputStream(target.toFile(), true), StandardCharsets.UTF_8);
            currentDay = today;
            currentFile = target;
            pruneOldFiles();
        }

        private void closeWriter() {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // 关闭失败无需处理。
                }
                writer = null;
            }
        }

        /** 只保留最近 {@code keepDays} 个日志文件。 */
        private void pruneOldFiles() {
            List<Path> candidates = new ArrayList<Path>();
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(directory, FILE_PREFIX + "*.log")) {
                for (Path path : stream) {
                    candidates.add(path);
                }
            } catch (IOException e) {
                return;
            }
            if (candidates.size() <= keepDays) {
                return;
            }
            Collections.sort(candidates, new Comparator<Path>() {
                @Override
                public int compare(Path left, Path right) {
                    return left.getFileName().toString().compareTo(right.getFileName().toString());
                }
            });
            int removeCount = candidates.size() - keepDays;
            for (int i = 0; i < removeCount; i++) {
                try {
                    Files.deleteIfExists(candidates.get(i));
                } catch (IOException ignored) {
                    // 删除失败不影响日志写入。
                }
            }
        }
    }
}
