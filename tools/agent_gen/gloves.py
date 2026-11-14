#!/usr/bin/env python3
"""Перчатки «Противостояния»: текстура кисти 64×64 (области: тыльная сторона, пальцы, ладонь, манжета) + иконка 32×32,
рукава формы Т/КТ 32×32. Выход: assets/combo-rush/textures/entity/gloves/*.png и textures/item/gloves/*.png."""
import math, os, sys
from PIL import Image, ImageDraw
from agents import hsh, vnoise, hexc, mul, mix

ROOT = sys.argv[1] if len(sys.argv) > 1 else '../../src/main/resources/assets/combo-rush'
ENT = os.path.join(ROOT, 'textures/entity/gloves'); ICO = os.path.join(ROOT, 'textures/item/gloves')
os.makedirs(ENT, exist_ok=True); os.makedirs(ICO, exist_ok=True)
SKIN = hexc('#c99a78')

def leather(c, x, y, s, k=0.10):
    return mul(c, 1 + (hsh(x, y, s) - .5) * k + (vnoise(x, y, s, 5) - .5) * 0.22)
def fabric(c, x, y, s):
    return mul(c, 1 + (hsh(x, y, s) - .5) * 0.10 + (0.04 if (x + y) % 2 else -0.03))

# области текстуры кисти
BACK = (0, 0, 32, 32); FING = (32, 0, 64, 16); PALM = (32, 16, 64, 32); CUFF = (0, 32, 32, 48)

def paint(img, reg, fn):
    x0, y0, x1, y1 = reg
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), fn(x - x0, y - y0, x1 - x0, y1 - y0) + (255,))

def fingers(base, seg=None, tip=None, s=1):
    """пальцы: 4 полосы по 4 px с тёмными щелями, суставы"""
    def f(a, b, w, h):
        c = leather(base, a, b, s)
        if a % 8 == 7: c = mul(c, 0.55)                     # щель между пальцами
        if seg and b in (5, 10): c = mul(seg, 1.0)
        if tip and b >= 12: c = leather(tip, a, b, s + 3, 0.06)
        if b == 0: c = mul(c, 1.15)
        return c
    return f

def edge(fn, k=0.78):
    def g(a, b, w, h):
        c = fn(a, b, w, h)
        return mul(c, k) if a in (0, w - 1) or b in (0, h - 1) else c
    return g

DESIGNS = {}
def design(gid, title):
    def deco(fn): DESIGNS[gid] = (title, fn); return fn
    return deco

@design('gloves_t', 'Перчатки (Т)')
def g_t(img):
    blk = hexc('#202020')
    paint(img, BACK, edge(lambda a, b, w, h: mul(leather(blk, a, b, 1), 1.25 if b in (8, 9) and a % 6 < 4 else 1)))
    paint(img, FING, fingers(blk, tip=SKIN))                       # без пальцев: кончики — кожа
    paint(img, PALM, edge(lambda a, b, w, h: leather(hexc('#2a2a2a'), a, b, 2)))
    paint(img, CUFF, edge(lambda a, b, w, h: fabric(hexc('#6c5b40'), a, b, 3)))

@design('gloves_ct', 'Перчатки (КТ)')
def g_ct(img):
    g = hexc('#34363a')
    def back(a, b, w, h):
        c = leather(g, a, b, 4)
        if 6 <= b <= 13 and 4 <= a <= 27:                       # защита костяшек
            c = mul(hexc('#202224'), 1.0 + (0.25 if b == 6 else 0) - (0.2 if b == 13 else 0))
            if a % 6 == 3: c = mul(c, 0.7)
        if b in (18, 19) and 6 <= a <= 25: c = mul(c, 0.8)
        return c
    paint(img, BACK, edge(back))
    paint(img, FING, fingers(g, seg=hexc('#222426')))
    paint(img, PALM, edge(lambda a, b, w, h: mul(leather(hexc('#3c3e42'), a, b, 5), 0.9 if (a + b) % 4 == 0 else 1)))
    def cuff(a, b, w, h):
        c = fabric(hexc('#1d1e21'), a, b, 6)
        if 4 <= a <= 12 and 3 <= b <= 12: c = mul(hexc('#5d6167'), 1 + (hsh(a, b, 7) - .5) * .3)   # липучка
        return c
    paint(img, CUFF, edge(cuff))

@design('gloves_sport_neon', 'Спортивные перчатки | Неон')
def g_neon(img):
    wh, pk, cy = hexc('#ececf0'), hexc('#ff3fa4'), hexc('#2fe0e8')
    def back(a, b, w, h):
        z = (a + 2 * b) % 12
        c = pk if z < 3 else cy if 6 <= z < 8 else wh
        if b > 24: c = cy
        return mul(c, 1 + (hsh(a, b, 8) - .5) * 0.08)
    paint(img, BACK, edge(back, 0.7))
    paint(img, FING, fingers(pk, seg=wh))
    paint(img, PALM, edge(lambda a, b, w, h: fabric(hexc('#2a2a33'), a, b, 9)))
    paint(img, CUFF, edge(lambda a, b, w, h: mul(cy if b % 4 < 2 else wh, 1 + (hsh(a, b, 10) - .5) * .1)))

@design('gloves_driver_leather', 'Водительские перчатки | Кожа')
def g_drv(img):
    br = hexc('#8a5a32')
    def back(a, b, w, h):
        c = leather(br, a, b, 11, 0.12)
        if 5 <= b <= 20 and 6 <= a <= 25 and a % 3 == 0 and b % 3 == 0: c = mul(c, 0.45)   # перфорация
        if a in (4, 27) and b % 2 == 0: c = mul(c, 1.3)                                     # строчка
        return c
    paint(img, BACK, edge(back))
    paint(img, FING, fingers(br, seg=mul(br, 0.8), s=12))
    paint(img, PALM, edge(lambda a, b, w, h: leather(hexc('#6e4628'), a, b, 13)))
    def cuff(a, b, w, h):
        c = leather(hexc('#5a3820'), a, b, 14)
        if 13 <= a <= 18 and 5 <= b <= 10: c = hexc('#c9c9c9') if (a + b) % 3 else hexc('#8d939a')   # кнопка
        return c
    paint(img, CUFF, edge(cuff))

@design('gloves_moto_carbon', 'Мотоперчатки | Карбон')
def g_moto(img):
    bk, rd = hexc('#18181a'), hexc('#d02a2a')
    def back(a, b, w, h):
        c = leather(bk, a, b, 15)
        if 5 <= b <= 14 and 3 <= a <= 28:                      # карбон: плетение 2×2
            c = hexc('#3a3c40') if ((a // 2) + (b // 2)) % 2 else hexc('#202124')
            if b == 5: c = mul(c, 1.4)
        if b in (16, 17) or a in (2, 29): c = rd
        return c
    paint(img, BACK, edge(back, 0.6))
    paint(img, FING, fingers(bk, seg=hexc('#4a4c52'), s=16))
    paint(img, PALM, edge(lambda a, b, w, h: mul(leather(hexc('#26262a'), a, b, 17), 0.85 if (a // 3 + b // 3) % 2 else 1)))
    paint(img, CUFF, edge(lambda a, b, w, h: rd if b in (2, 3) else leather(bk, a, b, 18)))

@design('gloves_tactical_camo', 'Тактические перчатки | Мультикам')
def g_camo(img):
    pal = [hexc('#a8936a'), hexc('#7c7a52'), hexc('#5d4a32'), hexc('#c4b48c'), hexc('#4f5a3a')]
    def camo(a, b, s):
        v = vnoise(a, b, s, 4) * 0.6 + vnoise(a + 7, b + 3, s + 1, 2.5) * 0.4
        return mul(pal[min(4, int(v * 5))], 1 + (hsh(a, b, s) - .5) * .08)
    paint(img, BACK, edge(lambda a, b, w, h: camo(a, b, 19)))
    paint(img, FING, fingers(hexc('#6a6a48'), seg=hexc('#55553a'), s=20))
    paint(img, PALM, edge(lambda a, b, w, h: leather(hexc('#8a7a58'), a, b, 21)))
    paint(img, CUFF, edge(lambda a, b, w, h: camo(a + 40, b, 22) if b < 12 else fabric(hexc('#7a6a48'), a, b, 23)))

@design('gloves_wraps_desert', 'Обмотки | Пустыня')
def g_wrap(img):
    cl, dirt = hexc('#d9c9a6'), hexc('#a08a62')
    def wrap(a, b, s, k=0):
        band = (a + b * 2 + k) % 9
        c = cl if band > 1 else mul(cl, 0.72)
        c = mix(c, dirt, max(0, vnoise(a, b, s, 6) - 0.55) * 1.6)
        return mul(c, 1 + (hsh(a, b, s) - .5) * .1)
    def back(a, b, w, h):
        c = wrap(a, b, 24)
        if 10 <= b <= 13: c = mul(hexc('#8d8f88'), 1 + (hsh(a, b, 25) - .5) * .2)   # изолента на костяшках
        return c
    paint(img, BACK, edge(back, 0.85))
    paint(img, FING, lambda a, b, w, h: wrap(a, b, 26) if b < 7 else (mul(SKIN, 0.7) if a % 8 == 7 else leather(SKIN, a, b, 27, 0.05)))
    paint(img, PALM, edge(lambda a, b, w, h: wrap(a, b, 28, 4), 0.85))
    paint(img, CUFF, edge(lambda a, b, w, h: wrap(a, b, 29, 2), 0.85))

def icon(gid, tex):
    """иконка: силуэт кисти (ладонь + 4 пальца + большой палец), залит узором тыльной стороны, с обводкой"""
    S = 32
    mask = Image.new('L', (S, S), 0); d = ImageDraw.Draw(mask)
    d.rounded_rectangle([8, 13, 23, 27], 3, fill=255)               # ладонь
    for i, (x, top) in enumerate(((8, 4), (12, 2), (16, 3), (20, 6))):
        d.rounded_rectangle([x, top, x + 3, 15], 2, fill=255)        # пальцы
    d.polygon([(23, 17), (29, 11), (31, 13), (25, 22)], fill=255)    # большой палец
    d.rectangle([9, 26, 22, 31], fill=255)                           # манжета
    out = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for x in range(S):
        for y in range(S):
            if not mask.getpixel((x, y)): continue
            if y >= 26: c = tex.getpixel((x - 4, 34 + (y - 26) * 2))
            elif y < 15 and not (y >= 13 and 8 <= x <= 23): c = tex.getpixel((32 + (x - 8) * 2 % 32, max(0, min(15, y))))
            else: c = tex.getpixel((x, y + 2))
            out.putpixel((x, y), c[:3] + (255,))
    # обводка и объём
    px = out.load()
    for x in range(S):
        for y in range(S):
            if px[x, y][3] == 0:
                if any(0 <= x + dx < S and 0 <= y + dy < S and px[x + dx, y + dy][3] == 255 and px[x + dx, y + dy][:3] != (10, 10, 12)
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    pass
    edge_px = []
    for x in range(S):
        for y in range(S):
            if px[x, y][3] and any(not (0 <= x + dx < S and 0 <= y + dy < S) or px[x + dx, y + dy][3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge_px.append((x, y))
    for x, y in edge_px: px[x, y] = (12, 12, 14, 255)
    return out

def sleeve(name, base, kind):
    img = Image.new('RGBA', (32, 32))
    for x in range(32):
        for y in range(32):
            c = mul(base, 1 + (hsh(x, y, 40) - .5) * 0.1 + (vnoise(x, y, 41, 5) - .5) * 0.14)
            if kind == 'ct' and (x % 8 == 0 or y % 8 == 0): c = mul(c, 0.95)
            if kind == 't' and (x + y) % 3 == 0: c = mul(c, 0.93)
            if y in (0, 31): c = mul(c, 0.8)
            img.putpixel((x, y), c + (255,))
    img.save(os.path.join(ENT, 'sleeve_' + name + '.png'))

if __name__ == '__main__':
    sheet = Image.new('RGBA', (len(DESIGNS) * 136, 140), (20, 22, 28, 255))
    for i, (gid, (title, fn)) in enumerate(DESIGNS.items()):
        tex = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        fn(tex)
        tex.save(os.path.join(ENT, gid + '.png'))
        ic = icon(gid, tex); ic.save(os.path.join(ICO, gid + '.png'))
        sheet.paste(tex.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        sheet.paste(ic.resize((64, 64), Image.NEAREST), (i * 136 + 60, 72), ic.resize((64, 64), Image.NEAREST))
    sleeve('t', hexc('#8a7656'), 't'); sleeve('ct', hexc('#334063'), 'ct')
    sheet.save('/tmp/gloves_sheet.png')
    print('\n'.join(f'{g}: {t}' for g, (t, f) in DESIGNS.items()))
