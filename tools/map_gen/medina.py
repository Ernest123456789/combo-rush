#!/usr/bin/env python3
"""Карта «Медина» v2 — объёмный макет (блоки). x → восток, z → юг, y — уровень (0 — блок пола улиц, стоят на 1).
Всё, что не вырезано, — массив зданий. Вырезаем улицы/площади/туннели/интерьеры, ставим лестницы, мосты, укрытия, декор.
Выход: проверка проходимости и время пробежки; превью: план по высотам, изометрия, разрезы."""
import collections, math, sys
import numpy as np

GW, GH = 172, 126
YMIN, YMAX = -7, 24
NY = YMAX - YMIN + 1
M = np.zeros((GW, GH, NY), dtype=np.int16)      # 0 — воздух
NAMES = ['air']; IDS = {'air': 0}
def mid(name):
    if name not in IDS: IDS[name] = len(NAMES); NAMES.append(name)
    return IDS[name]
def Y(y): return y - YMIN

# ------------------------------------------------------------- геометрия
def inside(poly, x, y):
    c = False; n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1: c = not c
    return c
def cells(poly):
    xs = [p[0] for p in poly]; zs = [p[1] for p in poly]
    out = []
    for z in range(max(0, int(min(zs))), min(GH, int(max(zs)) + 1)):
        for x in range(max(0, int(min(xs))), min(GW, int(max(xs)) + 1)):
            if inside(poly, x + 0.5, z + 0.5): out.append((x, z))
    return out
def rect(x0, x1, z0, z1): return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if 0 <= x < GW and 0 <= z < GH]

def tile(x, z, mats):
    h = (x * 7 + z * 13 + (x // 3) * 5) % len(mats)
    return mats[h]
FLOOR = {'sand': ['f_sand', 'f_sand', 'f_sand2', 'f_sand3'], 'tsp': ['f_tsp', 'f_tsp2', 'f_sand'], 'ctsp': ['f_ctsp', 'f_ctsp2'],
         'market': ['f_tile', 'f_tile2', 'f_sand'], 'cobble': ['f_cob', 'f_cob2', 'f_cob3'], 'siteA': ['f_siteA'], 'siteB': ['f_siteB', 'f_siteB2'],
         'tunnel': ['f_tun', 'f_tun2'], 'wood': ['f_wood', 'f_wood2'], 'roof': ['f_roof', 'f_roof2'], 'garden': ['f_grass', 'f_path', 'f_grass'],
         'stone': ['f_stone', 'f_stone2']}

def carve(cl, floor, top, fl='sand'):
    """пол на уровне floor, воздух floor+1..top (top — включительно)"""
    for x, z in cl:
        M[x, z, Y(floor)] = mid(tile(x, z, FLOOR[fl]))
        M[x, z, Y(floor) + 1:Y(top) + 1] = 0
def air(cl, y0, y1):
    for x, z in cl: M[x, z, Y(y0):Y(y1) + 1] = 0
def put(cl, y0, y1, name):
    i = mid(name)
    for x, z in cl: M[x, z, Y(y0):Y(y1) + 1] = i

STAIR = set()
def stairs(x0, x1, z0, z1, d, base, n, mat=None):
    mat = mat or 'stair_' + d
    """n ступеней вверх в сторону d; нижняя ступень на уровне base+1. Под ступенями — сплошное, над ними — 3 блока воздуха"""
    for i in range(n):
        if d == 'e': cl = rect(x0 + i, x0 + i, z0, z1)
        elif d == 'w': cl = rect(x1 - i, x1 - i, z0, z1)
        elif d == 's': cl = rect(x0, x1, z0 + i, z0 + i)
        else: cl = rect(x0, x1, z1 - i, z1 - i)
        lv = base + 1 + i
        put(cl, base, lv - 1, 'mass_fill')
        put(cl, lv, lv, mat)
        air(cl, lv + 1, lv + 3)
        for c in cl: STAIR.add((c[0], c[1], lv))

def bridge(cl, lv, rails_x=None, rails_z=None, mat='f_bridge'):
    put(cl, lv, lv, mat); air(cl, lv + 1, lv + 3)

def crate(x0, x1, z0, z1, base, h, mat='prop_crate'):
    put(rect(x0, x1, z0, z1), base + 1, base + h, mat)

def palm(x, z, base, h=6):
    put([(x, z)], base + 1, base + h, 'prop_log')
    put(rect(x - 2, x + 2, z, z) + rect(x, x, z - 2, z + 2), base + h, base + h, 'prop_leaves')
    put(rect(x - 1, x + 1, z - 1, z + 1), base + h + 1, base + h + 1, 'prop_leaves')

def stall(x0, x1, z0, z1, base, color):
    """лоток с навесом: прилавок вдоль северной стороны, столбы по углам, тент на высоте base+4"""
    put(rect(x0, x1, z0, z0), base + 1, base + 1, 'prop_crate')
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)): put([(x, z)], base + 1, base + 3, 'prop_log')
    put(rect(x0, x1, z0, z1), base + 4, base + 4, 'awn_' + color)

def parapet(cl_set, lv, every=0):
    """низкий бортик по краю площадки (там, где сосед — не эта площадка)"""
    s = set(cl_set); n = 0
    for x, z in cl_set:
        if any((x + dx, z + dz) not in s for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            n += 1
            if every and n % every == 0: continue
            if M[x, z, Y(lv)] != 0 and M[x, z, Y(lv) + 1] == 0: M[x, z, Y(lv) + 1] = mid('rail')

# ------------------------------------------------------------- массив зданий с разной высотой крыш
rng = np.random.default_rng(7)
for bx in range(0, GW, 9):
    for bz in range(0, GH, 9):
        h = int(rng.integers(7, 12))
        mat = ['mass_a', 'mass_b', 'mass_c'][int(rng.integers(0, 3))]
        M[bx:bx + 9, bz:bz + 9, :Y(h) + 1] = mid(mat)
        if rng.random() < 0.12:                          # купол
            cx, cz = bx + 4, bz + 4
            for r, y in ((3, h + 1), (2, h + 2), (1, h + 3)):
                put(rect(cx - r, cx + r, cz - r, cz + r), y, y, 'dome')
M[:, :, :Y(-6) + 1] = mid('rock')

ZONES = {}   # имя → (клетки, уровень пола)
ZPOLY = {}
def Z(name, poly, floor, top, fl='sand'):
    is_poly = len(poly) <= 12 and all(isinstance(p, tuple) for p in poly) and name not in ('Колодец', 'Переход')
    cl = cells(poly) if is_poly else poly
    if is_poly: ZPOLY[name] = poly
    else:
        xs = [c[0] for c in cl]; zs = [c[1] for c in cl]
        ZPOLY[name] = [(min(xs), min(zs)), (max(xs) + 1, min(zs)), (max(xs) + 1, max(zs) + 1), (min(xs), max(zs) + 1)]
    carve(cl, floor, top, fl); ZONES[name] = (cl, floor); return cl

OPEN = YMAX
# --- крыши (сначала: улицы потом прорежут их там, где перекрываются)
ROOF = Z('Крыши', [(46, 37), (66, 34), (68, 48), (58, 63), (43, 61)], 7, OPEN, 'roof')
GAL = Z('Галерея', [(87, 76), (107, 74), (108, 87), (89, 88)], 4, OPEN, 'stone')
BAL_POLY = [(106, 4), (124, 6), (124, 20), (107, 20)]
BAL = Z('Балкон А', BAL_POLY, 8, OPEN, 'stone')
# --- улицы и площади
Z('База Т', [(6, 62), (30, 54), (42, 70), (40, 94), (18, 100), (4, 84)], 0, OPEN, 'tsp')
Z('Верхняя улица', [(13, 60), (25, 58), (27, 47), (15, 47)], 0, OPEN)
Z('Рынок', [(8, 22), (40, 16), (50, 28), (49, 36), (46, 45), (14, 45), (6, 36)], 2, OPEN, 'market')
Z('Переулок', [(40, 46), (50, 45), (55, 76), (45, 78)], 0, 4, 'cobble')                 # подворотня: потолок на 5, над ней — крыши
Z('Лонг А', [(44, 24), (78, 17), (81, 29), (47, 37)], 2, OPEN)
Z('Аллея', [(38, 70), (60, 69), (63, 82), (40, 90)], 0, OPEN, 'cobble')
Z('Мид', [(58, 67), (104, 60), (106, 73), (60, 82)], 0, OPEN, 'cobble')
Z('Лестница', [(83, 33), (92, 33), (94, 58), (85, 58)], 4, OPEN, 'stone')
Z('Площадка А', [(77, 6), (108, 4), (114, 32), (80, 36)], 4, OPEN, 'siteA')
Z('Подъём КТ (верх)', [(111, 22), (124, 21), (125, 29), (112, 30)], 4, OPEN, 'stone')
Z('Подъём КТ', [(112, 33), (125, 31), (130, 44), (118, 47)], 1, OPEN)
Z('База КТ', [(121, 46), (147, 42), (156, 64), (132, 74), (118, 66)], 1, OPEN, 'ctsp')
Z('Ворота КТ', [(103, 61), (121, 55), (124, 66), (118, 80), (110, 80), (106, 73)], 1, OPEN, 'cobble')
Z('Сад', [(121, 66), (134, 66), (131, 86), (117, 85)], 0, OPEN, 'garden')
Z('Площадка Б', [(86, 88), (117, 84), (121, 112), (90, 116)], -2, OPEN, 'siteB')
carve(BAL, 8, OPEN, 'stone')
# галерея снова сверху (улицы могли прорезать край) и кухня под ней
carve(GAL, 4, OPEN, 'stone')
Z('Кухня', [(95, 76), (106, 75), (107, 86), (96, 87)], 0, 3, 'wood')
# склад Б: два этажа
Z('Склад Б', [(118, 94), (133, 92), (135, 110), (120, 112)], -2, 2, 'wood')
SKL2 = cells([(118, 94), (133, 92), (135, 110), (120, 112)])
carve(SKL2, 3, 7, 'wood')
ZONES['Склад Б (2 этаж)'] = (SKL2, 3)
# переход Сад → 2 этаж склада
Z('Переход', rect(124, 127, 85, 93), 0, 4, 'wood')
# туннели
Z('Тоннели', [(30, 90), (63, 90), (67, 103), (34, 106)], -4, -1, 'tunnel')
Z('Колодец', rect(44, 50, 84, 91), -4, -1, 'tunnel')
Z('Нижние тоннели', [(62, 91), (88, 87), (91, 98), (66, 103)], -4, -1, 'tunnel')

# ------------------------------------------------------------- переходы по высоте
stairs(15, 26, 45, 46, 'n', 0, 2)                    # Верхняя улица → Рынок (+2)
stairs(41, 49, 45, 46, 'n', 0, 2)                    # Переулок → Рынок
stairs(44, 48, 38, 40, 'e', 2, 5)                    # Рынок → Крыши (+7)
stairs(61, 63, 33, 37, 's', 2, 5)                    # Крыши ↔ Лонг А
stairs(77, 78, 18, 28, 'e', 2, 2)                    # Лонг А → площадка А (+4)
stairs(102, 105, 8, 10, 'e', 4, 4)                   # A → Балкон (+8)
stairs(116, 119, 20, 23, 'n', 4, 4)                  # Подъём КТ → Балкон
stairs(112, 124, 30, 32, 'n', 1, 3)                  # Подъём КТ: +1 → +4
stairs(86, 89, 58, 61, 'n', 0, 4)                    # Мид → Лестница (+4)
stairs(104, 104, 62, 71, 'e', 0, 1)                  # Мид → Ворота КТ (+1)
stairs(121, 124, 66, 66, 'n', 0, 1)                  # Сад → Ворота КТ
stairs(107, 109, 76, 78, 'w', 1, 3)                  # Ворота КТ → Галерея (+4)
stairs(98, 103, 85, 87, 'n', -2, 2)                  # Кухня ↓ Площадка Б
stairs(117, 120, 84, 87, 'n', -2, 2)                 # Сад ↓ Площадка Б
stairs(124, 127, 88, 90, 's', 0, 3)                  # Переход ↑ 2 этаж склада
stairs(121, 125, 96, 97, 'e', -2, 5)                 # склад: первый этаж → второй
stairs(32, 37, 95, 98, 'n', -4, 4)                   # База Т ↓ Тоннели (−4)
stairs(86, 89, 88, 91, 'e', -4, 2)                   # Нижние тоннели ↑ Площадка Б (−2)
stairs(76, 79, 64, 67, 'e', -2, 2)                   # Яма Мида: спуск/подъём
# Яма Мида
carve(rect(80, 85, 63, 70), -2, OPEN, 'cobble')
stairs(86, 87, 63, 70, 'e', -2, 2)
# Мост над Мидом: от Лестницы (+4) к Галерее (+4)
MOST = rect(89, 93, 58, 77)
bridge(MOST, 4)
for x, z in MOST:
    if x in (89, 93) and 62 <= z <= 74: M[x, z, Y(5)] = mid('rail')
# Колодец: дыра в полу Аллеи — спрыгнуть в тоннель (−4)
air(rect(46, 47, 85, 86), -3, 0)
# окно второго этажа склада на площадку Б и дверь первого этажа
air(rect(117, 119, 100, 104), 4, 5)
air(rect(117, 120, 98, 102), -1, 0)
# окно из Лестницы на площадку А не нужно: Лестница сама выходит на А

# ------------------------------------------------------------- свет под крышами: фонари в потолке
for n, (y0, y1) in (('Тоннели', (-4, -1)), ('Нижние тоннели', (-4, -1)), ('Колодец', (-4, -1)), ('Переулок', (0, 4)), ('Кухня', (0, 3)),
                     ('Склад Б', (-2, 2)), ('Склад Б (2 этаж)', (3, 7)), ('Переход', (0, 4))):
    for x, z in ZONES[n][0]:
        if x % 5 == 2 and z % 5 == 2 and M[x, z, Y(y1) + 1] != 0: M[x, z, Y(y1) + 1] = mid('lamp')
# ------------------------------------------------------------- бортики и перила
parapet(BAL, 8, every=3)
parapet(GAL, 4, every=3)
parapet(ROOF, 7, every=4)

# ------------------------------------------------------------- укрытия и интересные места
# База Т: грузовик, ящики, пальмы
put(rect(10, 16, 74, 76), 1, 3, 'prop_truck'); put(rect(10, 11, 74, 76), 1, 3, 'prop_truck_cab')
crate(30, 31, 60, 61, 0, 2); crate(24, 25, 88, 89, 0, 1); palm(8, 66, 0); palm(28, 94, 0, 5)
# Рынок: колодец, лотки с навесами, тележка
put(rect(25, 27, 30, 32), 3, 3, 'prop_stone'); put([(26, 31)], 3, 3, 'water'); put([(26, 31)], 2, 2, 'water')
for x, z in ((25, 30), (27, 30), (25, 32), (27, 32)): put([(x, z)], 4, 6, 'prop_log')
put(rect(25, 27, 30, 32), 7, 7, 'awn_red')
stall(12, 17, 24, 27, 2, 'red'); stall(31, 36, 20, 23, 2, 'blue'); stall(14, 19, 37, 40, 2, 'green'); stall(35, 40, 36, 39, 2, 'yellow')
crate(42, 43, 22, 23, 2, 2); crate(20, 21, 31, 31, 2, 1, 'prop_barrel')
# Лонг А: разбитый автобус, обломок стены, ящики, арка
put(rect(56, 65, 21, 23), 3, 5, 'prop_bus'); air(rect(60, 61, 21, 23), 5, 5)
put(rect(70, 71, 19, 23), 3, 4, 'mass_b'); crate(50, 51, 33, 34, 2, 1)
put(rect(75, 76, 16, 30), 8, 10, 'arch')
# Крыши: печные трубы и бельё
crate(54, 54, 44, 44, 7, 2, 'mass_c'); crate(62, 62, 40, 40, 7, 2, 'mass_c'); crate(56, 57, 52, 53, 7, 1)
# Площадка А: пирамида ящиков, колонны, тележка
crate(92, 96, 14, 18, 4, 1); crate(93, 95, 15, 17, 4, 2); crate(94, 94, 16, 16, 4, 3)
for x, z in ((84, 12), (104, 26), (86, 30)): put([(x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)], 5, 9, 'pillar')
crate(99, 101, 22, 23, 4, 1, 'prop_cart'); crate(82, 83, 20, 21, 4, 2)
# Лестница: ящики на площадке
crate(84, 85, 40, 41, 4, 1); crate(90, 91, 50, 50, 4, 1)
# Мид: ящики, повозка
crate(66, 67, 73, 74, 0, 2); crate(97, 98, 64, 65, 0, 1); crate(72, 73, 69, 69, 0, 1, 'prop_cart')
# Тоннели: опоры, фонари, вагонетка
for x in range(36, 88, 7):
    for z in (93, 100):
        if M[x, z, Y(-3)] == 0: put([(x, z)], -3, -1, 'pillar_tun')
crate(52, 53, 96, 97, -4, 1, 'prop_cart'); crate(74, 75, 92, 93, -4, 1)
# Площадка Б: ящики, бочки, разбитая машина
crate(98, 101, 96, 98, -2, 2); crate(99, 100, 96, 96, -2, 3); crate(108, 110, 106, 107, -2, 1); crate(93, 94, 106, 108, -2, 2, 'prop_barrel')
put(rect(104, 107, 90, 91), -1, 0, 'prop_car')
# Галерея: ящики-укрытия
crate(96, 97, 80, 80, 4, 1); crate(101, 102, 82, 82, 4, 1)
# Кухня: столы
crate(99, 100, 79, 79, 0, 1, 'prop_wood'); crate(103, 104, 82, 83, 0, 1, 'prop_wood')
# Склад Б: стеллажи
crate(127, 131, 102, 103, -2, 3); crate(128, 129, 106, 107, -2, 2); crate(126, 128, 104, 105, 3, 1)
# База КТ: минарет, пальмы, ящики
put(rect(147, 150, 48, 51), 2, 21, 'minaret'); put(rect(146, 151, 47, 52), 18, 18, 'minaret_top')
palm(128, 50, 1); palm(140, 66, 1, 5); crate(134, 135, 54, 55, 1, 2)
# Сад: фонтан, пальмы, клумбы
put(rect(123, 127, 73, 77), 1, 1, 'prop_stone'); put(rect(124, 126, 74, 76), 1, 1, 'water'); put([(125, 75)], 2, 3, 'prop_stone')
palm(120, 70, 0, 5); palm(130, 82, 0, 6); crate(128, 129, 69, 69, 0, 1, 'prop_hedge')

# ------------------------------------------------------------- снаружи — дюны: здания только у игровых улиц (контур не прямоугольный)
FOOT = np.zeros((GW, GH), bool)
for n, (cl, fl) in ZONES.items():
    for x, z in cl: FOOT[x, z] = True
R = 5
near = np.zeros_like(FOOT)
for dx in range(-R, R + 1):
    for dz in range(-R, R + 1):
        if dx * dx + dz * dz > R * R: continue
        near |= np.roll(np.roll(FOOT, dx, 0), dz, 1)
for x in range(GW):
    for z in range(GH):
        if not near[x, z]:
            hgt = int(-1 + 2.2 * (math.sin(x * 0.11) + math.cos(z * 0.13 + x * 0.05)))
            M[x, z, Y(hgt) + 1:] = 0
            M[x, z, Y(-6) + 1:Y(hgt) + 1] = mid('dune')

# ------------------------------------------------------------- метаданные
SITES = {'A': (95, 22, 4), 'B': (103, 100, -2)}
SPAWN_T = (30, 74, 0); SPAWN_CT = (128, 56, 1)
MIDP = (88, 68, 0)
SOLIDISH = None

def walkable_surfaces():
    """(x, z, s): s — уровень, на котором стоит игрок (над блоком s−1); над ним 2 блока воздуха; массивы зданий — не пол"""
    bad = {IDS.get(n) for n in NAMES if n.startswith('mass') or n in ('rock', 'dome', 'minaret', 'minaret_top', 'arch', 'water', 'prop_leaves', 'awn_red', 'awn_blue', 'awn_green', 'awn_yellow', 'pillar', 'pillar_tun', 'mass_fill')}
    out = set()
    for x in range(GW):
        for z in range(GH):
            col = M[x, z]
            for i in range(1, NY - 2):
                if col[i] == 0 and col[i + 1] == 0 and col[i - 1] != 0 and col[i - 1] not in bad:
                    out.add((x, z, i + YMIN))
    # массив под лестницей — тоже пол (ступени)
    return out

def bfs(surf, start):
    d = {start: 0.0}; q = collections.deque([start])
    while q:
        x, z, s = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for t in range(s + 1, s - 5, -1):
                if (nx, nz, t) in surf:
                    if t > s and M[x, z, Y(s) + 2] != 0: break      # для подъёма нужен воздух над головой
                    c = d[(x, z, s)] + (1.0 if t <= s else 1.25)
                    if c < d.get((nx, nz, t), 1e9):
                        d[(nx, nz, t)] = c; q.append((nx, nz, t))
                    break
    return d

def report():
    surf = walkable_surfaces()
    def snap(p):
        x, z, lv = p
        for s in range(lv + 1, lv + 4):
            if (x, z, s) in surf: return (x, z, s)
        return (x, z, lv + 1)
    st = snap(SPAWN_T); sc = snap(SPAWN_CT)
    dT = bfs(surf, st); dC = bfs(surf, sc)
    res = {}
    for nm, p in (('A', SITES['A']), ('B', SITES['B']), ('Мид', MIDP)):
        q = snap(p)
        res[nm] = (dT.get(q, float('nan')) / 5.6, dC.get(q, float('nan')) / 5.6)
    zone_ok = {}
    for n, (cl, fl) in ZONES.items():
        hit = sum(1 for (x, z) in cl[::7] if (x, z, fl + 1) in dT)
        zone_ok[n] = hit
    return surf, dT, dC, res, zone_ok

if __name__ == '__main__':
    surf, dT, dC, res, zok = report()
    for k, (a, b) in res.items(): print(f'{k}: Т {a:.1f} с, КТ {b:.1f} с')
    print('поверхностей', len(surf), 'достижимо Т', len(dT), 'КТ', len(dC))
    print('зоны без доступа от Т:', [n for n, v in zok.items() if v == 0])
