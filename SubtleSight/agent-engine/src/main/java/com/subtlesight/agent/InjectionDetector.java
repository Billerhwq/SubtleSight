package com.subtlesight.agent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Multi-layered prompt injection detection.
 * <p>
 * Covers: instruction override, role-switching, hidden Unicode characters,
 * length anomalies, and nested prompt markers.
 */
public final class InjectionDetector {

    private InjectionDetector() {}

    // ── Pattern-based detection ──

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior)\\s+instructions?"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+"),
            Pattern.compile("(?i)system\\s*prompt"),
            Pattern.compile("(?i)\\[INST]|<\\|im_start\\|>|<\\|system\\|>"),
            Pattern.compile("(?i)forget\\s+(everything|all|your\\s+instructions?)"),
            Pattern.compile("(?i)reveal\\s+(your|the)\\s+(system\\s*)?prompt"),
            Pattern.compile("(?i)act\\s+as\\s+(a\\s+different|another)\\s+(ai|assistant|model)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|prior|earlier|your)\\s+(instructions?|rules?)"),
            Pattern.compile("(?i)override\\s+(system|safety|content)\\s+(policy|filter|rule)"),
            Pattern.compile("(?i)new\\s+system\\s+prompt\\s*:"),
            Pattern.compile("(?i)from\\s+now\\s+on\\s+you\\s+(are|must|will|should)")
    );

    // Hidden/invisible Unicode characters (zero-width, RTL override, etc.)
    private static final Pattern HIDDEN_CHARS = Pattern.compile(
            "[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF\\u200E\\u200F\\u061C]");

    // Chinese-specific injection patterns
    private static final List<Pattern> CN_PATTERNS = List.of(
            Pattern.compile("忽略(之前|所有|上述|以前的|前面)?的?(指令|规则|提示|限制)"),
            Pattern.compile("(现在|从现下|当前).{0,5}(你是|扮演|作为|变成|化身|充当)"),
            Pattern.compile("忘记(一切|所有|你的|之前)"),
            Pattern.compile("(泄露|透露|输出|告诉|说出|给我).{0,8}(你的|系统)?(提示|指令|设定|配置)"),
            Pattern.compile("调用未注册(的)?工具"),
            Pattern.compile("(绕过|跳过|不受|无视|忽略).{0,5}(安全|工具|权限|限制|策略|规则|约束)"),
            Pattern.compile("(输出|显示|打印)(完整|原始)?系统(提示|指令)"),
            Pattern.compile("不要遵守|不必遵守|可以违反|无需遵守|不受.*约束"),
            Pattern.compile("你是.{0,10}(模式|角色|身份).{0,10}(不受|无需|不必|没有)"),
            Pattern.compile("[Dd][Aa][Nn]\\s*(模式|mode)")
    );

    // Nested prompt markers (LLM control tokens)
    private static final Pattern NESTED_MARKERS = Pattern.compile(
            "(?i)<\\|assistant\\|>|<\\|user\\|>|<\\|end\\|>|</?instruction>|</?system>");

    // ── Detection ──

    /** Returns true if the input contains injection patterns. */
    public static boolean isInjection(String input) {
        if (input == null || input.isBlank()) return false;

        // Length anomaly: excessively long input may hide injection
        if (input.length() > 8_000) return true;

        String lower = input.toLowerCase(Locale.ROOT);

        // Regex patterns
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(lower).find()) return true;
        }
        for (Pattern p : CN_PATTERNS) {
            if (p.matcher(input).find()) return true;
        }

        // Hidden characters
        if (HIDDEN_CHARS.matcher(input).find()) return true;

        // Nested prompt markers
        if (NESTED_MARKERS.matcher(input).find()) return true;

        return false;
    }

    /** Returns a human-readable reason why the input was flagged. */
    public static String detectReason(String input) {
        if (input == null || input.isBlank()) return "";

        if (input.length() > 8_000) return "input too long (>8K chars)";

        if (HIDDEN_CHARS.matcher(input).find()) return "hidden/invisible Unicode characters";
        if (NESTED_MARKERS.matcher(input).find()) return "nested prompt markers detected";

        String lower = input.toLowerCase(Locale.ROOT);
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(lower).find()) return "injection pattern: " + p.pattern();
        }
        for (Pattern p : CN_PATTERNS) {
            if (p.matcher(input).find()) return "injection pattern (CN): " + p.pattern();
        }
        return "unknown injection";
    }
}
