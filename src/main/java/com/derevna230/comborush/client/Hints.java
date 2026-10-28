package com.derevna230.comborush.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Подсказки для новичка: при первом открытии экрана мода — карточка с советами (клик или Enter — «Понятно»),
 * а в первом событии и после первых итогов — реплики «Подсказки» внизу экрана.
 * Что уже показано, хранится в config/combo-rush-hints.json; /crhud hints — показать всё заново.
 */
public final class Hints {
    private Hints() {}

    private record Card(String title, List<String> lines) {}

    private static final Map<Class<? extends Screen>, String> SCREENS = new LinkedHashMap<>();
    private static final Map<String, Card> CARDS = new HashMap<>();
    private static final Set<String> SEEN = new HashSet<>();
    private static boolean loaded = false;
    private static String showing = null;
    private static long shownAt = 0;
    private static String lastPhase = null;
    private static boolean hadResult = false;

    static {
        card("shop", ShopScreen.class, "МАГАЗИН",
            "Магазин открыт между событиями: трать монеты, пока идёт подготовка.",
            "Усилители — твои джокеры: меняют, за что и сколько очков ты получаешь.",
            "Слотов мало. Продавай слабые карты, чтобы освободить место.",
            "Негативная версия даёт лишний слот, но с минусом — «без игры» или «без очков».",
            "«ПЕРЕКРУТ» обновляет витрину за монеты. Наведи на карту — увидишь описание.");
        card("species", SpeciesScreen.class, "СУЩЕСТВА",
            "Существо — бонусы и штрафы на весь забег.",
            "Сменить его можно только до первого события или после сброса прогресса.",
            "Сбросы открывают новых существ и повышают ранг текущего.",
            "Листай стрелками или колесом, Enter — выбрать.");
        card("essence", EssenceShopScreen.class, "ЭССЕНЦИЯ",
            "Эссенция — память, которая переживает сброс прогресса.",
            "Кейсы дают броню, оружие, питомцев и декор. Чем дороже кейс — тем реже добыча.",
            "Комплект брони даёт бонус за каждую надетую часть, а полный — свою способность.",
            "Лишнее можно разобрать обратно в эссенцию.");
        card("journal", JournalScreen.class, "СЦЕНАРИЙ",
            "Это журнал сюжета: главы слева, сцены справа.",
            "«ОБЩЕЕ» — задание на весь сервер, награду получат все. «ЛИЧНОЕ» — только твоё.",
            "Трекер слева сверху ведёт к текущей цели. Открыть журнал — клавиша J.");
        card("skate", SkateScreen.class, "СКЕЙТ",
            "Улучшения хранятся в самом скейте — его можно передать другу вместе с прокачкой.",
            "Скорость оставляет след полихрома, прыжок поднимает на 3 блока, планирование — держи пробел в воздухе.");
    }

    private static void card(String key, Class<? extends Screen> cls, String title, String... lines) {
        SCREENS.put(cls, key);
        CARDS.put(key, new Card(title, List.of(lines)));
    }

    // ---------------------------------------------------------------- хранение
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("combo-rush-hints.json"); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file())) {
                JsonObject o = new Gson().fromJson(Files.readString(file(), StandardCharsets.UTF_8), JsonObject.class);
                if (o != null && o.has("seen")) o.getAsJsonArray("seen").forEach(e -> SEEN.add(e.getAsString()));
            }
        } catch (Exception ignored) { }
    }

    private static void markSeen(String key) {
        SEEN.add(key);
        save();
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            SEEN.forEach(a::add);
            o.add("seen", a);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    /** Показать все подсказки заново */
    public static void resetAll() { load(); SEEN.clear(); save(); }

    // ---------------------------------------------------------------- экраны
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            String key = SCREENS.get(screen.getClass());
            if (key == null) return;
            load();
            if (SEEN.contains(key)) return;
            showing = key;
            shownAt = System.nanoTime();
            ScreenEvents.afterRender(screen).register((s, c, mx, my, delta) -> render(c, s));
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
                if (showing == null) return true;
                if ((System.nanoTime() - shownAt) / 1e9 < 0.4) return false;   // случайный клик сразу после открытия
                dismiss();
                return false;
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key2, scancode, modifiers) -> {
                if (showing == null) return true;
                if (key2 == GLFW.GLFW_KEY_ENTER || key2 == GLFW.GLFW_KEY_SPACE || key2 == GLFW.GLFW_KEY_KP_ENTER) { dismiss(); return false; }
                return true;
            });
            ScreenEvents.remove(screen).register(s -> showing = null);
        });
    }

    private static void dismiss() {
        if (showing == null) return;
        markSeen(showing);
        showing = null;
        HudModel.play(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f);
    }

    private static void render(DrawContext c, Screen screen) {
        if (showing == null) return;
        Card card = CARDS.get(showing);
        if (card == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int W = screen.width, H = screen.height;
        float t = (System.nanoTime() - shownAt) / 1e9f;
        float a = Math.min(1f, t * 5f);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, 0, 500);
        c.fill(0, 0, W, H, StoryHud.argb(0x05030C, 0.55f * a));

        int w = Math.min(320, W - 30);
        List<String> wrapped = new ArrayList<>();
        for (String l : card.lines()) {
            List<String> ws = StoryHud.wrap(tr, l, (int) ((w - 30) / 0.85f));
            for (int i = 0; i < ws.size(); i++) wrapped.add((i == 0 ? "• " : "  ") + ws.get(i));
        }
        int h = 36 + wrapped.size() * 10 + 22;
        int x = (W - w) / 2, y = (H - h) / 2;
        float pop = 1 + 0.06f * (float) Math.max(0, 1 - t * 4) * (float) Math.sin(t * 20);
        m.push();
        m.translate(W / 2f, H / 2f, 0);
        m.scale(pop, pop, 1);
        m.translate(-W / 2f, -H / 2f, 0);
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, StoryHud.argb(0x000000, a));
        c.fillGradient(x, y, x + w, y + h, StoryHud.argb(0x2E2150, a), StoryHud.argb(0x151026, a));
        c.fill(x, y, x + w, y + 2, StoryHud.argb(0xFFC94A, a));
        StoryHud.text(c, tr, "ПОДСКАЗКА", x + 10, y + 7, 0.6f, StoryHud.argb(0xFFC94A, a));
        StoryHud.text(c, tr, card.title(), x + 10, y + 15, 1.3f, StoryHud.argb(0xFFFFFF, a));
        int ly = y + 34;
        for (String l : wrapped) { StoryHud.text(c, tr, l, x + 12, ly, 0.85f, StoryHud.argb(0xE6E0FF, a)); ly += 10; }
        // кнопка «Понятно»
        int bw = 90, bx = x + (w - bw) / 2, by = y + h - 20;
        boolean blink = (System.nanoTime() / 400_000_000L) % 2 == 0;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 15, StoryHud.argb(0x000000, a));
        c.fillGradient(bx, by, bx + bw, by + 14, StoryHud.argb(blink ? 0x8CF0A6 : 0x6FE08A, a), StoryHud.argb(0x1E8A4A, a));
        String ok = "ПОНЯТНО";
        StoryHud.text(c, tr, ok, bx + bw / 2f - tr.getWidth(ok) * 0.45f, by + 3.5f, 0.9f, StoryHud.argb(0xFFFFFF, a));
        m.pop();
        m.pop();
    }

    // ---------------------------------------------------------------- HUD: первое событие и первые итоги
    public static void tick() {
        if (MinecraftClient.getInstance().player == null) { lastPhase = null; return; }
        load();
        String phase = HudModel.phase;
        if (lastPhase != null && !lastPhase.equals(phase) && "EVENT".equals(phase) && !SEEN.contains("hud_event")) {
            markSeen("hud_event");
            say(List.of(
                "Событие началось! За 5 минут набери как можно больше очков.",
                "Очки = фишки × множитель. Фишки дают действия: добыча, бой, урожай.",
                "Множитель растёт от усилителей и комбо — делай разные действия подряд, не останавливаясь.",
                "Справа видно, какие карты сработали и сколько дали."));
        }
        lastPhase = phase;
        boolean has = HudModel.result != null;
        if (has && !hadResult && !SEEN.contains("hud_result")) {
            markSeen("hud_result");
            say(List.of(
                "Это итоги: медаль зависит от очков, а монеты — от медали.",
                "Монеты тратятся в магазине (/shop) до начала следующего события.",
                "Пороги медалей растут с каждым событием — усиливай колоду!"));
        }
        hadResult = has;
    }

    private static void say(List<String> lines) {
        JsonObject o = new JsonObject();
        o.addProperty("name", "Подсказка");
        o.addProperty("color", 0x8EF0A0);
        JsonArray arr = new JsonArray();
        lines.forEach(arr::add);
        o.add("lines", arr);
        StoryHud.readSay(o);
    }
}
