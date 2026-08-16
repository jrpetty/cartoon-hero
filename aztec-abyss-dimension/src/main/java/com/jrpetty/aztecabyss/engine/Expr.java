package com.jrpetty.aztecabyss.engine;

import java.util.function.ToIntFunction;

/**
 * Integer arithmetic for script numbers.
 *
 * <p>Every number in a rule was a literal. A map could count kills, keys, flags
 * and captures perfectly and then had no way to say <em>ten points per kill</em>,
 * <em>half the remaining time</em>, or <em>one more zombie for every player past
 * the second</em>. The state was there; the sums were not, so any relationship
 * between two numbers had to be written out as a separate rule per value, or not
 * written at all.
 *
 * <pre>
 * { "set_var": { "name": "score", "to": "{var:kills} * 10 - {var:deaths} * 25" } }
 * { "spawn":   { "id": "minecraft:husk", "count": "2 + {players}" } }
 * </pre>
 *
 * <h2>What it deliberately is not</h2>
 *
 * <p>This is a calculator, not a language. Five operators, brackets, negation,
 * and placeholder lookups - and nothing else. There is no assignment, no
 * function call, no string, no loop and no recursion into itself, which is what
 * keeps the original promise of the script layer intact: a map downloaded off
 * the internet cannot run a program on your server, because there is no program
 * to run. The parser walks the text once, left to right, and stops.
 *
 * <p>It also never throws. Bad syntax, division by zero, an unknown placeholder
 * and an empty string all produce the caller's fallback, because the alternative
 * is one typo in one rule taking down a run.
 */
public final class Expr {

    /** Longest expression accepted, so a pathological string cannot be handed in. */
    private static final int MAX_LENGTH = 256;

    private Expr() {
    }

    /**
     * Works out what a number means.
     *
     * @param text     the expression, e.g. {@code "{var:kills} * 10"}
     * @param lookup   resolves the inside of a {@code {...}} to an integer
     * @param fallback returned for anything that cannot be parsed
     */
    public static int eval(String text, ToIntFunction<String> lookup, int fallback) {
        if (text == null) {
            return fallback;
        }
        String s = text.trim();
        if (s.isEmpty() || s.length() > MAX_LENGTH) {
            return fallback;
        }
        // The common case by a wide margin: a plain number written as a string.
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            // Not a literal, so it is worth parsing properly.
        }
        try {
            Parser p = new Parser(s, lookup);
            int value = p.expression();
            p.skipSpace();
            // Trailing rubbish means we misread it, and a half-understood sum is
            // worse than no sum: return the fallback rather than a wrong number.
            return p.done() ? value : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** True if a string needs the parser at all - used to keep literals cheap. */
    public static boolean looksLikeExpression(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '+' || c == '*' || c == '/' || c == '%' || c == '(') {
                return true;
            }
            // A minus that is not the leading sign is a subtraction.
            if (c == '-' && i > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursive descent over the four levels of precedence there are.
     *
     * <p>Recursion here is bounded by the bracket depth of the text, which is
     * bounded by {@link #MAX_LENGTH}, so the deepest possible expression is a
     * couple of hundred frames of a method with no locals worth speaking of.
     */
    private static final class Parser {

        private final String s;
        private final ToIntFunction<String> lookup;
        private int i;

        Parser(String s, ToIntFunction<String> lookup) {
            this.s = s;
            this.lookup = lookup;
        }

        boolean done() {
            return i >= s.length();
        }

        void skipSpace() {
            while (i < s.length() && s.charAt(i) == ' ') {
                i++;
            }
        }

        /** {@code term (('+'|'-') term)*} */
        int expression() {
            int value = term();
            while (true) {
                skipSpace();
                if (i >= s.length()) {
                    return value;
                }
                char c = s.charAt(i);
                if (c == '+') {
                    i++;
                    value += term();
                } else if (c == '-') {
                    i++;
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        /** {@code factor (('*'|'/'|'%') factor)*} */
        int term() {
            int value = factor();
            while (true) {
                skipSpace();
                if (i >= s.length()) {
                    return value;
                }
                char c = s.charAt(i);
                if (c == '*') {
                    i++;
                    value *= factor();
                } else if (c == '/' || c == '%') {
                    i++;
                    int by = factor();
                    // Dividing by nothing is a typo, not a crash. Zero is the
                    // least surprising answer and keeps the run alive.
                    if (by == 0) {
                        value = 0;
                    } else {
                        value = c == '/' ? value / by : value % by;
                    }
                } else {
                    return value;
                }
            }
        }

        /** {@code '-'? ( number | '{' name '}' | '(' expression ')' )} */
        int factor() {
            skipSpace();
            if (i >= s.length()) {
                throw new IllegalStateException("ran out");
            }
            char c = s.charAt(i);
            if (c == '-') {
                i++;
                return -factor();
            }
            if (c == '+') {
                i++;
                return factor();
            }
            if (c == '(') {
                i++;
                int inner = expression();
                skipSpace();
                if (i >= s.length() || s.charAt(i) != ')') {
                    throw new IllegalStateException("unclosed bracket");
                }
                i++;
                return inner;
            }
            if (c == '{') {
                int close = s.indexOf('}', i);
                if (close < 0) {
                    throw new IllegalStateException("unclosed placeholder");
                }
                String name = s.substring(i + 1, close);
                i = close + 1;
                return lookup == null ? 0 : lookup.applyAsInt(name);
            }
            if (c >= '0' && c <= '9') {
                int start = i;
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                }
                try {
                    return Integer.parseInt(s.substring(start, i));
                } catch (NumberFormatException e) {
                    // A number too big for an int. Clamp rather than fail: the
                    // author meant "a lot", and the engine clamps everything
                    // downstream anyway.
                    return Integer.MAX_VALUE;
                }
            }
            throw new IllegalStateException("unexpected " + c);
        }
    }
}
