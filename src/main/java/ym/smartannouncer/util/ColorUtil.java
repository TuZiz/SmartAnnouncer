package ym.smartannouncer.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern AMPERSAND_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern ANGLE_HEX = Pattern.compile("(?i)<#([0-9a-f]{6})>");

    private ColorUtil() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', applyHexColors(input));
    }

    public static String strip(String input) {
        return ChatColor.stripColor(input == null ? "" : input);
    }

    private static String applyHexColors(String input) {
        String withAngleHex = replaceHex(ANGLE_HEX, input);
        return replaceHex(AMPERSAND_HEX, withAngleHex);
    }

    private static String replaceHex(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String toLegacyHex(String hex) {
        StringBuilder builder = new StringBuilder(14);
        builder.append(ChatColor.COLOR_CHAR).append('x');
        for (int index = 0; index < hex.length(); index++) {
            builder.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(hex.charAt(index)));
        }
        return builder.toString();
    }
}
