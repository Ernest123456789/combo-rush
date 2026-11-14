#!/usr/bin/env python3
"""Миниатюра «Медины» для вкладки «Карты»."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import medina as md
from medina_views import BASE, shade
from medina import M, NAMES, GW, GH, YMIN

S = 2
im = Image.new('RGB', (GW * S, GH * S), (30, 26, 22))
d = ImageDraw.Draw(im)
for x in range(GW):
    for z in range(GH):
        col = M[x, z]; nz = np.nonzero(col)[0]
        if not len(nz): continue
        t = nz[-1]; y = t + YMIN; n = NAMES[col[t]]
        c = BASE.get('stair' if n.startswith('stair_') else n, (200, 0, 200))
        if n == 'dune': c = shade((150, 128, 92), 0.9 + 0.04 * y)
        elif n.startswith('mass') or n in ('dome', 'rock'): c = (70, 60, 50) if n != 'dome' else (60, 110, 130)
        else: c = shade(c, 0.75 + 0.03 * max(-4, min(8, y)))
        d.rectangle([x * S, z * S, x * S + S - 1, z * S + S - 1], fill=c)
f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
for c, (x, z, lv) in md.SITES.items():
    X, Z = x * S, z * S
    d.ellipse([X - 11, Z - 11, X + 11, Z + 11], fill=(20, 20, 20), outline=(255, 210, 60), width=2)
    d.text((X, Z), c, fill=(255, 220, 80), font=f, anchor='mm')
for (x, z, lv), col in ((md.SPAWN_T, (230, 150, 50)), (md.SPAWN_CT, (90, 140, 230))):
    d.rectangle([x * S - 10, z * S - 7, x * S + 10, z * S + 7], outline=col, width=2)
im = im.crop((0, 0, GW * S, 120 * S)).resize((256, 179), Image.LANCZOS)
im.save(sys.argv[1]); print(im.size)
