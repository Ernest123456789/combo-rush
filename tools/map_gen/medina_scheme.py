#!/usr/bin/env python3
"""Черновая схема карты «Медина»: неквадратный контур, зоны, укрытия, пути и время пробежки (BFS по растру)."""
import collections, math, sys
from PIL import Image, ImageDraw, ImageFont

GW, GH = 170, 124
# name, kind, polygon (x — восток, y — юг), уровень
ZONES = [
    ("База Т", "tspawn", [(6, 62), (30, 54), (42, 70), (40, 94), (18, 100), (4, 84)], 0),
    ("Верхняя улица", "street", [(13, 60), (25, 58), (27, 44), (15, 44)], 0),
    ("Рынок", "plaza", [(8, 22), (40, 16), (50, 28), (46, 48), (14, 50), (6, 36)], 0),
    ("Лонг А", "street", [(44, 24), (78, 17), (81, 29), (47, 37)], 0),
    ("Площадка А", "siteA", [(77, 6), (108, 4), (114, 32), (80, 36)], 2),
    ("Балкон А", "high", [(107, 4), (124, 6), (123, 19), (110, 20)], 5),
    ("Переулок", "alley", [(40, 44), (49, 43), (54, 76), (45, 78)], 0),
    ("Аллея", "street", [(38, 78), (60, 72), (63, 82), (40, 90)], 0),
    ("Мид", "mid", [(58, 70), (104, 62), (106, 73), (60, 82)], 0),
    ("Лестница", "stairs", [(83, 33), (92, 33), (95, 64), (86, 66)], 1),
    ("Тоннели", "tunnel", [(30, 90), (63, 91), (67, 103), (34, 106)], -1),
    ("Нижние тоннели", "tunnel", [(62, 92), (87, 87), (90, 97), (66, 103)], -1),
    ("Площадка Б", "siteB", [(86, 82), (117, 78), (121, 108), (90, 113)], -2),
    ("Склад Б", "building", [(116, 88), (132, 86), (134, 104), (119, 106)], -2),
    ("Кухня", "alley", [(96, 70), (105, 69), (106, 84), (97, 85)], -1),
    ("Ворота КТ", "street", [(103, 61), (121, 55), (124, 66), (106, 73)], 0),
    ("База КТ", "ctspawn", [(121, 46), (147, 42), (156, 64), (132, 74), (118, 66)], 0),
    ("Подъём КТ", "street", [(111, 26), (123, 24), (130, 44), (118, 47)], 1),
    ("Сад", "plaza", [(121, 64), (134, 66), (130, 86), (117, 84)], 0),
]
COVERS = [  # (x0, y0, x1, y1, подпись или '')
    (22, 26, 26, 29, 'лотки'), (30, 34, 33, 37, ''), (18, 38, 21, 41, ''), (36, 24, 38, 28, ''),
    (60, 22, 63, 25, 'тележка'), (92, 14, 97, 18, 'пирамида'), (84, 22, 87, 25, ''), (100, 24, 103, 27, ''),
    (70, 72, 73, 75, 'ящики'), (88, 66, 90, 70, ''), (78, 68, 80, 71, ''),
    (98, 92, 102, 96, 'ящики'), (106, 100, 109, 103, ''), (92, 104, 95, 107, ''),
    (124, 92, 127, 95, ''), (20, 72, 23, 75, ''), (28, 84, 31, 87, ''), (130, 52, 134, 56, ''), (144, 56, 147, 59, ''),
    (124, 72, 127, 75, 'фонтан'), (48, 58, 50, 62, ''), (42, 84, 45, 86, ''),
]
SITES = {'A': (95, 20), 'B': (103, 95)}
SPAWN_T, SPAWN_CT = (22, 76), (140, 60)

def inside(poly, x, y):
    c = False; n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1: c = not c
    return c

grid = [[None] * GW for _ in range(GH)]
for zi, (n, k, poly, lv) in enumerate(ZONES):
    for y in range(GH):
        for x in range(GW):
            if grid[y][x] is None and inside(poly, x + 0.5, y + 0.5): grid[y][x] = zi
blocked = set()
for (x0, y0, x1, y1, _) in COVERS:
    for y in range(y0, y1):
        for x in range(x0, x1): blocked.add((x, y))

def bfs(start):
    d = {start: 0}; prev = {}
    q = collections.deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy, cst in ((1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414), (-1, -1, 1.414)):
            nx, ny = x + dx, y + dy
            if not (0 <= nx < GW and 0 <= ny < GH) or grid[ny][nx] is None or (nx, ny) in blocked: continue
            if dx and dy and (grid[y][nx] is None or grid[ny][x] is None): continue
            nd = d[(x, y)] + cst
            if nd < d.get((nx, ny), 1e9) - 1e-9:
                d[(nx, ny)] = nd; prev[(nx, ny)] = (x, y)
                q.append((nx, ny))
    return d, prev

def path(prev, start, goal):
    p = [goal]
    while p[-1] != start and p[-1] in prev: p.append(prev[p[-1]])
    return p[::-1]

SPEED = 5.6   # блоков/с — бег
dT, pT = bfs(SPAWN_T); dC, pC = bfs(SPAWN_CT)
MIDP = (82, 72)
stats = {}
for nm, pt in (('A', SITES['A']), ('B', SITES['B']), ('Мид', MIDP)):
    stats[nm] = (dT.get(pt, 0) / SPEED, dC.get(pt, 0) / SPEED)
    print(nm, 'Т: %.1f с' % stats[nm][0], 'КТ: %.1f с' % stats[nm][1])
reach = sum(1 for y in range(GH) for x in range(GW) if grid[y][x] is not None and (x, y) not in blocked)
print('клеток:', reach, 'достижимо от Т:', len(dT), 'от КТ:', len(dC))

# ------------------------------------------------------------------ рисунок
S = 8; PADL, PADT = 40, 90; PANEL = 420
IW, IH = PADL + GW * S + PANEL, PADT + GH * S + 40
im = Image.new('RGB', (IW, IH), (15, 18, 25))
d = ImageDraw.Draw(im, 'RGBA')
F = lambda sz, b=False: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if b else ''), sz)
COL = {'tspawn': (176, 118, 70), 'ctspawn': (78, 110, 160), 'street': (214, 192, 146), 'plaza': (226, 204, 156), 'alley': (196, 172, 128),
       'mid': (205, 196, 170), 'siteA': (214, 122, 62), 'siteB': (196, 104, 70), 'high': (236, 214, 166), 'stairs': (200, 190, 160),
       'tunnel': (120, 112, 104), 'building': (150, 120, 90)}
def P(x, y): return (PADL + x * S, PADT + y * S)
# пол
for y in range(GH):
    for x in range(GW):
        zi = grid[y][x]
        if zi is None: continue
        n, k, poly, lv = ZONES[zi]
        c = COL[k]
        sh = 1 + 0.035 * lv + ((x * 7 + y * 13) % 5 - 2) * 0.008
        c = tuple(max(0, min(255, int(v * sh))) for v in c)
        d.rectangle([*P(x, y), PADL + (x + 1) * S - 1, PADT + (y + 1) * S - 1], fill=c)
# штриховка крытых зон
for y in range(GH):
    for x in range(GW):
        zi = grid[y][x]
        if zi is not None and ZONES[zi][1] in ('tunnel', 'building') and (x + y) % 3 == 0:
            d.line([P(x, y), (PADL + (x + 1) * S, PADT + (y + 1) * S)], fill=(0, 0, 0, 70), width=1)
# стены — граница проходимой области
for y in range(GH):
    for x in range(GW):
        if grid[y][x] is None: continue
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if not (0 <= nx < GW and 0 <= ny < GH) or grid[ny][nx] is None:
                x0, y0 = P(x, y)
                if dx == 1: d.line([(x0 + S, y0), (x0 + S, y0 + S)], fill=(60, 44, 30), width=3)
                if dx == -1: d.line([(x0, y0), (x0, y0 + S)], fill=(60, 44, 30), width=3)
                if dy == 1: d.line([(x0, y0 + S), (x0 + S, y0 + S)], fill=(60, 44, 30), width=3)
                if dy == -1: d.line([(x0, y0), (x0 + S, y0)], fill=(60, 44, 30), width=3)
# перепады высот между зонами
for y in range(GH):
    for x in range(GW):
        a = grid[y][x]
        if a is None: continue
        for dx, dy in ((1, 0), (0, 1)):
            nx, ny = x + dx, y + dy
            if nx < GW and ny < GH and grid[ny][nx] is not None and grid[ny][nx] != a and ZONES[grid[ny][nx]][3] != ZONES[a][3]:
                x0, y0 = P(nx, ny)
                if dx: d.line([(x0, y0), (x0, y0 + S)], fill=(255, 255, 255, 90), width=1)
                else: d.line([(x0, y0), (x0 + S, y0)], fill=(255, 255, 255, 90), width=1)
# укрытия
for (x0, y0, x1, y1, lab) in COVERS:
    d.rectangle([*P(x0, y0), *P(x1, y1)], fill=(92, 64, 40), outline=(40, 28, 18), width=2)
    d.line([P(x0, y0), P(x1, y1)], fill=(0, 0, 0, 60))
    if lab: d.text((P((x0 + x1) / 2, y1)[0], P(0, y1)[1] + 2), lab, fill=(60, 44, 30), font=F(11), anchor='mt')
# пути
def draw_path(pts, col, dash=10):
    xy = [(PADL + (x + .5) * S, PADT + (y + .5) * S) for x, y in pts]
    acc = 0
    for i in range(len(xy) - 1):
        seg = math.dist(xy[i], xy[i + 1])
        if int(acc / dash) % 2 == 0: d.line([xy[i], xy[i + 1]], fill=col, width=4)
        acc += seg
    ex, ey = xy[-1]; px, py = xy[max(0, len(xy) - 6)]
    ang = math.atan2(ey - py, ex - px)
    d.polygon([(ex, ey), (ex - 16 * math.cos(ang - .45), ey - 16 * math.sin(ang - .45)), (ex - 16 * math.cos(ang + .45), ey - 16 * math.sin(ang + .45))], fill=col)
for goal in (SITES['A'], SITES['B'], MIDP):
    draw_path(path(pT, SPAWN_T, goal), (240, 140, 40, 210))
    draw_path(path(pC, SPAWN_CT, goal), (70, 140, 250, 210))
# площадки
for c, (x, y) in SITES.items():
    X, Y = P(x, y)
    d.ellipse([X - 26, Y - 26, X + 26, Y + 26], fill=(20, 20, 24, 220), outline=(255, 200, 50), width=4)
    d.text((X, Y), c, fill=(255, 210, 60), font=F(34, True), anchor='mm')
for (x, y), col, lab in ((SPAWN_T, (240, 150, 50), 'Т'), (SPAWN_CT, (90, 150, 250), 'КТ')):
    X, Y = P(x, y)
    d.rounded_rectangle([X - 30, Y - 20, X + 30, Y + 20], 8, fill=(20, 20, 24, 220), outline=col, width=4)
    d.text((X, Y), lab, fill=col, font=F(24, True), anchor='mm')
# подписи зон
for zi, (n, k, poly, lv) in enumerate(ZONES):
    if k in ('tspawn', 'ctspawn', 'siteA', 'siteB'): lab_y_off = 34
    else: lab_y_off = 0
    cx = sum(p[0] for p in poly) / len(poly); cy = sum(p[1] for p in poly) / len(poly)
    X, Y = P(cx, cy)
    t = n + ('' if lv == 0 else '  (%+d)' % lv)
    tw = d.textlength(t, font=F(13, True))
    d.rounded_rectangle([X - tw / 2 - 5, Y + lab_y_off - 10, X + tw / 2 + 5, Y + lab_y_off + 10], 5, fill=(15, 18, 25, 190))
    d.text((X, Y + lab_y_off), t, fill=(255, 255, 255), font=F(13, True), anchor='mm')
# заголовок
d.text((PADL, 22), 'Схема карты «Медина» — черновик', fill=(255, 255, 255), font=F(30, True))
d.text((PADL, 60), 'Старый город в пустыне. Вид сверху, 1 клетка = 1 блок, ~165 × 115 блоков. Контур — не прямоугольник: улицы, площади, тоннели.', fill=(170, 180, 200), font=F(15))
# панель справа
x0 = PADL + GW * S + 30; y = PADT
def line(t, col=(220, 226, 236), sz=15, b=False, dy=24):
    global y
    d.text((x0, y), t, fill=col, font=F(sz, b)); y += dy
line('Время пробежки (бег)', (255, 255, 255), 19, True, 32)
for nm in ('A', 'B', 'Мид'):
    tt, tc = stats[nm]
    line(f'{nm:4}  Т {tt:4.1f} с   ·   КТ {tc:4.1f} с', (230, 230, 230), 16, False, 26)
gaps = [stats[k][0] - stats[k][1] for k in ('A', 'B')]
line('КТ успевают на точки на %.0f–%.0f с раньше Т' % (min(gaps), max(gaps)), (150, 200, 150), 14, False, 36)
line('Обозначения', (255, 255, 255), 19, True, 32)
for col, t in (((240, 140, 40), 'пути Т (кратчайшие)'), ((70, 140, 250), 'пути КТ'), ((92, 64, 40), 'укрытия: ящики, лотки, тележки'),
               ((120, 112, 104), 'крытые проходы (тоннели, склад)'), ((214, 122, 62), 'площадки закладки'), ((255, 255, 255), 'тонкая белая линия — перепад высоты')):
    d.rectangle([x0, y + 3, x0 + 18, y + 17], fill=col); d.text((x0 + 28, y), t, fill=(220, 226, 236), font=F(14)); y += 24
y += 14
line('Идея', (255, 255, 255), 19, True, 32)
for t in ('• Три пути у Т: Рынок → Лонг А,', '   Аллея → Мид, Тоннели → Б.',
          '• Ротации у Т: Переулок (Рынок ↔ Мид).',
          '• Мид соединён с обеими точками:', '   Лестница вверх на А, Кухня вниз на Б.',
          '• КТ: Подъём на А, Сад к Б,', '   Ворота КТ — контроль Мида.',
          '• А приподнята (+2), есть Балкон (+5):', '   держит Лонг и Лестницу.',
          '• Б утоплена (−2), рядом Склад Б:', '   три входа — Тоннели, Кухня, Сад.',
          '• Длинные прострелы: Лонг А и Мид (AWP),', '   ближний бой: Тоннели, Кухня, Склад Б.'):
    line(t, (210, 216, 228), 14, False, 21)
im.save(sys.argv[1] if len(sys.argv) > 1 else '/tmp/medina_scheme.png')
