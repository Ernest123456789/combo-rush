package com.derevna230.comborush.sound;

import com.derevna230.comborush.ComboRush;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Свои звуки мода: музыка события, босс-блайнда и магазинов, стингеры, медали, очки, голос персонажей (assets/combo-rush/sounds) */
public final class ModSounds {
    private ModSounds() {}

    public static final SoundEvent MUSIC_EVENT = reg("music_event");
    public static final SoundEvent MUSIC_BOSS = reg("music_boss");
    public static final SoundEvent MUSIC_EVENT2 = reg("music_event2");
    public static final SoundEvent MUSIC_EVENT3 = reg("music_event3");
    public static final SoundEvent MUSIC_EVENT4 = reg("music_event4");
    public static final SoundEvent MUSIC_BOSS2 = reg("music_boss2");
    public static final SoundEvent MUSIC_EVENT5 = reg("music_event5");
    public static final SoundEvent MUSIC_EVENT6 = reg("music_event6");
    public static final SoundEvent MUSIC_EVENT7 = reg("music_event7");
    public static final SoundEvent MUSIC_EVENT8 = reg("music_event8");
    public static final SoundEvent MUSIC_EVENT9 = reg("music_event9");
    public static final SoundEvent MUSIC_EVENT10 = reg("music_event10");
    public static final SoundEvent MUSIC_EVENT11 = reg("music_event11");
    public static final SoundEvent MUSIC_EVENT12 = reg("music_event12");
    public static final SoundEvent MUSIC_EVENT13 = reg("music_event13");
    public static final SoundEvent MUSIC_EVENT14 = reg("music_event14");
    public static final SoundEvent MUSIC_EVENT15 = reg("music_event15");
    public static final SoundEvent MUSIC_EVENT16 = reg("music_event16");
    public static final SoundEvent MUSIC_EVENT17 = reg("music_event17");
    public static final SoundEvent MUSIC_EVENT18 = reg("music_event18");
    public static final SoundEvent MUSIC_EVENT19 = reg("music_event19");
    public static final SoundEvent MUSIC_EVENT20 = reg("music_event20");
    public static final SoundEvent MUSIC_BOSS3 = reg("music_boss3");
    public static final SoundEvent MUSIC_BOSS4 = reg("music_boss4");
    /** Темы события и босс-блайнда: выбираются «мешком» — пока не сыграют все, ни одна не повторяется */
    public static final SoundEvent[] EVENT_THEMES = {MUSIC_EVENT, MUSIC_EVENT2, MUSIC_EVENT3, MUSIC_EVENT4, MUSIC_EVENT5,
        MUSIC_EVENT6, MUSIC_EVENT7, MUSIC_EVENT8, MUSIC_EVENT9, MUSIC_EVENT10, MUSIC_EVENT11, MUSIC_EVENT12, MUSIC_EVENT13,
        MUSIC_EVENT14, MUSIC_EVENT15, MUSIC_EVENT16, MUSIC_EVENT17, MUSIC_EVENT18, MUSIC_EVENT19, MUSIC_EVENT20};
    public static final SoundEvent[] BOSS_THEMES = {MUSIC_BOSS, MUSIC_BOSS2, MUSIC_BOSS3, MUSIC_BOSS4};
    public static final SoundEvent MUSIC_SHOP1 = reg("music_shop1");
    public static final SoundEvent MUSIC_SHOP2 = reg("music_shop2");
    /** Спокойные темы магазинов: играют, пока открыт экран магазина вне события */
    public static final SoundEvent[] SHOP_THEMES = {MUSIC_SHOP1, MUSIC_SHOP2};
    public static final SoundEvent EVENT_START = reg("event_start");
    public static final SoundEvent EVENT_END = reg("event_end");
    public static final SoundEvent BOSS_START = reg("boss_start");
    public static final SoundEvent LAST_MINUTE = reg("last_minute");
    public static final SoundEvent TICK = reg("tick");
    public static final SoundEvent MEDAL_NONE = reg("medal_none");
    public static final SoundEvent MEDAL_BRONZE = reg("medal_bronze");
    public static final SoundEvent MEDAL_SILVER = reg("medal_silver");
    public static final SoundEvent MEDAL_GOLD = reg("medal_gold");
    public static final SoundEvent MEDAL_PLATINUM = reg("medal_platinum");
    public static final SoundEvent QUEST_DONE = reg("quest_done");
    public static final SoundEvent CHIPS = reg("chips");
    public static final SoundEvent MULT = reg("mult");
    public static final SoundEvent VOICE = reg("voice");
    public static final SoundEvent COIN = reg("coin");

    private static SoundEvent reg(String name) {
        Identifier id = Identifier.of(ComboRush.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Вызвать из onInitialize, чтобы звуки зарегистрировались до заморозки реестров */
    public static void init() { }
}
