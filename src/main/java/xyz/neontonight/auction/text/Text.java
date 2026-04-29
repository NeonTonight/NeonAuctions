package xyz.neontonight.auction.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

public final class Text {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");

    private Text() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase();
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : hex.toCharArray()) {
                replacement.append('§').append(character);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> color(List<String> input) {
        List<String> output = new ArrayList<>(input.size());
        for (String line : input) {
            output.add(color(line));
        }
        return output;
    }

    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }
}
