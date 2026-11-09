"""Текстурированный предпросмотр моделей (ортографика, painter's algorithm).
Использование: python3 texprev.py ak47 [out.png]  — три вида (сбоку / 3-4 / сверху)."""
import math, sys
import numpy as np
from PIL import Image, ImageDraw

def face_defs(c):
    x0, x1 = c.x; y0, y1 = c.y; z0, z1 = c.z
    # name: (origin, u-dir vector (full size), v-dir vector (full size), normal)
    return {
        'north': ((x0, y1, z0), (c.sx, 0, 0), (0, -c.sy, 0), (0, 0, -1)),
        'south': ((x1, y1, z1), (-c.sx, 0, 0), (0, -c.sy, 0), (0, 0, 1)),
        'east':  ((x1, y1, z1), (0, 0, -c.sz), (0, -c.sy, 0), (1, 0, 0)),
        'west':  ((x0, y1, z0), (0, 0, c.sz), (0, -c.sy, 0), (-1, 0, 0)),
        'up':    ((x0, y1, z1), (c.sx, 0, 0), (0, 0, -c.sz), (0, 1, 0)),
        'down':  ((x0, y0, z0), (c.sx, 0, 0), (0, 0, c.sz), (0, -1, 0)),
    }

def rotm(yaw, pitch):
    ya, pa = math.radians(yaw), math.radians(pitch)
    Ry = np.array([[math.cos(ya), 0, math.sin(ya)], [0, 1, 0], [-math.sin(ya), 0, math.cos(ya)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(pa), -math.sin(pa)], [0, math.sin(pa), math.cos(pa)]])
    return Rx @ Ry

def render(g, yaw=90, pitch=0, W=900, H=520, scale=16, img=None, bg=(34, 38, 46), center=None, light=(0.35, 0.8, 0.45)):
    """yaw=90: смотрим со стороны -x (видна west-грань), ствол вправо."""
    img = img or g.img
    R = rotm(yaw, pitch)
    allp = []
    for c in g.cubes:
        allp += [(c.x[0], c.y[0], c.z[0]), (c.x[1], c.y[1], c.z[1])]
    ctr = np.array(center) if center is not None else (np.min(allp, 0) + np.max(allp, 0)) / 2
    L = np.array(light) / np.linalg.norm(light)
    items = []
    for c in g.cubes:
        for name, (o, du, dv, nrm) in face_defs(c).items():
            o = np.array(o, float); du = np.array(du, float); dv = np.array(dv, float)
            n = R @ np.array(nrm, float)
            if n[2] <= 0:      # камера смотрит вдоль -z вида: видимы грани с n.z > 0
                continue
            P = [o, o + du, o + du + dv, o + dv]
            if c.rot:
                th = -math.radians(c.rot[0]); cs, sn = math.cos(th), math.sin(th)
                Rx = np.array([[1, 0, 0], [0, cs, -sn], [0, sn, cs]]); pv = np.array(c.rpiv, float)
                P = [pv + Rx @ (p - pv) for p in P]; nrm = tuple(Rx @ np.array(nrm, float))
                n = R @ np.array(nrm, float)
                if n[2] <= 0: continue
            Q = [R @ (p - ctr) for p in P]
            depth = np.mean([q[2] for q in Q])
            items.append((depth, c, name, Q, n))
    items.sort(key=lambda t: t[0])
    canvas = Image.new('RGB', (W, H), bg)
    for depth, c, name, Q, n in items:
        u, v, w, h = c.rects()[name]
        tex = img.crop((u, v, u + w, v + h)).convert('RGB')
        if name == 'down':
            pass
        pts = [(W / 2 + q[0] * scale, H / 2 - q[1] * scale) for q in Q]
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        bx0, by0 = int(math.floor(min(xs))) - 1, int(math.floor(min(ys))) - 1
        bx1, by1 = int(math.ceil(max(xs))) + 1, int(math.ceil(max(ys))) + 1
        bw, bh = max(1, bx1 - bx0), max(1, by1 - by0)
        # affine: texture (tx,ty) in texels -> screen. screen = P0 + a*tx/w + b*ty/h
        p0 = np.array(pts[0]); pu = np.array(pts[1]) - p0; pv = np.array(pts[3]) - p0
        M = np.array([[pu[0] / w, pv[0] / h], [pu[1] / w, pv[1] / h]])
        det = np.linalg.det(M)
        if abs(det) < 1e-6: continue
        Mi = np.linalg.inv(M)
        # inverse: tex = Mi @ (screen - p0) ; screen = (bx0+X, by0+Y)
        off = Mi @ (np.array([bx0, by0]) - p0)
        coeffs = (Mi[0, 0], Mi[0, 1], off[0], Mi[1, 0], Mi[1, 1], off[1])
        # expand texture with transparent border to avoid wrap
        layer = tex.transform((bw, bh), Image.AFFINE, coeffs, resample=Image.NEAREST)
        mask = Image.new('L', tex.size, 255).transform((bw, bh), Image.AFFINE, coeffs, resample=Image.NEAREST)
        shade = 0.55 + 0.5 * max(0.0, float(np.dot(n, L)))
        arr = np.clip(np.asarray(layer, float) * shade, 0, 255).astype(np.uint8)
        layer = Image.fromarray(arr)
        canvas.paste(layer, (bx0, by0), mask)
    return canvas

def sheet(g, W=900, H=520, scale=16):
    views = [render(g, 90, 0, W, H, scale*1.5), render(g, 50, 20, W, H, scale), render(g, 130, 26, W, H, scale), render(g, 0, 90, W, H, scale)]
    out = Image.new('RGB', (W * 2, H * 2))
    for i, v in enumerate(views): out.paste(v, ((i % 2) * W, (i // 2) * H))
    return out

if __name__ == '__main__':
    import export_all as X
    gid = sys.argv[1]
    g = X.ALLG[gid]() if len(sys.argv) < 4 else None
    g.paint()
    sc = float(sys.argv[3]) if len(sys.argv) > 3 else 14
    sheet(g, scale=sc).save(sys.argv[2] if len(sys.argv) > 2 else f'/tmp/tp_{gid}.png')
