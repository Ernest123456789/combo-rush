"""Расчёт display-трансформов оружия «Арсенала» (first/third person, gui, fixed, ground) + превью.
Запуск: python3 display.py [--write] [--preview DIR]
Модель Minecraft: Disp(p) = T/16 + R·(S·p), R = Rx·Ry·Rz (JOML rotationXYZ).
GeckoLib: x вершины = -(origin.x+size.x) — ось X зеркалится относительно geo-файла.
"""
import json, sys, os, glob
import numpy as np
from scipy.optimize import minimize

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "combo-rush")
GEO = ROOT + "/geo/item/weapon/{}.geo.json"
MODELS = ROOT + "/models/item/"
d2r = np.radians

def Rx(a): c, s = np.cos(a), np.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def Ry(a): c, s = np.cos(a), np.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def Rz(a): c, s = np.cos(a), np.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
def Rxyz(r): return Rx(d2r(r[0])) @ Ry(d2r(r[1])) @ Rz(d2r(r[2]))
def euler_xyz(R):
    """R = Rx(a)·Ry(b)·Rz(c) -> градусы"""
    b = np.arcsin(np.clip(R[0, 2], -1, 1))
    a = np.arctan2(-R[1, 2], R[2, 2]); c = np.arctan2(-R[0, 1], R[0, 0])
    return np.degrees([a, b, c])

GUNS = ["ak47", "awp", "glock18", "galil", "deagle", "fiveseven", "p90", "usp", "revolver", "m4a4", "m4a1s", "famas", "mp9", "mp7", "scar20", "nova", "xm1014", "mag7", "sawedoff", "mac10", "taser", "grenade_he", "grenade_flash", "grenade_smoke", "grenade_molotov"]
KNIVES = ["claw", "knife", "bayonet", "skeleton"]
ALL = GUNS + KNIVES
# где рука: (pistol_grip y,z центр) и передняя рука (z, y); для ножей рукоять
HOLD = {}

def cubes(name):
    g = json.load(open(GEO.format(name), encoding="utf-8"))["minecraft:geometry"][0]
    out = []
    for b in g["bones"]:
        if b["name"].startswith("FX_"): continue
        for c in b.get("cubes", []):
            o, s = np.array(c["origin"], float), np.array(c["size"], float)
            # в модель: x -> -(x+sx)
            lo = np.array([-(o[0] + s[0]), o[1], o[2]]); hi = np.array([-o[0], o[1] + s[1], o[2] + s[2]])
            out.append((b["name"], lo / 16, hi / 16))
    return out

def bone_box(cs, bone):
    bs = [(lo, hi) for n, lo, hi in cs if n == bone]
    if not bs: return None
    lo = np.min([b[0] for b in bs], 0); hi = np.max([b[1] for b in bs], 0); return lo, hi

def bbox(cs):
    return np.min([c[1] for c in cs], 0), np.max([c[2] for c in cs], 0)

def anchors(name):
    cs = cubes(name); lo, hi = bbox(cs)
    if name in KNIVES:
        h = bone_box(cs, "handle") or bone_box(cs, "weapon")
        hz = (h[0][2] + min(h[1][2], h[0][2] + 8 / 16)) / 2 if name in ("knife", "bayonet", "skeleton") else (h[0][2] + h[1][2]) / 2
        gy = h[0][1] + 5 / 16 if name == "bayonet" else (h[0][1] + h[1][1]) / 2     # у штыка кольцо под клинком: рука на рукояти
        grip = np.array([(lo[0] + hi[0]) / 2, gy, hz])
        return dict(cs=cs, lo=lo, hi=hi, grip=grip, fore=None, ctr=(lo + hi) / 2)
    if name.startswith("grenade"):
        gb = bone_box(cs, "pistol_grip")
        return dict(cs=cs, lo=lo, hi=hi, grip=(gb[0] + gb[1]) / 2, fore=None, ctr=(lo + hi) / 2)
    g = bone_box(cs, "pistol_grip")
    grip = np.array([(lo[0] + hi[0]) / 2, g[0][1] * 0.35 + g[1][1] * 0.65, (g[0][2] + g[1][2]) / 2])
    fb = bone_box(cs, "foregrip")
    if fb is not None:
        fore = np.array([grip[0], fb[0][1] - 0.5 / 16, (fb[0][2] + fb[1][2]) / 2 + 1 / 16])
    else:
        fr = bone_box(cs, "frame") or bone_box(cs, "slide")
        if fr is not None: fore = np.array([grip[0], fr[0][1], fr[1][2] - 3 / 16])   # рука под передней частью рамки
        else:
            st = bone_box(cs, "stock"); fore = np.array([grip[0], st[0][1] - 0.5 / 16, 21 / 16])   # AWP: под цевьём
    return dict(cs=cs, lo=lo, hi=hi, grip=grip, fore=fore, ctr=(lo + hi) / 2)

# ------------------------------------------------------------------ рендер-цепочки
def disp_fn(rot, tr, sc):
    R = Rxyz(rot)
    return lambda p: np.array(tr) / 16 + R @ (np.array(sc) * (np.asarray(p) + np.array([0, 0.01, 0])))

ARM = {"R": (-np.pi / 2 + 0.1, -0.3, 0.0), "L": (-1.5, 0.6, 0.0)}   # CROSSBOW_HOLD: (x,y,z)
def arm_lin(side):
    ax, ay, az = ARM[side]
    return Rz(az) @ Ry(ay) @ Rx(ax)
FLIP = np.diag([1, -1, -1])
def tp_point(p, disp, side="R"):
    left = side == "L"
    a = disp(p)
    h = a + np.array([(-1 if left else 1) / 16, 0.125, -0.625])
    h = Rx(-np.pi / 2) @ Ry(np.pi) @ h
    h = arm_lin(side) @ h
    piv = np.array([(5 if left else -5) / 16, 2 / 16, 0])
    M = piv + h
    return np.array([M[0], 1.501 - M[1], -M[2]])
def tp_hand(side):
    ax, ay, az = ARM[side]
    piv = np.array([(5 if side == "L" else -5) / 16, 2 / 16, 0])
    M = piv + arm_lin(side) @ np.array([0, 0.66, 0])
    return np.array([M[0], 1.501 - M[1], -M[2]])
FP_BASE = np.array([0.56, -0.52, -0.72])
def fp_point(p, disp): return FP_BASE + disp(p)

# ------------------------------------------------------------------ расчёты
def solve_tp(name):
    A = anchors(name)
    hr, hl = tp_hand("R"), tp_hand("L")
    def build(x):
        s, yaw, pit, roll = float(np.clip(x[0], 0.42, 0.6)), x[1], x[2], x[3]
        # хотим: модельный +z -> вперёд (E +z) с рысканием yaw и тангажом pit; модельный +y -> вверх
        fwd = np.array([-np.sin(d2r(yaw)) * np.cos(d2r(pit)), np.sin(d2r(pit)), np.cos(d2r(yaw)) * np.cos(d2r(pit))])
        up = np.array([0, 1, 0.0]); 
        ex = np.cross(up, fwd); ex /= np.linalg.norm(ex)
        up2 = np.cross(fwd, ex)
        Rr = Rz_axis(fwd, roll)
        Dm = np.stack([Rr @ ex, Rr @ up2, fwd], 1)       # столбцы: образы осей модели x,y,z
        # E = FLIP-связь: E = FLIP·(M-часть) где M = piv + L·(R_d·(S p)+...)
        P = FLIP @ arm_lin("R") @ Rx(-np.pi / 2) @ Ry(np.pi)
        Rd = P.T @ Dm
        return s, Rd
    def cost(x, ret=False):
        s, Rd = build(x)
        # позиция хвата -> руке (подбираем смещение t так, чтобы хват лёг в руку) : считаем t аналитически через tp_point
        disp0 = lambda p: Rd @ (s * (np.asarray(p) + np.array([0, 0.01, 0])))
        # tp_point с нулевым t
        g0 = tp_point_lin(A["grip"], Rd, s, np.zeros(3))
        t = (hr + np.array(x[4:7])) - g0      # E-сдвиг, нужно превратить в item-сдвиг
        # перевод: E-сдвиг dE = FLIP·Linear·dTrans => dTrans = (FLIP·Linear)^-1 dE
        Lm = FLIP @ arm_lin("R") @ Rx(-np.pi / 2) @ Ry(np.pi)
        tr = np.linalg.solve(Lm, t)
        if ret: return s, Rd, tr
        gE = hr + np.array(x[4:7])
        f = tp_point_lin(A["fore"], Rd, s, tr) if A["fore"] is not None else None
        c = 0.0
        if f is not None: c += 5 * np.sum((f - hl) ** 2)
        c += 3 * np.sum(np.array(x[4:7]) ** 2) + 50 * (max(0, 0.42 - x[0]) + max(0, x[0] - 0.6)) ** 2
        c += 0.0004 * (x[1] ** 2) + 0.0004 * x[2] ** 2 + 0.0002 * x[3] ** 2
        return c
    best = None
    for s0 in (0.45, 0.6):
        x0 = np.array([s0, 8, 0, 0, 0, 0, 0.0])
        r = minimize(cost, x0, method="Nelder-Mead", options=dict(maxiter=3000, xatol=1e-4, fatol=1e-8))
        if best is None or r.fun < best.fun: best = r
    x = best.x
    s, Rd, tr = cost(x, True)
    return dict(rot=euler_xyz(Rd).tolist(), tr=(tr * 16).tolist(), sc=[float(s)] * 3, x=x.tolist())

def Rz_axis(axis, ang):
    a = d2r(ang); k = axis / np.linalg.norm(axis)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + np.sin(a) * K + (1 - np.cos(a)) * K @ K
def tp_point_lin(p, Rd, s, tr):
    disp = lambda q: tr + Rd @ (s * (np.asarray(q) + np.array([0, 0.01, 0])))
    return tp_point(p, disp, "R")

def fp_disp(name, pose):
    """pose: dict(yaw,pitch,roll,s,pos=(x,y,z) куда ставим хват в камере)"""
    A = anchors(name)
    # модельный +z -> камера -z (вперёд); +y -> вверх; модельный x -> камера -x (после Ry180)
    R0 = Ry(np.pi)
    # доп. вращения: yaw вокруг вертикали (внутрь = положительный yaw вокруг +y сдвигает ствол влево), pitch, roll вокруг ствола
    R = Ry(d2r(pose["yaw"])) @ Rx(d2r(pose["pitch"])) @ R0 @ Rz(d2r(pose["roll"]))
    s = pose["s"]
    g = np.asarray(A["grip"]) + np.array([0, 0.01, 0])
    target = np.array(pose["pos"])
    tr = (target - FP_BASE) - R @ (s * g)
    return dict(rot=euler_xyz(R).tolist(), tr=(tr * 16).tolist(), sc=[s] * 3)

def gui_disp(name):
    A = anchors(name)
    ln = (A["hi"] - A["lo"])[2] * 16
    s = float(np.clip(14.0 / max(ln, (A["hi"] - A["lo"])[1] * 16), 0.3, 0.9)) if True else 0.5
    if name.startswith("grenade"): s = float(np.clip(13.0 / max((A["hi"] - A["lo"])[1] * 16, 1), 0.5, 1.4))
    R = Rx(d2r(-12)) @ Ry(d2r(28)) if name.startswith("grenade") else Rx(d2r(-22)) @ Ry(d2r(90)) if name not in KNIVES else Rx(d2r(-22)) @ Ry(d2r(90)) @ Rz(d2r(-35))
    c = np.asarray(A["ctr"]) + np.array([0, 0.01, 0])
    tr = -R @ (s * c)
    return dict(rot=euler_xyz(R).tolist(), tr=(tr * 16).tolist(), sc=[s] * 3)

def ground_disp(name, kind):
    A = anchors(name); c = np.asarray(A["ctr"]) + np.array([0, 0.01, 0])
    if name.startswith("grenade"): R, s = Ry(d2r(0)), 0.5 if kind == "ground" else 0.7      # граната стоит вертикально
    elif kind == "ground": R, s = Rx(d2r(90)) @ Ry(d2r(90)), 0.3
    else: R, s = Ry(d2r(90)), 0.42
    tr = -R @ (s * c)
    if kind == "ground" and not name.startswith("grenade"): tr = tr + np.array([0, 0.12, 0])
    return dict(rot=euler_xyz(R).tolist(), tr=(tr * 16).tolist(), sc=[s] * 3)

# позы первого лица: (yaw внутрь, pitch вверх, roll, scale, точка хвата в камере)
FP_POSE = {
    "rifle":  dict(yaw=5.0, pitch=-1.0, roll=0.0, s=0.72, pos=(0.38, -0.60, -0.55)),
    "long":   dict(yaw=5.0, pitch=-1.0, roll=0.0, s=0.62, pos=(0.38, -0.60, -0.55)),
    "smg":    dict(yaw=5.0, pitch=-1.0, roll=0.0, s=0.78, pos=(0.38, -0.58, -0.55)),
    "pistol": dict(yaw=5.0, pitch=2.0, roll=0.0, s=0.85, pos=(0.30, -0.52, -0.74)),
    "grenade": dict(yaw=0.0, pitch=0.0, roll=0.0, s=0.95, pos=(0.40, -0.52, -0.62)),
    "knife":  dict(yaw=10.0, pitch=28.0, roll=-8.0, s=0.90, pos=(0.32, -0.50, -0.55)),
}
KIND = {"ak47": "rifle", "galil": "rifle", "m4a4": "rifle", "m4a1s": "long", "awp": "long", "p90": "smg", "famas": "rifle", "mp9": "smg", "mp7": "smg", "scar20": "long", "mac10": "smg", "nova": "long", "xm1014": "long", "mag7": "rifle", "sawedoff": "rifle", "grenade_he": "grenade", "grenade_flash": "grenade", "grenade_smoke": "grenade", "grenade_molotov": "grenade",
        "glock18": "pistol", "usp": "pistol", "deagle": "pistol", "fiveseven": "pistol", "revolver": "pistol", "taser": "pistol",
        "claw": "knife", "knife": "knife", "bayonet": "knife", "skeleton": "knife"}

def compute_all():
    out = {}
    for n in ALL:
        fp = fp_disp(n, FP_POSE[KIND[n]])
        tp = solve_tp(n)
        out[n] = dict(fp=fp, tp=tp, gui=gui_disp(n), fixed=ground_disp(n, "fixed"), ground=ground_disp(n, "ground"))
    return out

def r(v): return [round(float(x), 3) for x in v]
def entry(e): return {"rotation": r(e["rot"]), "translation": r(e["tr"]), "scale": r(e["sc"])}
def display_json(e):
    fp, tp = entry(e["fp"]), entry(e["tp"])
    return {"thirdperson_righthand": tp, "thirdperson_lefthand": tp, "firstperson_righthand": fp, "firstperson_lefthand": fp,
            "gui": entry(e["gui"]), "fixed": entry(e["fixed"]), "ground": entry(e["ground"])}

if __name__ == "__main__":
    res = compute_all()
    json.dump(res, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "display_out.json"), "w"), indent=1)
    if "--write" in sys.argv:
        only = sys.argv[sys.argv.index('--only') + 1].split(',') if '--only' in sys.argv else None
        for n in ALL:
            if only and n not in only: continue
            files = [MODELS + n + ".json"] + sorted(glob.glob(MODELS + n + "_*.json"))
            for f in files:
                base = os.path.basename(f)[:-5]
                if n == "knife" and base not in ("knife", "knife_t", "knife_ct"): continue
                if n != "knife" and base != n and not base.startswith(n + "_"): continue
                m = json.load(open(f, encoding="utf-8")); m["display"] = display_json(res[n])
                json.dump(m, open(f, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
        print("written")
