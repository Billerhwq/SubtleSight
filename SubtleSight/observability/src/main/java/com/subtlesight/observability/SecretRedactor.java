package com.subtlesight.observability;

import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final Pattern BEARER=Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+",Pattern.MULTILINE);
    private static final Pattern KEYS=Pattern.compile("(?i)(api[_-]?key|token|password|cookie|secret)(\\s*[=:]\\s*)[\"']?[^\\s,;\"']+");
    private static final Pattern OPENAI=Pattern.compile("\\bsk-[A-Za-z0-9_-]{12,}\\b");
    private SecretRedactor(){}
    public static String redact(String value){if(value==null)return null;String x=BEARER.matcher(value).replaceAll("$1***");x=KEYS.matcher(x).replaceAll("$1$2***");return OPENAI.matcher(x).replaceAll("***");}
}

