package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.ShopActionPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Небольшое окно с вопросом и двумя кнопками: приглашение от NPC лавки («Играть?») и покупка ключа к кейсу.
 * Подтверждение уходит на сервер действием action / c; сервер всё перепроверяет.
 */
public class PromptScreen extends Screen {
    private final String title, sub;
    private final int accent;
    private final List<String> lines;
    private final String yes, no, action, arg;
    private final int argA;
    private final boolean yesEnabled;
    private long openAt = System.nanoTime();

    private PromptScreen(String title, String sub, int accent, List<String> lines, String yes, boolean yesEnabled, String no, String action, int argA, String arg) {
        super(Text.literal(title));
        this.title = title; this.sub = sub; this.accent = accent; this.lines = lines;
        this.yes = yes; this.yesEnabled = yesEnabled; this.no = no; this.action = action; this.argA = argA; this.arg = arg;
    }

    /** Лавка Лобби: игра, NPC и текст приглашения */
    public static void lobbyGame(JsonObject o) {
        List<String> lines = new ArrayList<>();
        JsonArray arr = o.getAsJsonArray("lines");
        if (arr != null) arr.forEach(e -> lines.add(e.getAsString()));
        boolean open = o.get("open").getAsBoolean();
        int party = o.has("party") ? o.get("party").getAsInt() : 1;
        if (open && party > 1) { lines.add(""); lines.add("§7Группа из " + party + " чел. пойдёт с вами, если все в Лобби."); }
        int[] colors = {0xFF6A5A, 0x5AA8FF, 0x7CE06A, 0xFFD24A};
        int idx = o.get("idx").getAsInt();
        MinecraftClient.getInstance().setScreen(new PromptScreen(o.get("game").getAsString().toUpperCase(), o.get("npc").getAsString(),
            colors[Math.floorMod(idx, 4)], lines, open ? "Играть" : "Скоро", open, "Не сейчас", "lobby_play", idx, ""));
        HudModel.play(SoundEvents.ENTITY_VILLAGER_YES, 1.2f, 0.5f);
    }

    /** Кейс с ключом: подтверждение покупки за эссенцию */
    public static void caseKey(JsonObject o) {
        int price = o.get("price").getAsInt(), have = o.get("essence").getAsInt();
        List<String> lines = new ArrayList<>();
        lines.add("Чтобы открыть кейс, нужен ключ.");
        lines.add("Цена ключа: §d" + price + " эссенции§r.");
        lines.add("У вас: §f" + have + " эссенции§r" + (have < price ? " §c— не хватает " + (price - have) : ""));
        MinecraftClient.getInstance().setScreen(new PromptScreen(o.get("name").getAsString(), "Нужен ключ", 0xC77DFF, lines,
            "Купить ключ и открыть", have >= price, "Отмена", "case_key_buy", 0, o.get("case").getAsString()));
        HudModel.play(SoundEvents.BLOCK_CHEST_LOCKED, 1.0f, 1.3f);
    }

    @Override
    protected void init() {
        int w = 230, x = (width - w) / 2, y = (height + 120) / 2 - 30;
        ButtonWidget ok = ButtonWidget.builder(Text.literal(yes), b -> {
            if (ClientPlayNetworking.canSend(ShopActionPayload.ID)) ClientPlayNetworking.send(new ShopActionPayload(action, argA, 0, arg));
            close();
        }).dimensions(x, y, 150, 20).build();
        ok.active = yesEnabled;
        addDrawableChild(ok);
        addDrawableChild(ButtonWidget.builder(Text.literal(no), b -> close()).dimensions(x + 156, y, 74, 20).build());
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fillGradient(0, 0, width, height, 0x90000000, 0xC0000000);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        float appear = Math.min(1f, (System.nanoTime() - openAt) / 1e6f / 250f);
        int w = 250, h = 120 + Math.max(0, lines.size() - 3) * 10;
        int x = (width - w) / 2, y = (height - h) / 2 + (int) ((1f - appear) * 8);
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000 | accent);
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        c.fillGradient(x, y, x + w, y + h, 0xF01A1630, 0xF00A0818);
        c.fill(x, y, x + w, y + 22, 0xFF000000 | (accent & 0xFFFFFF));
        c.fillGradient(x, y, x + w, y + 22, 0xFF000000 | accent, 0xFF000000 | ((accent >> 1) & 0x7F7F7F));
        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, y + 7, 0xFFFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer, sub, width / 2, y + 28, 0xFF000000 | accent);
        int ly = y + 44;
        for (String l : lines) { c.drawCenteredTextWithShadow(textRenderer, l, width / 2, ly, 0xFFE6E2F5); ly += 10; }
        super.render(c, mouseX, mouseY, delta);
    }
}
