package com.wyq.agent.online_agent.domain.service.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文件类基础工具的统一服务：read_file / write_file / edit_file / glob / grep / list_dir。
 * 供大模型（协调者 agent）通过 ToolCallbacks.from(this) 调用。
 * 语义对齐 local-agent 的 filetools.go（路径解析、上限常量、只读/只写边界见 FileToolSupport）。
 */
@Component
public class FileService {

    private final FileToolSupport support = new FileToolSupport();

    // =====================================================================
    // read_file：读取工作区文本文件内容（带行号），支持 offset/limit 分片
    // =====================================================================

    @Tool(description = "读取工作区内某个文本文件的内容，返回带行号的文本。大文件可用 offset/limit 分片读取；查找内容请优先用 grep")
    public String readFile(
            @ToolParam(required = true, description = "要读取的文件路径（相对工作区目录或绝对路径）") String path,
            @ToolParam(description = "起始行号（从 1 开始，可选）") Integer offset,
            @ToolParam(description = "最多读取行数（默认 2000）") Integer limit) throws IOException {

        Path filePath = support.resolve(path);
        String content = support.readTextSafe(filePath);
        List<String> lines = support.splitLines(content);
        int total = lines.size();

        int start = (offset == null || offset < 1) ? 1 : offset;
        int maxLines = (limit == null || limit < 1) ? FileToolSupport.DEFAULT_READ_LIMIT : limit;
        if (start > total) {
            return support.rel(filePath) + " 共 " + total + " 行，起始行 " + start + " 超出范围";
        }

        int end = start - 1 + maxLines;
        if (end > total) {
            end = total;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(support.rel(filePath)).append("（共 ").append(total).append(" 行，显示 ")
          .append(start).append("-").append(end).append("）\n");
        for (int i = start - 1; i < end; i++) {
            sb.append(i + 1).append("\t").append(lines.get(i)).append("\n");
        }
        if (end < total) {
            sb.append("…（还有 ").append(total - end)
              .append(" 行未显示，可用 offset=").append(end + 1).append(" 继续）\n");
        }
        return sb.toString();
    }

    // =====================================================================
    // write_file：写入（新建或整体覆盖）一个文本文件
    // =====================================================================

    @Tool(description = "写入（新建或整体覆盖）一个文本文件。局部修改请用 edit_file，避免覆盖他人改动")
    public String writeFile(
            @ToolParam(required = true, description = "要写入的文件路径（相对工作区目录或绝对路径）") String path,
            @ToolParam(required = true, description = "完整文件内容（会覆盖原有内容）") String content) throws IOException {

        Path filePath = support.resolve(path);
        if (Files.isDirectory(filePath)) {
            return "错误：" + support.rel(filePath) + " 是目录";
        }
        if (content.length() > FileToolSupport.MAX_WRITE_FILE_BYTES) {
            return "错误：内容过大（" + content.length() + " 字节，上限 "
                    + FileToolSupport.MAX_WRITE_FILE_BYTES + "）";
        }

        String before = "";
        if (Files.exists(filePath)) {
            try {
                before = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));

        String verdict = before.isEmpty() ? "已新建" : "已覆盖";
        List<String> lines = support.splitLines(content);
        return verdict + " " + support.rel(filePath)
                + "（" + lines.size() + " 行，" + content.length() + " 字节）";
    }

    // =====================================================================
    // edit_file：对文件做精确字符串替换（字面匹配，非正则）
    // =====================================================================

    @Tool(description = "对文件做精确字符串替换。old_string 必须在文件中唯一出现（否则用 replace_all 或补充上下文）；不确定文件内容时先用 read_file 查看")
    public String editFile(
            @ToolParam(required = true, description = "要修改的文件路径") String path,
            @ToolParam(required = true, description = "被替换的原文（需与文件内容完全一致，含缩进）") String oldString,
            @ToolParam(required = true, description = "替换后的新文本") String newString,
            @ToolParam(description = "为 true 时替换所有匹配处，默认 false（要求唯一匹配）") Boolean replaceAll) throws IOException {

        Path filePath = support.resolve(path);
        if (oldString.isEmpty()) {
            return "错误：old_string 不能为空";
        }
        if (oldString.equals(newString)) {
            return "错误：old_string 与 new_string 相同，无需修改";
        }
        boolean all = replaceAll != null && replaceAll;

        String before = support.readTextSafe(filePath);
        int count = countOccurrences(before, oldString);
        if (count == 0) {
            return "错误：在 " + support.rel(filePath) + " 中未找到 old_string"
                    + "（注意缩进与空行需完全一致；建议先 read_file 确认原文）";
        }
        if (count > 1 && !all) {
            return "错误：old_string 在 " + support.rel(filePath) + " 中出现 " + count
                    + " 次，不唯一；请补充上下文使其唯一，或设置 replace_all=true";
        }

        String after = all
                ? before.replace(oldString, newString)
                : before.replaceFirst(Pattern.quote(oldString), newString);
        if (after.length() > FileToolSupport.MAX_WRITE_FILE_BYTES) {
            return "错误：修改后内容过大（" + after.length() + " 字节，上限 "
                    + FileToolSupport.MAX_WRITE_FILE_BYTES + "）";
        }
        Files.write(filePath, after.getBytes(StandardCharsets.UTF_8));
        return "已更新 " + support.rel(filePath) + "（替换 " + count + " 处）";
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // =====================================================================
    // glob：按 glob 模式查找文件（支持 ** 跨目录），按修改时间倒序返回
    // =====================================================================

    @Tool(description = "按 glob 模式查找文件（支持 ** 跨目录），按修改时间倒序返回。查找文件内容请用 grep")
    public String glob(
            @ToolParam(required = true, description = "文件匹配模式，如 *.java、src/**/*.java、**/*Test.java") String pattern,
            @ToolParam(description = "搜索起点目录（默认工作区根目录）") String path) throws IOException {

        if (pattern == null || pattern.trim().isEmpty()) {
            return "错误：pattern 参数是必需的";
        }
        Pattern regex = support.globToRegex(pattern);

        final Path base;
        if (path != null && !path.trim().isEmpty()) {
            base = support.resolve(path);
        } else {
            base = support.baseDir();
        }
        if (!Files.isDirectory(base)) {
            return "错误：目录不存在: " + support.rel(base);
        }

        List<Path> hits = new ArrayList<>();
        support.walk(base, p -> {
            // 匹配相对搜索起点的路径（正斜杠），与 Go 版语义一致
            String relPath = base.relativize(p).toString().replace('\\', '/');
            if (regex.matcher(relPath).matches()) {
                hits.add(p);
            }
            return hits.size() < FileToolSupport.MAX_GLOB_RESULTS * 4;
        });

        hits.sort(Comparator.comparingLong(this::lastModifiedMillis).reversed());

        boolean truncated = false;
        List<Path> display = hits;
        if (hits.size() > FileToolSupport.MAX_GLOB_RESULTS) {
            display = new ArrayList<>(hits.subList(0, FileToolSupport.MAX_GLOB_RESULTS));
            truncated = true;
        }
        if (display.isEmpty()) {
            return "没有匹配 " + pattern + " 的文件（搜索起点：" + support.rel(base) + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("匹配 ").append(pattern).append(" 的文件（").append(display.size()).append(" 个）:\n");
        for (Path h : display) {
            sb.append(support.rel(h)).append("\n");
        }
        if (truncated) {
            sb.append("…（结果过多，仅返回前 ").append(FileToolSupport.MAX_GLOB_RESULTS).append(" 个）\n");
        }
        return sb.toString();
    }

    private long lastModifiedMillis(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    // =====================================================================
    // grep：按正则搜索文件内容（只读、不解释 shell 语法）
    // =====================================================================

    @Tool(description = "在工作区内按正则搜索文件内容。直接搜内容比用 shell 跑 grep 更安全（只读、不解释 shell 语法）")
    public String grep(
            @ToolParam(required = true, description = "正则表达式") String pattern,
            @ToolParam(description = "搜索起点目录或文件（默认工作区根目录）") String path,
            @ToolParam(description = "只搜索匹配该模式的文件，如 *.java") String glob,
            @ToolParam(description = "files_with_matches（默认）/ content / count") String outputMode,
            @ToolParam(description = "忽略大小写") Boolean caseInsensitive,
            @ToolParam(description = "结果上限（默认 200）") Integer headLimit) throws IOException {

        if (pattern == null || pattern.trim().isEmpty()) {
            return "错误：pattern 参数是必需的";
        }
        String mode = (outputMode == null || outputMode.trim().isEmpty())
                ? "files_with_matches" : outputMode.trim();
        if (!List.of("files_with_matches", "content", "count").contains(mode)) {
            return "错误：output_mode 只能是 files_with_matches / content / count，收到 " + mode;
        }
        int limit = (headLimit == null || headLimit <= 0)
                ? FileToolSupport.MAX_GREP_RESULTS : headLimit;

        String expr = pattern;
        if (Boolean.TRUE.equals(caseInsensitive)) {
            expr = "(?i)" + expr;
        }
        Pattern regex;
        try {
            regex = Pattern.compile(expr);
        } catch (Exception e) {
            return "错误：正则表达式无效: " + e.getMessage();
        }

        final Pattern nameFilter;
        if (glob != null && !glob.trim().isEmpty()) {
            try {
                nameFilter = support.globToRegex(glob);
            } catch (Exception e) {
                return "错误：glob 模式无效: " + e.getMessage();
            }
        } else {
            nameFilter = null;
        }

        final Path base;
        if (path != null && !path.trim().isEmpty()) {
            base = support.resolve(path);
        } else {
            base = support.baseDir();
        }
        if (!Files.exists(base)) {
            return "错误：路径不存在: " + support.rel(base);
        }

        List<String> fileHits = new ArrayList<>();
        List<String> countHits = new ArrayList<>();
        List<String> contentHits = new ArrayList<>();
        int[] scanned = {0};
        int[] fileHitCount = {0};

        java.util.function.Consumer<Path> handleFile = p -> {
            try {
                if (nameFilter != null) {
                    String relPath = base.relativize(p).toString().replace('\\', '/');
                    if (!nameFilter.matcher(relPath).matches()) {
                        return;
                    }
                }
                if (Files.size(p) > FileToolSupport.MAX_GREP_FILE_BYTES) {
                    return;
                }
                byte[] data = Files.readAllBytes(p);
                if (support.looksBinary(data)) {
                    return;
                }
                scanned[0]++;
                String rel = support.rel(p);
                List<String> lines = support.splitLines(new String(data, StandardCharsets.UTF_8));
                int n = 0;
                for (int i = 0; i < lines.size(); i++) {
                    if (!regex.matcher(lines.get(i)).find()) {
                        continue;
                    }
                    n++;
                    if (mode.equals("content") && contentHits.size() < limit) {
                        contentHits.add(rel + ":" + (i + 1) + ":" + lines.get(i).trim());
                    }
                }
                if (n > 0) {
                    fileHits.add(rel);
                    countHits.add(rel + ": " + n);
                    fileHitCount[0]++;
                }
            } catch (IOException | RuntimeException ignored) {
                // 单个文件出错跳过，不中断整体搜索
            }
        };

        if (Files.isRegularFile(base)) {
            handleFile.accept(base);
        } else {
            support.walk(base, p -> {
                if (!Files.isDirectory(p)) {
                    handleFile.accept(p);
                }
                return scanned[0] < FileToolSupport.MAX_GREP_FILES
                        && fileHitCount[0] < limit * 4;
            });
        }

        switch (mode) {
            case "content": {
                if (contentHits.isEmpty()) {
                    return "没有匹配 \"" + pattern + "\" 的内容（起始：" + support.rel(base) + "）";
                }
                StringBuilder sb = new StringBuilder();
                for (String m : contentHits) {
                    sb.append(m).append("\n");
                }
                if (contentHits.size() >= limit) {
                    sb.append("…（结果达到上限 ").append(limit).append("）\n");
                }
                return sb.toString();
            }
            case "count": {
                if (countHits.isEmpty()) {
                    return "没有匹配 \"" + pattern + "\" 的内容";
                }
                return String.join("\n", countHits) + "\n";
            }
            default: {
                if (fileHits.isEmpty()) {
                    return "没有匹配 \"" + pattern + "\" 的文件（起始：" + support.rel(base) + "）";
                }
                return "匹配 \"" + pattern + "\" 的文件（" + fileHits.size() + " 个）:\n"
                        + String.join("\n", fileHits) + "\n";
            }
        }
    }

    // =====================================================================
    // list_dir：列出目录内容（目录在前、文件在后，含大小）
    // =====================================================================

    @Tool(description = "列出目录内容（目录在前、文件在后，含大小）。比 ls 更适合被模型直接使用")
    public String listDir(
            @ToolParam(description = "要列出的目录（默认工作区根目录）") String path) throws IOException {

        Path dir = support.baseDir();
        if (path != null && !path.trim().isEmpty()) {
            dir = support.resolve(path);
        }
        if (!Files.isDirectory(dir)) {
            return "错误：目录不存在: " + support.rel(dir);
        }

        List<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(entries::add);
        }

        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (Path e : entries) {
            if (Files.isDirectory(e)) {
                if (!FileToolSupport.isSkipDir(e.getFileName().toString())) {
                    dirs.add(e);
                }
            } else {
                files.add(e);
            }
        }
        dirs.sort(Comparator.comparing(p -> p.getFileName().toString()));
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));

        StringBuilder sb = new StringBuilder();
        sb.append(support.rel(dir)).append("/（").append(dirs.size())
          .append(" 个目录，").append(files.size()).append(" 个文件）\n");
        int shown = 0;
        for (Path p : dirs) {
            if (shown >= FileToolSupport.MAX_DIR_ENTRIES) {
                sb.append("…（达到上限 ").append(FileToolSupport.MAX_DIR_ENTRIES).append(" 条）\n");
                return sb.toString();
            }
            sb.append(p.getFileName()).append("/\n");
            shown++;
        }
        for (Path p : files) {
            if (shown >= FileToolSupport.MAX_DIR_ENTRIES) {
                sb.append("…（达到上限 ").append(FileToolSupport.MAX_DIR_ENTRIES).append(" 条）\n");
                return sb.toString();
            }
            long size = 0;
            try {
                size = Files.size(p);
            } catch (IOException ignored) {
            }
            sb.append(p.getFileName()).append("\t").append(size).append("\n");
            shown++;
        }
        return sb.toString();
    }
}
