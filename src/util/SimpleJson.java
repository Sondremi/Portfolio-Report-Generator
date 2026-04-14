package util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {

    private SimpleJson() {}

    public static Object parse(String json) {
        if (json == null) {
            return null;
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        return value;
    }

    private static final class Parser {
        private final String text;
        private int index = 0;

        private Parser(String text) {
            this.text = text;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                return null;
            }
            char c = text.charAt(index);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            LinkedHashMap<String, Object> object = new LinkedHashMap<>();
            index++; // {
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }

            while (index < text.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (peek(':')) index++;
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    break;
                }
                if (peek(',')) {
                    index++;
                }
            }
            return object;
        }

        private List<Object> parseArray() {
            ArrayList<Object> array = new ArrayList<>();
            index++; // [
            skipWhitespace();
            if (peek(']')) {
                index++;
                return array;
            }

            while (index < text.length()) {
                Object value = parseValue();
                array.add(value);
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    break;
                }
                if (peek(',')) {
                    index++;
                }
            }
            return array;
        }

        private String parseString() {
            if (!peek('"')) {
                return "";
            }
            index++; // opening quote
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && index < text.length()) {
                    char esc = text.charAt(index++);
                    switch (esc) {
                        case '"': out.append('"'); break;
                        case '\\': out.append('\\'); break;
                        case '/': out.append('/'); break;
                        case 'b': out.append('\b'); break;
                        case 'f': out.append('\f'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        case 'u':
                            if (index + 3 < text.length()) {
                                String hex = text.substring(index, index + 4);
                                try {
                                    out.append((char) Integer.parseInt(hex, 16));
                                } catch (NumberFormatException ignored) {
                                    // ignore malformed unicode escape
                                }
                                index += 4;
                            }
                            break;
                        default:
                            out.append(esc);
                            break;
                    }
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private Object parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            return Boolean.FALSE;
        }

        private Object parseNull() {
            if (text.startsWith("null", index)) {
                index += 4;
            }
            return null;
        }

        private Object parseNumber() {
            int start = index;
            while (index < text.length()) {
                char c = text.charAt(index);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    index++;
                } else {
                    break;
                }
            }
            String token = text.substring(start, index);
            if (token.isBlank()) {
                return 0.0;
            }
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }
    }
}
