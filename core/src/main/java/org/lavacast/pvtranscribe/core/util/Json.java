package org.lavacast.pvtranscribe.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON reader/writer for the messages of cloud speech APIs, so the plugin does not depend on a JSON
 * library that differs between server platforms. Objects become {@link Map}, arrays {@link List},
 * numbers {@link Double}.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static @Nullable Object parse(@NotNull String text) {
        Json p = new Json(text);
        p.ws();
        Object value = p.value();
        p.ws();
        return value;
    }

    /**
     * Follows a path of object keys / array indexes, e.g. {@code get(json, "channel", "alternatives", 0, "transcript")}.
     */
    public static @Nullable Object get(@Nullable Object node, Object... path) {
        Object cur = node;
        for (Object key : path) {
            if (key instanceof String k && cur instanceof Map<?, ?> m) cur = m.get(k);
            else if (key instanceof Integer idx && cur instanceof List<?> l) cur = idx < l.size() ? l.get(idx) : null;
            else return null;
        }
        return cur;
    }

    public static @NotNull String string(@Nullable Object node, Object... path) {
        Object v = get(node, path);
        return v instanceof String str ? str : "";
    }

    public static boolean bool(@Nullable Object node, Object... path) {
        return Boolean.TRUE.equals(get(node, path));
    }

    /**
     * Serializes maps, lists, strings, numbers, booleans and null.
     */
    public static @NotNull String write(@Nullable Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String str) {
            sb.append('"');
            for (int k = 0; k < str.length(); k++) {
                char c = str.charAt(k);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            sb.append('"');
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                write(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            sb.append(v);
        }
    }

    private Object value() {
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') {
            i++;
            return map;
        }
        while (true) {
            ws();
            String key = string();
            ws();
            i++; // ':'
            ws();
            map.put(key, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') {
            i++;
            return list;
        }
        while (true) {
            ws();
            list.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return list;
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        return Double.parseDouble(s.substring(start, i));
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }
}
