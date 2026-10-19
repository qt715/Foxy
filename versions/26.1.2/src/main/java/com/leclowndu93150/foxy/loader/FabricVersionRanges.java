package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates Fabric version predicates ({@code ">=0.9.1- <=0.9.1"}, {@code "=2.2.2"}, {@code "*"}, ...)
 * into NeoForge/Maven version ranges.
 */
final class FabricVersionRanges {
    static final String ANY = "[0,)";

    private FabricVersionRanges() {}

    /**
     * Range used for a translated {@code depends} entry: only the lowest lower bound across all
     * alternatives is kept. Upper bounds are dropped on purpose: Fabric mods pin exact dependency
     * builds (Voxy pins one Sodium patch release), and NeoForge ports of those dependencies use
     * different version strings, so honouring the upper bound would reject working setups.
     */
    static String lowestLowerBound(JsonElement constraint) {
        String lowest = null;
        for (String alternative : alternatives(constraint)) {
            Bounds bounds = Bounds.parse(alternative);
            if (bounds == null || bounds.lower == null) return ANY;
            if (lowest == null || compare(bounds.lower, lowest) < 0) lowest = bounds.lower;
        }
        return lowest == null ? ANY : "[" + lowest + ",)";
    }

    /** One exact Maven range per alternative, used for {@code breaks}; unparseable alternatives are skipped. */
    static List<String> exactRanges(JsonElement constraint) {
        List<String> ranges = new ArrayList<>();
        for (String alternative : alternatives(constraint)) {
            Bounds bounds = Bounds.parse(alternative);
            if (bounds != null) ranges.add(bounds.toMavenRange());
        }
        return ranges;
    }

    private static List<String> alternatives(JsonElement constraint) {
        List<String> result = new ArrayList<>();
        if (constraint == null) return result;
        if (constraint.isJsonPrimitive()) {
            result.add(constraint.getAsString().strip());
        } else if (constraint.isJsonArray()) {
            for (JsonElement element : constraint.getAsJsonArray()) {
                if (element.isJsonPrimitive()) result.add(element.getAsString().strip());
            }
        }
        return result;
    }

    private record Bounds(@Nullable String lower, boolean lowerInclusive, @Nullable String upper, boolean upperInclusive) {
        @Nullable
        static Bounds parse(String predicate) {
            if (predicate.isEmpty() || predicate.equals("*")) return new Bounds(null, true, null, false);
            String lower = null;
            boolean lowerInclusive = true;
            String upper = null;
            boolean upperInclusive = false;
            for (String term : predicate.split("\\s+")) {
                String op;
                if (term.startsWith(">=") || term.startsWith("<=")) {
                    op = term.substring(0, 2);
                } else if (term.startsWith(">") || term.startsWith("<") || term.startsWith("=")
                        || term.startsWith("~") || term.startsWith("^")) {
                    op = term.substring(0, 1);
                } else {
                    op = "=";
                }
                String version = clean(term.substring(term.startsWith(op) ? op.length() : 0));
                if (version.isEmpty() || version.equals("*")) continue;
                if (version.endsWith(".x") || version.endsWith(".X") || version.endsWith(".*")) {
                    op = "~";
                    version = version.substring(0, version.length() - 2);
                }
                switch (op) {
                    case ">=" -> { lower = version; lowerInclusive = true; }
                    case ">" -> { lower = version; lowerInclusive = false; }
                    case "<=" -> { upper = version; upperInclusive = true; }
                    case "<" -> { upper = version; upperInclusive = false; }
                    case "~" -> { lower = version; lowerInclusive = true; upper = bump(version, 1); upperInclusive = false; }
                    case "^" -> { lower = version; lowerInclusive = true; upper = bump(version, 0); upperInclusive = false; }
                    default -> { lower = version; lowerInclusive = true; upper = version; upperInclusive = true; }
                }
            }
            return new Bounds(lower, lowerInclusive, upper, upperInclusive);
        }

        String toMavenRange() {
            if (lower != null && lower.equals(upper) && lowerInclusive && upperInclusive) return "[" + lower + "]";
            return (lower == null ? "(" : lowerInclusive ? "[" : "(")
                    + (lower == null ? "" : lower) + ","
                    + (upper == null ? "" : upper)
                    + (upper == null ? ")" : upperInclusive ? "]" : ")");
        }
    }

    /** Fabric marks "include pre-releases" with a trailing '-'; Maven ranges have no such notion. */
    private static String clean(String version) {
        String v = version.strip();
        while (v.endsWith("-")) v = v.substring(0, v.length() - 1);
        return v;
    }

    /** Smallest version above every version sharing the first {@code index + 1} components. */
    private static String bump(String version, int index) {
        String[] parts = version.split("[.+-]");
        if (parts.length <= index) index = parts.length - 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < index; i++) sb.append(parts[i]).append('.');
        try {
            sb.append(Integer.parseInt(parts[index]) + 1);
        } catch (NumberFormatException e) {
            return null;
        }
        return sb.toString();
    }

    static int compare(String left, String right) {
        String[] a = left.split("[.+-]");
        String[] b = right.split("[.+-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            int c;
            try {
                c = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
            } catch (NumberFormatException e) {
                c = x.compareToIgnoreCase(y);
            }
            if (c != 0) return c;
        }
        return 0;
    }
}
