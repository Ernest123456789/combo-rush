#!/usr/bin/env python3
"""Превью макета «Медины»: план с высотами и путями, изометрия с двух сторон, разрезы."""
import math, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import medina as md
from medina import M, NAMES, GW, GH, YMIN, NY, Y

F = lambda sz, b=False: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if b else ''), sz)
BASE = {'mass_a': (205, 178, 128), 'mass_b': (190, 160, 112), 'mass_c': (214, 190, 142), 'mass_fill': (200, 175, 125), 'rock': (110, 100, 90), 'dune': (232, 205, 140),
        'dome': (90, 150, 170), 'minaret': (225, 205, 160), 'minaret_top': (60, 130, 150), 'arch': (180, 150, 100),
        'f_sand': (226, 206, 152), 'f_sand2': (218, 196, 140), 'f_sand3': (232, 214, 166), 'f_tsp': (176, 104, 70), 'f_tsp2': (160, 96, 64),
        'f_ctsp': (84, 112, 160), 'f_ctsp2': (98, 128, 176), 'f_tile': (196, 110, 70), 'f_tile2': (226, 206, 152), 'f_cob': (150, 146, 140),
        'f_cob2': (138, 134, 128), 'f_cob3': (160, 156, 150), 'f_siteA': (222, 124, 58), 'f_siteB': (196, 100, 64), 'f_siteB2': (184, 92, 58),
        'f_tun': (96, 92, 92), 'f_tun2': (86, 82, 84), 'f_wood': (140, 100, 62), 'f_wood2': (124, 88, 54), 'f_roof': (170, 86, 60), 'f_roof2': (150, 76, 54),
        'f_grass': (96, 150, 70), 'f_path': (200, 180, 130), 'f_stone': (176, 176, 170), 'f_stone2': (164, 164, 158), 'f_bridge': (150, 120, 84),
        'stair': (190, 180, 160), 'rail': (110, 86, 60), 'water': (60, 130, 200), 'prop_crate': (150, 104, 56), 'prop_barrel': (120, 84, 50),
        'prop_log': (100, 74, 46), 'prop_leaves': (70, 140, 60), 'prop_truck': (200, 200, 196), 'prop_truck_cab': (180, 50, 40),
        'prop_bus': (220, 190, 60), 'prop_cart': (130, 92, 60), 'prop_stone': (160, 160, 160), 'prop_car': (70, 90, 120), 'prop_wood': (150, 110, 70),
        'prop_hedge': (60, 120, 50), 'pillar': (230, 214, 170), 'pillar_tun': (110, 104, 100),
        'awn_red': (200, 60, 50), 'awn_blue': (60, 100, 190), 'awn_green': (70, 150, 80), 'awn_yellow': (230, 190, 60)}
BASE['lamp'] = (255, 245, 190)
COLS = np.array([(0, 0, 0)] + [BASE.get('stair' if n.startswith('stair_') else n, (255, 0, 255)) for n in NAMES[1:]], dtype=np.float32)

def shade(c, k): return tuple(int(max(0, min(255, v * k))) for v in c)

# ---------------------------------------------------------------- изометрия
def iso(A, S=5, title=''):
    W, H, N = A.shape
    a, b, h = math.cos(math.radians(30)) * S, math.sin(math.radians(30)) * S, S * 1.0
    def P(x, y, z): return ((x + z) * a, (z - x) * b - (y) * h)
    xs = [P(0, 0, 0)[0], P(W, 0, H)[0]]; ys = [P(W, N, 0)[1], P(0, 0, H)[1]]
    ox, oy = -min(xs) + 30, -min(ys) + 70
    im = Image.new('RGB', (int(max(xs) - min(xs) + 60), int(max(ys) - min(ys) + 100)), (16, 19, 26))
    d = ImageDraw.Draw(im)
    solid = A != 0
    # видимые воксели
    top = solid.copy(); top[:, :, :-1] &= ~solid[:, :, 1:]
    west = solid.copy(); west[1:, :, :] &= ~solid[:-1, :, :]
    south = solid.copy(); south[:, :-1, :] &= ~solid[:, 1:, :]
    vis = top | west | south
    vis[:, :, :Y(-5)] = False
    xs_, zs_, ys_ = np.nonzero(vis)
    order = np.lexsort((ys_, -(xs_ - zs_)))
    for i in order:
        x, z, yi = int(xs_[i]), int(zs_[i]), int(ys_[i])
        y = yi + YMIN
        c = COLS[A[x, z, yi]]
        k = 1.0 + 0.012 * (y - 4)
        def poly(pts, kk):
            d.polygon([(px + ox, py + oy) for px, py in pts], fill=shade(c, kk * k))
        if top[x, z, yi]: poly([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], 1.0)
        if west[x, z, yi]: poly([P(x, y, z), P(x, y, z + 1), P(x, y + 1, z + 1), P(x, y + 1, z)], 0.66)
        if south[x, z, yi]: poly([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], 0.80)
    d.text((24, 18), title, fill=(255, 255, 255), font=F(26, True))
    return im, (ox, oy, P)

# ---------------------------------------------------------------- план
def plan(path_out):
    surf, dT, dC, res, zok = md.report()
    S = 7; PADL, PADT, PANEL = 30, 96, 470
    im = Image.new('RGB', (PADL + GW * S + PANEL, PADT + GH * S + 30), (16, 19, 26))
    d = ImageDraw.Draw(im, 'RGBA')
    hmin, hmax = -4, 12
    for x in range(GW):
        for z in range(GH):
            col = M[x, z]
            nz = np.nonzero(col)[0]
            if len(nz) == 0: continue
            t = nz[-1]; y = t + YMIN
            name = NAMES[col[t]]
            c = BASE.get(name, (255, 0, 255))
            if name == 'dune': c = shade((120, 106, 80), 0.9 + 0.05 * y)
            elif name.startswith('mass') or name in ('dome', 'rock'):
                c = (52, 46, 40) if y <= 9 else (60, 54, 46)                 # здания — тёмным, по ним не ходят
                if name == 'dome': c = (50, 90, 110)
            else:
                k = 0.72 + 0.4 * (min(hmax, max(hmin, y)) - hmin) / (hmax - hmin)
                c = shade(c, k)
            d.rectangle([PADL + x * S, PADT + z * S, PADL + x * S + S - 1, PADT + z * S + S - 1], fill=c)
    # подземные проходы — штриховка поверх
    for n in ('Тоннели', 'Колодец', 'Нижние тоннели'):
        cl, fl = md.ZONES[n]
        for x, z in cl:
            if (x + z) % 3 == 0:
                d.line([(PADL + x * S, PADT + z * S + S), (PADL + x * S + S, PADT + z * S)], fill=(150, 220, 255, 150), width=2)
        for x, z in cl:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, z + dz) not in set(cl):
                    pass
    tun = set()
    for n in ('Тоннели', 'Колодец', 'Нижние тоннели'): tun |= set(md.ZONES[n][0])
    for x, z in tun:
        X, Z = PADL + x * S, PADT + z * S
        if (x + 1, z) not in tun: d.line([(X + S, Z), (X + S, Z + S)], fill=(150, 220, 255, 220), width=2)
        if (x - 1, z) not in tun: d.line([(X, Z), (X, Z + S)], fill=(150, 220, 255, 220), width=2)
        if (x, z + 1) not in tun: d.line([(X, Z + S), (X + S, Z + S)], fill=(150, 220, 255, 220), width=2)
        if (x, z - 1) not in tun: d.line([(X, Z), (X + S, Z)], fill=(150, 220, 255, 220), width=2)
    # пути: спуск по полю расстояний
    def route(dist, goal):
        x, z, lv = goal
        best = None
        for s in range(lv, lv + 4):
            if (x, z, s) in dist: best = (x, z, s); break
        if best is None: return []
        pts = [best]
        while dist[pts[-1]] > 0 and len(pts) < 2000:
            x, z, s = pts[-1]; nxt = None
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for t in range(s - 1, s + 5):
                    q = (x + dx, z + dz, t)
                    if q in dist and dist[q] < dist[pts[-1]] and (nxt is None or dist[q] < dist[nxt]): nxt = q
            if nxt is None: break
            pts.append(nxt)
        return pts
    def draw_route(pts, col):
        xy = [(PADL + (x + .5) * S, PADT + (z + .5) * S) for x, z, s in pts]
        for i in range(0, len(xy) - 1):
            if (i // 4) % 2 == 0: d.line([xy[i], xy[i + 1]], fill=col, width=4)
    for g in (md.SITES['A'], md.SITES['B'], md.MIDP):
        draw_route(route(dT, g), (255, 150, 40, 230))
        draw_route(route(dC, g), (80, 150, 255, 230))
    # площадки и базы
    for c, (x, z, lv) in md.SITES.items():
        X, Z = PADL + x * S, PADT + z * S
        d.ellipse([X - 24, Z - 24, X + 24, Z + 24], fill=(20, 20, 24, 220), outline=(255, 200, 50), width=4)
        d.text((X, Z), c, fill=(255, 210, 60), font=F(30, True), anchor='mm')
    for (x, z, lv), col, lab in ((md.SPAWN_T, (255, 150, 50), 'Т'), (md.SPAWN_CT, (90, 150, 255), 'КТ')):
        X, Z = PADL + x * S, PADT + z * S
        d.rounded_rectangle([X - 28, Z - 18, X + 28, Z + 18], 8, fill=(20, 20, 24, 220), outline=col, width=4)
        d.text((X, Z), lab, fill=col, font=F(22, True), anchor='mm')
    # подписи зон
    skip = {'Колодец', 'Подъём КТ (верх)', 'Переход', 'Склад Б (2 этаж)', 'Кухня'}
    for n, (cl, fl) in md.ZONES.items():
        if n in skip or not cl: continue
        cx = sum(p[0] for p in cl) / len(cl); cz = sum(p[1] for p in cl) / len(cl)
        X, Z = PADL + cx * S, PADT + cz * S
        if n in ('Площадка А', 'Площадка Б'): Z += 34
        if n == 'База Т': Z += 30
        if n == 'База КТ': Z += 30
        t = n + ('' if fl == 0 else '  %+d' % fl)
        tw = d.textlength(t, font=F(13, True))
        d.rounded_rectangle([X - tw / 2 - 5, Z - 10, X + tw / 2 + 5, Z + 10], 5, fill=(14, 17, 24, 200))
        d.text((X, Z), t, fill=(255, 255, 255), font=F(13, True), anchor='mm')
    # пронумерованные «фишки»
    FEAT = [(91, 70, 'Мост над Мидом (+4): сверху — КТ, снизу — проход Мида'),
            (83, 67, 'Яма Мида (−2): ломает прострел, тут можно спрятаться'),
            (47, 86, 'Колодец: дыра в Аллее — прыжок вниз в Тоннели (−4)'),
            (52, 55, 'Крыши (+7) над Подворотней: верхний путь Рынок → Лонг А'),
            (121, 17, 'Балкон А (+8): держит Лонг, площадку и Лестницу'),
            (105, 85, 'Галерея (+4) на крыше Кухни: вид сверху на Б и Мид'),
            (131, 108, 'Склад Б в 2 этажа: окно 2-го этажа на площадку'),
            (15, 31, 'Рынок (+2): колодец, лотки под навесами'),
            (60, 22, 'Разбитый автобус на Лонге: укрытие и проход'),
            (131, 72, 'Сад: фонтан и пальмы, спуск на Б'),
            (148, 49, 'Минарет — ориентир, виден с любой точки')]
    for i, (x, z, t) in enumerate(FEAT):
        X, Z = PADL + x * S, PADT + z * S
        d.ellipse([X - 12, Z - 12, X + 12, Z + 12], fill=(255, 255, 255, 235), outline=(20, 20, 20), width=2)
        d.text((X, Z), str(i + 1), fill=(10, 10, 10), font=F(14, True), anchor='mm')
    # заголовок и панель
    d.text((PADL, 18), 'Карта «Медина» — объёмный макет, вид сверху', fill=(255, 255, 255), font=F(30, True))
    d.text((PADL, 58), 'Цвет пола светлее — выше. Тёмное — здания (по крышам ходить нельзя, кроме отмеченных). Голубая штриховка — тоннели под землёй.',
           fill=(170, 180, 200), font=F(15))
    x0 = PADL + GW * S + 26; y = PADT
    def L(t, col=(220, 226, 236), sz=15, b=False, dy=22):
        nonlocal y
        d.text((x0, y), t, fill=col, font=F(sz, b)); y += dy
    L('Время пробежки', (255, 255, 255), 19, True, 30)
    for k, (a, b) in res.items(): L(f'{k:4}  Т {a:4.1f} с  ·  КТ {b:4.1f} с', (230, 230, 230), 16, False, 25)
    L('Пунктир: кратчайшие пути Т (оранж.) и КТ (син.)', (160, 170, 190), 13, False, 32)
    L('Уровни', (255, 255, 255), 19, True, 28)
    for t in ('−4  тоннели Т к Б (+колодец из Аллеи)', '−2  площадка Б, Яма Мида', ' 0  улицы: База Т, Аллея, Мид, Сад',
              '+1  База КТ, Ворота, Подъём', '+2  Рынок и Лонг А (терраса)', '+4  площадка А, Лестница, Мост, Галерея',
              '+7  Крыши над Подворотней', '+8  Балкон А'):
        L(t, (210, 216, 228), 14, False, 21)
    y += 10
    L('Интересные места', (255, 255, 255), 19, True, 28)
    for i, (x, z, t) in enumerate(FEAT):
        words = t; first = True
        while words:
            cut = words if len(words) <= 44 else words[:words.rfind(' ', 0, 44)]
            L(('%2d. ' % (i + 1) if first else '     ') + cut, (210, 216, 228), 13, False, 18)
            words = words[len(cut):].strip(); first = False
    im.save(path_out)

# ---------------------------------------------------------------- разрезы
def section(axis, at, rng_, title, labels):
    S = 7
    if axis == 'x': cols = [(at, z) for z in range(*rng_)]
    else: cols = [(x, at) for x in range(*rng_)]
    yl, yh = -6, 14
    W = max(len(cols) * S + 80, int(ImageDraw.Draw(Image.new('RGB', (1, 1))).textlength(title, font=F(16, True))) + 30); H = (yh - yl + 1) * S + 70
    im = Image.new('RGB', (W, H), (16, 19, 26)); d = ImageDraw.Draw(im)
    d.text((10, 8), title, fill=(255, 255, 255), font=F(16, True))
    for i, (x, z) in enumerate(cols):
        for y in range(yl, yh + 1):
            v = M[x, z, Y(y)]
            X0 = 60 + i * S; Y0 = 40 + (yh - y) * S
            if v == 0: c = (34, 44, 62) if y > 0 else (26, 30, 40)
            else:
                n = NAMES[v]; c = BASE.get('stair' if n.startswith('stair_') else n, (255, 0, 255))
                if n.startswith('mass') or n in ('rock',): c = (70, 62, 52)
            d.rectangle([X0, Y0, X0 + S - 1, Y0 + S - 1], fill=c)
    for y in range(yl, yh + 1, 2):
        d.text((54, 40 + (yh - y) * S + S // 2), f'{y:+d}' if y else '0', fill=(150, 160, 180), font=F(10), anchor='rm')
    for pos, t in labels:
        i = pos - rng_[0]
        d.text((60 + i * S, H - 22), t, fill=(240, 220, 160), font=F(12, True), anchor='mm')
    return im

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '/tmp'
    plan(out + '/medina_plan.png')
    a, _ = iso(M, 5, 'Вид с юго-запада (со стороны Т)')
    b, _ = iso(np.ascontiguousarray(M[::-1, ::-1, :]), 5, 'Вид с северо-востока (со стороны КТ)')
    both = Image.new('RGB', (max(a.width, b.width), a.height + b.height), (16, 19, 26))
    both.paste(a, (0, 0)); both.paste(b, (0, a.height)); both.save(out + '/medina_iso.png')
    s1 = section('x', 91, (30, 120), 'Разрез с севера на юг по x=91: площадка А → Лестница → Мост над Мидом → Галерея над Кухней → площадка Б',
                 [(33, 'А +4'), (47, 'Лестница +4'), (67, 'Мост над Мидом'), (82, 'Галерея'), (102, 'Б −2')])
    s2 = section('z', 97, (10, 140), 'Разрез с запада на восток по z=97: База Т → спуск → Тоннели (−4) → площадка Б → Склад Б (2 этажа)',
                 [(20, 'База Т'), (35, 'спуск'), (60, 'Тоннели −4'), (100, 'Б (−2)'), (127, 'Склад Б')])
    s3 = section('x', 49, (14, 100), 'Разрез с севера на юг по x=49: Лонг (+2) → Крыши (+7), под ними Подворотня → Аллея → Колодец в Тоннели (−4)',
                 [(26, 'Лонг +2'), (44, 'Крыши +7'), (56, 'Подворотня'), (78, 'Аллея'), (91, 'Тоннели −4')])
    W = max(s1.width, s2.width, s3.width)
    sec = Image.new('RGB', (W, s1.height + s2.height + s3.height + 20), (16, 19, 26))
    yy = 0
    for s in (s1, s2, s3): sec.paste(s, (0, yy)); yy += s.height + 10
    sec.save(out + '/medina_sections.png')
    print('ok')
