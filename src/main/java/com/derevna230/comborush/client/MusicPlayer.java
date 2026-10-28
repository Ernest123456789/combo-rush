package com.derevna230.comborush.client;

import com.derevna230.comborush.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Музыка и стингеры цикла: своя тема во время события (и отдельная — в босс-блайнде),
 * удар на старте, «будильник» на последней минуте (музыка чуть ускоряется), тиканье перед стартом.
 * Громкость музыки — ползунок «Музыка» в настройках; ванильная музыка на время события молчит.
 */
public final class MusicPlayer {
    private MusicPlayer() {}

    private static Loop cur = null;
    private static SoundEvent curEv = null;
    private static String lastPhase = null;
    private static int lastSec = -1, tick = 0;

    static final class Loop extends MovingSoundInstance {
        float target = 0.6f, targetPitch = 1f;
        boolean fading = false;

        Loop(SoundEvent ev) {
            super(ev, SoundCategory.MUSIC, SoundInstance.createRandom());
            this.repeat = true;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.volume = 0.05f;
            this.pitch = 1f;
        }

        @Override public boolean shouldAlwaysPlay() { return true; }

        @Override
        public void tick() {
            float t = fading ? 0f : target;
            this.volume += (t - this.volume) * (fading ? 0.12f : 0.05f);
            this.pitch += (targetPitch - this.pitch) * 0.05f;
            if (fading && this.volume < 0.02f) this.setDone();
        }

        void fadeOut() { fading = true; }
    }

    public static void reset() {
        if (cur != null) cur.fadeOut();
        cur = null; curEv = null; lastPhase = null; lastSec = -1;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { if (cur != null) reset(); return; }
        tick++;
        String phase = HudModel.phase;
        boolean event = "EVENT".equals(phase);
        int secsLeft = Math.max(0, HudModel.secs - (int) ((System.nanoTime() - HudModel.secsAt) / 1_000_000_000L));

        // стингеры на смену фазы (не при первом пакете после входа)
        if (lastPhase != null && !lastPhase.equals(phase)) {
            if (event) HudModel.play(HudModel.blind ? ModSounds.BOSS_START : ModSounds.EVENT_START, 1f, 0.8f);
            else HudModel.play(ModSounds.EVENT_END, 1f, 0.7f);
            lastSec = -1;
        }
        lastPhase = phase;

        if (secsLeft != lastSec) {
            if (!event && secsLeft >= 1 && secsLeft <= 5) HudModel.play(ModSounds.TICK, 1f + (5 - secsLeft) * 0.08f, 0.6f);
            if (event && secsLeft == 60 && lastSec > 60) HudModel.play(ModSounds.LAST_MINUTE, 1f, 0.7f);
            if (event && secsLeft <= 10 && secsLeft >= 1 && lastSec > secsLeft) HudModel.play(ModSounds.TICK, 1.3f, 0.45f);
            lastSec = secsLeft;
        }

        SoundEvent want = event ? (HudModel.blind ? ModSounds.MUSIC_BOSS : ModSounds.MUSIC_EVENT) : null;
        if (want != curEv) {
            if (cur != null) cur.fadeOut();
            cur = null;
            curEv = want;
            if (want != null) {
                cur = new Loop(want);
                mc.getSoundManager().play(cur);
            }
        }
        if (cur != null) {
            cur.targetPitch = event && secsLeft <= 60 ? 1.06f : 1f;      // последняя минута — темп чуть выше
            if (tick % 40 == 0) mc.getMusicTracker().stop();             // ванильная музыка не мешает
            if (cur.isDone() && curEv != null) {                        // вдруг звук оборвался — перезапустить
                cur = new Loop(curEv);
                mc.getSoundManager().play(cur);
            }
        }
    }
}
