package com.derevna230.comborush.match;

/** Команды матча: террористы (несут бомбу) и спецназ (защищает точки и обезвреживает) */
public enum Team {
    T("T", "§6", "Террористы"), CT("CT", "§9", "Спецназ");

    public final String tag, color, title;

    Team(String tag, String color, String title) { this.tag = tag; this.color = color; this.title = title; }

    public Team opposite() { return this == T ? CT : T; }
    public String coloredTag() { return color + "§l" + tag + "§r"; }

    public static Team parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) { case "t", "т" -> T; case "ct", "кт" -> CT; default -> null; };
    }
}
