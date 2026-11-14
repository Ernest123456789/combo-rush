#!/usr/bin/env python3
"""Миниатюра карты «Дюны» для вкладки «Карты» (вид сверху, 2 px на блок)."""
import sys, numpy as np
from PIL import Image, ImageDraw, ImageFont
import dust
from dust import *

dust.analyse()
S = 2
W, H = (GX1 - GX0 + 1), (GZ1 - GZ0 + 1)
im = Image.new('RGB', (W * S, H * S))
px = im.load()
def colour(n):
    if 'barrier' in n: return (0, 0, 0)
    for k, c in (('orange_terracotta', (214, 112, 52)), ('yellow_concrete', (240, 196, 40)), ('white_concrete', (240, 240, 240)),
                 ('red_terracotta', (150, 70, 55)), ('brown_terracotta', (110, 80, 60)), ('blue_terracotta', (70, 95, 150)),
                 ('light_blue_terracotta', (100, 130, 165)), ('yellow_terracotta', (190, 150, 50)), ('terracotta', (160, 110, 85)),
                 ('red_sand', (192, 104, 62)), ('sand', (226, 206, 152)), ('planks', (130, 92, 56)), ('log', (100, 75, 45)),
                 ('lantern', (255, 245, 190)), ('concrete', (150, 150, 150)), ('stone', (150, 150, 156)), ('andesite', (140, 140, 146))):
        if k in n: return c
    return (180, 180, 180)
for x in range(GX0, GX1 + 1):
    for z in range(GZ0, GZ1 + 1):
        col = G[ix(x), iz(z)]
        top = 0
        for t in range(len(col) - 1, -1, -1):
            if col[t] and dust.MATNAME[col[t]] != 'barrier': top = t; break
        name = dust.MATNAME.get(int(col[top]), 'air').split('_stairs_')[0]
        lv = top + LV0
        k = 0.62 + 0.05 * max(-4, min(8, lv))
        c = tuple(int(max(0, min(255, v * k))) for v in colour(name))
        for i in range(S):
            for j in range(S):
                px[(x - GX0) * S + i, (z - GZ0) * S + j] = c
d = ImageDraw.Draw(im)
f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
for c, cx, cz, r, lv in SITES:
    X, Y = (cx - GX0) * S, (cz - GZ0) * S
    d.ellipse([X - 11, Y - 11, X + 11, Y + 11], fill=(20, 20, 20), outline=(255, 210, 60), width=2)
    d.text((X, Y), c, fill=(255, 220, 80), font=f, anchor='mm')
for (xs, col) in ((-56, (230, 150, 50)), (56, (90, 140, 230))):
    X = (xs - GX0) * S
    d.rectangle([X - 6, (0 - GZ0) * S - 20, X + 6, (0 - GZ0) * S + 20], outline=col, width=2)
out = sys.argv[1]
im = im.resize((256, int(256 * im.height / im.width)), Image.LANCZOS)
im.save(out)
print(im.size)
