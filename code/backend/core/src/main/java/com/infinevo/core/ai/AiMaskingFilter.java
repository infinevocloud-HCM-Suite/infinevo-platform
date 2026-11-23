package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Hides what the AI helper never needs (W-114.4, spec "Masking"): PAN, Aadhaar, bank account and IFSC, UAN, ESI and
 * passport numbers, passwords and tokens. Applied to the user's text before it is stored or sent, to every tool result,
 * to the history sent again, and to the model's answer before it is stored or returned.
 *
 * <p>Two passes, in this order. Pass 1, JSON only: a value whose key names a sensitive field becomes
 * {@code "[hidden]"}. Pass 2, every string: five patterns. Amounts, leave balances, dates, times, names and email
 * addresses pass through.
 */
@Component
public class AiMaskingFilter {

    static final String HIDDEN = "[hidden]";

    /** Pass 1: a key containing one of these, once lower-cased with {@code _} and {@code -} removed. */
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "aadhaar",
            "aadhar",
            "accountnumber",
            "bankaccount",
            "ifsc",
            "esinumber",
            "passport",
            "password",
            "secret",
            "token",
            "apikey");

    /** Pass 1: short names that sit inside ordinary words ({@code company}, {@code quantity}), so whole key only. */
    private static final Set<String> SENSITIVE_WHOLE_KEYS =
            Set.of("pan", "pannumber", "panno", "uan", "uannumber", "uanno");

    /** Pass 2, in the spec's order: tokens first, so a JWT's digits are not taken for an account number. */
    private static final List<Map.Entry<Pattern, String>> PATTERNS = List.of(
            Map.entry(Pattern.compile("\\bBearer\\s+[A-Za-z0-9._~+/=-]{20,}"), "[TOKEN]"),
            Map.entry(
                    Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), "[TOKEN]"),
            Map.entry(Pattern.compile("\\b[A-Za-z]{5}[0-9]{4}[A-Za-z]\\b"), "[PAN]"),
            Map.entry(Pattern.compile("\\b[A-Za-z]{4}0[A-Za-z0-9]{6}\\b"), "[IFSC]"),
            Map.entry(Pattern.compile("\\b[2-9][0-9]{3}[ -]?[0-9]{4}[ -]?[0-9]{4}\\b"), "[AADHAAR]"),
            Map.entry(Pattern.compile("\\b[0-9]{9,18}\\b"), "[NUMBER]"));

    /** Pass 2 on free text. {@code null} stays {@code null}. */
    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<Pattern, String> rule : PATTERNS) {
            out = rule.getKey().matcher(out).replaceAll(rule.getValue());
        }
        return out;
    }

    /** Both passes on a copy of {@code node}; the argument is not changed. {@code null} stays {@code null}. */
    public JsonNode mask(JsonNode node) {
        if (node == null) {
            return null;
        }
        return maskNode(node);
    }

    private JsonNode maskNode(JsonNode node) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (node.isObject()) {
            ObjectNode out = f.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (isSensitiveKey(field.getKey()) && !value.isNull()) {
                    out.put(field.getKey(), HIDDEN);
                } else {
                    out.set(field.getKey(), maskNode(value));
                }
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = f.arrayNode();
            node.forEach(item -> out.add(maskNode(item)));
            return out;
        }
        if (node.isTextual()) {
            return f.textNode(mask(node.textValue()));
        }
        if (node.isNumber()) {
            // A long account number sent as a JSON number is still a long number.
            String text = node.asText();
            String masked = mask(text);
            return masked.equals(text) ? node.deepCopy() : f.textNode(masked);
        }
        return node.deepCopy();
    }

    static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (SENSITIVE_WHOLE_KEYS.contains(k)) {
            return true;
        }
        for (String part : SENSITIVE_KEY_PARTS) {
            if (k.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
