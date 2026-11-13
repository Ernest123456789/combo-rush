#!/usr/bin/env python3
"""Генератор карты «Дюны» (x → восток, z → юг). Пишет data/combo-rush/maps/dust.map, проверяет проходимость и рисует превью.
Уровни: 0 — блок пола земли (поверхность на высоте 1), 1.. — над землёй, отрицательные — ниже."""
import sys, os, collections
import numpy as np

X0, X1, Z0, Z1 = -60, 60, -44, 44          # игровая область
GX0, GX1, GZ0, GZ1 = X0 - 1, X1 + 1, Z0 - 1, Z1 + 1   # вместе с внешней стеной
LV0, LV1 = -4, 24
NL = LV1 - LV0 + 1
W, H = GX1 - GX0 + 1, GZ1 - GZ0 + 1
G = np.zeros((W, H, NL), dtype=np.int16)

# ---------------------------------------------------------------- палитра
MATS = {}   # имя -> (id, blockstate)
def mat(name, state=None):
    if name not in MATS: MATS[name] = (len(MATS) + 1, state or name)
    return MATS[name][0]
FACE = {'e': 'east', 'w': 'west', 'n': 'north', 's': 'south'}
def stair(base, d):
    return mat(f'{base}_stairs_{d}', f'{base}_stairs[facing={FACE[d]},half=bottom,shape=straight]')
STAIR_BASE = {}   # id ступени -> материал (для проверки проходимости)
STAIRS = set()

def ix(x): return x - GX0
def iz(z): return z - GZ0
def il(l): return l - LV0

def fill(x0, x1, z0, z1, l0, l1, name):
    m = 0 if name is None else mat(name)
    xa, xb = max(x0, GX0), min(x1, GX1)
    za, zb = max(z0, GZ0), min(z1, GZ1)
    if xa > xb or za > zb: return
    G[ix(xa):ix(xb) + 1, iz(za):iz(zb) + 1, il(l0):il(l1) + 1] = m
def carve(x0, x1, z0, z1, l0, l1): fill(x0, x1, z0, z1, l0, l1, None)
def put(x, z, l, name):
    if GX0 <= x <= GX1 and GZ0 <= z <= GZ1: G[ix(x), iz(z), il(l)] = mat(name)
def wall(x0, x1, z0, z1, h, name, base=0): fill(x0, x1, z0, z1, base + 1, base + h, name)
def crate(x0, x1, z0, z1, h, base=0, name='spruce_planks'): fill(x0, x1, z0, z1, base + 1, base + h, name)

def stairs(x0, x1, z0, z1, d, base, n, name='sandstone', solid='sandstone'):
    """Лестница из n ступеней, вверх в сторону d. base — уровень блока пола внизу; ступень i стоит на уровне base+1+i.
    Первая (нижняя) колонка — на краю прямоугольника против направления подъёма."""
    cols = []
    if d == 'e': cols = [(x0 + i, None) for i in range(n)]
    elif d == 'w': cols = [(x1 - i, None) for i in range(n)]
    elif d == 's': cols = [(None, z0 + i) for i in range(n)]
    elif d == 'n': cols = [(None, z1 - i) for i in range(n)]
    sid = stair(name, d)
    STAIRS.add(sid)
    for i, (cx, cz) in enumerate(cols):
        xa, xb = (cx, cx) if cx is not None else (x0, x1)
        za, zb = (cz, cz) if cz is not None else (z0, z1)
        lv = base + 1 + i
        if lv - 1 >= base + 1: fill(xa, xb, za, zb, base + 1, lv - 1, solid)
        # на месте ступени уже могло что-то стоять — заменяем
        fill(xa, xb, za, zb, lv, lv, None)
        G[ix(xa):ix(xb) + 1, iz(za):iz(zb) + 1, il(lv)] = sid

# ---------------------------------------------------------------- пол и зоны
def checker(x, z, a, b, c=None):
    h = (x * 7 + z * 13) & 7
    return a if h < 5 else (b if (c is None or h < 7) else c)

def paint(x0, x1, z0, z1, lv, fn):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if GX0 <= x <= GX1 and GZ0 <= z <= GZ1:
                G[ix(x), iz(z), il(lv)] = mat(fn(x, z))

GLYPH = {'A': [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
         'B': ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."]}
def site_floor(c, cx, cz, r, lv, base_mat, rim_mat, letter_mat):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            x, z = cx + dx, cz + dz
            if abs(dx) == r or abs(dz) == r: m = rim_mat
            else:
                row, col = 3 - dx, dz + 2
                m = letter_mat if 0 <= row < 7 and 0 <= col < 5 and GLYPH[c][row][col] == '#' else base_mat
            put(x, z, lv, m)

def build():
    G[:] = 0
    # --- земля
    fill(GX0, GX1, GZ0, GZ1, -4, -1, 'stone')
    paint(GX0, GX1, GZ0, GZ1, 0, lambda x, z: checker(x, z, 'smooth_sandstone', 'cut_sandstone', 'sandstone'))
    paint(X0, -48, Z0, Z1, 0, lambda x, z: checker(x, z, 'red_terracotta', 'terracotta', 'brown_terracotta'))
    paint(48, X1, Z0, Z1, 0, lambda x, z: checker(x, z, 'blue_terracotta', 'light_blue_terracotta'))
    paint(-45, -7, -5, 5, 0, lambda x, z: checker(x, z, 'polished_andesite', 'stone', 'andesite'))          # мид
    paint(-12, 30, -25, 25, 0, lambda x, z: checker(x, z, 'stone_bricks', 'smooth_stone', 'cracked_stone_bricks'))  # центр
    paint(31, 45, -5, 5, 0, lambda x, z: checker(x, z, 'smooth_sandstone', 'yellow_terracotta'))
    paint(-45, 6, -43, -28, 0, lambda x, z: checker(x, z, 'sandstone', 'cut_sandstone', 'smooth_sandstone'))  # лонг
    paint(-45, 6, 28, 43, 0, lambda x, z: checker(x, z, 'cut_sandstone', 'smooth_sandstone', 'sandstone'))
    # --- внешняя стена
    for x in range(GX0, GX1 + 1):
        for z in range(GZ0, GZ1 + 1):
            if x in (GX0, GX1) or z in (GZ0, GZ1):
                for l in range(1, 12): G[ix(x), iz(z), il(l)] = mat('sandstone' if (x + z + l) % 4 else 'cut_sandstone')
                for l in range(12, 24): G[ix(x), iz(z), il(l)] = mat('barrier')

    # --- стены баз с воротами
    for xa in (-47, 46):
        wall(xa, xa + 1, Z0, Z1, 9, 'cut_sandstone')
        fill(xa, xa + 1, Z0, Z1, 4, 4, 'chiseled_sandstone')
    carve(-47, -46, -38, -32, 1, 5); carve(-47, -46, -4, 4, 1, 5); carve(-47, -46, 32, 38, 1, 5)
    carve(46, 47, -36, -30, 1, 5); carve(46, 47, -4, 4, 1, 5); carve(46, 47, 30, 36, 1, 5)

    # --- разделители лонгов (стены z −27..−26 и 26..27)
    for (za, zb) in ((-27, -26), (26, 27)):
        wall(-45, 45, za, zb, 7, 'cut_sandstone')
        fill(-45, 45, za, zb, 4, 4, 'chiseled_sandstone')

    # ======== склад (север) и тоннели (юг)
    def building(x0, x1, z0, z1, rh=8, ceil=6, light=6):
        wall(x0, x1, z0, z1, rh, 'cut_sandstone')
        fill(x0, x1, z0, z1, 4, 4, 'chiseled_sandstone')
        carve(x0 + 2, x1 - 2, z0 + 2, z1 - 2, 1, rh)                     # внутри
        fill(x0 + 2, x1 - 2, z0 + 2, z1 - 2, ceil + 1, rh, 'smooth_sandstone')   # потолок
        for lx in range(x0 + 4, x1 - 3, 6):
            for lz in range(z0 + 4, z1 - 3, 6):
                put(lx, lz, ceil + 1, 'sea_lantern')
    building(-43, -13, -27, -6)          # склад
    building(-43, -13, 6, 27)            # тоннели
    paint(-41, -15, -25, -8, 0, lambda x, z: checker(x, z, 'dark_oak_planks', 'spruce_planks'))
    paint(-41, -15, 8, 25, 0, lambda x, z: checker(x, z, 'deepslate_bricks', 'stone_bricks'))
    # двери склада
    carve(-31, -27, -27, -26, 1, 4)      # на лонг
    carve(-24, -20, -7, -6, 1, 4)        # на мид
    carve(-43, -42, -19, -15, 1, 4)      # в переулок Т
    carve(-14, -13, -19, -15, 1, 4)      # на развязку
    carve(-34, -32, -7, -6, 2, 3)        # окно на мид
    carve(-38, -36, -27, -26, 2, 3)      # окно на лонг
    # внутри склада: колонны, ящики, перегородки, антресоль
    for (px, pz) in ((-37, -22), (-37, -12), (-29, -22), (-29, -12), (-21, -22), (-21, -12)):
        wall(px, px + 1, pz, pz + 1, 6, 'stripped_spruce_log')
    crate(-33, -32, -17, -16, 2); crate(-25, -24, -19, -18, 2, name='dark_oak_planks'); crate(-25, -24, -14, -13, 3)
    crate(-18, -17, -22, -21, 2); crate(-19, -18, -11, -10, 2, name='dark_oak_planks')
    wall(-30, -26, -16, -15, 2, 'stone_bricks')            # низкая стенка в центре
    wall(-23, -22, -24, -20, 2, 'stone_bricks')
    fill(-41, -34, -25, -23, 1, 3, 'sandstone')            # антресоль (пол на уровне 3)
    paint(-41, -34, -25, -23, 3, lambda x, z: 'spruce_planks')
    wall(-41, -34, -22, -22, 1, 'spruce_planks', base=3)   # перила
    stairs(-33, -31, -25, -23, 'w', 0, 3, 'sandstone')
    # тоннели: перегородка с окном, кольцо
    wall(-39, -17, 16, 17, 6, 'stone_bricks')
    carve(-30, -28, 16, 17, 2, 3)
    carve(-43, -42, 10, 14, 1, 4)        # в переулок
    carve(-36, -32, 6, 7, 1, 4)          # на мид
    carve(-40, -35, 26, 27, 1, 4)        # на нижний лонг
    carve(-14, -13, 10, 14, 1, 4); carve(-14, -13, 19, 23, 1, 4)    # выходы на восток
    carve(-30, -28, 6, 7, 2, 3)
    crate(-34, -33, 11, 12, 2); crate(-26, -25, 21, 22, 3, name='dark_oak_planks'); crate(-22, -21, 10, 11, 2)
    crate(-37, -36, 22, 23, 2, name='dark_oak_planks'); crate(-19, -18, 22, 23, 2)
    for (px, pz) in ((-36, 20), (-22, 12), (-22, 20), (-30, 12)):
        wall(px, px + 1, pz, pz + 1, 6, 'stripped_spruce_log')
    wall(-27, -24, 13, 13, 2, 'stone_bricks'); wall(-34, -31, 19, 19, 2, 'stone_bricks')

    # ======== мид
    # двери мида: проход через стенку
    wall(-27, -26, -5, -3, 6, 'stone_bricks'); wall(-27, -26, 3, 5, 6, 'stone_bricks')
    fill(-27, -26, -5, -3, 4, 4, 'chiseled_sandstone')
    crate(-38, -37, -3, -2, 2); crate(-32, -31, 2, 3, 2, name='dark_oak_planks'); crate(-20, -19, 3, 4, 3)
    wall(-18, -14, -3, -3, 2, 'stone_bricks'); wall(-12, -11, 3, 4, 4, 'stripped_spruce_log')
    wall(-10, -9, -5, -4, 5, 'stripped_spruce_log')

    # ======== лонг (север) и нижний (юг)
    # грузовик
    fill(-24, -18, -36, -34, 1, 3, 'white_concrete'); fill(-24, -22, -36, -34, 1, 3, 'red_concrete')
    fill(-24, -23, -36, -34, 2, 3, 'light_gray_concrete')
    fill(-24, -18, -36, -34, 1, 1, 'black_concrete')
    crate(-36, -35, -41, -40, 2); crate(-36, -36, -33, -33, 1); crate(-10, -9, -38, -37, 3, name='dark_oak_planks')
    crate(-4, -3, -33, -32, 2); crate(-31, -30, -31, -30, 2, name='dark_oak_planks')
    wall(-16, -12, -34, -33, 2, 'stone_bricks'); wall(-30, -26, -41, -40, 2, 'stone_bricks')
    wall(-44, -42, -42, -41, 3, 'stone_bricks')
    carve(-11, -8, -27, -26, 2, 4)       # окно из лонга в центр (на ту сторону — развязка)
    # южный
    fill(-24, -18, 34, 36, 1, 3, 'orange_concrete'); fill(-24, -18, 34, 36, 1, 1, 'black_concrete')
    fill(-18, -18, 34, 36, 2, 3, 'light_gray_concrete')
    crate(-36, -35, 40, 41, 2, name='dark_oak_planks'); crate(-36, -36, 33, 33, 1); crate(-10, -9, 37, 38, 3)
    crate(-4, -3, 32, 33, 2, name='dark_oak_planks'); crate(-31, -30, 30, 31, 2)
    wall(-16, -12, 33, 34, 2, 'stone_bricks'); wall(-30, -26, 40, 41, 2, 'stone_bricks')
    wall(-44, -42, 41, 42, 3, 'stone_bricks')
    carve(-11, -8, 26, 27, 2, 4)
    # арки-ворота лонгов
    for (zc0, zc1) in ((-43, -28), (28, 43)):
        for xa in (-6,):
            wall(xa, xa + 1, zc0, zc0 + 1, 8, 'chiseled_sandstone'); wall(xa, xa + 1, zc1 - 1, zc1, 8, 'chiseled_sandstone')
            fill(xa, xa + 1, zc0 + 2, zc1 - 2, 7, 8, 'cut_sandstone')

    # ======== центр
    # мост на уровне 4
    fill(6, 26, -2, 2, 4, 4, 'smooth_stone'); fill(6, 26, -2, 2, 3, 3, 'stone_bricks')
    paint(6, 26, -2, 2, 4, lambda x, z: checker(x, z, 'smooth_stone', 'stone_bricks'))
    stairs(2, 5, -2, 2, 'e', 0, 4, 'stone_brick', 'stone_bricks')
    stairs(27, 30, -2, 2, 'w', 0, 4, 'stone_brick', 'stone_bricks')
    fill(6, 26, -3, -3, 4, 5, None)
    wall(6, 26, -2, -2, 1, 'stone_bricks', base=4); wall(6, 26, 2, 2, 1, 'stone_bricks', base=4)
    for (px, pz) in ((12, -2), (12, 1), (20, -2), (20, 1)):
        wall(px, px + 1, pz, pz + 1, 3, 'stone_bricks')
    # колонны и укрытия вокруг
    for (px, pz, h) in ((-2, -13, 6), (-2, 12, 6), (8, -16, 4), (8, 15, 4), (22, -16, 5), (22, 15, 5), (14, -9, 3), (14, 8, 3)):
        wall(px, px + 1, pz, pz + 1, h, 'stripped_spruce_log' if h > 3 else 'stone_bricks')
    wall(-8, -4, -9, -9, 2, 'stone_bricks'); wall(-8, -8, -9, -6, 2, 'stone_bricks')
    wall(-8, -4, 9, 9, 2, 'stone_bricks'); wall(-8, -8, 6, 9, 2, 'stone_bricks')
    wall(16, 20, -12, -12, 2, 'mossy_stone_bricks'); wall(16, 20, 12, 12, 2, 'mossy_stone_bricks')
    crate(4, 5, -8, -7, 2); crate(4, 5, 7, 8, 2, name='dark_oak_planks'); crate(24, 25, -9, -8, 3); crate(24, 25, 8, 9, 3, name='dark_oak_planks')
    crate(0, 1, -5, -4, 2, name='dark_oak_planks'); crate(0, 1, 4, 5, 2); crate(10, 11, -20, -19, 2); crate(10, 11, 19, 20, 2)
    crate(26, 27, -20, -19, 1); crate(26, 27, 19, 20, 1)
    # статуя-постамент на западе центра
    wall(-5, -3, -1, 1, 2, 'chiseled_sandstone'); wall(-4, -4, 0, 0, 4, 'cut_sandstone', base=2)

    # ======== площадка A (пол на уровне 2) и лестницы
    fill(9, 41, -43, -28, 1, 2, 'sandstone')
    paint(9, 41, -43, -28, 2, lambda x, z: checker(x, z, 'cut_sandstone', 'smooth_sandstone', 'sandstone'))
    stairs(7, 8, -43, -28, 'e', 0, 2, 'sandstone')
    stairs(42, 43, -39, -29, 'w', 0, 2, 'sandstone')
    # лестница из центра на A: проход в стене z −27..−26
    carve(20, 27, -27, -26, 1, 7)
    fill(20, 27, -27, -27, 1, 2, 'sandstone'); paint(20, 27, -27, -27, 2, lambda x, z: 'cut_sandstone')
    stairs(20, 27, -26, -25, 'n', 0, 2, 'sandstone')
    carve(34, 36, -27, -26, 3, 4)        # окно-«прыжок» с A в центр
    carve(28, 33, -27, -26, 4, 7)        # борт A: низкая стенка, видно центр
    # площадка: буква, яма, укрытия
    site_floor('A', 27, -35, 6, 2, 'orange_terracotta', 'yellow_concrete', 'white_concrete')
    carve(29, 33, -39, -36, 1, 2)       # яма
    paint(29, 33, -39, -36, 0, lambda x, z: 'orange_terracotta')
    stairs(28, 29, -39, -36, 'w', 0, 2, 'sandstone')
    # «Ящики А»
    crate(22, 25, -42, -41, 2, base=2); crate(22, 23, -40, -39, 2, base=2); crate(25, 25, -40, -40, 1, base=2)
    crate(34, 35, -33, -32, 2, base=2, name='dark_oak_planks')
    wall(14, 18, -36, -35, 2, 'stone_bricks', base=2); wall(14, 15, -32, -31, 3, 'stone_bricks', base=2)
    for (px, pz) in ((14, -41), (14, -30), (38, -31)):
        wall(px, px + 1, pz, pz + 1, 5, 'stripped_spruce_log', base=2)
    # балкон
    fill(38, 45, -43, -37, 3, 6, 'cut_sandstone')
    paint(38, 45, -43, -37, 6, lambda x, z: checker(x, z, 'smooth_sandstone', 'cut_sandstone'))
    stairs(34, 37, -43, -41, 'e', 2, 4, 'sandstone')
    wall(38, 45, -37, -37, 1, 'cut_sandstone', base=6); wall(38, 38, -40, -37, 1, 'cut_sandstone', base=6)
    wall(45, 45, -43, -37, 1, 'cut_sandstone', base=6)
    # арка A
    wall(3, 4, -43, -42, 8, 'chiseled_sandstone'); wall(3, 4, -29, -28, 8, 'chiseled_sandstone'); fill(3, 4, -41, -30, 7, 8, 'cut_sandstone')
    carve(46, 47, -36, -30, 1, 5)

    # ======== площадка B (пол на уровне −2), лестницы и борт
    carve(9, 45, 28, 43, -1, 0)
    paint(9, 45, 28, 43, -2, lambda x, z: checker(x, z, 'red_sandstone', 'cut_red_sandstone', 'smooth_red_sandstone'))
    stairs(7, 8, 31, 40, 'w', -2, 2, 'red_sandstone')
    stairs(44, 45, 28, 35, 'e', -2, 2, 'red_sandstone')
    carve(20, 27, 26, 27, 1, 7)
    stairs(20, 27, 27, 28, 'n', -2, 2, 'red_sandstone')   # вход с центра вниз: ступени вверх на север
    carve(34, 36, 26, 27, 1, 3)          # окно-«прыжок» из центра в B
    carve(28, 33, 26, 27, 2, 7)          # борт B: низкая стенка
    fill(28, 33, 22, 25, 1, 1, 'cut_sandstone'); fill(28, 33, 22, 25, 1, 1, 'cut_sandstone')
    paint(28, 33, 22, 25, 1, lambda x, z: 'cut_sandstone')
    stairs(28, 33, 21, 21, 's', 0, 1, 'sandstone')       # заход на борт
    site_floor('B', 27, 35, 6, -2, 'orange_terracotta', 'yellow_concrete', 'white_concrete')
    # склад Б
    wall(36, 45, 36, 43, 9, 'cut_sandstone', base=-2)
    carve(38, 44, 38, 42, -1, 6)         # внутри (пол −2 остаётся)
    paint(38, 44, 38, 42, -2, lambda x, z: checker(x, z, 'dark_oak_planks', 'spruce_planks'))
    fill(38, 44, 38, 42, 7, 8, 'smooth_sandstone'); put(41, 40, 7, 'sea_lantern'); put(39, 39, 7, 'sea_lantern'); put(43, 41, 7, 'sea_lantern')
    carve(36, 37, 39, 41, -1, 2)         # дверь на площадку
    carve(40, 42, 36, 37, 0, 1)          # окошко на борт-стену (север)
    crate(39, 40, 41, 42, 2, base=-2); crate(43, 44, 38, 39, 3, base=-2, name='dark_oak_planks')
    # ящики, колонны, мостик на площадке B
    crate(18, 18, 39, 40, 1, base=-2); crate(14, 17, 38, 41, 2, base=-2); crate(14, 15, 38, 39, 3, base=-2); crate(17, 17, 38, 38, 3, base=-2, name='dark_oak_planks')
    for (px, pz, h) in ((14, 30, 5), (30, 41, 5), (38, 31, 5), (20, 33, 3)):
        wall(px, px + 1, pz, pz + 1, h, 'stripped_spruce_log' if h > 3 else 'stone_bricks', base=-2)
    wall(16, 19, 33, 33, 2, 'red_sandstone', base=-2)
    fill(14, 26, 42, 43, -1, 1, 'red_sandstone'); paint(14, 26, 42, 43, 1, lambda x, z: 'cut_red_sandstone')
    stairs(27, 29, 42, 43, 'w', -2, 3, 'red_sandstone')
    wall(14, 26, 43, 43, 1, 'red_sandstone', base=1)
    # арка B
    wall(3, 4, 28, 29, 8, 'chiseled_sandstone'); wall(3, 4, 42, 43, 8, 'chiseled_sandstone'); fill(3, 4, 30, 41, 7, 8, 'cut_sandstone')
    carve(46, 47, 30, 36, 1, 5)

    # ======== казармы КТ (над центром востока) и коридор КТ
    for (z0, z1, dz0, dz1) in ((-25, -6, -7, -6), (6, 25, 6, 7)):
        wall(31, 45, z0, z1, 8, 'cut_sandstone'); fill(31, 45, z0, z1, 4, 4, 'chiseled_sandstone')
        carve(33, 43, z0 + 2, z1 - 2, 1, 8)
        fill(33, 43, z0 + 2, z1 - 2, 7, 8, 'smooth_sandstone')
        for lx in (35, 41):
            for lz in range(z0 + 4, z1 - 3, 6): put(lx, lz, 7, 'sea_lantern')
    paint(33, 43, -23, -8, 0, lambda x, z: checker(x, z, 'spruce_planks', 'dark_oak_planks'))
    paint(33, 43, 8, 23, 0, lambda x, z: checker(x, z, 'spruce_planks', 'dark_oak_planks'))
    carve(36, 38, -7, -6, 1, 4); carve(36, 38, 6, 7, 1, 4)       # двери на коридор
    carve(31, 32, -20, -17, 2, 4); carve(31, 32, 17, 20, 2, 4)   # окна на центр
    crate(35, 36, -18, -17, 2); crate(40, 41, -13, -12, 2, name='dark_oak_planks'); wall(38, 42, -20, -20, 2, 'stone_bricks')
    crate(35, 36, 17, 18, 2, name='dark_oak_planks'); crate(40, 41, 12, 13, 2); wall(38, 42, 20, 20, 2, 'stone_bricks')
    # коридор КТ
    crate(38, 39, -3, -2, 2); crate(38, 39, 2, 3, 2, name='dark_oak_planks'); wall(42, 43, -1, 1, 3, 'cut_sandstone')

    # ======== дворы баз
    for sx in (-1, 1):
        base_x = sx * 56
        crate(base_x - 1, base_x + 1, -20, -19, 2); crate(base_x - 1, base_x + 1, 19, 20, 2, name='dark_oak_planks')
        wall(base_x + sx * 2, base_x + sx * 2 + 1, -28, -26, 3, 'stone_bricks'); wall(base_x + sx * 2, base_x + sx * 2 + 1, 26, 28, 3, 'stone_bricks')
        for z in (-14, -9, 9, 14): wall(base_x + sx * 3, base_x + sx * 3 + 1, z, z + 1, 4, 'stripped_spruce_log')
        crate(base_x - sx * 2, base_x - sx * 2 + 1, -32, -31, 2); crate(base_x - sx * 2, base_x - sx * 2 + 1, 31, 32, 2)

    # свет в лонгах и центре: фонарные столбы
    for (lx, lz) in [(-40, -30), (-20, -30), (0, -30), (-40, 30), (-20, 30), (0, 30), (-8, -14), (-8, 14), (12, -22), (12, 22), (28, -14), (28, 14)]:
        pass

build()

# ---------------------------------------------------------------- метаданные
ZONES = [  # name, x0, x1, z0, z1, (приоритет по порядку)
    ("Балкон А", 38, 45, -43, -37), ("Яма А", 28, 33, -39, -36), ("Ящики А", 22, 25, -42, -39),
    ("Площадка А", 14, 42, -43, -28), ("Арка А", 3, 13, -43, -28), ("Ступени А", 20, 27, -27, -22),
    ("Склад Б", 36, 45, 36, 43), ("Мостик Б", 14, 29, 42, 43), ("Борт Б", 28, 33, 21, 25),
    ("Площадка Б", 14, 42, 28, 43), ("Арка Б", 3, 13, 28, 43), ("Ступени Б", 20, 27, 22, 27),
    ("Антресоль", -41, -34, -25, -23), ("Склад", -43, -13, -27, -6), ("Тоннели", -43, -13, 6, 27),
    ("Двери мида", -29, -24, -5, 5), ("Мост", 2, 30, -2, 2), ("Казарма А", 31, 45, -25, -6), ("Казарма Б", 31, 45, 6, 25),
    ("Коридор КТ", 31, 45, -5, 5), ("Мид", -45, -7, -5, 5),
    ("Ворота лонга", -8, -4, -43, -28), ("Лонг", -45, 6, -43, -28),
    ("Нижний", -45, 6, 28, 43), ("Центр", -12, 30, -25, 25), ("Развязка", -13, -6, -26, 26),
    ("Двор Т", -60, -47, -44, 44), ("Двор КТ", 47, 60, -44, 44),
    ("Переулок Т", -46, -43, -26, 26), ("Подход КТ", 44, 46, -43, 43),
]
SITES = [('A', 27, -35, 6, 2), ('B', 27, 35, 6, -2)]
SPAWN_T = [(-56, z, 0) for z in (-8, -4, 0, 4, 8)]
SPAWN_CT = [(56, z, 0) for z in (-8, -4, 0, 4, 8)]
# позиции для ботов: (x, z, уровень)
HOLD = {
    'A': [(41, -40, 6), (24, -41, 4), (31, -37, 0), (19, -33, 2), (14, -38, 2), (44, -33, 0), (36, -31, 2), (27, -30, 2)],
    'B': [(40, 40, -2), (41, 38, -2), (16, 39, 0), (20, 42, 1), (30, 23, 1), (20, 36, -2), (43, 33, -2), (22, 41, -2)],
    'M': [(12, 0, 0), (16, 0, 4), (24, 0, 4), (36, 0, 0), (22, 8, 0), (22, -8, 0), (7, 0, 0)],
}
ENTRY = {   # где террористы выходят к площадке
    'A': [(0, -35, 0), (-8, -34, 0), (23, -23, 0), (-28, -30, 0)],
    'B': [(0, 36, 0), (-8, 35, 0), (23, 23, 0), (-30, 33, 0)],
}
POST = {    # где терроры держат площадку после закладки
    'A': [(24, -41, 4), (19, -33, 2), (36, -31, 2), (30, -37, 0), (40, -40, 6), (14, -38, 2)],
    'B': [(16, 39, 0), (41, 40, -2), (20, 42, 1), (22, 41, -2), (20, 31, -2), (36, 30, -2)],
}

def zone_at(x, z):
    for n, a, b, c, d in ZONES:
        if a <= x <= b and c <= z <= d: return n
    return "Карта"

# ---------------------------------------------------------------- проверка проходимости
def surfaces(x, z):
    """Уровни стоянок (поверхность = lv+1) в колонке: блок опоры + два свободных блока над ним"""
    col = G[ix(x), iz(z)]
    res = []
    for l in range(LV0, LV1 - 2):
        m = col[il(l)]
        if m == 0 or MATNAME[m] == 'barrier': continue
        if col[il(l + 1)] == 0 or col[il(l + 1)] in STAIRS_IDS:
            if col[il(l + 1)] != 0: continue     # на ступень не встаём как на пол
            if col[il(l + 2)] == 0: res.append(l + 1)
    # ступени: стоять можно на ступени, если над ней два свободных
    for l in range(LV0, LV1 - 2):
        m = col[il(l)]
        if m in STAIRS_IDS and col[il(l + 1)] == 0 and col[il(l + 2)] == 0: res.append(l + 1)
    return sorted(set(res))

MATNAME = {}
STAIRS_IDS = set()
def analyse(verbose=True):
    global MATNAME, STAIRS_IDS
    MATNAME = {v[0]: k for k, v in MATS.items()}
    STAIRS_IDS = {v[0] for k, v in MATS.items() if '_stairs_' in k}
    surf = {}
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            for s in surfaces(x, z): surf[(x, z, s)] = 1
    def start(x, z, lv=0):
        return (x, z, lv + 1)
    seen = {}
    q = collections.deque()
    for p in SPAWN_T: q.append(start(*p)); seen[start(*p)] = 0
    while q:
        x, z, s = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (X0 <= nx <= X1 and Z0 <= nz <= Z1): continue
            for t in range(s - 3, s + 2):       # падение до 3, подъём до 1
                if (nx, nz, t) in surf and (nx, nz, t) not in seen:
                    # между блоками: на пути ни стена
                    # проверим свободность на высоте max(s,t) .. +2 в обеих колонках уже есть — достаточно
                    seen[(nx, nz, t)] = seen[(x, z, s)] + 1
                    q.append((nx, nz, t))
    reach = set(seen)
    total = set(surf)
    unreachable = total - reach
    return surf, seen, unreachable

def report():
    surf, seen, unreach = analyse()
    print('поверхностей:', len(surf), 'достижимо с базы Т:', len(seen), 'недостижимо:', len(unreach))
    for p in SPAWN_CT:
        k = (p[0], p[1], p[2] + 1)
        print('КТ-спавн достижим' if k in seen else 'КТ-спавн НЕ достижим', p)
    for c, cx, cz, r, lv in SITES:
        k = (cx, cz, lv + 1)
        print('точка', c, 'достижима, путь Т:', seen.get(k, 'НЕТ'))
    # путь КТ → площадки
    ct = {}
    q = collections.deque()
    for p in SPAWN_CT: k = (p[0], p[1], p[2] + 1); ct[k] = 0; q.append(k)
    while q:
        x, z, s = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for t in range(s - 3, s + 2):
                if (nx, nz, t) in surf and (nx, nz, t) not in ct:
                    ct[(nx, nz, t)] = ct[(x, z, s)] + 1; q.append((nx, nz, t))
    for c, cx, cz, r, lv in SITES:
        print('точка', c, 'путь КТ:', ct.get((cx, cz, lv + 1), 'НЕТ'))
    for name, lst in list(HOLD.items()) + list(ENTRY.items()) + list(POST.items()):
        pass
    bad = []
    for grp in (HOLD, ENTRY, POST):
        for name, lst in grp.items():
            for (x, z, lv) in lst:
                if (x, z, lv + 1) not in surf: bad.append((name, x, z, lv))
                elif (x, z, lv + 1) not in seen: bad.append((name, x, z, lv, 'недостижима'))
    print('плохие точки ботов:', bad)
    return surf, seen, unreach

def write(path):
    analyse()
    out = []
    out.append('# карта «Дюны»: сгенерировано tools/map_gen/dust.py')
    out.append(f'BOUNDS {X0} {X1} {Z0} {Z1} {LV0}')
    for k, (i, st) in MATS.items(): out.append(f'B {i} {st}')
    for n, a, b, c, d in ZONES: out.append(f'Z {n}|{a}|{b}|{c}|{d}')
    for c, cx, cz, r, lv in SITES: out.append(f'SITE {c} {cx} {cz} {r} {lv}')
    for p in SPAWN_T: out.append('SPAWN T %d %d %d' % p)
    for p in SPAWN_CT: out.append('SPAWN CT %d %d %d' % p)
    for g, d in (('HOLD', HOLD), ('ENTRY', ENTRY), ('POST', POST)):
        for k, lst in d.items():
            for p in lst: out.append('%s %s %d %d %d' % (g, k, *p))
    # колонки: RLE от LV0 до верхнего непустого
    for x in range(GX0, GX1 + 1):
        for z in range(GZ0, GZ1 + 1):
            col = G[ix(x), iz(z)]
            nz = np.nonzero(col)[0]
            if len(nz) == 0: continue
            top = nz[-1]
            runs = []; i = 0
            while i <= top:
                m = col[i]; j = i
                while j <= top and col[j] == m: j += 1
                runs.append(f'{m}:{j - i}'); i = j
            out.append(f'C {x} {z} ' + ' '.join(runs))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print('записано', path, len(out), 'строк,', os.path.getsize(path) // 1024, 'КБ')

# ---------------------------------------------------------------- превью
def preview(path):
    from PIL import Image, ImageDraw, ImageFont
    S = 10
    im = Image.new('RGB', ((GX1 - GX0 + 1) * S, (GZ1 - GZ0 + 1) * S))
    px = im.load()
    COL = {'barrier': (0, 0, 0)}
    def colour(name):
        n = name
        if 'terracotta' in n: return {'red_terracotta': (150, 70, 55), 'blue_terracotta': (80, 100, 150), 'light_blue_terracotta': (110, 140, 170), 'orange_terracotta': (210, 110, 50), 'yellow_terracotta': (190, 150, 50), 'brown_terracotta': (110, 80, 60)}.get(n, (160, 110, 85))
        if 'red_sand' in n: return (190, 100, 60)
        if 'sand' in n: return (225, 205, 150)
        if 'concrete' in n: return {'white_concrete': (245, 245, 245), 'yellow_concrete': (240, 200, 30), 'red_concrete': (180, 40, 40), 'black_concrete': (20, 20, 25), 'orange_concrete': (230, 110, 20), 'light_gray_concrete': (150, 150, 150)}.get(n, (120, 120, 120))
        if 'planks' in n: return (140, 100, 60) if 'spruce' in n else (80, 60, 40)
        if 'log' in n: return (100, 75, 45)
        if 'stone' in n or 'andesite' in n or 'deepslate' in n: return (150, 150, 155)
        if 'lantern' in n: return (255, 245, 180)
        return (200, 100, 200)
    for x in range(GX0, GX1 + 1):
        for z in range(GZ0, GZ1 + 1):
            col = G[ix(x), iz(z)]
            nz = np.nonzero(col)[0]
            top = nz[-1] if len(nz) else 0
            # верхний непустой кроме барьера
            for t in range(top, -1, -1):
                if col[t] and MATNAME[col[t]] != 'barrier': top = t; break
            m = col[top]
            lv = top + LV0
            c = colour(MATNAME[m].replace('_stairs_e', '').replace('_stairs_w', '').replace('_stairs_n', '').replace('_stairs_s', ''))
            k = 0.55 + 0.045 * max(-4, min(10, lv))
            c = tuple(int(max(0, min(255, v * k))) for v in c)
            for i in range(S):
                for j in range(S):
                    px[(x - GX0) * S + i, (z - GZ0) * S + j] = c
    d = ImageDraw.Draw(im)
    try: f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 11)
    except Exception: f = None
    for n, a, b, c, dd in ZONES:
        if n in ('Двор Т', 'Двор КТ', 'Карта'): continue
        cx, cz = (a + b) / 2, (c + dd) / 2
        d.text(((cx - GX0) * S - 3 * len(n), (cz - GZ0) * S - 5), n, fill=(20, 20, 20), font=f)
    d.text((8 * S, 40 * S), 'Двор Т', fill=(255, 255, 255), font=f); d.text((108 * S, 40 * S), 'Двор КТ', fill=(255, 255, 255), font=f)
    for k, lst in HOLD.items():
        for (x, z, lv) in lst: d.ellipse([(x - GX0) * S - 3, (z - GZ0) * S - 3, (x - GX0) * S + 3, (z - GZ0) * S + 3], outline=(0, 90, 255), width=2)
    for k, lst in ENTRY.items():
        for (x, z, lv) in lst: d.ellipse([(x - GX0) * S - 3, (z - GZ0) * S - 3, (x - GX0) * S + 3, (z - GZ0) * S + 3], outline=(255, 120, 0), width=2)
    im.save(path)

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '../../src/main/resources/data/combo-rush/maps/dust.map'
    report()
    write(out)
    preview('/tmp/dust_preview.png')
