package fun.gaslt.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tiny dependency-free JSON object writer. Only what this service needs:
 * string/number/boolean/null values and nested {@link Json} objects, serialised
 * in insertion order with correct escaping. Keeping this in-house avoids pulling
 * a JSON library into the service jar.
 */
public final class Json {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    public static Json obj() {
        return new Json();
    }

    /** Add a field. {@code value} may be a String, Number, Boolean, Json, or null. */
    public Json put(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            append(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Json || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
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
        return sb.toString();
    }
}
