package com.wyq.agent.online_agent.domain.service.tool.impl;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class ReadFileTool {
    /** 默认最多读取行数 */
    private static final int DEFAULT_READ_LIMIT = 200;
    @Tool(description = "读取工作区内某个文本文件的内容，返回带行号的文本。大文件可用 offset/limit 分片读取；查找内容请优先用 grep")
    public String readFile(
            @ToolParam(required = true, description = "要读取的文件路径（相对工作区目录或绝对路径）") String path,
            @ToolParam(description = "起始行号（从 1 开始，可选）") Integer offset,
            @ToolParam(description = "最多读取行数（默认 200）") Integer limit) throws IOException {
        // ===== 路径解析与校验 =====
        Path filePath = Paths.get(path).toAbsolutePath();
        if (!Files.exists(filePath)) {
            return "错误：文件不存在：" + path;
        }
        if (Files.isDirectory(filePath)) {
            return "错误：这是一个目录，不是文件：" + path;
        }
        if (!Files.isReadable(filePath)) {
            return "错误：文件不可读（权限不足）：" + path;
        }
        // ===== 参数归一 =====
        int start = (offset == null || offset < 1) ? 1 : offset;
        int maxLines = (limit == null || limit < 1) ? DEFAULT_READ_LIMIT : limit;
        // ===== 读取（UTF-8）=====
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        int total = lines.size();
        if (start > total) {
            return "错误：offset(" + start + ") 超出文件总行数（共 " + total + " 行）";
        }
        int end = Math.min(start + maxLines - 1, total);
        StringBuilder sb = new StringBuilder();
        sb.append("文件 ").append(filePath).append("（共 ").append(total).append(" 行），显示第 ")
                .append(start).append("-").append(end).append(" 行：\n");
        for (int i = start - 1; i < end; i++) {
            sb.append(String.format("%5d | %s%n", i + 1, lines.get(i)));
        }
        return sb.toString();
    }
}