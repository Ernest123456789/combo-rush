package com.derevna230.comborush.score;

public enum ScoreSource {
    KILL(10, "kills", "Убийства"), MINE(5, "ores", "Руда"), HARVEST(3, "crops", "Урожай"),
    SHOOT(8, "shots", "Выстрелы"),        // попадание из лука/арбалета/трезубца с дистанции 10+ блоков
    BREW(15, "brews", "Зелья"),           // забрал зелье из варочной стойки
    ENCHANT(15, "enchants", "Зачарование"), // зачаровал предмет
    BREED(10, "breeds", "Разведение"),    // развёл животных
    TRADE(8, "trades", "Торговля"),       // торговля с жителем
    FISH(7, "fish", "Рыбалка");           // поймал предмет на удочку

    public final double baseChips;
    /** ключ счётчика в статистике (kills → life_kills) */
    public final String statKey;
    public final String label;
    ScoreSource(double baseChips, String statKey, String label) {
        this.baseChips = baseChips; this.statKey = statKey; this.label = label;
    }
}
