package com.tongji.cst3492.jentor.index;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenization shared by indexing and querying.
 *
 * <p>The rules intentionally mirror tools/build_index.py. If one side changes,
 * rebuild the index and update the other side together.</p>
 */
public final class Tokens {
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_]+|[\\u4e00-\\u9fff]");
    private static final Pattern CJK_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern CAMEL_PATTERN =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern LETTER_DIGIT_PATTERN =
            Pattern.compile("[A-Za-z]+|[0-9]+");

    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an",
            "and", "any", "are", "as", "at", "be", "because", "been", "before",
            "being", "below", "between", "both", "but", "by", "can", "could", "did",
            "do", "does", "doing", "down", "during", "each", "few", "for", "from",
            "further", "had", "has", "have", "having", "he", "her", "here", "hers",
            "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is",
            "it", "its", "itself", "just", "me", "more", "most", "my", "myself", "no",
            "nor", "not", "now", "of", "off", "on", "once", "only", "or", "other",
            "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
            "so", "some", "such", "than", "that", "the", "their", "theirs", "them",
            "themselves", "then", "there", "these", "they", "this", "those", "through",
            "to", "too", "under", "until", "up", "very", "vs", "was", "we", "were",
            "what", "when", "where", "which", "while", "who", "whom", "why", "will",
            "with", "would", "you", "your", "yours", "yourself", "yourselves"));

    private static final Set<String> CODE_KEYWORDS = Set.copyOf(List.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "record", "sealed", "permits", "yield", "var"));

    private Tokens() {
    }

    public static List<String> tokenize(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        List<String> rawTokens = new ArrayList<>();
        String previousCjk = null;
        while (matcher.find()) {
            String raw = matcher.group();
            if (CJK_PATTERN.matcher(raw).matches()) {
                rawTokens.add(raw);
                if (previousCjk != null) {
                    rawTokens.add(previousCjk + raw);
                }
                previousCjk = raw;
                continue;
            }

            previousCjk = null;
            rawTokens.add(raw.toLowerCase(Locale.ROOT));

            String[] camelParts = CAMEL_PATTERN.split(raw);
            if (camelParts.length > 1) {
                for (String part : camelParts) {
                    if (!part.isEmpty()) {
                        rawTokens.add(part.toLowerCase(Locale.ROOT));
                    }
                }
            }

            String[] underscoreParts = raw.split("_");
            if (underscoreParts.length > 1) {
                for (String part : underscoreParts) {
                    if (!part.isEmpty()) {
                        rawTokens.add(part.toLowerCase(Locale.ROOT));
                    }
                }
            }

            Matcher letterDigitMatcher = LETTER_DIGIT_PATTERN.matcher(raw);
            List<String> letterDigitParts = new ArrayList<>();
            while (letterDigitMatcher.find()) {
                letterDigitParts.add(letterDigitMatcher.group());
            }
            if (letterDigitParts.size() > 1) {
                for (String part : letterDigitParts) {
                    rawTokens.add(part.toLowerCase(Locale.ROOT));
                }
            }
        }

        List<String> result = new ArrayList<>();
        for (String token : rawTokens) {
            boolean keep = !STOPWORDS.contains(token) || CODE_KEYWORDS.contains(token);
            boolean singleLatinLetter = token.length() == 1
                    && Character.isLetter(token.charAt(0))
                    && !CJK_PATTERN.matcher(token).matches();
            if (keep && !singleLatinLetter) {
                result.add(stem(token));
            }
        }
        return result;
    }

    public static String stem(String token) {
        if (CODE_KEYWORDS.contains(token) || token.length() < 5 || !isAlphabetic(token)) {
            return token;
        }
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("es") && token.length() > 4) {
            String withoutEs = token.substring(0, token.length() - 2);
            if (withoutEs.endsWith("ss")
                    || withoutEs.endsWith("x")
                    || withoutEs.endsWith("ch")
                    || withoutEs.endsWith("sh")
                    || withoutEs.endsWith("z")) {
                return withoutEs;
            }
            return token.substring(0, token.length() - 1);
        }
        if (token.endsWith("s")
                && !token.endsWith("ss")
                && !token.endsWith("us")
                && !token.endsWith("is")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static boolean isAlphabetic(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isLetter(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static List<String> bigramKeys(List<String> tokens) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i + 1 < tokens.size(); i++) {
            keys.add(tokens.get(i) + "\u0000" + tokens.get(i + 1));
        }
        return keys;
    }

    public static String bigramKey(String first, String second) {
        return first + "\u0000" + second;
    }

    public static Set<String> tokenSet(String text) {
        return new HashSet<>(tokenize(text));
    }
}
