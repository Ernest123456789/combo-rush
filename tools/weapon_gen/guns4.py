"""Стволы v4: те же построители (hd.py / hd2.py), но
  * 4 тексела на единицу вместо 2 (насечки, швы, заклёпки, износ — вдвое тоньше);
  * фаски: у крупных коробок рёбра вдоль длинной оси срезаются (коробка → «крест» из двух) — силуэт мягче;
  * маркировка (гравировка названия, калибра, номера) на нерабочей стороне ствольной коробки/затвора;
  * невидимая кость «stattrak» — место модуля счётчика StatTrak™ на видимой стороне (его рисует Java).
Кости, пивоты и габариты прежние — анимации и display-трансформы не меняются.
Запуск: python3 guns4.py [--write] [ids...]   (превью /tmp/g4_<id>.png)"""
import sys, os, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wgen
from wgen import MATS, hsh, clamp

_orig = wgen.Gun.__init__
TW = [512]
def _init(s, gid, tw=256, K=2):
    _orig(s, gid, TW[0], 4)
wgen.Gun.__init__ = _init

import hd, hd2  # noqa: E402  (после подмены)
import skins as SK

KNIVES = ('claw', 'knife', 'bayonet', 'skeleton')
IDS = [k for k in hd.ALLG if k not in KNIVES]

# ------------------------------------------------------------------------------- фаски
def chamfer(g, c_=0.25, min_cross=1.0):
    out = []
    for c in g.cubes:
        dims = [c.sx, c.sy, c.sz]
        if c.rot or c.part.startswith('FX') or c.bone.startswith('FX'):
            out.append(c); continue
        L = max(range(3), key=lambda i: dims[i])
        o = [i for i in range(3) if i != L]
        if dims[o[0]] < min_cross or dims[o[1]] < min_cross or dims[L] < 1.0 or c.pat in ('lens',):
            out.append(c); continue
        rng = [list(c.x), list(c.y), list(c.z)]
        A = [r[:] for r in rng]; Bb = [r[:] for r in rng]
        A[o[0]] = [rng[o[0]][0] + c_, rng[o[0]][1] - c_]           # A: узкая по первой поперечной, полная по второй
        Bb[o[1]] = [rng[o[1]][0] + c_, rng[o[1]][1] - c_]
        Bb[L] = [rng[L][0] + 0.02, rng[L][1] - 0.02]                # торцы B чуть утоплены — без z-файтинга с A
        for R in (A, Bb):
            n = wgen.Cube(c.bone, c.part, c.mat, tuple(R[0]), tuple(R[1]), tuple(R[2]), c.pat, None, None, c.name, K=g.K)
            n.src = c
            out.append(n)
    return out


def coplanar_pairs(g):
    bad = g.coplanar()
    return bad


def apply_chamfer(g):
    orig = list(g.cubes)
    g.cubes = chamfer(g)
    # откатываем фаски у кубов, которые после разбиения дают совпадающие грани с соседями
    for _ in range(6):
        bad = g.coplanar()
        if not bad: break
        srcs = set()
        for b in bad:
            for cc in g.cubes:
                if (cc.x, cc.y, cc.z) in (b[5], b[6]) and getattr(cc, 'src', None) is not None: srcs.add(id(cc.src))
        if not srcs: break
        nc = []
        done = set()
        for cc in g.cubes:
            s = getattr(cc, 'src', None)
            if s is not None and id(s) in srcs:
                if id(s) not in done: nc.append(s); done.add(id(s))
            else: nc.append(cc)
        g.cubes = nc
    return g


# ------------------------------------------------------------------------------- маркировка
FONT = {  # 3×5
    'A': '010101111101101', 'B': '110101110101110', 'C': '011100100100011', 'D': '110101101101110', 'E': '111100110100111',
    'F': '111100110100100', 'G': '011100101101011', 'H': '101101111101101', 'I': '111010010010111', 'K': '101101110101101',
    'L': '100100100100111', 'M': '101111111101101', 'N': '110101101101101', 'O': '010101101101010', 'P': '110101110100100',
    'R': '110101110101101', 'S': '011100010001110', 'T': '111010010010010', 'U': '101101101101111', 'V': '101101101101010',
    'W': '101101111111101', 'X': '101101010101101', 'Y': '101101010010010', 'Z': '111001010100111',
    '0': '111101101101111', '1': '010110010010111', '2': '110001010100111', '3': '110001010001110', '4': '101101111001001',
    '5': '111100110001110', '6': '011100111101111', '7': '111001010010010', '8': '111101111101111', '9': '111101111001110',
    '-': '000000111000000', '.': '000000000000010', ' ': '000000000000000', '/': '001001010100100', '×': '000101010101000',
}
MARK = {
    'ak47': [('receiver', 'AK-47  7.62X39'), ('magazine', '30')], 'awp': [('receiver', 'AWP  .338 LAPUA'), ('scope', '10X')],
    'glock18': [('slide', 'GLOCK 18'), ('frame', '9X19')], 'galil': [('receiver', 'GALIL AR  5.56')],
    'deagle': [('slide', 'DESERT EAGLE'), ('barrel', '.50 AE')], 'fiveseven': [('slide', 'FIVE-SEVEN'), ('frame', '5.7X28')],
    'p90': [('receiver', 'P90  5.7X28')], 'usp': [('slide', 'USP-S  .45'), ('silencer', 'SD')],
    'revolver': [('frame', 'R8  .44 MAG')], 'm4a4': [('receiver', 'M4A4  5.56 NATO'), ('magazine', '30')],
    'm4a1s': [('receiver', 'M4A1-S  5.56'), ('silencer', 'SD')], 'famas': [('receiver', 'FAMAS F1')],
    'mp9': [('receiver', 'MP9  9X19')], 'mp7': [('receiver', 'MP7  4.6X30')], 'scar20': [('receiver', 'SCAR-20  7.62'), ('scope', '8X')],
    'taser': [('receiver', 'ZEUS X27')], 'nova': [('receiver', 'NOVA  12 GA')], 'xm1014': [('receiver', 'XM1014  12 GA')],
    'mag7': [('receiver', 'MAG-7  12 GA')], 'sawedoff': [('receiver', '12 GA')], 'mac10': [('receiver', 'MAC-10  .45')],
    'grenade_he': [('pistol_grip', 'HE')], 'grenade_flash': [('pistol_grip', 'FB')], 'grenade_smoke': [('pistol_grip', 'SMOKE')],
}
SERIAL = lambda gid: 'NO ' + str(10000 + (sum(map(ord, gid)) * 7919) % 89999)


def side_cube(g, part, face='west'):
    """самый большой куб части по площади боковой грани"""
    best, ba = None, 0
    for c in g.cubes:
        if c.part != part or c.rot: continue
        a = c.fd * c.fh + c.sx * 1000          # снаружи — самый широкий по x (у фаски это нижняя «полоса» креста)
        if a > ba and c.fd >= 12 and c.fh >= 7: best, ba = c, a
    return best


def engrave(img, rect, text, cx=0.5, cy=0.5, dark=0.42, light=1.25):
    """гравировка 3×5 с подсветкой снизу-справа (утоплено)"""
    u, v, w, h = rect
    tw = len(text) * 4 - 1
    if tw > w - 2 or h < 7: return False
    x0 = u + int((w - tw) * cx); y0 = v + int((h - 5) * cy)
    px = img.load()
    for i, ch in enumerate(text.upper()):
        m = FONT.get(ch, FONT[' '])
        for yy in range(5):
            for xx in range(3):
                if m[yy * 3 + xx] == '1':
                    X, Y = x0 + i * 4 + xx, y0 + yy
                    r, gg, b, a = px[X, Y]
                    px[X, Y] = (clamp(r * dark), clamp(gg * dark), clamp(b * dark), a)
                    if Y + 1 < v + h:
                        r, gg, b, a = px[X, Y + 1]
                        if (yy + 1 > 4 or m[(yy + 1) * 3 + xx] == '0'): px[X, Y + 1] = (clamp(r * light), clamp(gg * light), clamp(b * light), a)
    return True


def mark(g):
    """маркировка на стороне 'west' (в игре — правая, невидимая от первого лица); номер — на 'east' сзади"""
    for part, text in MARK.get(g.id, []):
        c = side_cube(g, part, 'west')
        if c is None: continue
        r = c.rects()
        if not engrave(g.img, r['west'], text, 0.5, 0.45):
            engrave(g.img, r['west'], text.split('  ')[0], 0.5, 0.45)
    main = 'slide' if any(c.part == 'slide' for c in g.cubes) else 'receiver'
    c = side_cube(g, main, 'east')
    if c is not None: engrave(g.img, c.rects()['east'], SERIAL(g.id), 0.1, 0.88, dark=0.55)


# ------------------------------------------------------------------------------- StatTrak™
ST_PARENT = {'glock18': 'frame', 'deagle': 'frame', 'fiveseven': 'frame', 'usp': 'frame', 'revolver': 'frame'}


def stattrak(g):
    """Кость-метка модуля StatTrak на видимой от первого лица стороне (python min x = «east» в игре).
    Ставим к передней трети ствольной коробки (у пистолетов — к рамке под затвором)."""
    if g.id.startswith('grenade') or g.id == 'taser': return
    par = ST_PARENT.get(g.id, 'receiver')
    cs = [c for c in g.cubes if c.bone == par and not c.rot]
    if not cs: return
    x0 = min(c.x[0] for c in cs); y0 = min(c.y[0] for c in cs); y1 = max(c.y[1] for c in cs)
    z0 = min(c.z[0] for c in cs); z1 = max(c.z[1] for c in cs)
    pistol = par == 'frame'
    L, H = (2.5, 1.0) if pistol else (3.5, 1.25)
    zc = z0 + (z1 - z0) * (0.62 if pistol else 0.55)
    yc = (y0 + y1) / 2 + (0.0 if pistol else (y1 - y0) * 0.12)
    if pistol:          # под затвором, на рамке: верх рамки
        yc = y1 - H / 2 - 0.25
    if g.id == 'revolver':                    # у револьвера рамка с горбом — модуль на боковине под барабаном
        zc, yc = z0 + (z1 - z0) * 0.42, y0 + (y1 - y0) * 0.35
    za, zb, ya, yb = zc - L / 2, zc + L / 2, yc - H / 2, yc + H / 2
    # наружная поверхность в этой области
    xs = [c.x[0] for c in g.cubes if not c.rot and c.z[0] < zb and c.z[1] > za and c.y[0] < yb and c.y[1] > ya and not c.bone.startswith('FX')]
    xo = min(xs) if xs else x0
    piv = g.bones[par]['pivot']
    g.bone('stattrak', par, piv)
    c = wgen.Cube('stattrak', 'stattrak', 'black', (xo - 0.4, xo + 0.05), (ya, yb), (za, zb), None, None, None, None, K=g.K)
    g.cubes.append(c)
    if 'stattrak' not in g.parts: g.parts.append('stattrak')


def build(gid):
    for tw in (512, 1024):
        TW[0] = tw
        g = hd.ALLG[gid]()
        apply_chamfer(g)
        stattrak(g)
        g.pack()
        if max(b['x'] + b['w'] for b in g.blocks.values()) <= g.tw: break
    g.paint()
    mark(g)
    return g


def preview(g, out):
    import texprev as TP
    from PIL import Image
    keep = g.cubes; g.cubes = [c for c in keep if c.part != 'stattrak' and not c.bone.startswith('FX')]
    W, H = 1100, 500
    allz = [c.z[1] for c in g.cubes] + [-c.z[0] for c in g.cubes]
    sc = min(30, 900 / (max(c.z[1] for c in g.cubes) - min(c.z[0] for c in g.cubes) + 1))
    vs = [TP.render(g, 90, 0, W, H, sc, bg=(52, 58, 70)), TP.render(g, -90, 0, W, H, sc, bg=(52, 58, 70)),
          TP.render(g, 60, 22, W, H, sc * .9, bg=(52, 58, 70)), TP.render(g, -120, 20, W, H, sc * .9, bg=(52, 58, 70))]
    im = Image.new('RGB', (W * 2, H * 2))
    for i, v in enumerate(vs): im.paste(v, ((i % 2) * W, (i // 2) * H))
    g.cubes = keep
    im.save(out)


ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
A = ROOT + '/src/main/resources/assets/combo-rush'; DOC = ROOT + '/docs/weapons'

if __name__ == '__main__':
    write = '--write' in sys.argv
    ids = [a for a in sys.argv[1:] if not a.startswith('--')] or IDS
    for gid in ids:
        g = build(gid)
        print(gid, len(g.cubes), g.tw, g.th)
        if not write:
            preview(g, f'/tmp/g4_{gid}.png'); continue
        json.dump(g.geo(), open(f'{A}/geo/item/weapon/{gid}.geo.json', 'w'), indent=1)
        g.img.save(f'{A}/textures/item/weapon/{gid}.png')
        g.template().convert('RGB').save(f'{DOC}/{gid}_template.png')
        json.dump(g.uv_layout(), open(f'{DOC}/{gid}_uv_layout.json', 'w'), indent=1)
        for sid, ru, en, tier, m, fu in SK.SKINS.get(gid, []):
            im = SK.apply(g, m, fu, sparkle={'epic': .012, 'legendary': .02}.get(tier, 0))
            g2img = g.img; g.img = im; mark(g); g.img = g2img       # маркировка поверх покраски
            im.save(f'{A}/textures/item/weapon/skins/{gid}_{sid}.png')
