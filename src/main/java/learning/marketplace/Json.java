package learning.marketplace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.done()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) { this.text = text; }
        private boolean done() { return pos == text.length(); }
        private void space() { while (!done() && Character.isWhitespace(text.charAt(pos))) pos++; }

        private Object value() {
            space();
            if (done()) throw new IllegalArgumentException("Missing JSON value");
            return switch (text.charAt(pos)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            space();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                space();
                expect(':');
                map.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return map;
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++;
            space();
            if (take(']')) return list;
            do {
                list.add(value());
                space();
            } while (take(','));
            expect(']');
            return list;
        }

        private String string() {
            expect('\"');
            StringBuilder out = new StringBuilder();
            while (!done()) {
                char c = text.charAt(pos++);
                if (c == '\"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (done()) throw new IllegalArgumentException("Bad JSON escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '\"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad JSON escape");
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object number() {
            int start = pos;
            while (!done() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
            String raw = text.substring(start, pos);
            return raw.contains(".") || raw.contains("e") || raw.contains("E")
                    ? Double.parseDouble(raw) : Long.parseLong(raw);
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, pos)) throw new IllegalArgumentException("Bad JSON literal");
            pos += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!done() && text.charAt(pos) == expected) { pos++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }
}
