package com.github.ruediste.p2psync.ui.driver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer used by the development server. Keeping this
 * local avoids adding a serialization dependency for a developer-only feature.
 */
final class Json {
    private Json() {
    }

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd())
            throw new IllegalArgumentException("Unexpected trailing JSON content at " + parser.index);
        return value;
    }

    static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        write(value, builder);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (value == null)
            return Map.of();
        if (value instanceof Map<?, ?> map)
            return (Map<String, Object>) map;
        throw new IllegalArgumentException("Expected a JSON object");
    }

    private static void write(Object value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String text) {
            writeString(text, builder);
        } else if (value instanceof Boolean || value instanceof Number) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first)
                    builder.append(',');
                first = false;
                writeString(String.valueOf(entry.getKey()), builder);
                builder.append(':');
                write(entry.getValue(), builder);
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first)
                    builder.append(',');
                first = false;
                write(item, builder);
            }
            builder.append(']');
        } else if (value.getClass().isArray()) {
            builder.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (index > 0)
                    builder.append(',');
                write(java.lang.reflect.Array.get(value, index), builder);
            }
            builder.append(']');
        } else {
            writeString(String.valueOf(value), builder);
        }
    }

    private static void writeString(String text, StringBuilder builder) {
        builder.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20)
                        builder.append(String.format("\\u%04x", (int) character));
                    else
                        builder.append(character);
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean isAtEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(text.charAt(index)))
                index++;
        }

        private boolean isEnd() {
            return index >= text.length();
        }

        Object readValue() {
            skipWhitespace();
            if (isEnd())
                throw new IllegalArgumentException("Unexpected end of JSON input");
            char character = text.charAt(index);
            return switch (character) {
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
            expect('{');
            var result = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            var result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!isEnd()) {
                char character = text.charAt(index++);
                if (character == '"')
                    return builder.toString();
                if (character != '\\') {
                    builder.append(character);
                    continue;
                }
                if (isEnd())
                    throw new IllegalArgumentException("Unterminated JSON escape");
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length())
                            throw new IllegalArgumentException("Incomplete unicode escape");
                        builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape \\" + escaped);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object readNumber() {
            int start = index;
            if (peek('-'))
                index++;
            while (!isEnd() && Character.isDigit(text.charAt(index)))
                index++;
            boolean decimal = false;
            if (!isEnd() && text.charAt(index) == '.') {
                decimal = true;
                index++;
                while (!isEnd() && Character.isDigit(text.charAt(index)))
                    index++;
            }
            if (!isEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!isEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-'))
                    index++;
                while (!isEnd() && Character.isDigit(text.charAt(index)))
                    index++;
            }
            String number = text.substring(start, index);
            if (number.isEmpty() || number.equals("-"))
                throw new IllegalArgumentException("Invalid JSON number at " + start);
            return decimal ? Double.valueOf(number) : Long.valueOf(number);
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index))
                throw new IllegalArgumentException("Invalid JSON literal at " + index);
            index += literal.length();
            return value;
        }

        private boolean peek(char character) {
            return !isEnd() && text.charAt(index) == character;
        }

        private void expect(char character) {
            if (!peek(character))
                throw new IllegalArgumentException("Expected '" + character + "' at " + index);
            index++;
        }
    }
}
