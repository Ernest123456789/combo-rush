"""Проверка связности: куб-графы (OBB/SAT). python3 conn.py claw [eps]
eps>0: касание считается связью; печатает компоненты."""
import sys, math, itertools
import numpy as np
sys.path.insert(0, '/home/claude/tpl/tools/weapon_gen')

def corners(c):
    x0, x1 = c.x; y0, y1 = c.y; z0, z1 = c.z
    P = np.array([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], float)
    if c.rot:
        th = -math.radians(c.rot[0]); cs, sn = math.cos(th), math.sin(th)
        Rx = np.array([[1, 0, 0], [0, cs, -sn], [0, sn, cs]]); pv = np.array(c.rpiv, float)
        P = np.array([pv + Rx @ (p - pv) for p in P])
    return P

def axes(P):
    # P[0]=(x0,y0,z0) P[4]=(x1,..) P[2]=(.,y1,.) P[1]=(.,.,z1)
    return [(P[4] - P[0]), (P[2] - P[0]), (P[1] - P[0])]

def overlap(A, B, eps):
    ax = [a / np.linalg.norm(a) for a in axes(A) + axes(B) if np.linalg.norm(a) > 1e-9]
    cand = list(ax)
    for a, b in itertools.product(ax[:3], ax[3:]):
        n = np.cross(a, b)
        if np.linalg.norm(n) > 1e-6: cand.append(n / np.linalg.norm(n))
    for n in cand:
        pa = A @ n; pb = B @ n
        if pa.max() < pb.min() - eps or pb.max() < pa.min() - eps: return False
    return True

def components(g, eps=1e-3):
    P = [corners(c) for c in g.cubes]
    n = len(P); par = list(range(n))
    def f(i):
        while par[i] != i: par[i] = par[par[i]]; i = par[i]
        return i
    for i in range(n):
        for j in range(i + 1, n):
            if overlap(P[i], P[j], eps): par[f(i)] = f(j)
    comp = {}
    for i in range(n): comp.setdefault(f(i), []).append(i)
    return list(comp.values())

if __name__ == '__main__':
    import hd
    eps = float(sys.argv[2]) if len(sys.argv) > 2 else 1e-3
    g = getattr(hd, sys.argv[1])()
    cs = components(g, eps)
    print(sys.argv[1], len(g.cubes), 'cubes,', len(cs), 'component(s)')
    for comp in sorted(cs, key=len)[:-1] if len(cs) > 1 else []:
        print('  isolated:', [(g.cubes[i].bone, g.cubes[i].part, g.cubes[i].x, g.cubes[i].y, g.cubes[i].z) for i in comp])
