package team.washer.server.v2.global.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 운영 로그에 포함될 수 있는 인증정보와 개인정보를 마스킹합니다. */
public final class SensitiveLogSanitizer {

    private static final Pattern WEBHOOK_URL_PATTERN = Pattern
            .compile("(?i)(https?://[^\\s/]+/api/webhooks/\\d+/)[^\\s,;}\\]]+");
    private static final Pattern DUPLICATE_ENTRY_PATTERN = Pattern.compile("(?i)(Duplicate entry\\s+)(['\"])(.*?)\\2");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)Bearer\\s+[^\\r\\n,;}\\]]+");
    private static final Pattern AUTHORIZATION_VALUE_PATTERN = Pattern
            .compile("(?i)([\\\"']?authorization[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\r\\n,;}\\]]+)");
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)([\\\"']?(?:access_token|refresh_token|smartthings_token|password|token)[\\\"']?)(\\s*[:=]\\s*)(?!\\[REDACTED\\])(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}\\]]+)");
    private static final Pattern SENSITIVE_UNQUOTED_VALUE_PATTERN = Pattern.compile(
            "(?i)([\\\"']?(?:access_token|refresh_token|smartthings_token|password|token)[\\\"']?\\s*[:=]\\s*)(?![\\\"'])([^\\r\\n,;}\\]]+?)(?=\\s+(?:[A-Za-z_][A-Za-z0-9_-]*\\s*[:=]|[\\{\\[])|[\\r\\n,;}\\]]|$)");

    private SensitiveLogSanitizer() {
    }

    public static String sanitize(final String value) {
        var sanitized = WEBHOOK_URL_PATTERN.matcher(value.replace("\\\"", "\"")).replaceAll("$1[REDACTED]");
        sanitized = replaceDuplicateEntry(sanitized);
        sanitized = replaceSensitiveValues(sanitized, AUTHORIZATION_VALUE_PATTERN, false);
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = replaceSensitiveValues(sanitized, SENSITIVE_UNQUOTED_VALUE_PATTERN, false);
        return replaceSensitiveValues(sanitized, SENSITIVE_VALUE_PATTERN, true);
    }

    private static String replaceDuplicateEntry(final String value) {
        return DUPLICATE_ENTRY_PATTERN.matcher(value).replaceAll("$1$2[REDACTED]$2");
    }

    private static String replaceSensitiveValues(final String value,
            final Pattern pattern,
            final boolean hasSeparateKeyAndSeparatorGroups) {
        final var matcher = pattern.matcher(value);
        final var result = new StringBuffer();
        while (matcher.find()) {
            final var prefix = hasSeparateKeyAndSeparatorGroups
                    ? matcher.group(1) + matcher.group(2)
                    : matcher.group(1);
            final var matchedValue = matcher.group(matcher.groupCount());
            final var replacement = hasSeparateKeyAndSeparatorGroups && isQuoted(matchedValue)
                    ? prefix + matchedValue.substring(0, 1) + "[REDACTED]"
                            + matchedValue.substring(matchedValue.length() - 1)
                    : prefix + "[REDACTED]";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isQuoted(final String value) {
        if (value.length() < 2) {
            return false;
        }
        final var first = value.charAt(0);
        final var last = value.charAt(value.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'');
    }
}
