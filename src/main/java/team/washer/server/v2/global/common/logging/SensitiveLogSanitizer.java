package team.washer.server.v2.global.common.logging;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 운영 로그에 포함될 수 있는 인증정보와 개인정보를 마스킹합니다. */
public final class SensitiveLogSanitizer {

    private static final Pattern WEBHOOK_URL_PATTERN = Pattern
            .compile("(?i)(https?://[^\\s/]+/api(?:/v\\d+)?/webhooks/\\d+/)[^\\s,;}\\]]+");
    private static final Pattern WEBHOOK_PATH_PATTERN = Pattern
            .compile("(?i)(/api(?:/v\\d+)?/webhooks/\\d+/)[^\\s/?#]+");
    private static final String ESCAPED_QUOTE = Pattern.quote("\\\"");
    private static final Pattern ESCAPED_SENSITIVE_VALUE_PATTERN = Pattern.compile("(?i)(" + ESCAPED_QUOTE
            + "(?:access_token|refresh_token|smartthings_token|password|token|authorization_code|code|client_secret|client_id|fcm_token|webhook_url)"
            + ESCAPED_QUOTE + "\\s*[:=]\\s*" + ESCAPED_QUOTE + ")(.*?)(" + ESCAPED_QUOTE + ")");
    private static final Pattern DUPLICATE_ENTRY_PATTERN = Pattern.compile("(?i)(Duplicate entry\\s+)(['\"])(.*?)\\2");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)Bearer\\s+[^\\r\\n,;}\\]]+");
    private static final Pattern AUTHORIZATION_VALUE_PATTERN = Pattern
            .compile("(?i)([\\\"']?authorization[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\r\\n,;}\\]]+)");
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)((?<![A-Za-z0-9_])[\\\"']?(?:access_token|refresh_token|smartthings_token|password|token|authorization_code|code|client_secret|client_id|fcm_token|webhook_url)[\\\"']?)(\\s*[:=]\\s*)(?!\\[REDACTED\\])(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}\\]]+)");
    private static final Pattern SENSITIVE_UNQUOTED_VALUE_PATTERN = Pattern.compile(
            "(?i)((?<![A-Za-z0-9_])[\\\"']?(?:access_token|refresh_token|smartthings_token|password|token|authorization_code|code|client_secret|client_id|fcm_token|webhook_url)[\\\"']?\\s*[:=]\\s*)(?![\\\"'])([^\\r\\n,;}\\]]+?)(?=\\s+(?:[A-Za-z_][A-Za-z0-9_-]*\\s*[:=]|[\\{\\[])|[\\r\\n,;}\\]]|$)");

    private SensitiveLogSanitizer() {
    }

    public static String sanitize(final String value) {
        var sanitized = WEBHOOK_URL_PATTERN.matcher(value).replaceAll("$1[REDACTED]");
        sanitized = replaceDuplicateEntry(sanitized);
        sanitized = replaceEscapedSensitiveValues(sanitized);
        sanitized = replaceSensitiveValues(sanitized, AUTHORIZATION_VALUE_PATTERN, false);
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = replaceSensitiveValues(sanitized, SENSITIVE_UNQUOTED_VALUE_PATTERN, false);
        return replaceSensitiveValues(sanitized, SENSITIVE_VALUE_PATTERN, true);
    }

    public static String sanitizeEndpoint(final String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "unknown";
        }

        final String withoutQuery = stripQueryAndFragment(rawUrl);
        if (withoutQuery.startsWith("/")) {
            return sanitizeWebhookPath(withoutQuery);
        }

        try {
            final URI uri = new URI(withoutQuery);
            final String authority = uri.getRawAuthority();
            if (uri.getScheme() == null || authority == null) {
                return "unknown";
            }
            final int userInfoSeparator = authority.lastIndexOf('@');
            final String safeAuthority = userInfoSeparator >= 0
                    ? authority.substring(userInfoSeparator + 1)
                    : authority;
            final String path = uri.getRawPath();
            return sanitizeWebhookPath(
                    uri.getScheme() + "://" + safeAuthority + (path == null || path.isBlank() ? "/" : path));
        } catch (URISyntaxException e) {
            return "unknown";
        }
    }

    private static String stripQueryAndFragment(final String value) {
        final int queryIndex = value.indexOf('?');
        final int fragmentIndex = value.indexOf('#');
        int endIndex = value.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        return value.substring(0, endIndex);
    }

    private static String sanitizeWebhookPath(final String value) {
        return WEBHOOK_PATH_PATTERN.matcher(value).replaceAll("$1[REDACTED]");
    }

    private static String replaceDuplicateEntry(final String value) {
        return DUPLICATE_ENTRY_PATTERN.matcher(value).replaceAll("$1$2[REDACTED]$2");
    }

    private static String replaceEscapedSensitiveValues(final String value) {
        final var matcher = ESCAPED_SENSITIVE_VALUE_PATTERN.matcher(value);
        final var result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]" + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
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
