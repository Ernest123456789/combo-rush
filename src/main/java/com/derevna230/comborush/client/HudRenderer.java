package com.derevna230.comborush.client;

import com.derevna230.comborush.client.HudModel.Card;
import com.derevna230.comborush.client.HudModel.Floating;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Правая колонка: панель счёта (фишки × множитель) и карточки усилителей в стиле Balatro */
public final class HudRenderer {
    private HudRenderer() {}

    // ---- размеры (в пикселях GUI; при нехватке места всё уменьшается целиком) ----
    private static final int PW = 116, PH = 90;
    private static final int CARD_W = 26, CARD_H = 30, GAP = 4;
    private static final int MARGIN = 6;
    private static final int TOP = 58;               // ниже иконок эффектов зелий
    private static final int CARDS_TOP = PH + 8;
    private static final int CONS_S = 24, CONS_H = 40;

    // ---- цвета ----
    private static final int PANEL_BG = 0xC0101018, PANEL_BORDER = 0xFF5A4630, GOLD = 0xFFF2B72B;
    private static final int CHIPS_BG = 0xFF2358C7, CHIPS_HI = 0xFF6FA8FF;
    private static final int MULT_BG = 0xFFC02828, MULT_HI = 0xFFFF7A7A;
    private static final ItemStack COIN = new ItemStack(Items.GOLD_NUGGET);

    private static long lastFrame = 0;
    private static final int[] cardY = new int[16];
    private static int boxesY, scoreY;
    private static float coinTargetX, coinTargetY;       // где в экранных координатах счётчик монет

    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!HudModel.enabled || mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;

        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        HudModel.update(now);
        double diff = HudModel.targetTotal - HudModel.shownTotal;
        if (Math.abs(diff) < 0.5) HudModel.shownTotal = HudModel.targetTotal;
        else HudModel.shownTotal += diff * (1 - Math.exp(-dt * 9));

        int W = c.getScaledWindowWidth(), H = c.getScaledWindowHeight();
        int rows = Math.max(HudModel.slots, HudModel.cards.size());
        int contentH = CARDS_TOP + rows * (CARD_H + GAP) + CONS_H;
        float s = Math.max(0.45f, Math.min(1f, (H - TOP - 6) / (float) contentH));

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(W - MARGIN, TOP, 0);
        m.scale(s, s, 1f);
        coinTargetX = W - MARGIN + (-PW + 8) * s;
        coinTargetY = TOP + 86 * s;

        drawPanel(c, tr, now);
        drawCards(c, tr, now);
        drawConsumables(c, tr, now);
        drawFloats(c, tr, now);

        m.pop();
        drawBanner(c, tr, now, W, H);
        drawResult(c, tr, now, W, H);
        drawHover(c, tr, mc, W, H, s);
        StoryHud.render(c, tr, W, H);
    }

    // ===================================================== панель счёта
    private static void drawPanel(DrawContext c, TextRenderer tr, long now) {
        MatrixStack m = c.getMatrices();
        boolean event = HudModel.phase.equals("EVENT");

        // тряска панели при большом выигрыше
        float shakeT = (now - HudModel.totalShakeAt) / 1e6f;
        float shake = shakeT < 350 ? HudModel.totalShakeAmp * (1 - shakeT / 350f) : 0;
        m.push();
        m.translate((float) Math.sin(now / 4e6) * shake, (float) Math.cos(now / 5e6) * shake * 0.6f, 0);

        // фон
        c.fill(-PW - 1, -1, 1, PH + 1, PANEL_BORDER);
        c.fill(-PW, 0, 0, PH, PANEL_BG);
        c.fill(-PW, 0, 0, 1, 0x40FFFFFF);

        // заголовок и таймер
        int left = Math.max(0, HudModel.secs - (int) ((now - HudModel.secsAt) / 1_000_000_000L));
        String head = event ? (HudModel.eventName.isEmpty() ? "СОБЫТИЕ" : HudModel.eventName) : "До события";
        int headColor = event ? 0xFF8A8A : 0xB8C4FF;
        if (event && !HudModel.boss.isEmpty()) {                      // босс-блайнд
            head = "БОСС: " + HudModel.boss;
            headColor = ((int) (180 + 60 * Math.sin(now / 2.5e8)) << 16) | 0x3030;
            c.fill(-PW - 1, -1, 1, 1, 0xFFE03030);
        }
        text(c, tr, fit(tr, head, 68, 0.8f), -PW + 5, 4, 0.8f, 0xFF000000 | headColor, 0);
        text(c, tr, String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60), -5, 4, 0.9f, 0xFFFFFFFF, 2);

        // общий счёт
        scoreY = 14;
        float tb = (now - HudModel.totalBounce) / 1e6f;
        float ts = 1.8f + (tb < 300 ? 0.45f * (float) Math.exp(-tb / 90f) : 0);
        long shown = Math.round(event ? HudModel.shownTotal : HudModel.lastEventTotal);
        text(c, tr, event ? "ОЧКИ" : "ИТОГ", -PW + 5, 18, 0.6f, 0xFFA0A0A8, 0);
        text(c, tr, (HudModel.blind && event) ? "???" : HudModel.fmtInt(shown), -5, scoreY + 2 - (ts - 1.8f) * 3, ts, event ? GOLD : 0xFF9A8F70, 2);

        // название «руки» (что именно считаем)
        boolean busy = HudModel.animating() || (now - HudModel.idleSince) / 1e6 < 700;
        float alpha = busy ? 1f : Math.max(0.35f, 1f - ((now - HudModel.idleSince) / 1e6f - 700f) / 400f);
        if (busy && !HudModel.handLabel.isEmpty())
            text(c, tr, HudModel.handLabel.toUpperCase(Locale.ROOT), -PW / 2f, 35, 0.7f, 0xFFE8D9A0, 1);

        // фишки × множитель
        boxesY = 44;
        int bw = 47, bh = 20;
        box(c, tr, -PW + 5, boxesY, bw, bh, CHIPS_BG, CHIPS_HI,
            (HudModel.blind && event) ? "?" : HudModel.fmtInt(Math.round(HudModel.chipsShown)), HudModel.chipsBounce, now, alpha);
        box(c, tr, -5 - bw, boxesY, bw, bh, MULT_BG, MULT_HI,
            (HudModel.blind && event) ? "?" : String.format(Locale.ROOT, "%.2f", HudModel.multShown), HudModel.multBounce, now, alpha);
        text(c, tr, "×", -PW / 2f, boxesY + 4, 1.4f, argb(0xFFFFFF, alpha), 1);

        // таймер серии (комбо)
        float frac = HudModel.comboWinMs <= 0 ? 0
            : Math.max(0f, 1f - (now - HudModel.comboAt) / 1e6f / HudModel.comboWinMs);
        text(c, tr, "СЕРИЯ", -PW + 5, 68, 0.55f, 0xFF9A9AA4, 0);
        if (frac > 0)
            text(c, tr, "×" + String.format(Locale.ROOT, "%.2f", HudModel.comboMult), -5, 67, 0.65f, 0xFFFFD27A, 2);
        c.fill(-PW + 5, 76, -5, 79, 0xFF22222A);
        int barW = (int) ((PW - 10) * frac);
        if (barW > 0) {
            int col = frac > 0.5f ? 0xFFF2B72B : frac > 0.25f ? 0xFFF0802B : 0xFFE03C3C;
            c.fill(-PW + 5, 76, -PW + 5 + barW, 79, col);
        }

        // монеты и существо
        m.push();
        m.translate(-PW + 4, 80, 0);
        m.scale(0.6f, 0.6f, 1f);
        c.drawItem(COIN, 0, 0);
        m.pop();
        float cb = (now - HudModel.coinBounceAt) / 1e6f;
        float cs = 0.8f + (cb < 300f ? 0.35f * (float) Math.exp(-cb / 90f) : 0f);
        text(c, tr, Integer.toString(HudModel.coins), -PW + 16, 82 - (cs - 0.8f) * 4f, cs, GOLD, 0);
        String sp = HudModel.species.isEmpty() ? "" : fit(tr, HudModel.species, 52, 0.65f) + " " + HudModel.rank + "/5";
        text(c, tr, sp, -5, 83, 0.65f, 0xFFB8B8C0, 2);
        m.pop();
    }

    private static void box(DrawContext c, TextRenderer tr, int x, int y, int w, int h,
                            int bg, int hi, String txt, long bounceAt, long now, float alpha) {
        MatrixStack m = c.getMatrices();
        float t = (now - bounceAt) / 1e6f;
        float sc = 1f + (t < 300 ? 0.30f * (float) Math.exp(-t / 75f) * (float) Math.cos(t / 40f) : 0f);
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(sc, sc, 1f);
        m.translate(-w / 2f, -h / 2f, 0);
        c.fill(-1, -1, w + 1, h + 1, argb(0x000000, alpha));
        c.fillGradient(0, 0, w, h, argb(hi & 0xFFFFFF, alpha), argb(bg & 0xFFFFFF, alpha));
        c.fill(1, 1, w - 1, 2, argb(0xFFFFFF, alpha * 0.35f));
        text(c, tr, txt, w / 2f, h / 2f - 4f, txt.length() > 6 ? 0.8f : 1.1f, argb(0xFFFFFF, alpha), 1);
        m.pop();
    }

    // ===================================================== карточки усилителей
    private static void drawCards(DrawContext c, TextRenderer tr, long now) {
        MatrixStack m = c.getMatrices();
        int n = Math.max(HudModel.slots, HudModel.cards.size());

        for (int i = 0; i < n; i++) {
            int y = CARDS_TOP + i * (CARD_H + GAP);
            if (i < cardY.length) cardY[i] = y;

            if (i >= HudModel.cards.size()) {          // пустой слот
                dashed(c, -CARD_W, y, CARD_W, CARD_H, 0x66FFFFFF);
                text(c, tr, "+", -CARD_W / 2f, y + CARD_H / 2f - 4, 1.2f, 0x44FFFFFF, 1);
                continue;
            }
            Card card = HudModel.cards.get(i);
            int[] pal = palette(card);

            // подпись слева от карточки
            int lx = -CARD_W - 5;
            c.fill(lx - 92, y + 3, lx + 3, y + CARD_H - 3, 0x70000000);
            text(c, tr, fit(tr, card.name, 86, 0.8f), lx, y + 6, 0.8f, card.active ? rarityColor(card.rar, now) : 0xFF8A8A8A, 2);
            text(c, tr, card.active ? catLabel(card) : "не работает", lx, y + 17, 0.6f,
                card.active ? (pal[0] | 0xFF000000) : 0xFF777777, 2);

            // покачивание и «подпрыгивание» при срабатывании
            long jt = (now - card.jiggleAt) / 1_000_000L;
            float jig = jt < 700 ? (float) Math.exp(-jt / 110.0) : 0f;
            float angle = card.jiggleAmp * 8f * (float) Math.sin(jt / 26.0) * jig
                + (float) Math.sin(now / 9e8 + i) * 1.2f;
            float scale = 1f + 0.16f * jig * card.jiggleAmp;
            float bob = (float) Math.sin(now / 7e8 + i * 1.3) * 0.7f;

            m.push();
            m.translate(-CARD_W / 2f, y + CARD_H / 2f + bob, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            m.scale(scale, scale, 1f);
            m.translate(-CARD_W / 2f, -CARD_H / 2f, 0);
            drawCard(c, tr, card, pal, i, now);
            m.pop();
        }
    }

    private static void drawCard(DrawContext c, TextRenderer tr, Card card, int[] pal, int i, long now) {
        MatrixStack m = c.getMatrices();
        int border = card.neg ? 0xFFFFFFFF : rarityBorder(card, pal[2], now);

        final boolean city = !card.world.isEmpty();
        if (city) {
            CityCardFx.frame(c, CARD_W, CARD_H, now, i, border | 0xFF000000, 0, pal[0]);
        } else {
            c.fill(-1, -1, CARD_W + 1, CARD_H + 1, border);
            c.fill(0, 0, CARD_W, CARD_H, 0xFF000000);
            c.fillGradient(1, 1, CARD_W - 1, CARD_H - 1, 0xFF000000 | pal[0], 0xFF000000 | pal[1]);
            c.fill(2, 2, CARD_W - 2, 3, 0x55FFFFFF);                  // блик сверху
        }
        if (card.lvl > 0) {                                           // уровень усилителя
            c.fill(1, 1, 12, 8, 0xC0000000);
            text(c, tr, "Ур" + card.lvl, 2, 2, 0.55f, 0xFFFFE08A, 0);
        }
        if (!card.ed.isEmpty()) {                                     // издание
            int by = city ? 5 : 1;
            c.fill(CARD_W - 9, by, CARD_W - 1, by + 7, 0xC0000000);
            text(c, tr, edLetter(card.ed), CARD_W - 5, by + 0.5f, 0.6f, editionColor(card.ed, now), 1);
        }

        // иконка предмета крупнее обычной
        if (!card.stack.isEmpty()) {
            if (city) CityCardFx.halo(c, 13, 11, 10, now, i);
            m.push();
            m.translate(3, 1, 0);
            m.scale(1.2f, 1.2f, 1f);
            c.drawItem(card.stack, 0, 0);
            m.pop();
        }

        // лента с числом
        c.fill(city ? 3 : 1, CARD_H - 10, CARD_W - 1, CARD_H - 1, 0xC0000000);
        if (!card.tags.isEmpty()) {
            int idx = (int) ((now / 1_200_000_000L) % card.tags.size());
            HudModel.Tag tg = card.tags.get(idx);
            text(c, tr, tg.text, CARD_W / 2f, CARD_H - 8.5f, 0.72f, tagColor(tg.kind), 1);
        } else if (card.cat.equals("game") || card.cat.equals("hybrid")) {
            text(c, tr, "игр.", CARD_W / 2f, CARD_H - 8.5f, 0.6f, 0xFFD0F0D0, 1);
        }

        // «негативный» усилитель
        if (card.neg) c.fill(1, 1, CARD_W - 1, CARD_H - 1, 0x66000000);
        // тусклая, если эффект сейчас не работает
        if (!card.active) c.fill(0, 0, CARD_W, CARD_H, 0xA0101014);

        // периодический блик-проблеск
        if (city) CityCardFx.overlay(c, CARD_W, CARD_H, now, i);
        if (card.active && !city) {
            double period = card.ed.equals("foil") || card.ed.equals("poly") ? 2.2 : 4.5;
            double ph = ((now / 1e9) + i * 0.8) % period / period;
            if (ph < 0.3) {
                int sx = (int) (-8 + (CARD_W + 16) * (ph / 0.3));
                int x1 = Math.max(1, sx), x2 = Math.min(CARD_W - 1, sx + 6);
                if (x2 > x1) c.fill(x1, 1, x2, CARD_H - 1, 0x30FFFFFF);
            }
        }
    }

    // ===================================================== всплывающие числа
    private static void drawFloats(DrawContext c, TextRenderer tr, long now) {
        if (HudModel.blind && HudModel.phase.equals("EVENT")) return;   // босс «Слепота»
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(0, 0, 300);
        for (Floating f : HudModel.floats) {
            float t = (now - f.born) / 1e6f / 950f;
            if (t < 0 || t > 1) continue;
            float rise = 20f * (1 - (1 - t) * (1 - t));
            int a = Math.max(30, (int) (255 * (1 - t * t)));
            float x, y;
            if (f.card >= 0 && f.card < cardY.length) { x = -CARD_W / 2f; y = cardY[f.card] - 4 - rise; }
            else if (f.box.equals("chips")) { x = -PW + 5 + 23; y = boxesY - 8 - rise; }
            else if (f.box.equals("mult"))  { x = -5 - 23;      y = boxesY - 8 - rise; }
            else                            { x = -PW / 2f;     y = scoreY - rise * 0.6f; }
            text(c, tr, f.text, x, y, f.scale, (a << 24) | (f.rgb & 0xFFFFFF), 1);
        }
        m.pop();
    }

    // ===================================================== баннер начала/конца события
    private static void drawBanner(DrawContext c, TextRenderer tr, long now, int W, int H) {
        if (HudModel.bannerKind.isEmpty()) return;
        if (HudModel.bannerKind.equals("end") && HudModel.result != null) return;   // вместо баннера — экран итогов
        float t = (now - HudModel.bannerAt) / 1e6f;
        float life = HudModel.bannerKind.equals("start") ? 3800f : 3200f;
        if (t < 0 || t > life) return;
        float fade = t > life - 600f ? Math.max(0f, (life - t) / 600f) : 1f;
        float pop = 1f + 0.6f * (float) Math.exp(-t / 140f);
        int a = Math.max(8, (int) (255 * fade));

        String big, sub;
        if (HudModel.bannerKind.equals("start")) {
            big = "СОБЫТИЕ" + (HudModel.eventName.isEmpty() ? "" : ": " + HudModel.eventName.toUpperCase(Locale.ROOT));
            sub = "Набирайте очки!";
        } else {
            big = "СОБЫТИЕ ЗАВЕРШЕНО";
            sub = "Итог: " + HudModel.fmtInt(HudModel.lastEventTotal);
        }
        int cx = W / 2, cy = (int) (H * 0.2f);
        text(c, tr, big, cx, cy - 6 * pop, 2f * pop, (a << 24) | 0xF2B72B, 1);
        text(c, tr, sub, cx, cy + 14, 1.0f, (a << 24) | 0xFFFFFF, 1);
    }


    // ===================================================== расходники в правой колонке
    private static void drawConsumables(DrawContext c, TextRenderer tr, long now) {
        int rows = Math.max(HudModel.slots, HudModel.cards.size());
        int y = CARDS_TOP + rows * (CARD_H + GAP) + 2;
        text(c, tr, "РАСХОДНИКИ", 0, y, 0.55f, 0xFF9A9AA4, 2);
        int sy = y + 9;
        for (int i = 0; i < 2; i++) {
            int x = consX(i);
            if (i < HudModel.cons.size()) {
                HudModel.Cons cc = HudModel.cons.get(i);
                int border = rarFlat(cc.rar, now);
                float bob = (float) Math.sin(now / 6e8 + i * 1.7) * 0.6f;
                c.fill(x - 1, sy - 1, x + CONS_S + 1, sy + CONS_S + 1, border);
                c.fillGradient(x, sy, x + CONS_S, sy + CONS_S, 0xFF34344A, 0xFF16161E);
                c.fill(x + 1, sy + 1, x + CONS_S - 1, sy + 2, 0x44FFFFFF);
                if (!cc.stack.isEmpty()) {
                    MatrixStack m = c.getMatrices();
                    m.push();
                    m.translate(x + 2.5f, sy + 2.5f + bob, 0);
                    m.scale(1.2f, 1.2f, 1f);
                    c.drawItem(cc.stack, 0, 0);
                    m.pop();
                }
            } else {
                dashed(c, x, sy, CONS_S, CONS_S, 0x55FFFFFF);
            }
        }
    }

    private static int consX(int i) { return -(2 * CONS_S + 4) + i * (CONS_S + 4); }

    private static int rarFlat(String rar, long now) {
        float pulse = 0.78f + 0.22f * (float) Math.sin(now / 3.5e8);
        return switch (rar) {
            case "uncommon"  -> 0xFF5CE08A;
            case "rare"      -> 0xFF4C9BFF;
            case "epic"      -> scale(0xB45BFF, pulse);
            case "legendary" -> scale(0xFFC040, pulse);
            default          -> 0xFFB8B8C0;
        };
    }

    // ===================================================== подсказки при наведении (когда курсор свободен: чат, инвентарь)
    private static void drawHover(DrawContext c, TextRenderer tr, MinecraftClient mc, int W, int H, float s) {
        if (mc.currentScreen == null) return;
        // свои экраны мода (магазин, существа, журнал…) рисуют свои подсказки — иначе две накладываются
        if (mc.currentScreen.getClass().getName().startsWith("com.derevna230.")) return;
        double mx = mc.mouse.getX() * W / mc.getWindow().getWidth();
        double my = mc.mouse.getY() * H / mc.getWindow().getHeight();
        float lx = (float) ((mx - (W - MARGIN)) / s);
        float ly = (float) ((my - TOP) / s);

        List<Text> tip = null;
        int rows = Math.max(HudModel.slots, HudModel.cards.size());
        for (int i = 0; i < rows && tip == null; i++) {
            int y = CARDS_TOP + i * (CARD_H + GAP);
            if (ly >= y && ly < y + CARD_H && lx >= -(CARD_W + 98) && lx <= 0) {
                tip = i < HudModel.cards.size() ? cardTip(tr, HudModel.cards.get(i))
                                                : List.of(Text.literal("§7Свободный слот усилителя"));
            }
        }
        int cy = CARDS_TOP + rows * (CARD_H + GAP) + 2 + 9;
        for (int i = 0; i < 2 && tip == null; i++) {
            int x = consX(i);
            if (ly >= cy && ly < cy + CONS_S && lx >= x && lx < x + CONS_S) {
                tip = i < HudModel.cons.size() ? consTip(tr, HudModel.cons.get(i))
                                               : List.of(Text.literal("§7Слот расходника"));
            }
        }
        if (tip != null) c.drawTooltip(tr, tip, (int) mx, (int) my);
    }

    private static String rarCode(String r) {
        return switch (r) { case "uncommon" -> "§a"; case "rare" -> "§9"; case "epic" -> "§5"; case "legendary" -> "§6"; default -> "§f"; };
    }
    private static String rarLabel(String r) {
        return switch (r) { case "uncommon" -> "Необычный"; case "rare" -> "Редкий"; case "epic" -> "Эпический";
                            case "legendary" -> "Легендарный"; default -> "Обычный"; };
    }

    private static List<String> wrap(TextRenderer tr, String s, int maxPx) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (tr.getWidth(test) > maxPx && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(w); }
            else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static List<Text> cardTip(TextRenderer tr, Card card) {
        List<Text> t = new ArrayList<>();
        t.add(Text.literal(rarCode(card.rar) + "§l" + card.name));
        t.add(Text.literal("§7" + rarLabel(card.rar) + " · " + catLabel(card)));
        if (!card.world.isEmpty()) t.add(Text.literal("§b◆ §dНочной город §7— особый"));
        for (String l : wrap(tr, card.desc, 170)) t.add(Text.literal("§f" + l));
        if (card.lvl > 0) t.add(Text.literal("§eУровень: " + card.lvl + "/5 §7(+" + card.lvl * 20 + "% к плюсам)"));
        if (!card.ed.isEmpty()) t.add(Text.literal("§bИздание: §f" + edName(card.ed) + " §7(" + edDesc(card.ed) + ")"));
        if (!card.tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (HudModel.Tag tg : card.tags) sb.append(sb.length() == 0 ? "" : "  ").append(tg.text);
            t.add(Text.literal("§aСейчас: §f" + sb));
        }
        if (!card.active) t.add(Text.literal("§cСейчас не работает (условие не выполнено)"));
        return t;
    }

    private static List<Text> consTip(TextRenderer tr, HudModel.Cons cc) {
        List<Text> t = new ArrayList<>();
        t.add(Text.literal(rarCode(cc.rar) + "§l" + cc.name));
        t.add(Text.literal("§7Расходник · " + rarLabel(cc.rar)));
        for (String l : wrap(tr, cc.desc, 170)) t.add(Text.literal("§f" + l));
        t.add(Text.literal("§8Используется в магазине (/shop) между событиями"));
        return t;
    }

    private static String edName(String e) {
        return switch (e) { case "foil" -> "Фольга"; case "holo" -> "Голограмма"; case "poly" -> "Полихром"; default -> e; };
    }
    private static String edDesc(String e) {
        return switch (e) { case "foil" -> "+25 фишек к каждому действию"; case "holo" -> "+0.4 к множителю";
                            case "poly" -> "×1.2 к множителю"; default -> ""; };
    }

    // ===================================================== экран итогов события
    private static final String[] MEDAL_SHORT = {"Б", "С", "З", "П"};
    private static final int[] MEDAL_RGB = {0xCD7F32, 0xC8CED6, 0xFFD700, 0x7FE8FF};

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float easeOut(float v) { v = clamp01(v); return 1f - (1f - v) * (1f - v) * (1f - v); }

    private static int srcColor(String k) {
        return switch (k) {
            case "KILL" -> 0xFF6B6B; case "MINE" -> 0x6FA8FF; case "HARVEST" -> 0x58D68D; case "SHOOT" -> 0xF0A030;
            case "BREW" -> 0xC084FC; case "ENCHANT" -> 0xFF7AD9; case "BREED" -> 0xA3E635; case "TRADE" -> 0xFFD45B;
            case "FISH" -> 0x5EEAD4; case "SHARE" -> 0x2DD4BF; case "RIFT" -> 0xB46BFF; case "DRIFT" -> 0xFF9F43; default -> 0xFFFFFF;
        };
    }
    private static String srcName(String k) {
        return switch (k) { case "BONUS" -> "Бонусы"; case "SHARE" -> "Ком. дух"; case "RIFT" -> "Разломы"; default -> HudModel.srcLabel(k); };
    }

    private static void disc(DrawContext c, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, argb);
        }
    }

    private static void drawResult(DrawContext c, TextRenderer tr, long now, int W, int H) {
        HudModel.Result r = HudModel.result;
        if (r == null) return;
        float t = (now - HudModel.resultAt) / 1e6f;
        float in = clamp01(t / 350f);
        float out = t > HudModel.RESULT_LIFE - 900 ? clamp01((float) (HudModel.RESULT_LIFE - t) / 900f) : 1f;
        float alpha = Math.min(in, out);
        if (alpha <= 0.01f) return;

        final int RW = 232;
        int rows = Math.min(7, r.rows.size());
        int yRows = 70, yThr = yRows + rows * 12 + 8, yCoins = yThr + 32, RH = yCoins + 42;
        float rs = Math.max(0.4f, Math.min(1f, Math.min((W - 150f) / (RW + 8f), (H - 16f) / RH)));
        float ox = Math.max(4f, (W - 140f) / 2f - RW * rs / 2f);
        float oy = Math.max(6f, H * 0.08f) + (1f - in) * 14f;

        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale(rs, rs, 1f);

        // фон и заголовок
        c.fill(-1, -1, RW + 1, RH + 1, argb(0x5A4630, alpha));
        c.fill(0, 0, RW, RH, argb(0x101018, alpha * 0.9f));
        c.fill(0, 0, RW, 1, argb(0xFFFFFF, alpha * 0.25f));
        text(c, tr, "ИТОГИ СОБЫТИЯ №" + r.number, 8, 6, 1.0f, argb(0xF2B72B, alpha), 0);
        if (!r.event.isEmpty()) text(c, tr, fit(tr, r.event, 90, 0.7f), RW - 8, 7, 0.7f, argb(0xB8B8C0, alpha), 2);
        c.fill(8, 18, RW - 8, 19, argb(0x5A4630, alpha));

        // общий счёт
        float ks = easeOut((t - 350f) / 900f);
        text(c, tr, "ОЧКИ", 8, 24, 0.6f, argb(0xA0A0A8, alpha), 0);
        text(c, tr, HudModel.fmtInt(Math.round(r.score * ks)), 8, 32, 2.2f, argb(0xF2B72B, alpha), 0);

        // медаль
        drawMedal(c, tr, r, RW - 36, 41, t, alpha);   // ниже заголовка, подпись не наезжает на строки

        // источники очков
        double max = r.rows.stream().mapToDouble(x -> x.points).max().orElse(1);
        double sum = Math.max(1, r.rows.stream().mapToDouble(x -> x.points).sum());
        for (int i = 0; i < rows; i++) {
            HudModel.Row row = r.rows.get(i);
            int y = yRows + i * 12;
            float p = easeOut((t - (600f + i * 130f)) / 500f);
            int col = srcColor(row.key);
            text(c, tr, srcName(row.key), 8, y + 1, 0.75f, argb(col, alpha), 0);
            int bx = 74, bw = 96;
            c.fill(bx, y + 2, bx + bw, y + 8, argb(0x23232C, alpha));
            int fw = (int) (bw * (row.points / max) * p);
            if (fw > 0) {
                c.fillGradient(bx, y + 2, bx + fw, y + 8, argb(col, alpha), argb(col, alpha * 0.55f));
                c.fill(bx, y + 2, bx + fw, y + 3, argb(0xFFFFFF, alpha * 0.35f));
            }
            String num = HudModel.fmtInt(Math.round(row.points * p)) + "  " + Math.round(row.points / sum * 100 * p) + "%";
            text(c, tr, num, RW - 8, y + 1, 0.7f, argb(0xE0E0E8, alpha), 2);
        }

        // шкала порогов медалей
        int tbx = 8, tbw = RW - 16, ty = yThr + 10;
        double maxS = Math.max(r.thr[3] * 1.15, r.score);
        float p2 = easeOut((t - 1900f) / 800f);
        c.fill(tbx - 1, ty - 1, tbx + tbw + 1, ty + 7, argb(0x000000, alpha));
        c.fill(tbx, ty, tbx + tbw, ty + 6, argb(0x23232C, alpha));
        int fillW = (int) (tbw * Math.min(r.score, maxS) / maxS * p2);
        if (fillW > 0) c.fillGradient(tbx, ty, tbx + fillW, ty + 6, argb(0x6FA8FF, alpha), argb(0x2358C7, alpha));
        for (int j = 0; j < 4; j++) {
            int mx = tbx + (int) (tbw * r.thr[j] / maxS);
            boolean lit = fillW >= mx - tbx;
            c.fill(mx, ty - 3, mx + 1, ty + 9, argb(lit ? MEDAL_RGB[j] : 0x606068, alpha));
            text(c, tr, MEDAL_SHORT[j], mx, ty - 11, 0.6f, argb(lit ? MEDAL_RGB[j] : 0x808088, alpha), 1);
        }
        String cap = r.next > 0 ? "До «" + r.nextName + "»: ещё " + HudModel.fmtInt(Math.round(r.next - r.score)) + " очков"
                                : "Максимальная медаль!";
        text(c, tr, cap, 8, ty + 11, 0.65f, argb(r.next > 0 ? 0xB8B8C0 : 0x7FE8FF, alpha), 0);

        // монеты
        c.fill(8, yCoins - 2, RW - 8, yCoins - 1, argb(0x5A4630, alpha));
        float kc = easeOut((t - (float) HudModel.T_COINS) / 1200f);
        text(c, tr, "МОНЕТЫ", 8, yCoins + 3, 0.6f, argb(0xA0A0A8, alpha), 0);
        text(c, tr, "+" + Math.round(r.total * kc), 8, yCoins + 11, 2.0f, argb(0xF2B72B, alpha), 0);
        String parts = "база " + r.pure + (r.bonus > 0 ? "  бонусы +" + r.bonus : "") + (r.interest > 0 ? "  проценты +" + r.interest : "");
        text(c, tr, parts, RW - 8, yCoins + 6, 0.65f, argb(0xB8B8C0, alpha), 2);
        if (!r.boss.isEmpty())
            text(c, tr, r.bossWin ? "Босс-блайнд «" + r.boss + "» пройден!" : "Босс-блайнд «" + r.boss + "» провален",
                RW - 8, yCoins + 17, 0.65f, argb(r.bossWin ? 0xF2B72B : 0xE03030, alpha), 2);
        if (!r.consumable.isEmpty())
            text(c, tr, "Расходник: " + r.consumable, RW - 8, yCoins + 27, 0.65f, argb(0x8EF0A0, alpha), 2);

        m.pop();

        // летящие в счётчик монеты (экранные координаты)
        float sx = ox + 52 * rs, sy = oy + (yCoins + 22) * rs;
        for (HudModel.Fly f : HudModel.flies) {
            float p = (now - f.start) / (float) f.dur;
            if (p < 0f || p > 1f) continue;
            float e = p * p * (3f - 2f * p);
            float x = sx + (coinTargetX - sx) * e + (f.rnd - 0.5f) * 36f * (1f - e);
            float y = sy + (coinTargetY - sy) * e - (float) Math.sin(Math.PI * p) * (30f + 25f * f.rnd);
            m.push();
            m.translate(x - 6f, y - 6f, 100);
            m.scale(0.75f, 0.75f, 1f);
            c.drawItem(COIN, 0, 0);
            m.pop();
        }
    }

    private static void drawMedal(DrawContext c, TextRenderer tr, HudModel.Result r, int cx, int cy, float t, float alpha) {
        MatrixStack m = c.getMatrices();
        int idx = switch (r.medal) { case "BRONZE" -> 0; case "SILVER" -> 1; case "GOLD" -> 2; case "PLATINUM" -> 3; default -> -1; };
        float mt = t - (float) HudModel.T_MEDAL;
        int rad = 14;

        m.push();
        m.translate(cx, cy, 0);
        if (mt < 0) {
            disc(c, 0, 0, rad, argb(0x30303A, alpha * 0.8f));
            text(c, tr, "?", 0, -4, 1.3f, argb(0x707078, alpha), 1);
            m.pop();
            return;
        }
        float sc = Math.max(0.2f, 1f + 0.7f * (float) Math.exp(-mt / 110f) * (float) Math.cos(mt / 45f));
        m.scale(sc, sc, 1f);

        if (idx >= 0) {
            int rgb = MEDAL_RGB[idx];
            c.fill(-8, -rad - 7, -3, -rad + 3, argb(0xC02828, alpha));       // ленточки
            c.fill(3, -rad - 7, 8, -rad + 3, argb(0x2858C0, alpha));
            disc(c, 0, 0, rad + 1, argb(0x000000, alpha));
            disc(c, 0, 0, rad, argb(rgb, alpha));
            disc(c, 0, 0, rad - 3, argb(0xFFFFFF, alpha * 0.35f));
            disc(c, 0, 0, rad - 5, argb(rgb, alpha));
            text(c, tr, MEDAL_SHORT[idx], 0, -5, 1.5f, argb(0x2A2A30, alpha), 1);
            // блик
            float sh = ((mt / 1000f) % 2.5f) / 2.5f;
            if (sh < 0.3f) {
                int sx = (int) (-rad + 2 * rad * (sh / 0.3f));
                c.fill(sx, -rad + 3, sx + 3, rad - 3, argb(0xFFFFFF, alpha * 0.45f));
            }
        } else {
            disc(c, 0, 0, rad, argb(0x3A3A44, alpha));
            text(c, tr, "—", 0, -4, 1.5f, argb(0x90909A, alpha), 1);
        }
        m.pop();

        // искры вокруг медали
        if (idx >= 0 && mt < 1000f) {
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3 + mt * 0.004;
                float rr = rad + 6 + mt * 0.018f;
                int px = cx + (int) (Math.cos(ang) * rr), py = cy + (int) (Math.sin(ang) * rr);
                float a = alpha * (1f - mt / 1000f);
                c.fill(px - 1, py, px + 2, py + 1, argb(0xFFFFFF, a));
                c.fill(px, py - 1, px + 1, py + 2, argb(0xFFFFFF, a));
            }
        }
        text(c, tr, r.medalLabel.isEmpty() ? "Без медали" : r.medalLabel, cx, cy + rad + 4, 0.7f,
            argb(idx >= 0 ? MEDAL_RGB[idx] : 0x90909A, alpha), 1);
    }

    // ===================================================== редкость и издания
    private static int rarityColor(String rar, long now) {
        return switch (rar) {
            case "uncommon" -> 0xFF6BFF8B;
            case "rare"     -> 0xFF6BB5FF;
            case "epic"     -> 0xFFC77DFF;
            case "legendary" -> {
                float k = 0.5f + 0.5f * (float) Math.sin(now / 4e8);
                yield 0xFF000000 | ((int) (255) << 16) | ((int) (170 + 70 * k) << 8) | (int) (20 + 80 * k);
            }
            default -> 0xFFFFFFFF;
        };
    }

    private static int rarityBorder(Card card, int base, long now) {
        switch (card.ed) {
            case "poly": return rainbow(now, 0);
            case "holo": return ((now / 400_000_000L) % 2 == 0) ? 0xFFFF7AD9 : 0xFF7AF0FF;
            case "foil": return 0xFFB8E6FF;
            default: break;
        }
        float pulse = 0.78f + 0.22f * (float) Math.sin(now / 3.5e8);
        return switch (card.rar) {
            case "uncommon"  -> 0xFF5CE08A;
            case "rare"      -> 0xFF4C9BFF;
            case "epic"      -> scale(0xB45BFF, pulse);
            case "legendary" -> scale(0xFFC040, pulse);
            default          -> base;
        };
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k + 30 * (k - 0.78f) * 4));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k + 30 * (k - 0.78f) * 4));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k + 30 * (k - 0.78f) * 4));
        return 0xFF000000 | (Math.max(0, r) << 16) | (Math.max(0, g) << 8) | Math.max(0, b);
    }

    private static String edLetter(String ed) {
        return switch (ed) { case "foil" -> "Ф"; case "holo" -> "Г"; case "poly" -> "П"; default -> ""; };
    }

    private static int editionColor(String ed, long now) {
        return switch (ed) { case "foil" -> 0xFFA8E0FF; case "holo" -> 0xFFFF9AE0; default -> rainbow(now, 0.3); };
    }

    private static int rainbow(long now, double offset) {
        float h = (float) (((now / 1e9) * 0.6 + offset) % 1.0);
        int i = (int) (h * 6);
        float f = h * 6 - i, v = 1f, s = 0.55f, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ===================================================== помощники
    private static int[] palette(Card card) {
        // {верх, низ, рамка}
        if (card.neg) return new int[] {0x3A3A3A, 0x050505, 0xFFFFFFFF};
        return switch (card.cat) {
            case "mult"   -> new int[] {0xFF6B6B, 0xA82020, 0xFFFFB0B0};
            case "econ"   -> new int[] {0xFFD45B, 0xA8740E, 0xFFFFEBA0};
            case "game"   -> new int[] {0x58D68D, 0x1E7A44, 0xFFB0F0C8};
            case "hybrid" -> new int[] {0xC084FC, 0x5E1E9E, 0xFFE0C0FF};
            case "team"   -> new int[] {0x5EEAD4, 0x0F766E, 0xFF99F6E4};
            default       -> new int[] {0x5B9BFF, 0x1B46A8, 0xFFB0D0FF};   // chips
        };
    }

    private static String catLabel(Card card) {
        if (card.neg) return "негативный";
        return switch (card.cat) {
            case "mult" -> "множитель"; case "econ" -> "экономика"; case "game" -> "игровой";
            case "hybrid" -> "гибрид"; case "team" -> "команда"; default -> "фишки";
        };
    }

    private static int tagColor(String kind) {
        return switch (kind) {
            case "chips", "chipsx" -> 0xFF9CC8FF;
            case "mult", "xmult"   -> 0xFFFF9A9A;
            case "coin", "sell", "interest" -> 0xFFFFDA70;
            case "share" -> 0xFF8EF0E0;
            case "disc", "reroll"  -> 0xFFB0F0B0;
            default -> 0xFFFFFFFF;
        };
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static String fit(TextRenderer tr, String s, int maxPx, float scale) {
        if (tr.getWidth(s) * scale <= maxPx) return s;
        while (s.length() > 1 && tr.getWidth(s + "…") * scale > maxPx) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static void dashed(DrawContext c, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 4) {
            c.fill(x + i, y, Math.min(x + w, x + i + 2), y + 1, color);
            c.fill(x + i, y + h - 1, Math.min(x + w, x + i + 2), y + h, color);
        }
        for (int j = 0; j < h; j += 4) {
            c.fill(x, y + j, x + 1, Math.min(y + h, y + j + 2), color);
            c.fill(x + w - 1, y + j, x + w, Math.min(y + h, y + j + 2), color);
        }
    }

    /** align: 0 — по левому краю, 1 — по центру, 2 — по правому краю (x — опорная точка) */
    private static void text(DrawContext c, TextRenderer tr, String s, float x, float y, float scale, int argb, int align) {
        if (s == null || s.isEmpty()) return;
        float w = tr.getWidth(s) * scale;
        float px = align == 1 ? x - w / 2f : align == 2 ? x - w : x;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(tr, s, 0, 0, argb, ((argb >> 16 & 255) * 3 + (argb >> 8 & 255) * 6 + (argb & 255)) > 700);
        m.pop();
    }
}
