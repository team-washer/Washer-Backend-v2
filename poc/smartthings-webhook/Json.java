import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PoC 전용 최소 JSON 파서·직렬화기. 외부 의존성 없이 JDK만으로 실행하기 위해 둔다.
 *
 * <p>
 * 객체는 {@link LinkedHashMap}, 배열은 {@link List}, 숫자는 {@link Double} 또는 {@link Long}, 그 외는 String/Boolean/null로
 * 변환한다.
 */
final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    static Object parse(String src) {
        var parser = new Json(src);
        parser.skipWhitespace();
        var value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != src.length()) {
            throw new IllegalArgumentException("JSON 뒤에 불필요한 문자가 있습니다. pos=" + parser.pos);
        }
        return value;
    }

    /** 점 경로("a.b.0.c")로 중첩 값을 꺼낸다. 중간 값이 없으면 null을 반환한다. */
    static Object at(Object root, String path) {
        var current = root;
        for (var key : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (current instanceof List<?> list && key.chars().allMatch(Character::isDigit)) {
                var index = Integer.parseInt(key);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    static String str(Object root, String path) {
        var value = at(root, path);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object root, String path) {
        return at(root, path) instanceof List<?> list ? (List<Object>) list : List.of();
    }

    static String write(Object value) {
        var sb = new StringBuilder();
        writeTo(sb, value);
        return sb.toString();
    }

    private static void writeTo(StringBuilder sb, Object value) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> writeString(sb, s);
            case Number n -> sb.append(n);
            case Boolean b -> sb.append(b);
            case Map<?, ?> map -> {
                sb.append('{');
                var first = true;
                for (var entry : map.entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeString(sb, String.valueOf(entry.getKey()));
                    sb.append(':');
                    writeTo(sb, entry.getValue());
                }
                sb.append('}');
            }
            case List<?> list -> {
                sb.append('[');
                for (var i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    writeTo(sb, list.get(i));
                }
                sb.append(']');
            }
            default -> writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private Object readValue() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("JSON이 예상보다 일찍 끝났습니다.");
        }
        return switch (src.charAt(pos)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        var map = new LinkedHashMap<String, Object>();
        pos++;
        skipWhitespace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            var key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> readArray() {
        var list = new ArrayList<Object>();
        pos++;
        skipWhitespace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String readString() {
        expect('"');
        var sb = new StringBuilder();
        while (pos < src.length()) {
            var c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            var escaped = src.charAt(pos++);
            switch (escaped) {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(escaped);
            }
        }
        throw new IllegalArgumentException("닫히지 않은 JSON 문자열입니다.");
    }

    private Object readLiteral(String literal, Object value) {
        if (!src.startsWith(literal, pos)) {
            throw new IllegalArgumentException("알 수 없는 JSON 토큰입니다. pos=" + pos);
        }
        pos += literal.length();
        return value;
    }

    private Number readNumber() {
        var start = pos;
        while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        var token = src.substring(start, pos);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("알 수 없는 JSON 토큰입니다. pos=" + start);
        }
        return token.contains(".") || token.contains("e") || token.contains("E")
                ? Double.parseDouble(token)
                : Long.parseLong(token);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(char c) {
        return pos < src.length() && src.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw new IllegalArgumentException("'" + c + "'가 필요합니다. pos=" + pos);
        }
        pos++;
    }
}
