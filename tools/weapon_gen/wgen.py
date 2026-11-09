import json, math, os
from PIL import Image, ImageDraw, ImageFont

XC = -12.72   # x centre in file space (same as an94 -> same display transforms)

def hsh(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0

MATS = {
 'steel': (88, 93, 104), 'blued': (66, 72, 86), 'darksteel': (42, 46, 55), 'bright': (170, 176, 188),
 'wood': (150, 96, 52), 'woodd': (112, 68, 36), 'poly': (58, 61, 71), 'olive': (80, 96, 60),
 'rubber': (28, 28, 31), 'brass': (200, 162, 66), 'glass': (70, 130, 178), 'black': (22, 22, 26),
 'slide': (88, 90, 100), 'white': (226, 226, 230), 'oliveD': (58, 70, 44), 'orange': (214, 120, 40), 'tan': (176, 152, 108), 'smoke': (104, 98, 92), 'navy': (50, 60, 84),
 'chrome': (186, 192, 204), 'red': (212, 44, 44), 'green': (74, 224, 118), 'grip': (50, 52, 60), 'gold': (214, 176, 76), 'steelL': (110, 116, 128),
}
NOISE = {'wood': 9, 'woodd': 8, 'steel': 5, 'blued': 4, 'darksteel': 3, 'poly': 3, 'olive': 5, 'oliveD': 4,
         'bright': 4, 'slide': 4, 'rubber': 2, 'black': 2, 'brass': 6, 'glass': 3, 'white': 3, 'orange': 4, 'tan': 5, 'smoke': 3, 'navy': 3, 'chrome': 3, 'red': 3, 'green': 3, 'grip': 3, 'gold': 5, 'steelL': 4}
FACE_K = {'up': 1.07, 'down': 0.93, 'north': 1.0, 'south': 1.0, 'east': 1.0, 'west': 1.0}
FACES = ['north', 'east', 'south', 'west', 'up', 'down']
PART_TINT = [(230, 90, 90), (90, 200, 120), (90, 140, 230), (230, 200, 80), (200, 110, 220), (80, 210, 210),
             (240, 150, 70), (150, 150, 240), (170, 230, 100), (240, 120, 180), (120, 180, 150), (200, 170, 120)]

def clamp(v): return max(0, min(255, int(v)))

class Cube:
    def __init__(s, bone, part, mat, x, y, z, pat, rot, rpiv, name, inflate=0.0, K=1):
        s.bone, s.part, s.mat, s.pat = bone, part, mat, pat
        s.x, s.y, s.z = x, y, z
        s.rot, s.rpiv, s.name, s.inflate = rot, rpiv, name, inflate
        s.sx, s.sy, s.sz = x[1] - x[0], y[1] - y[0], z[1] - z[0]
        s.K = K
        s.fw, s.fh, s.fd = math.ceil(s.sx * K - 1e-6), math.ceil(s.sy * K - 1e-6), math.ceil(s.sz * K - 1e-6)
        s.fw, s.fh, s.fd = max(1, s.fw), max(1, s.fh), max(1, s.fd)
        s.foot = (2 * s.fd + 2 * s.fw, s.fd + s.fh)
        s.u = s.v = 0          # position inside part block first, then global
    def rects(s):
        u, v, w, h, d = s.u, s.v, s.fw, s.fh, s.fd
        return {'north': (u + d, v + d, w, h), 'east': (u, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
                'west': (u + d + w, v + d, d, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}

class Gun:
    def __init__(s, gid, tw=256, K=2):
        s.id, s.tw, s.K = gid, tw, K
        s.bones = {}   # name -> dict(parent,pivot)
        s.order = []
        s.cubes = []
        s.parts = []
        s.fxs = []
        s.bone('root', None, (-XC, -8, 0))
    def bone(s, name, parent, pivot):
        s.bones[name] = {'parent': parent, 'pivot': pivot}
        s.order.append(name)
    def box(s, bone, part, mat, x, y, z, pat=None, rot=None, rpiv=None, name=None):
        if part not in s.parts: s.parts.append(part)
        c = Cube(bone, part, mat, x, y, z, pat, rot, rpiv, name, K=s.K)
        s.cubes.append(c)
        return c
    def fx(s, bone, p):
        s.fxs.append((bone, p))

    # ---------------- packing ----------------
    def pack(s):
        blocks = []
        s.blocks = {}
        for part in s.parts:
            cs = [c for c in s.cubes if c.part == part]
            bw = max(40 * s.K, max(c.foot[0] for c in cs) + 1)
            cs_sorted = sorted(cs, key=lambda c: -c.foot[1])
            x = y = rowh = 0
            for c in cs_sorted:
                if x + c.foot[0] + 1 > bw:
                    x = 0; y += rowh + 1; rowh = 0
                c.u, c.v = x, y
                x += c.foot[0] + 1; rowh = max(rowh, c.foot[1])
            bh = y + rowh + 1
            s.blocks[part] = {'w': bw, 'h': bh}
            blocks.append(part)
        # atlas
        tw = s.tw
        x, y, rowh = 4, 0, 2   # reserve 4x2 for fx block at (0,0)
        order = sorted(blocks, key=lambda p: -s.blocks[p]['h'])
        for p in order:
            b = s.blocks[p]
            if x + b['w'] > tw:
                x = 0; y += rowh; rowh = 0
            b['x'], b['y'] = x, y
            x += b['w']; rowh = max(rowh, b['h'])
        th = y + rowh
        p2 = 16
        while p2 < th: p2 *= 2
        s.th = p2
        for c in s.cubes:
            b = s.blocks[c.part]
            c.u += b['x']; c.v += b['y']

    # ---------------- painting ----------------
    def px(s, c, face, x, y, w, h):
        base = MATS[c.mat]
        n = NOISE.get(c.mat, 3)
        k = FACE_K[face]
        r, g, b = [base[i] * k for i in range(3)]
        nz = (hsh(x + c.u, y + c.v, 7) - 0.5) * 1.0 * n + (hsh((x + c.u) // 3, (y + c.v) // 3, 19) - 0.5) * 1.4 * n
        r += nz; g += nz; b += nz
        pat = c.pat
        side = face in ('north', 'south', 'east', 'west')
        metal = c.mat in ('steel', 'blued', 'darksteel', 'bright', 'slide', 'chrome', 'steelL')
        # soft vertical gradient on side faces (top lighter)
        if side and h >= 4:
            gk = 1 + 0.10 * (0.5 - y / (h - 1))
            r *= gk; g *= gk; b *= gk
        # bevel / edge light
        if w >= 3 and h >= 3:
            e = 16 if metal else 11
            if y == 0 or x == 0: r += e; g += e; b += e
            if y == h - 1 or x == w - 1: r -= e; g -= e; b -= e
            if w >= 6 and h >= 6:
                if y == 1 or x == 1: r += e * .35; g += e * .35; b += e * .35
                if y == h - 2 or x == w - 2: r -= e * .35; g -= e * .35; b -= e * .35
        if w >= 4 and h >= 4 and (x in (0, w - 1) or y in (0, h - 1)):
            r *= .86; g *= .86; b *= .86
        # light edge wear on metals
        if metal and (x in (0, w - 1) or y in (0, h - 1)) and hsh(x + c.u, y + c.v, 41) > 0.82:
            r += 34; g += 34; b += 34
        if pat == 'grain' and side:
            long_h = max(c.fw, c.fd) >= c.fh
            idx = y if long_h else x
            if hsh(idx, c.u + c.v, 3) < 0.28: r *= 0.82; g *= 0.82; b *= 0.82
            if hsh(idx, c.u + c.v, 5) > 0.88: r *= 1.12; g *= 1.12; b *= 1.12
            if hsh(x // 3, y, c.u) > 0.93: r *= 0.7; g *= 0.7; b *= 0.7
        elif pat == 'rib_h' and side:
            if y % 2 == 1: r *= .78; g *= .78; b *= .78
        elif pat == 'rib_v' and side:
            if x % 2 == 1: r *= .78; g *= .78; b *= .78
        elif pat == 'fin' and side:
            if x % 4 >= 2: r *= .62; g *= .62; b *= .62
        elif pat == 'stipple':
            if (x + y) % 2 == 0: r *= .72; g *= .72; b *= .72
            elif hsh(x, y, c.u) > 0.8: r *= 1.2; g *= 1.2; b *= 1.2
        elif pat == 'lens' and face in ('north', 'south') and w >= 3 and h >= 3:
            cx, cy = (w - 1) / 2, (h - 1) / 2
            d = max(abs(x - cx) / (w / 2), abs(y - cy) / (h / 2))
            if d > 0.78: r, g, b = 24, 26, 32
            else:
                t = (x + y) / (w + h)
                r, g, b = 40 + 50 * t, 92 + 70 * t, 150 + 60 * t
                if x == 1 and y == 1: r, g, b = 220, 235, 255
                if x == w - 3 and y == h - 3: r, g, b = 120, 170, 220
        elif pat == 'slits' and side:
            if x % 3 == 1 and 0 < y < h - 1: r, g, b = 14, 14, 18
        elif pat == 'vent' and side:
            if x % 4 in (1, 2) and y % 4 in (1, 2) and 1 < x < w - 2 and 1 < y < h - 2: r, g, b = 12, 12, 16
        elif pat == 'slot_h' and side:
            if y % 4 in (1, 2) and 1 < x < w - 2 and 0 < y < h - 1: r, g, b = 12, 12, 16
        elif pat == 'magwell' and face == 'down' and w >= 3 and h >= 3:
            if 0 < x < w - 1 and 0 < y < h - 1: r, g, b = 14, 14, 18
        elif pat == 'dot' and face in ('north', 'south') and x == w // 2 and y == h // 2:
            r, g, b = 235, 235, 235
        elif pat == 'tri' and face in ('north', 'south'):
            if x == w // 2 and y == h // 2: r, g, b = 120, 255, 150
            elif x == w // 2 and y == h // 2 + 1 and h > 3: r, g, b = 60, 160, 90
        elif pat == 'redtip' and face in ('north', 'south'):
            if y < max(1, h // 3): r, g, b = 226, 50, 40
        elif pat == 'port' and side:
            if 1 <= x <= w - 2 and 1 <= y <= h - 2: r, g, b = 16, 16, 20
        elif pat == 'chk' and side:
            if (x // 2 + y // 2) % 2 == 0: r *= .8; g *= .8; b *= .8
        elif pat == 'knurl' and side:
            if (x + y) % 3 == 0: r *= .7; g *= .7; b *= .7
        elif pat == 'scratch' and side:
            if hsh(x, y, 11) > 0.93: r += 40; g += 40; b += 40
            if hsh(x // 4, y, 13) > 0.97: r *= .6; g *= .6; b *= .6
        elif pat == 'rounds' and side and h >= 3:
            if y == h // 2 and x % 3 == 1: r, g, b = 224, 140, 40
            elif y == h // 2 - 1 and x % 3 == 1: r, g, b = 150, 90, 30
        elif pat == 'edge' and side:
            if y >= h - 1: r, g, b = 235, 240, 250
            elif y == 0: r *= .8; g *= .8; b *= .8
        elif pat == 'stripe_h' and side:
            if y == h // 2 or (h >= 6 and y == h // 2 - 1): r, g, b = 214, 120, 40
        elif pat == 'seam' and side and h >= 4:
            if y == h // 2: r *= .5; g *= .5; b *= .5
        elif pat == 'seam_v' and side and w >= 4:
            if x == w // 2: r *= .5; g *= .5; b *= .5
        elif pat == 'engrave' and side and h >= 5 and w >= 8:
            if y in (h // 2, h // 2 + 1) and 2 <= x <= w - 3 and hsh(x // 2, c.u, 17) > 0.35: r *= .45; g *= .45; b *= .45
        elif pat == 'screw' and side and w >= 4 and h >= 4:
            cx, cy = w // 2, h // 2
            if (x, y) in ((cx, cy), (cx - 1, cy), (cx, cy - 1), (cx - 1, cy - 1)):
                r, g, b = (150, 154, 166) if (x, y) == (cx - 1, cy - 1) else (46, 48, 56)
        elif pat == 'rivets' and side and h >= 4:
            if x % 8 == 3 and y == h // 2: r, g, b = 168, 172, 184
            elif x % 8 == 4 and y == h // 2: r, g, b = 36, 38, 46
        elif pat == 'weave' and side:
            if (x + y) % 4 == 0 or (x - y) % 4 == 0: r *= .78; g *= .78; b *= .78
        elif pat == 'glint' and side:
            if y == 1 and x > 1: r += 40; g += 40; b += 40
        return (clamp(r), clamp(g), clamp(b), 255)

    def paint(s):
        s.pack()
        img = Image.new('RGBA', (s.tw, s.th), (0, 0, 0, 0))
        pix = img.load()
        for c in s.cubes:
            for face, (u, v, w, h) in c.rects().items():
                for yy in range(h):
                    for xx in range(w):
                        pix[u + xx, v + yy] = s.px(c, face, xx, yy, w, h)
        s.img = img
        return img

    # ---------------- outputs ----------------
    def geo(s):
        bones = []
        cubes_by_bone = {}
        for c in s.cubes: cubes_by_bone.setdefault(c.bone, []).append(c)
        fxby = {}
        for b, p in s.fxs: fxby.setdefault(b, []).append(p)
        for name in s.order:
            b = s.bones[name]
            d = {'name': name, 'pivot': [round(XC + b['pivot'][0], 4), b['pivot'][1], b['pivot'][2]]}
            if b['parent']: d['parent'] = b['parent']
            cl = []
            for c in cubes_by_bone.get(name, []):
                r = c.rects()
                uv = {}
                for f in FACES:
                    u, v, w, h = r[f]
                    # real (possibly fractional) face size
                    dims = {'north': (c.sx, c.sy), 'south': (c.sx, c.sy), 'east': (c.sz, c.sy), 'west': (c.sz, c.sy),
                            'up': (c.sx, c.sz), 'down': (c.sx, c.sz)}[f]
                    dims = (dims[0] * s.K, dims[1] * s.K)
                    if f == 'down':
                        uv[f] = {'uv': [u, v + h], 'uv_size': [dims[0], -dims[1]]}
                    else:
                        uv[f] = {'uv': [u, v], 'uv_size': [dims[0], dims[1]]}
                cd = {'origin': [round(XC + c.x[0], 4), c.y[0], c.z[0]], 'size': [c.sx, c.sy, c.sz]}
                if c.rot:
                    cd['pivot'] = [round(XC + c.rpiv[0], 4), c.rpiv[1], c.rpiv[2]]
                    cd['rotation'] = list(c.rot)
                cd['uv'] = uv
                cl.append(cd)
            for p in fxby.get(name, []):
                t = 0.01
                cl.append({'origin': [round(XC + p[0] - t / 2, 4), p[1] - t / 2, p[2] - t / 2], 'size': [t, t, t],
                           'uv': {f: {'uv': [0.5, 0.5], 'uv_size': [1, 1]} for f in FACES}})
            if cl: d['cubes'] = cl
            bones.append(d)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.combo_rush.' + s.id, 'texture_width': s.tw, 'texture_height': s.th,
                            'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
            'bones': bones}]}

    def template(s, scale=6):
        W, H = s.tw * scale, s.th * scale
        im = Image.new('RGBA', (W, H), (24, 26, 32, 255))
        d = ImageDraw.Draw(im)
        try: font = ImageFont.load_default()
        except Exception: font = None
        for i, p in enumerate(s.parts):
            b = s.blocks[p]; col = PART_TINT[i % len(PART_TINT)]
            d.rectangle([b['x'] * scale, b['y'] * scale, (b['x'] + b['w']) * scale - 1, (b['y'] + b['h']) * scale - 1],
                        fill=(col[0] // 5 + 20, col[1] // 5 + 20, col[2] // 5 + 20, 255), outline=col, width=2)
        for c in s.cubes:
            col = PART_TINT[s.parts.index(c.part) % len(PART_TINT)]
            for f, (u, v, w, h) in c.rects().items():
                d.rectangle([u * scale, v * scale, (u + w) * scale - 1, (v + h) * scale - 1], outline=(col[0], col[1], col[2], 120))
        for i, p in enumerate(s.parts):
            b = s.blocks[p]; col = PART_TINT[i % len(PART_TINT)]
            d.text((b['x'] * scale + 4, b['y'] * scale + 3), p.upper(), fill=col, font=font)
        return im

    def uv_layout(s):
        return {'texture': [s.tw, s.th], 'parts': {p: [b['x'], b['y'], b['w'], b['h']] for p, b in s.blocks.items()}}

    # coplanar same-direction overlapping faces (z-fight) check
    def coplanar(s):
        faces = []
        for c in s.cubes:
            if c.rot: continue
            for ax, (lo, hi) in enumerate((c.x, c.y, c.z)):
                other = [a for a in range(3) if a != ax]
                rng = [(c.x, c.y, c.z)[a] for a in other]
                faces.append((ax, lo, -1, rng, c)); faces.append((ax, hi, +1, rng, c))
        bad = []
        for i in range(len(faces)):
            for j in range(i + 1, len(faces)):
                a, b = faces[i], faces[j]
                if a[0] != b[0] or a[2] != b[2] or abs(a[1] - b[1]) > 1e-6 or a[4] is b[4]: continue
                ov = all(min(a[3][k][1], b[3][k][1]) - max(a[3][k][0], b[3][k][0]) > 1e-6 for k in range(2))
                if ov: bad.append(('xyz'[a[0]], a[1], a[2], a[4].part, b[4].part, (a[4].x, a[4].y, a[4].z), (b[4].x, b[4].y, b[4].z)))
        return bad
