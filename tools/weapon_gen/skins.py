import math
from wgen import MATS, hsh
from PIL import Image

METAL = {'steel', 'blued', 'darksteel', 'bright', 'slide'}
FURN = {'wood', 'woodd', 'poly', 'olive', 'oliveD', 'tan', 'smoke', 'navy', 'rubber'}

def lerp(a, b, t):
    t = max(0.0, min(1.0, t)); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def ramp(cols, t):
    t = max(0.0, min(0.9999, t)) * (len(cols) - 1); i = int(t)
    return lerp(cols[i], cols[i + 1], t - i)

# painter(p, h, k) -> rgb ; p = (x,y,z) normalised 0..1 ; h = hash 0..1 per texel ; ab = absolute coords (mm-ish units)
def solid(c): return lambda p, h, ab: c
def grad(cols, axis=2, jitter=0.0): return lambda p, h, ab: ramp(cols, p[axis] + (h - .5) * jitter)
def stripes(c1, c2, period=3, axis=2, diag=False):
    def f(p, h, ab):
        v = ab[axis] + (ab[1] if diag else 0)
        return c1 if int(math.floor(v / period)) % 2 == 0 else c2
    return f
def camo(c1, c2, c3, s=1.6):
    def f(p, h, ab):
        n = hsh(int(ab[0] // s), int(ab[2] // s) + 31 * int(ab[1] // s), 5)
        return c1 if n < .38 else (c2 if n < .72 else c3)
    return f
def digi(c1, c2, c3):
    def f(p, h, ab):
        n = hsh(int(ab[0] // 1.0), int(ab[2] // 1.0) + 17 * int(ab[1] // 1.0), 9)
        return c1 if n < .4 else (c2 if n < .75 else c3)
    return f
def carbon(c1, c2):
    def f(p, h, ab):
        return c1 if (int(ab[0] // 1) + int(ab[1] // 1) + int(ab[2] // 1)) % 2 == 0 else c2
    return f
def flame(cols):
    def f(p, h, ab):
        t = 1 - p[1] * 1.15 + (hsh(int(ab[2] // 1), int(ab[0] // 1), 3) - .5) * .45 + math.sin(ab[2] * .7) * .12
        return ramp(cols, 1 - t)
    return f
def hazard(c1, c2, period=2.5):
    def f(p, h, ab):
        return c1 if int(math.floor((ab[2] + ab[1] * 1.2) / period)) % 2 == 0 else c2
    return f
def marble(cols, s=1.3):
    def f(p, h, ab):
        n = (math.sin(ab[2] * .55 + math.sin(ab[1] * .9) * 2 + ab[0] * .4) + 1) / 2
        n = n * .7 + hsh(int(ab[2] // s), int(ab[1] // s) + 7 * int(ab[0] // s), 2) * .3
        return ramp(cols, n)
    return f
def web(c1, c2):
    def f(p, h, ab):
        a = (ab[2] * 1.7 + ab[1] * 2.3) % 4; b = (ab[2] * 1.7 - ab[1] * 2.3) % 5
        return c2 if (a < .55 or b < .55) else c1
    return f
def tiger(c1, c2):
    def f(p, h, ab):
        v = math.sin(ab[2] * 1.3 + math.sin(ab[1] * 1.7) * 1.4)
        return c2 if v > .35 else c1
    return f

def bounds(g):
    xs = [c.x for c in g.cubes]; ys = [c.y for c in g.cubes]; zs = [c.z for c in g.cubes]
    return ((min(a[0] for a in xs), max(a[1] for a in xs)), (min(a[0] for a in ys), max(a[1] for a in ys)),
            (min(a[0] for a in zs), max(a[1] for a in zs)))

def face_pos(c, face, xx, yy, w, h):
    fx, fy = (xx + .5) / w, (yy + .5) / h
    x0, x1 = c.x; y0, y1 = c.y; z0, z1 = c.z
    if face == 'north': return (x0 + fx * (x1 - x0), y1 - fy * (y1 - y0), z1)
    if face == 'south': return (x1 - fx * (x1 - x0), y1 - fy * (y1 - y0), z0)
    if face == 'east':  return (x1, y1 - fy * (y1 - y0), z1 - fx * (z1 - z0))
    if face == 'west':  return (x0, y1 - fy * (y1 - y0), z0 + fx * (z1 - z0))
    if face == 'up':    return (x0 + fx * (x1 - x0), y1, z1 - fy * (z1 - z0))
    return (x0 + fx * (x1 - x0), y0, z0 + fy * (z1 - z0))

def apply(g, metal=None, furn=None, special=None, gloss=1.0):
    """special: {part: painter} overrides. Luminance of the base texel is preserved relative to material."""
    bx, by, bz = bounds(g)
    img = g.img.copy(); px = img.load()
    for c in g.cubes:
        if c.part in (special or {}): pt = special[c.part]
        elif c.mat in METAL: pt = metal
        elif c.mat in FURN: pt = furn
        else: pt = None
        if pt is None: continue
        base = MATS[c.mat]; bl = .3 * base[0] + .59 * base[1] + .11 * base[2]
        for face, (u, v, w, h) in c.rects().items():
            for yy in range(h):
                for xx in range(w):
                    r, gg, b, a = px[u + xx, v + yy]
                    if a == 0: continue
                    P = face_pos(c, face, xx, yy, w, h)
                    pn = ((P[0] - bx[0]) / max(1e-6, bx[1] - bx[0]), (P[1] - by[0]) / max(1e-6, by[1] - by[0]),
                          (P[2] - bz[0]) / max(1e-6, bz[1] - bz[0]))
                    col = pt(pn, hsh(u + xx, v + yy, 21), P)
                    lum = .3 * r + .59 * gg + .11 * b
                    k = max(.25, min(1.7, lum / bl))
                    # lift dark materials so skins stay vivid
                    k = k ** .9
                    px[u + xx, v + yy] = tuple(max(0, min(255, int(col[i] * k * gloss))) for i in range(3)) + (255,)
    return img

C = lambda h: tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
BLK = C('#16181c'); RED = C('#d02a2a'); DRED = C('#7a1418'); WHT = C('#e9edf2'); GRY = C('#6c727c')
GOLD = C('#e6b94a'); DGOLD = C('#a67c22'); CYAN = C('#27d7e6'); MAG = C('#e03ccf'); PUR = C('#6b3bd1')
SAND = C('#c8a869'); SAND2 = C('#a98a52'); SAND3 = C('#8a6e3e'); GRN = C('#4f7a3a'); GRN2 = C('#34552a'); GRN3 = C('#7c9a52')
NAVY = C('#24365c'); BLU = C('#3d78d6'); ICE = C('#bfe9ff'); ORG = C('#f08a24'); YEL = C('#f5d62a'); LIME = C('#7fe03a')
SLATE = C('#4a5561'); RUST = C('#a4502a'); RUST2 = C('#6e3118'); PINK = C('#ff6fb0')

# id, ru, en, tier, metal painter, furniture painter, special
SKINS = {
 'ak47': [
  ('jungle', 'Джунгли', 'Jungle', 'uncommon', camo(GRN, GRN2, GRN3), solid(C('#2f3b22'))),
  ('redline', 'Алая линия', 'Redline', 'rare', stripes(BLK, RED, 2.6, 2, True), solid(C('#202228'))),
  ('dragon', 'Дракон', 'Dragon', 'legendary', flame([C('#1a0a0a'), DRED, RED, ORG, YEL]), solid(C('#2a1210'))),
 ],
 'awp': [
  ('dune', 'Дюна', 'Dune', 'uncommon', camo(SAND, SAND2, SAND3, 2.0), solid(C('#7d6a44'))),
  ('arctic', 'Арктика', 'Arctic', 'rare', camo(WHT, C('#b9c9d8'), C('#8aa0b6'), 2.0), solid(C('#cdd8e4'))),
  ('aurora', 'Аврора', 'Aurora', 'legendary', grad([C('#1d2a6e'), CYAN, LIME, MAG, PUR], 2), solid(C('#161a3a'))),
 ],
 'deagle': [
  ('rust', 'Ржавчина', 'Rust', 'uncommon', marble([RUST2, RUST, C('#c9784a'), RUST]), solid(C('#3a2a22'))),
  ('hazard', 'Опасность', 'Hazard', 'rare', hazard(YEL, BLK), solid(C('#202020'))),
  ('goldage', 'Золотой век', 'Golden Age', 'legendary', marble([DGOLD, GOLD, C('#fff2a8'), GOLD]), solid(C('#2a1c10'))),
 ],
 'm4a4': [
  ('urban', 'Город', 'Urban', 'uncommon', digi(SLATE, C('#6d7885'), C('#2e353d')), solid(C('#272c33'))),
  ('carbon', 'Карбон', 'Carbon', 'rare', carbon(C('#2c3038'), C('#14161a')), carbon(C('#23262c'), C('#0f1013'))),
  ('bloodmoon', 'Кровавая луна', 'Blood Moon', 'legendary', grad([BLK, DRED, RED, C('#ffb0a0')], 1), solid(C('#2b0d10'))),
 ],
 'glock18': [
  ('slate', 'Сланец', 'Slate', 'uncommon', solid(C('#58667a')), solid(C('#2f3644'))),
  ('hazefade', 'Закат', 'Sunset Fade', 'epic', grad([PUR, MAG, ORG, YEL], 2), solid(C('#2a1f3a'))),
  ('ember', 'Угли', 'Ember', 'rare', flame([BLK, DRED, ORG]), solid(C('#2a2a2a'))),
 ],
 'usp': [
  ('khaki', 'Хаки', 'Khaki', 'uncommon', solid(C('#8a8458')), solid(C('#4a4a32'))),
  ('toxic', 'Токсин', 'Toxic', 'epic', marble([C('#10240f'), LIME, C('#3a8a22')]), solid(C('#1a2a14'))),
  ('midnight', 'Полночь', 'Midnight', 'rare', stripes(C('#1c2240'), C('#2e3c78'), 3, 2), solid(C('#14172a'))),
 ],
 'galil': [
  ('sandstorm', 'Песчаная буря', 'Sandstorm', 'uncommon', camo(SAND, SAND2, SAND3, 1.6), solid(C('#6d5a38'))),
  ('crystal', 'Кристалл', 'Crystal', 'epic', marble([C('#1b4a8a'), CYAN, ICE, CYAN]), solid(C('#12304e'))),
  ('plum', 'Слива', 'Plum', 'rare', grad([C('#3a1a4a'), PUR, MAG], 1), solid(C('#241230'))),
 ],
 'p90': [
  ('olivedrab', 'Хаки-поле', 'Field', 'uncommon', camo(C('#6d7a44'), C('#505c30'), C('#8c9a5a'), 1.5), solid(C('#2c331c'))),
  ('cosmos', 'Космос', 'Cosmos', 'epic', marble([C('#0a0a2a'), PUR, MAG, C('#101040')], 1.0), solid(C('#10102a'))),
  ('sky', 'Небо', 'Sky', 'rare', grad([BLU, C('#9fd2ff'), WHT], 1), solid(C('#2b4a78'))),
 ],
 'fiveseven': [
  ('olivegrid', 'Оливковая сетка', 'Olive Grid', 'uncommon', digi(C('#5c6a3a'), C('#46532c'), C('#76864a')), solid(C('#2d3420'))),
  ('cyber', 'Киберпанк', 'Cyberpunk', 'epic', stripes(C('#12121f'), CYAN, 2.4, 2), solid(C('#1c1c30'))),
  ('coral', 'Коралл', 'Coral', 'rare', grad([C('#e0566a'), PINK, C('#ffd2c0')], 2), solid(C('#4a2a32'))),
 ],
 'revolver': [
  ('copper', 'Медь', 'Copper', 'uncommon', marble([C('#6e3a1c'), C('#c0743a'), C('#e09a60')]), solid(C('#3a2412'))),
  ('pirategold', 'Пиратское золото', 'Pirate Gold', 'epic', marble([DGOLD, GOLD, C('#fff2a8')]), solid(C('#3a1a12'))),
  ('abyss', 'Бездна', 'Abyss', 'rare', grad([C('#06121f'), NAVY, CYAN], 1), solid(C('#0c1a2a'))),
 ],
 'm4a1s': [
  ('graphite', 'Графит', 'Graphite', 'uncommon', solid(C('#464c56')), solid(C('#2a2e36'))),
  ('goldsteel', 'Золото и сталь', 'Gold & Steel', 'epic', stripes(C('#9aa4b4'), GOLD, 2.2, 2), solid(C('#2a2a30'))),
  ('mint', 'Мята', 'Mint', 'rare', grad([C('#1d6e5a'), C('#46d6a8'), C('#d4ffe9')], 2), solid(C('#16352c'))),
 ],
}
KNIFE = [
 ('fade', 'Градиент', 'Fade', grad([C('#6a3bd1'), MAG, ORG, YEL], 2), solid(C('#201a2a'))),
 ('doppler', 'Допплер', 'Doppler', marble([C('#1a2a8a'), BLU, C('#9a3bd6'), C('#2a3aaa')], 1.0), solid(C('#181c34'))),
 ('tiger', 'Тигровый зуб', 'Tiger Tooth', tiger(C('#e0b040'), C('#6a3a10')), solid(C('#2a1a0c'))),
 ('crimsonweb', 'Багровая паутина', 'Crimson Web', web(C('#8a1218'), C('#1a0508')), solid(C('#2a0a0c'))),
 ('heat', 'Закалка', 'Case Hardened', marble([C('#2a3a8a'), C('#6a4ac0'), C('#d8b040'), C('#3a58c0')], 1.1), solid(C('#1a1a22'))),
]
TIERS = ['uncommon', 'rare', 'epic', 'legendary']

C2 = lambda h: tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))
# стандартные ножи команд: id, ru, en, metal, furniture
STD = [('t', 'Нож (Т)', 'Knife (T)', solid(C2('#9a9ea6')), solid(C2('#7a5430'))),
       ('ct', 'Нож (КТ)', 'Knife (CT)', solid(C2('#c4d0e0')), solid(C2('#2f4f8c')))]
