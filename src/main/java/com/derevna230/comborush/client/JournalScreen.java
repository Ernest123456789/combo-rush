package com.derevna230.comborush.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Журнал «Сценарий»: главы слева, карточки сцен справа (в стиле экранов Balatro) */
public class JournalScreen extends Screen {
    private static final int DW = 440, DH = 262;
    private static final ItemStack ESSENCE = new ItemStack(Items.AMETHYST_SHARD);

    static final class Ch { String id = "", title = "", sub = "", state = "locked"; }
    static final class Q { String id = "", ch = "", state = "locked", title = "", desc = "", hint = "", reward = ""; int prog, max = 1; boolean server; }
    static final class Data { List<Ch> chapters = new ArrayList<>(); List<Q> quests = new ArrayList<>(); int essence; String current = ""; }

    private Data d;
    private String sel;
    private float u = 1, ox, oy, scroll = 0, scrollTarget = 0, contentH = 0;
    private final long openAt = System.nanoTime();
    private final List<float[]> btn = new ArrayList<>();
    private final List<String> btnId = new ArrayList<>();

    private JournalScreen(Data d) {
        super(Text.literal("Сценарий"));
        this.d = d;
        this.sel = chapterOf(d, d.current);
    }

    public static void onPacket(String kind, JsonObject o) {
        Data n = parse(o);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (kind.equals("camp_open")) {
            mc.setScreen(new JournalScreen(n));
            HudModel.play(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
            return;
        }
        if (mc.currentScreen instanceof JournalScreen s) s.d = n;
    }

    private static String chapterOf(Data d, String questId) {
        for (Q q : d.quests) if (q.id.equals(questId)) return q.ch;
        for (Ch c : d.chapters) if (!c.state.equals("locked") && !c.state.equals("soon")) return c.id;
        return d.chapters.isEmpty() ? "" : d.chapters.get(0).id;
    }

    private static String s(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

    private static Data parse(JsonObject o) {
        Data d = new Data();
        d.essence = o.has("essence") ? o.get("essence").getAsInt() : 0;
        d.current = s(o, "current");
        for (JsonElement e : o.getAsJsonArray("chapters")) {
            JsonObject j = e.getAsJsonObject();
            Ch c = new Ch(); c.id = s(j, "id"); c.title = s(j, "title"); c.sub = s(j, "sub"); c.state = s(j, "state");
            d.chapters.add(c);
        }
        for (JsonElement e : o.getAsJsonArray("quests")) {
            JsonObject j = e.getAsJsonObject();
            Q q = new Q(); q.id = s(j, "id"); q.ch = s(j, "ch"); q.state = s(j, "state"); q.title = s(j, "title");
            q.desc = s(j, "desc"); q.hint = s(j, "hint"); q.reward = s(j, "reward");
            q.prog = j.get("prog").getAsInt(); q.max = Math.max(1, j.get("max").getAsInt()); q.server = j.get("server").getAsBoolean();
            d.quests.add(q);
        }
        return d;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - 196), scrollTarget - (float) v * 24));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float px = (float) ((mouseX - ox) / u), py = (float) ((mouseY - oy) / u);
        for (int i = 0; i < btn.size(); i++) {
            float[] b = btn.get(i);
            if (px >= b[0] && px < b[0] + b[2] && py >= b[1] && py < b[1] + b[3]) {
                String id = btnId.get(i);
                HudModel.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
                if (id.equals("#close")) { close(); return true; }
                if (!id.equals(sel)) { sel = id; scroll = scrollTarget = 0; HudModel.play(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.2f, 0.6f); }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float t = (now - openAt) / 1e9f;
        u = Math.min(width / (float) DW, height / (float) DH) * 0.96f;
        ox = (width - DW * u) / 2f; oy = (height - DH * u) / 2f;
        float mx = (mouseX - ox) / u, my = (mouseY - oy) / u;
        scroll += (scrollTarget - scroll) * 0.3f;

        c.fillGradient(0, 0, width, height, 0xF0140A26, 0xF00C1424);
        for (int i = 0; i < 70; i++) {
            float sx = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1 * width;
            float sy = (float) ((Math.sin(i * 78.233) * 12543.1) % 1 + 1) % 1 * height;
            float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(t * 1.3 + i));
            c.fill((int) sx, (int) sy, (int) sx + 1, (int) sy + 1, StoryHud.argb(0xFFFFFF, 0.22f * tw));
        }

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(u, u, 1f);
        btn.clear(); btnId.clear();

        // заголовок
        String title = "СЦЕНАРИЙ";
        float tw0 = textRenderer.getWidth(title) * 2.2f, tx = DW / 2f - tw0 / 2f;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float wob = (float) Math.sin(t * 3 + i * 0.6) * 1.5f;
            text(c, ch, tx, 6 + wob, 2.2f, 0xFF000000 | hsv((t * 0.12f + i * 0.07f) % 1f), 0);
            tx += textRenderer.getWidth(ch) * 2.2f;
        }
        text(c, "Шоу «Combo Rush» · сезон идёт", DW / 2f, 28, 0.6f, 0xFFB8B0D0, 1);

        // главы
        float cy = 44;
        for (Ch ch : d.chapters) {
            boolean selected = ch.id.equals(sel);
            boolean open = !ch.state.equals("locked") && !ch.state.equals("soon");
            boolean hv = in(mx, my, 6, cy, 112, 36);
            drawChapter(c, ch, 6 + (selected ? 4 : hv && open ? 2 : 0), cy, selected, t);
            if (open) { btn.add(new float[]{6, cy, 112, 36}); btnId.add(ch.id); }
            cy += 41;
        }
        // эссенция и закрыть
        panel(c, 6, DH - 52, 112, 22);
        m.push(); m.translate(10, DH - 49, 0); c.drawItem(ESSENCE, 0, 0); m.pop();
        text(c, "Эссенция: " + d.essence, 30, DH - 45, 0.75f, 0xFFD9A8FF, 0);
        boolean hc = in(mx, my, 6, DH - 26, 112, 20);
        button(c, 6, DH - 26, 112, 20, "ЗАКРЫТЬ", hc ? 0xFFFF6B6B : 0xFFE04848, 0xFF8A1C1C);
        btn.add(new float[]{6, DH - 26, 112, 20}); btnId.add("#close");

        // сцены выбранной главы (со скроллом)
        float px = 128, pw = DW - px - 6, top = 44, viewH = DH - top - 6;
        panel(c, px - 2, top - 2, pw + 4, viewH + 4);
        int sx1 = (int) (ox + (px - 2) * u), sy1 = (int) (oy + (top - 1) * u), sx2 = (int) (ox + (px + pw + 2) * u), sy2 = (int) (oy + (top + viewH + 1) * u);
        c.enableScissor(sx1, sy1, sx2, sy2);
        float y = top + 4 - scroll;
        for (Q q : d.quests) {
            if (!q.ch.equals(sel)) continue;
            y += drawQuest(c, q, px + 4, y, pw - 8, t) + 6;
        }
        contentH = y + scroll - top;
        c.disableScissor();
        if (contentH > viewH) {
            float bh = viewH * viewH / contentH, by = top + (viewH - bh) * (scroll / Math.max(1, contentH - viewH));
            c.fill((int) (px + pw - 2), (int) by, (int) (px + pw), (int) (by + bh), 0x88FFFFFF);
        }
        m.pop();
        super.render(c, mouseX, mouseY, delta);
    }

    private void drawChapter(DrawContext c, Ch ch, float x, float y, boolean selected, float t) {
        int accent = switch (ch.state) { case "done" -> 0x6FE08A; case "active" -> 0xFFC94A; default -> 0x6A6A78; };
        c.fill((int) x - 1, (int) y - 1, (int) x + 113, (int) y + 37, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) x + 112, (int) y + 36, selected ? 0xFF3A2A5E : 0xFF2A1F46, 0xFF141024);
        c.fill((int) x, (int) y, (int) x + 3, (int) y + 36, 0xFF000000 | accent);
        if (ch.state.equals("active")) {
            float p = 0.4f + 0.4f * (float) Math.sin(t * 4);
            c.fill((int) x, (int) y, (int) x + 112, (int) y + 1, StoryHud.argb(accent, p));
        }
        boolean dim = ch.state.equals("locked") || ch.state.equals("soon");
        text(c, ch.title.toUpperCase(), x + 8, y + 6, 1.0f, dim ? 0xFF7A7488 : 0xFFFFFFFF, 0);
        text(c, "«" + ch.sub + "»", x + 8, y + 17, 0.65f, dim ? 0xFF5E5870 : 0xFFB8B0D0, 0);
        String badge = switch (ch.state) { case "done" -> "✔ СЫГРАНО"; case "active" -> "➜ ИДЁТ"; case "soon" -> "СКОРО"; default -> "ЗАКРЫТО"; };
        text(c, badge, x + 8, y + 26, 0.55f, 0xFF000000 | accent, 0);
    }

    /** @return высота карточки */
    private float drawQuest(DrawContext c, Q q, float x, float y, float w, float t) {
        boolean done = q.state.equals("done"), active = q.state.equals("active");
        List<String> desc = StoryHud.wrap(textRenderer, q.desc, (int) ((w - 16) / 0.7f));
        List<String> hint = active && !q.hint.isEmpty() ? StoryHud.wrap(textRenderer, "➜ " + q.hint, (int) ((w - 16) / 0.7f)) : List.of();
        boolean bar = active && q.max > 1;
        float h = 18 + desc.size() * 7.5f + (hint.isEmpty() ? 0 : hint.size() * 7.5f + 2) + (bar ? 9 : 0) + (q.reward.isEmpty() || q.state.equals("locked") ? 0 : 10) + 4;
        int accent = done ? 0x6FE08A : active ? (q.id.equals(d.current) ? 0xFFC94A : 0xE8A84A) : 0x5A5468;
        boolean cur = q.id.equals(d.current);
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        if (cur) {
            float p = 0.55f + 0.45f * (float) Math.sin(t * 3.5);
            c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, StoryHud.argb(accent, p));
        }
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), done ? 0xFF1E3326 : 0xFF2A1F46, done ? 0xFF121E18 : 0xFF141024);
        c.fill((int) x, (int) y, (int) x + 3, (int) (y + h), 0xFF000000 | accent);
        // заголовок и метки
        String mark = done ? "✔ " : active ? "" : "· ";
        text(c, mark + q.title, x + 8, y + 5, 0.95f, q.state.equals("locked") ? 0xFF7A7488 : 0xFFFFFFFF, 0);
        String scope = q.server ? "ОБЩЕЕ" : "ЛИЧНОЕ";
        float bw = textRenderer.getWidth(scope) * 0.55f + 6;
        int bc = q.server ? 0x3A6FA8 : 0x8A5A2A;
        c.fill((int) (x + w - bw - 4), (int) y + 4, (int) (x + w - 4), (int) y + 12, 0xFF000000 | bc);
        text(c, scope, x + w - bw - 1, y + 5.5f, 0.55f, 0xFFFFFFFF, 0);
        float yy = y + 16;
        for (String l : desc) { text(c, l, x + 8, yy, 0.7f, q.state.equals("locked") ? 0xFF6A6478 : 0xFFC8C0E0, 0); yy += 7.5f; }
        if (!hint.isEmpty()) {
            yy += 2;
            for (String l : hint) { text(c, l, x + 8, yy, 0.7f, 0xFFFFE08A, 0); yy += 7.5f; }
        }
        if (bar) {
            float bwid = w - 50;
            c.fill((int) x + 8, (int) yy + 2, (int) (x + 8 + bwid), (int) yy + 7, 0xFF000000);
            c.fill((int) x + 9, (int) yy + 3, (int) (x + 9 + (bwid - 2) * (q.prog / (float) q.max)), (int) yy + 6, 0xFF000000 | accent);
            text(c, q.prog + " / " + q.max, x + 12 + bwid, yy + 1.5f, 0.65f, 0xFFE6E0FF, 0);
            yy += 9;
        }
        if (!q.reward.isEmpty() && !q.state.equals("locked")) {
            yy += 1;
            text(c, (done ? "Получено: " : "Награда: ") + q.reward, x + 8, yy, 0.62f, done ? 0xFF8EF0A0 : 0xFFD9A8FF, 0);
        }
        return h;
    }

    // ---------------------------------------------------------------- мелочи
    private boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    private void panel(DrawContext c, float x, float y, float w, float h) {
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF221A38, 0xFF161026);
    }

    private void button(DrawContext c, float x, float y, float w, float h, String label, int top, int bot) {
        c.fill((int) x - 1, (int) y - 1, (int) (x + w) + 1, (int) (y + h) + 1, 0xFF000000);
        c.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), top, bot);
        c.fill((int) x + 1, (int) y + 1, (int) (x + w) - 1, (int) y + 2, 0x55FFFFFF);
        text(c, label, x + w / 2f, y + h / 2f - 4, 0.9f, 0xFFFFFFFF, 1);
    }

    private void text(DrawContext c, String s, float x, float y, float scale, int argb, int align) {
        if (s == null || s.isEmpty()) return;
        float w = textRenderer.getWidth(s) * scale;
        float px = align == 1 ? x - w / 2f : align == 2 ? x - w : x;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(textRenderer, s, 0, 0, argb, false);
        m.pop();
    }

    private static int hsv(float h) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        int ri = (int) (Math.max(0, Math.min(1, r)) * 200 + 55), gi = (int) (Math.max(0, Math.min(1, g)) * 200 + 55),
            bi = (int) (Math.max(0, Math.min(1, b)) * 200 + 55);
        return (ri << 16) | (gi << 8) | bi;
    }
}
