package com.derevna230.comborush.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;

/** Авторы в главном меню: справа снизу, над строкой «Copyright Mojang AB» */
public final class TitleCredits {
    private TitleCredits() {}

    private static final String[] LINES = {"§7Idea — §fDerevna230", "§7Code — §fClaude"};

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen)) return;
            ScreenEvents.afterRender(screen).register((s, c, mx, my, delta) -> {
                var tr = client.textRenderer;
                int y = s.height - 10 - 10 * LINES.length;
                for (String line : LINES) {
                    c.drawTextWithShadow(tr, line, s.width - tr.getWidth(line) - 2, y, 0xFFFFFFFF);
                    y += 10;
                }
            });
        });
    }
}
