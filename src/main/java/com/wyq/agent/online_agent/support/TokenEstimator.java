package com.wyq.agent.online_agent.support;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * token 估算工具（真实 BPE 计数）。
 *
 * 词表用 cl100k_base：与 Spring AI 自带的 {@code JTokkitTokenCountEstimator} 默认词表一致
 * （GPT-4o / o 系列同族）。DeepSeek 等兼容协议的词表不同，但量级接近，用于"预算判断"够用。
 *
 * 为什么不再用"ASCII 4 字符 = 1 token、中文 1 字符 = 1 token"的启发式：
 * 代码与中文混排下误差可达 2 倍，{@link com.wyq.agent.online_agent.consts.Constant#MAX_PROMPT_TOKENS}
 * 这类预算判断会失真。jtokkit 是 spring-ai-commons 的传递依赖（compile 作用域），不新增依赖。
 *
 * ⚠️ 一律使用 Ordinary 系列 API（countTokensOrdinary / encodeOrdinary）：
 * 它们把 {@code <|endoftext|>} 之类的特殊串当普通文本；而 countTokens/encode 遇到特殊串会抛
 * {@code UnsupportedOperationException}（已实测）。工具结果里出现这类字符串完全可能，
 * 不能让估算本身把对话打断。
 *
 * 用于两处：
 * 1. 工具结果截断（TruncatingToolCallback）——控制单条结果体积；
 * 2. 上下文压缩（ContextCompactor）——控制整段 prompt 体积。
 */
public final class TokenEstimator {

    /** cl100k_base 词表；registry 惰性加载，首次调用才真正解析词表数据 */
    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private TokenEstimator() {
    }

    /** 估算文本 token 数；null/空返回 0 */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokensOrdinary(text);
    }

    /** 估算一组消息文本的 token 总数（null 元素按 0 计） */
    public static int estimateAll(Iterable<String> texts) {
        if (texts == null) {
            return 0;
        }
        int total = 0;
        for (String t : texts) {
            total += estimate(t);
        }
        return total;
    }

    /**
     * 返回一个字符下标 idx，使 text[0, idx) 的估算 token 数不超过 budget。
     * 若整段都在预算内，返回 text.length()。
     */
    public static int prefixIndexForTokens(String text, int budget) {
        if (text == null || text.isEmpty() || budget <= 0) {
            return 0;
        }
        // 截断式编码：token 数够了就停止处理，大文本下比"逐字符累加 + 反复计数"快得多
        EncodingResult result = ENCODING.encodeOrdinary(text, budget);
        if (!result.isTruncated()) {
            return text.length();
        }
        // lastProcessedCharacterIndex 是"最后一个被处理字符的下标"（含），按 Java 字符（UTF-16）计
        // —— 已用 BMP 字符与代理对（emoji）交叉验证，故前缀为 [0, idx + 1)
        return Math.min(text.length(), result.getLastProcessedCharacterIndex() + 1);
    }

    /**
     * 在 [minIdx, maxIdx) 范围内找最后一个换行符位置 +1（即按行边界截断的下标）。
     * 找不到时返回 maxIdx（调用方自行决定是否接受"切断一行"）。
     */
    public static int lastLineBreakBefore(String text, int maxIdx, int minIdx) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int limit = Math.min(maxIdx, text.length());
        for (int i = limit - 1; i >= Math.max(minIdx, 1); i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return limit;
    }

    /** 粗略统计行数（用于摘要文案） */
    public static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        // 结尾换行不算独立一行
        return text.charAt(text.length() - 1) == '\n' ? lines - 1 : lines;
    }
}
