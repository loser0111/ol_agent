package com.wyq.agent.online_agent.domain.service.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 文件类工具的内部支撑：路径解析、二进制检测、目录遍历、glob→正则。
 * 语义对齐 local-agent 的 filetools.go；由 FileService 直接持有。
 */
public class FileToolSupport {

    // ===== 各类上限（对齐 Go 版）=====
    public static final long MAX_READ_FILE_BYTES = 2L << 20;    // 单文件读取上限 2MB
    public static final int DEFAULT_READ_LIMIT = 300;         // read_file 默认返回行数（与工具描述、AGENT.md 保持一致）
    public static final int MAX_READ_LINES = 2000;             // read_file 单次返回行数上限
    public static final long MAX_WRITE_FILE_BYTES = 2L << 20;   // 单文件写入上限 2MB
    public static final int MAX_GLOB_RESULTS = 200;             // glob 结果上限
    public static final int MAX_GREP_FILES = 100;             // grep 扫描文件数上限
    public static final long MAX_GREP_FILE_BYTES = 2L << 20;    // grep 单文件扫描上限
    public static final int MAX_GREP_RESULTS = 200;             // grep 结果上限
    public static final int MAX_DIR_ENTRIES = 500;              // list_dir 条目上限

    /** 遍历时跳过的噪音目录（噪音大且几乎不会被有意搜索） */
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", ".idea", ".vscode", "__pycache__", ".venv", "venv", "target");

    // ===== 路径解析 =====

    /** 基准目录：进程工作目录（后续可扩展为会话工作区目录） */
    public Path baseDir() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    /**
     * 把参数路径解析成绝对路径：支持 ~、相对路径（基于 baseDir）、绝对路径；
     * 空路径报错，首尾引号自动剥离。
     */
    public Path resolve(String raw) {
        String p = raw == null ? "" : raw.trim();
        if (p.length() >= 2
                && ((p.startsWith("\"") && p.endsWith("\"")) || (p.startsWith("'") && p.endsWith("'")))) {
            p = p.substring(1, p.length() - 1);
        }
        if (p.isEmpty()) {
            throw new IllegalArgumentException("path 参数不能为空");
        }
        if (p.equals("~")) {
            p = System.getProperty("user.home");
        } else if (p.startsWith("~/") || p.startsWith("~\\")) {
            p = System.getProperty("user.home") + p.substring(1);
        }
        Path path = Paths.get(p);
        if (!path.isAbsolute()) {
            path = baseDir().resolve(path);
        }
        return path.normalize();
    }

    /** 返回相对工作区的展示路径（工作区外原样返回绝对路径） */
    public String rel(Path path) {
        Path base = baseDir();
        try {
            Path r = base.relativize(path);
            if (!r.toString().startsWith("..")) {
                return r.toString().replace('\\', '/');
            }
        } catch (IllegalArgumentException ignored) {
            // 跨盘符等 relativize 失败，走绝对路径
        }
        return path.toString().replace('\\', '/');
    }

    // ===== 文件读取辅助 =====

    /** 粗判二进制：前 8KB 含 NUL，或控制字符占比过高。字节按无符号比较，避免 UTF-8 多字节字符被误判。 */
    public boolean looksBinary(byte[] data) {
        int headLen = Math.min(data.length, 8192);
        for (int i = 0; i < headLen; i++) {
            if ((data[i] & 0xFF) == 0) {
                return true;
            }
        }
        if (headLen == 0) {
            return false;
        }
        int ctrl = 0;
        for (int i = 0; i < headLen; i++) {
            int b = data[i] & 0xFF;   // 无符号化：0x80-0xFF（UTF-8 多字节）不参与控制字符统计
            if (b < 0x09 || (b > 0x0d && b < 0x20)) {
                ctrl++;
            }
        }
        return ctrl * 100 / headLen > 10;
    }

    /** 读取文本文件：大小/类型检查 + UTF-8 解码，返回完整内容 */
    public String readTextSafe(Path path) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("文件不存在: " + rel(path));
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException(rel(path) + " 是目录，请用 list_dir");
        }
        long size = Files.size(path);
        if (size > MAX_READ_FILE_BYTES) {
            throw new IllegalArgumentException("文件过大（" + size + " 字节，上限 "
                    + MAX_READ_FILE_BYTES + "），请用 grep 定位或分片读取");
        }
        byte[] data = Files.readAllBytes(path);
        if (looksBinary(data)) {
            throw new IllegalArgumentException("疑似二进制文件，未返回内容: " + rel(path));
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /** 按行切分（保留末行无换行的情形；结尾换行不算一行） */
    public List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        for (String line : content.split("\n", -1)) {
            lines.add(line);
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    // ===== 目录遍历 =====

    /** 遍历目录树，跳过噪音目录；visitor 返回 false 表示提前结束 */
    public void walk(Path root, Function<Path, Boolean> visitor) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (visitor.apply(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    return FileVisitResult.TERMINATE;
                }
            });
        } catch (IOException ignored) {
            // 单个条目出错不中断整体遍历
        }
    }

    public static boolean isSkipDir(String name) {
        return SKIP_DIRS.contains(name);
    }

    // ===== glob → 正则 =====

    /**
     * 把 glob 模式编译成正则：
     * ** 跨目录（可匹配零个或多个路径段）；* 段内任意字符（不含 /）；? 段内单个字符。
     */
    public Pattern globToRegex(String glob) {
        glob = glob.trim().replace('\\', '/');
        if (glob.startsWith("./")) {
            glob = glob.substring(2);
        }
        if (glob.isEmpty()) {
            throw new IllegalArgumentException("模式不能为空");
        }
        String[] segs = glob.split("/");
        StringBuilder b = new StringBuilder("^");
        for (int i = 0; i < segs.length; i++) {
            String seg = segs[i];
            boolean last = i == segs.length - 1;
            if (seg.equals("**")) {
                if (last) {
                    b.append(".*");
                } else {
                    b.append("(?:[^/]+/)*");
                }
                continue;
            }
            b.append(segmentRegex(seg));
            if (!last) {
                b.append("/");
            }
        }
        b.append("$");
        return Pattern.compile(b.toString());
    }

    private String segmentRegex(String seg) {
        StringBuilder b = new StringBuilder();
        for (char c : seg.toCharArray()) {
            switch (c) {
                case '*' -> b.append("[^/]*");
                case '?' -> b.append("[^/]");
                default -> b.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return b.toString();
    }
}
