#!/usr/bin/env python3
"""Превью моделей: ортографический рендер с z-буфером по json + png (как в игре: box-UV, кости, повороты)."""
import json, math, sys
import numpy as np
from PIL import Image

BONES = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0), 'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}

def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]); Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def faces(js, tex, pose):
    K = js['k']; out = []
    for b in js['boxes']:
        bone = b['b']; u, v = b['uv']; ox, oy, oz = b['o']; sx, sy, sz = b['s']
        dx, dy, dz = round(sx * K), round(sy * K), round(sz * K)
        piv = np.array(BONES[bone], float); off = np.array(js['off'].get(bone, [0, 0, 0]), float)
        Rb = rot(*pose.get(bone, (0, 0, 0)))
        if 'r' in b:
            rp = np.array(b['r'][:3]); Rr = rot(*b['r'][3:])
        else:
            rp = np.zeros(3); Rr = np.eye(3)
        def T(p):
            p = np.array(p, float)
            p = Rr @ (p - rp) + rp
            return Rb @ (p + off) + piv
        x0, y0, z0, x1, y1, z1 = ox, oy, oz, ox + sx, oy + sy, oz + sz
        # грань: начало, ось a (вправо по текстуре), ось b (вниз по текстуре), область атласа
        F = [
            ((x0, y0, z0), (x1, y0, z0), (x0, y1, z0), (u + dz, v + dz, dx, dy)),            # n: a→+x, b→+y
            ((x1, y0, z1), (x0, y0, z1), (x1, y1, z1), (u + 2 * dz + dx, v + dz, dx, dy)),   # s: a→−x
            ((x0, y0, z1), (x0, y0, z0), (x0, y1, z1), (u, v + dz, dz, dy)),                 # w: a→−z
            ((x1, y0, z0), (x1, y0, z1), (x1, y1, z0), (u + dz + dx, v + dz, dz, dy)),       # e: a→+z
            ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0), (u + dz, v, dx, dz)),                 # u: a→+x, b→−z
            ((x0, y1, z1), (x1, y1, z1), (x0, y1, z0), (u + dz + dx, v, dx, dz)),            # d
        ]
        for p0, pa, pb, reg in F:
            out.append((T(p0), T(pa), T(pb), reg))
    return out

def render(js, tex, yaw=0.0, pitch=0.0, S=12, pose={}, W=None, H=None):
    T = np.array(tex.convert('RGBA')).astype(np.float32)
    fs = faces(js, tex, pose)
    R = rot(pitch, yaw, 0)
    W = W or int(26 * S); H = H or int(36 * S)
    img = np.zeros((H, W, 3), np.float32); img[:] = (38, 42, 50)
    zb = np.full((H, W), 1e9)
    L = np.array([-0.4, -0.7, -0.6]); L /= np.linalg.norm(L)
    for p0, pa, pb, (ru, rv, rw, rh) in fs:
        p0, pa, pb = R @ p0, R @ pa, R @ pb
        ea, eb = pa - p0, pb - p0
        n = np.cross(ea, eb); nn = np.linalg.norm(n)
        if nn < 1e-9: continue
        n /= nn
        bright = 0.55 + 0.45 * max(0.0, -float(n @ L))
        # экранные координаты: x → x, y → y (y вниз), глубина z
        def scr(p): return np.array([p[0] * S + W / 2, p[1] * S + H * 0.06 + 9.5 * S])
        s0 = scr(p0); sa = scr(pa) - s0; sb = scr(pb) - s0
        det = sa[0] * sb[1] - sa[1] * sb[0]
        if abs(det) < 1e-6: continue
        xs = [s0[0], s0[0] + sa[0], s0[0] + sb[0], s0[0] + sa[0] + sb[0]]
        ys = [s0[1], s0[1] + sa[1], s0[1] + sb[1], s0[1] + sa[1] + sb[1]]
        xa, xb = max(0, int(min(xs))), min(W - 1, int(max(xs)) + 1)
        ya, yb = max(0, int(min(ys))), min(H - 1, int(max(ys)) + 1)
        if xa > xb or ya > yb: continue
        X, Y = np.meshgrid(np.arange(xa, xb + 1) + 0.5, np.arange(ya, yb + 1) + 0.5)
        dx, dy = X - s0[0], Y - s0[1]
        a = (dx * sb[1] - dy * sb[0]) / det
        b = (sa[0] * dy - sa[1] * dx) / det
        m = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
        if not m.any(): continue
        z = p0[2] + a * ea[2] + b * eb[2]
        tu = np.clip((ru + a * rw).astype(int), 0, T.shape[1] - 1); tv = np.clip((rv + b * rh).astype(int), 0, T.shape[0] - 1)
        col = T[tv, tu]
        m &= col[..., 3] > 10
        sub = zb[ya:yb + 1, xa:xb + 1]
        m &= z < sub - 1e-4
        if not m.any(): continue
        sub[m] = z[m]
        img[ya:yb + 1, xa:xb + 1][m] = col[..., :3][m] * bright
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))

if __name__ == '__main__':
    d = sys.argv[1]; out = sys.argv[2]
    ids = sys.argv[3].split(',') if len(sys.argv) > 3 else ['ct', 't']
    sheets = []
    for mid in ids:
        js = json.load(open(f'{d}/agents/{mid}.json')); tex = Image.open(f'{d}/textures/entity/agent_{mid}.png')
        views = [render(js, tex, 0, 0), render(js, tex, -0.7, 0.12), render(js, tex, math.pi, 0)]
        w = sum(v.width for v in views); sh = Image.new('RGB', (w, views[0].height))
        x = 0
        for v in views: sh.paste(v, (x, 0)); x += v.width
        sheets.append(sh)
    cols = 2
    rows = (len(sheets) + cols - 1) // cols
    full = Image.new('RGB', (sheets[0].width * cols, sheets[0].height * rows))
    for i, s_ in enumerate(sheets): full.paste(s_, ((i % cols) * sheets[0].width, (i // cols) * sheets[0].height))
    full.save(out)
