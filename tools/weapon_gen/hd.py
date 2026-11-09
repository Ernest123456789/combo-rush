"""Детализированные модели (плотность текстуры 2 px/единицу). Кости, пивоты и FX берутся из старых
построителей (guns*.py), поэтому анимации и позы остаются прежними."""
from wgen import *
import guns as G1, guns2 as G2, guns3 as G3

def newgun(gid, old, tw=256):
    o = old(); n = Gun(gid, tw)
    for name in o.order:
        if name == 'root': continue
        n.bone(name, o.bones[name]['parent'], o.bones[name]['pivot'])
    n.fxs = list(o.fxs)
    return n

def sym(B, bone, part, mat, xr, y, z, pat=None):
    """зеркальная пара боковых деталей: xr=(a,b) c a>0"""
    B(bone, part, mat, (xr[0], xr[1]), y, z, pat)
    B(bone, part, mat, (-xr[1], -xr[0]), y, z, pat)

def rail(B, bone, part, mat, xr, y, z0, z1, h=0.5, pitch=1.0):
    """планка Пикатинни: основание + зубцы"""
    B(bone, part, mat, xr, (y, y + h), (z0, z1), 'seam_v')
    z = z0
    while z + pitch / 2 <= z1 + 1e-6:
        B(bone, part, mat, xr, (y + h, y + 2 * h), (z, z + pitch / 2))
        z += pitch

def rod(B, bone, part, mat, cx, cy, z0, z1, r, pat=None, q=0.5):
    """«круглый» ствол/труба: крест из двух боксов (восьмигранник)"""
    B(bone, part, mat, (cx - r, cx + r), (cy - r + q, cy + r - q), (z0, z1), pat)
    B(bone, part, mat, (cx - r + q, cx + r - q), (cy - r, cy + r), (z0 + .25, z1 - .25), pat)

def ring(B, bone, part, mat, cx, cy, z0, z1, r, pat=None, q=0.5):
    rod(B, bone, part, mat, cx, cy, z0, z1, r, pat, q)

# =====================================================================================================
def ak47():
    g = newgun('ak47', G1.ak47); B = g.box
    # ---- ресивер
    B('receiver', 'receiver', 'blued', (-1.5, 1.5), (-1.5, 1.5), (3, 15), 'scratch')
    B('receiver', 'lower', 'darksteel', (-1.25, 1.25), (-2.5, -1.5), (4, 14), 'seam')
    B('receiver', 'dustcover', 'steel', (-1.25, 1.25), (1.5, 2.5), (3.5, 15), 'scratch')
    B('receiver', 'dustcover', 'steel', (-1, 1), (2.5, 3), (4, 14.5), 'seam_v')
    B('receiver', 'dustcover', 'darksteel', (-1.25, 1.25), (2.5, 3), (3.5, 4), None)       # задний срез крышки
    for z in (6.5, 9.5):
        B('receiver', 'ribs', 'steel', (-1, 1), (3, 3.5), (z, z + .5))
    # окно выброса, предохранитель, заклёпки
    B('receiver', 'port', 'darksteel', (1.5, 1.75), (0.5, 1.5), (7, 12), 'port')
    B('receiver', 'selector', 'steel', (1.5, 1.75), (-1, -0.5), (4, 9), 'engrave')
    B('receiver', 'selector', 'bright', (1.75, 2), (-1, -0.5), (4, 5.5))
    B('receiver', 'plate', 'steel', (-1.75, -1.5), (-1, 1), (4, 14), 'rivets')
    B('receiver', 'plate', 'darksteel', (1.5, 1.75), (-1.5, -1), (4, 14), 'rivets')
    # целик
    B('receiver', 'sights', 'steel', (-1, 1), (3, 3.5), (10, 14), 'scratch')
    B('receiver', 'sights', 'darksteel', (-1, -0.5), (3.5, 4.5), (12, 12.5))
    B('receiver', 'sights', 'darksteel', (0.5, 1), (3.5, 4.5), (12, 12.5))
    B('receiver', 'sights', 'steel', (-0.5, 0.5), (3.5, 4), (10.5, 13.5), 'seam')
    # спусковая скоба
    B('receiver', 'guard', 'blued', (-0.5, 0.5), (-5, -4.5), (7.5, 11.5))
    B('receiver', 'guard', 'blued', (-0.5, 0.5), (-4.5, -2.5), (11, 11.5))
    B('receiver', 'guard', 'blued', (-0.5, 0.5), (-4.5, -2.5), (7.5, 8))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -2.5), (9, 9.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -3.5), (8.5, 9))
    # затвор / рукоять
    B('bolt', 'bolt', 'bright', (1.5, 2.5), (0.5, 1.25), (12, 14.5), 'scratch')
    B('bolt', 'bolt', 'bright', (2.5, 3.5), (0, 1.75), (12.5, 14), 'knurl')
    # ---- магазин (банан)
    B('magazine', 'magazine', 'steel', (-1.25, 1.25), (-4.5, -3), (12, 16), 'rib_h')
    B('magazine', 'magazine', 'steel', (-1.25, 1.25), (-6.5, -4.5), (12.5, 16.5), 'rib_h')
    B('magazine', 'magazine', 'steel', (-1.25, 1.25), (-8.5, -6.5), (13, 17), 'rib_h')
    B('magazine', 'magazine', 'steel', (-1.25, 1.25), (-10.5, -8.5), (13.5, 17.5), 'rib_h')
    B('magazine', 'ribs', 'darksteel', (-0.5, 0.5), (-10, -3), (17, 17.5) if False else (16.5, 17), 'fin') if False else None
    B('magazine', 'base', 'darksteel', (-1.5, 1.5), (-11.5, -10.5), (13, 18.5), 'seam_v')
    B('magazine', 'lip', 'blued', (-1.5, 1.5), (-3, -2.5), (11.5, 16.5), 'seam_v')
    for yy, zz in ((-5.5, 16.5), (-7.5, 17), (-9.5, 17.5)):
        B('magazine', 'ribs', 'darksteel', (-0.5, 0.5), (yy - .5, yy + .5), (zz, zz + .5))
    # ---- деревянный приклад
    B('stock', 'buttplate', 'blued', (-1.25, 1.25), (-5.5, 2.5), (-7, -6.5), 'chk')
    segs = [(-5, 1, -6.5, -4.5), (-4.5, 1, -4.5, -2.5), (-4, 1.5, -2.5, -0.5), (-3.5, 1.5, -0.5, 1.5), (-3, 1.5, 1.5, 3)]
    for yb, yt, z0, z1 in segs:
        B('stock', 'stock', 'wood', (-1.25, 1.25), (yb, yt), (z0, z1), 'grain')
    B('stock', 'cheek', 'woodd', (-1, 1), (1, 1.5), (-6.5, -2.5), 'grain')
    B('stock', 'swivel', 'steel', (-0.25, 0.25), (-5.5, -5), (-6, -5))
    sym(B, 'stock', 'screw', 'steel', (1.25, 1.5), (0.25, 0.75), (-6, -5.5))
    # ---- пистолетная рукоять
    B('pistol_grip', 'grip', 'woodd', (-1, 1), (-4.5, -2.5), (4, 7.5), 'grain')
    B('pistol_grip', 'grip', 'woodd', (-1, 1), (-6.5, -4.5), (3.5, 7), 'grain')
    B('pistol_grip', 'grip', 'woodd', (-1, 1), (-8.5, -6.5), (3, 6.5), 'grain')
    B('pistol_grip', 'cap', 'blued', (-1, 1), (-9.5, -8.5), (3, 6), 'seam')
    B('pistol_grip', 'bulge', 'woodd', (-0.75, 0.75), (-6.5, -5), (7, 7.5), 'grain')
    sym(B, 'pistol_grip', 'screw', 'steel', (1, 1.25), (-5.5, -5), (4.5, 5.5), 'screw')
    # ---- цевьё
    B('foregrip', 'handguard', 'wood', (-1.5, 1.5), (-1.5, 1.5), (15, 22.5), 'grain')
    B('foregrip', 'handguard', 'woodd', (-1.25, 1.25), (1.5, 3.5), (15, 21.5), 'grain')
    for z in (16.5, 18, 19.5):
        B('foregrip', 'grooves', 'darksteel', (-1.5, -1.25) if False else (1.5, 1.75), (-1, 0), (z, z + .5))
        B('foregrip', 'grooves', 'darksteel', (-1.75, -1.5), (-1, 0), (z, z + .5))
    B('foregrip', 'cap', 'steel', (-1.5, 1.5), (-1.5, 1.5), (22.5, 23.5), 'seam')
    B('foregrip', 'clamp', 'steel', (-1.25, 1.25), (1.5, 3.5), (21.5, 22.5), 'seam_v')
    B('foregrip', 'gas', 'steel', (-1, 1), (1.5, 3.5), (22.5, 27), 'scratch')
    B('foregrip', 'gas', 'blued', (-0.5, 0.5), (3.5, 4), (23.5, 26), 'seam')
    # ---- ствол и мушка
    B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (0.5, 1.75), (22, 28.5), 'scratch')
    B('barrel', 'rod', 'bright', (-0.25, 0.25), (0, 0.5), (23.5, 28.5))
    B('barrel', 'sights', 'steel', (-1.25, 1.25), (-0.25, 2), (26, 28), 'scratch')
    B('barrel', 'sights', 'steel', (-1, 1), (2.75, 3.25), (26.5, 27.5)) if False else None
    B('barrel', 'sights', 'darksteel', (-1.25, -0.75), (2, 4.5), (26.5, 27.5))
    B('barrel', 'sights', 'darksteel', (0.75, 1.25), (2, 4.5), (26.5, 27.5))
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (3.5, 5), (26.75, 27.25), 'tri')
    B('barrel', 'sights', 'steel', (-0.75, 0.75), (2, 2.5), (26, 28))
    B('barrel', 'muzzle', 'blued', (-1, 1), (0.25, 2.25), (29, 31), 'slits')
    B('barrel', 'muzzle', 'darksteel', (-0.75, 0.75), (0.5, 2), (31, 32), 'port')
    B('barrel', 'muzzle', 'steel', (-1.25, 1.25), (0, 2.5), (28.5, 29))
    return g

if __name__ == '__main__':
    pass

# =====================================================================================================
#                                           ПИСТОЛЕТЫ
# =====================================================================================================
def lower(g, fm, gm, mm, hw=1.0, zf=16, gpat='stipple', rail_slots=True, magw=0.75, grip_hw=1.0, panel=None):
    """рамка + скоба + рукоять + магазин (общая часть пистолетов)"""
    B = g.box
    B('frame', 'frame', fm, (-hw, hw), (-1, 0), (2, 10), 'scratch')
    B('frame', 'frame', fm, (-hw, hw), (-2, 0), (10, zf), 'slot_h' if rail_slots else 'scratch')
    B('frame', 'rail', fm, (-hw + .25, hw - .25), (-2.5, -2), (11, zf - 1), 'seam_v')
    B('frame', 'guard', fm, (-0.5, 0.5), (-5, -4.5), (6, 11.5))
    B('frame', 'guard', fm, (-0.5, 0.5), (-4.5, -2.5), (11, 11.5))
    B('frame', 'beaver', fm, (-hw, hw), (-1, 0), (1.5, 2), None)
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3, -1), (8, 8.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3, -2.5), (7.5, 8))
    # рукоять с наклоном назад
    for yb, yt, z0, z1 in ((-3, -1, 2, 6), (-5, -3, 1.5, 5.5), (-7, -5, 1, 5), (-9, -7, 0.5, 4.5)):
        B('pistol_grip', 'grip', gm, (-grip_hw, grip_hw), (yb, yt), (z0, z1), gpat)
    if panel:
        B('pistol_grip', 'panel', panel, (grip_hw, grip_hw + .25), (-8, -3.5), (1.5, 4.5), 'chk')
        B('pistol_grip', 'panel', panel, (-grip_hw - .25, -grip_hw), (-8, -3.5), (1.5, 4.5), 'chk')
    B('pistol_grip', 'lanyard', 'steel', (-0.25, 0.25), (-9, -8), (0, 0.5))
    # магазин
    B('magazine', 'magazine', mm, (-magw, magw), (-8.5, -4), (1.75, 4.25), 'rib_h')
    B('magazine', 'base', 'bright', (-grip_hw - .25, grip_hw + .25), (-10, -9), (0.5, 5.5), 'seam_v')

def slide_common(g, sm, hw, sh, z0, z1, serr=(1, 4), port=(8, 12.5), front_cuts=True, top=True):
    B = g.box
    B('slide', 'slide', sm, (-hw, hw), (0, sh), (z0, serr[0] + serr[1] - 1 if False else serr[1]), 'fin')
    B('slide', 'slide', sm, (-hw, hw), (0, sh), (serr[1], z1), 'scratch')
    if top:
        B('slide', 'top', sm, (-hw + .5, hw - .5), (sh, sh + .5), (z0 + .5, z1 - .5), 'seam')
    # окно выброса
    B('slide', 'port', 'darksteel', (hw, hw + .25), (sh - 1.75, sh - .5), port, 'port')
    B('slide', 'port', 'darksteel', (-hw - .25, -hw), (0.5, 1.25), (port[0] + 1, port[0] + 3), 'engrave')
    if front_cuts:
        B('slide', 'cuts', 'darksteel', (hw, hw + .25), (0.5, sh - .5), (z1 - 3, z1 - 2.5))
        B('slide', 'cuts', 'darksteel', (-hw - .25, -hw), (0.5, sh - .5), (z1 - 3, z1 - 2.5))

def sights(g, y, zr, zf, mat='blued', hw=0.75):
    B = g.box
    B('slide', 'sights', mat, (-hw, -hw + .5), (y, y + .75), (zr, zr + 1), 'dot')
    B('slide', 'sights', mat, (hw - .5, hw), (y, y + .75), (zr, zr + 1), 'dot')
    B('slide', 'sights', mat, (-hw + .5, hw - .5), (y, y + .25), (zr, zr + 1))
    B('slide', 'sights', mat, (-0.25, 0.25), (y, y + .75), (zf, zf + .75), 'tri')

def glock18():
    g = newgun('glock18', G1.glock18); B = g.box
    slide_common(g, 'slide', 1.25, 3, 1, 18, serr=(1, 4.5), port=(8, 12.5))
    sights(g, 3.5, 1.5, 16.5, 'blued', 0.75)
    B('barrel', 'barrel', 'bright', (-0.5, 0.5), (1, 2), (18, 19.5))
    B('barrel', 'barrel', 'darksteel', (-0.25, 0.25), (1.25, 1.75), (19.5, 19.75)) if False else None
    B('slide', 'lug', 'steel', (-1.5, -1.25), (-0.5, 0.5), (8, 11), 'knurl') if False else None
    B('frame', 'slidestop', 'steel', (-1.5, -1), (-0.5, 0), (8, 11), 'knurl')
    lower(g, 'poly', 'poly', 'steel', hw=1.0, zf=16, panel='grip')
    B('frame', 'mark', 'bright', (-1.25, -1), (0.25, 0.5), (3, 5), None) if False else None
    return g

def fiveseven():
    g = newgun('fiveseven', G2.fiveseven); B = g.box
    slide_common(g, 'slide', 1.0, 3, 1, 16, serr=(1, 4), port=(8, 13))
    B('slide', 'slide', 'slide', (-0.75, 0.75), (0.5, 3), (16, 19), 'scratch')
    B('slide', 'cuts', 'darksteel', (0.75, 1), (1, 2.5), (16, 16.5)) if False else None
    sights(g, 3.5, 1.5, 17.5, 'blued', 0.75)
    B('barrel', 'barrel', 'bright', (-0.5, 0.5), (1, 2), (19, 20.5))
    B('barrel', 'muzzle', 'darksteel', (-0.25, 0.25), (1.25, 1.75), (20.5, 21)) if False else None
    lower(g, 'navy', 'navy', 'steel', hw=1.0, zf=18, panel='rubber')
    B('frame', 'slidestop', 'bright', (-1.25, -1), (-0.5, 0), (8, 10.5), 'knurl')
    return g

def usp():
    g = newgun('usp', G3.usp); B = g.box
    slide_common(g, 'blued', 1.25, 3, 1, 17, serr=(1, 5), port=(7, 12))
    sights(g, 3.5, 1.5, 15.5, 'darksteel', 0.75)
    B('slide', 'hammer', 'darksteel', (-0.5, 0.5), (1, 3), (0, 1), 'fin')
    B('barrel', 'barrel', 'bright', (-0.5, 0.5), (1, 2), (17, 19.5))
    B('barrel', 'thread', 'darksteel', (-0.25, 0.25), (1.25, 1.75), (19.5, 20.0)) if False else None
    rod(B, 'silencer', 'silencer', 'darksteel', 0, 1.5, 20, 27, 1.25, 'fin')
    rod(B, 'silencer', 'silencer', 'blued', 0, 1.5, 19, 20, 1.5)
    rod(B, 'silencer', 'silencer', 'blued', 0, 1.5, 27, 27.5, 1.0)
    B('silencer', 'silencer', 'bright', (-0.25, 0.25), (1.25, 1.75), (27.5, 27.75)) if False else None
    B('silencer', 'silencer', 'black', (-0.5, 0.5), (1, 2), (27.5, 27.75))
    lower(g, 'steel', 'poly', 'steel', hw=1.0, zf=16, panel='black')
    return g

# ---- Desert Eagle
def deagle():
    g = newgun('deagle', G2.deagle); B = g.box
    B('slide', 'slide', 'chrome', (-1.5, 1.5), (0, 3.5), (1, 5), 'fin')
    B('slide', 'slide', 'chrome', (-1.5, 1.5), (0, 3.5), (5, 18), 'scratch')
    B('slide', 'rib', 'steel', (-0.5, 0.5), (3.5, 4.5), (5, 20), 'seam_v')
    B('slide', 'rib', 'steel', (-0.5, 0.5), (3.5, 4.5), (1, 3.5), None) if False else None
    for z in (6, 8, 10, 12, 14, 16):
        B('slide', 'rib', 'darksteel', (-0.5, 0.5), (4.5, 4.75), (z, z + .5))
    B('slide', 'sights', 'blued', (-0.75, 0.75), (3.5, 4.25), (1, 3), 'dot')
    B('slide', 'sights', 'bright', (-0.25, 0.25), (4.5, 5.25), (19, 19.75), 'tri')
    B('slide', 'port', 'darksteel', (1.5, 1.75), (1, 3), (7, 13), 'port')
    B('slide', 'port', 'darksteel', (-1.75, -1.5), (1, 3), (7, 13), 'port')
    B('slide', 'gasport', 'darksteel', (1.5, 1.75), (0.5, 3), (15, 17), 'vent')
    B('slide', 'gasport', 'darksteel', (-1.75, -1.5), (0.5, 3), (15, 17), 'vent')
    B('barrel', 'shroud', 'steel', (-1.25, 1.25), (0.5, 3), (18, 22), 'scratch')
    B('barrel', 'shroud', 'darksteel', (-1.25, 1.25), (0, 0.5), (18, 22), 'seam_v')
    B('barrel', 'rail', 'darksteel', (-0.5, 0.5), (3, 3.5), (18, 22)) if False else None
    B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1, 2.5), (22, 23), 'port')
    B('frame', 'frame', 'steel', (-1.25, 1.25), (-1.5, 0), (2, 18), 'scratch')
    B('frame', 'frame', 'steel', (-1, 1), (-2.5, -1.5), (10, 19), 'slot_h')
    B('frame', 'frame', 'chrome', (-1.5, -1.25), (-1, 0), (7, 11), 'knurl')
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-5, -4.5), (6.5, 13.5))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-4.5, -2.5), (13, 13.5))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-5, -4.5), (6, 6.5)) if False else None
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -1.5), (8.5, 9.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -3), (8, 8.5))
    for yb, yt, z0, z1 in ((-3.5, -1.5, 2, 7), (-5.5, -3.5, 1.5, 6.5), (-7.5, -5.5, 1, 6), (-10, -7.5, 0.5, 5.5)):
        B('pistol_grip', 'grip', 'rubber', (-1.25, 1.25), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'panel', 'wood', (1.25, 1.5), (-9, -3.5), (1.5, 6), 'grain') if False else None
    B('magazine', 'magazine', 'steel', (-1, 1), (-9.5, -4), (1.75, 5.25), 'rib_h')
    B('magazine', 'base', 'bright', (-1.5, 1.5), (-11, -10), (0.5, 6.5), 'seam_v')
    return g

# =====================================================================================================
#                                           ВИНТОВКИ
# =====================================================================================================
def ak_trigger_group(B, gm):
    B('receiver', 'guard', gm, (-0.5, 0.5), (-5, -4.5), (7.5, 11.5))
    B('receiver', 'guard', gm, (-0.5, 0.5), (-4.5, -2.5), (11, 11.5))
    B('receiver', 'guard', gm, (-0.5, 0.5), (-4.5, -2.5), (7.5, 8))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -2.5), (9, 9.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -3.5), (8.5, 9))

def banana(B, mm, mat2, base='darksteel', w=1.25):
    segs = ((-4.5, -3, 12, 16), (-6.5, -4.5, 12.5, 16.5), (-8.5, -6.5, 13, 17), (-10.5, -8.5, 13.5, 17.5))
    for yb, yt, z0, z1 in segs:
        B('magazine', 'magazine', mm, (-w, w), (yb, yt), (z0, z1), 'rib_h')
    B('magazine', 'base', base, (-w - .25, w + .25), (-11.5, -10.5), (13, 18.5), 'seam_v')
    B('magazine', 'lip', 'blued', (-w - .25, w + .25), (-3, -2.5), (11.5, 16.5), 'seam_v')
    for yy, zz in ((-5.5, 16.5), (-7.5, 17), (-9.5, 17.5)):
        B('magazine', 'ribs', mat2, (-0.5, 0.5), (yy - .5, yy + .5), (zz, zz + .5))

def galil():
    g = newgun('galil', G2.galil); B = g.box
    B('receiver', 'receiver', 'blued', (-1.5, 1.5), (-1.5, 1.5), (3, 15), 'scratch')
    B('receiver', 'lower', 'darksteel', (-1.25, 1.25), (-2.5, -1.5), (4, 14), 'seam')
    B('receiver', 'dustcover', 'steel', (-1.25, 1.25), (1.5, 2.5), (3.5, 15), 'scratch')
    B('receiver', 'dustcover', 'steel', (-1, 1), (2.5, 3), (4, 14.5), 'seam_v')
    B('receiver', 'port', 'darksteel', (1.5, 1.75), (0.5, 1.5), (7, 12), 'port')
    B('receiver', 'selector', 'steel', (1.5, 1.75), (-1, -0.5), (4, 9), 'engrave')
    B('receiver', 'plate', 'steel', (-1.75, -1.5), (-1, 1), (4, 14), 'rivets')
    B('receiver', 'plate', 'darksteel', (1.5, 1.75), (-1.5, -1), (4, 14), 'rivets')
    # диоптрический целик
    B('receiver', 'sights', 'steel', (-1, 1), (3, 3.5), (4.5, 8.5), 'scratch')
    B('receiver', 'sights', 'darksteel', (-1, -0.5), (3.5, 4.75), (5.5, 6.5))
    B('receiver', 'sights', 'darksteel', (0.5, 1), (3.5, 4.75), (5.5, 6.5))
    B('receiver', 'sights', 'darksteel', (-0.5, 0.5), (4.25, 4.75), (5.5, 6.5))
    ak_trigger_group(B, 'poly')
    B('bolt', 'bolt', 'bright', (1.5, 2.5), (0.5, 1.25), (12, 14.5), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.5, 3.5), (0, 1.75), (12.5, 14), 'knurl')
    banana(B, 'poly', 'darksteel', w=1.25)
    # скелетный приклад
    B('stock', 'buttplate', 'rubber', (-1.25, 1.25), (-3.5, 2.5), (-8, -7.5), 'rib_h')
    B('stock', 'bars', 'poly', (-0.5, 0.5), (1, 2), (-7.5, 3), 'weave')
    B('stock', 'bars', 'poly', (-0.5, 0.5), (-3, -2), (-7.5, 3), 'weave')
    B('stock', 'bars', 'poly', (-0.5, 0.5), (-2, 1), (-7.5, -6.5), 'weave')
    B('stock', 'bars', 'poly', (-0.5, 0.5), (-2, 1), (2, 3), 'weave')
    B('stock', 'bars', 'poly', (-0.5, 0.5), (-2, 1), (-3, -2), 'weave')
    B('stock', 'cheek', 'rubber', (-0.75, 0.75), (2, 2.5), (-6.5, -1.5), 'rib_h')
    B('stock', 'hinge', 'steel', (-0.75, 0.75), (-0.5, 0.5), (2, 3)) if False else None
    # рукоять
    for yb, yt, z0, z1 in ((-4.5, -2.5, 4, 7.5), (-6.5, -4.5, 3.5, 7), (-8.5, -6.5, 3, 6.5)):
        B('pistol_grip', 'grip', 'poly', (-1, 1), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'cap', 'darksteel', (-1, 1), (-9.5, -8.5), (3, 6), 'seam')
    # цевьё
    B('foregrip', 'handguard', 'tan', (-1.5, 1.5), (-1.5, 1.5), (15, 24), 'vent')
    B('foregrip', 'handguard', 'tan', (-1.25, 1.25), (1.5, 3), (15, 23), 'rib_v')
    B('foregrip', 'bipod', 'steel', (-1, 1), (-2.5, -1.5), (16, 22), 'scratch')
    B('foregrip', 'bipod', 'darksteel', (-0.25, 0.25), (-3, -2.5), (17, 18))
    B('foregrip', 'cap', 'steel', (-1.5, 1.5), (-1.5, 1.5), (24, 24.5), 'seam')
    B('foregrip', 'gas', 'steel', (-0.75, 0.75), (3, 3.75), (15, 24), 'seam_v')
    B('foregrip', 'gas', 'steel', (-1, 1), (1.5, 3), (24.5, 27), 'scratch')
    # ствол
    B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (0.5, 1.75), (23.5, 30.5), 'scratch')
    B('barrel', 'sights', 'steel', (-1.25, 1.25), (-0.25, 2), (26, 28), 'scratch')
    B('barrel', 'sights', 'darksteel', (-1.25, -0.75), (2, 4.5), (26.5, 27.5))
    B('barrel', 'sights', 'darksteel', (0.75, 1.25), (2, 4.5), (26.5, 27.5))
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (3.5, 5), (26.75, 27.25), 'tri')
    B('barrel', 'sights', 'steel', (-0.75, 0.75), (2, 2.5), (26, 28))
    B('barrel', 'muzzle', 'steel', (-1.25, 1.25), (0, 2.5), (30.5, 31), 'seam')
    B('barrel', 'muzzle', 'blued', (-1, 1), (0.25, 2.25), (31, 33), 'slits')
    return g

def _m4(gid, old, silenced):
    g = newgun(gid, old); B = g.box
    hg = 'tan' if silenced else 'poly'
    # ресивер
    B('receiver', 'receiver', 'blued', (-1.5, 1.5), (0, 3), (3, 15), 'scratch')
    B('receiver', 'lower', 'poly', (-1.5, 1.5), (-2, 0), (3, 15), 'scratch')
    B('receiver', 'seam', 'darksteel', (-1.75, -1.5), (0, 0.5), (3, 15)) if False else None
    B('receiver', 'port', 'steel', (1.5, 1.75), (1, 2.5), (7, 11), 'port')
    B('receiver', 'assist', 'steel', (1.5, 2.25), (1.5, 2.5), (11.5, 12.5), 'knurl')
    B('receiver', 'magrel', 'steel', (1.5, 1.75), (-1, -0.25), (11, 12.5), 'knurl')
    B('receiver', 'pin', 'steel', (-1.75, -1.5), (-1, 0), (4, 5), 'screw')
    B('receiver', 'pin', 'steel', (-1.75, -1.5), (-1, 0), (13, 14), 'screw')
    B('receiver', 'engrave', 'darksteel', (-1.75, -1.5), (1, 2.5), (5, 12), 'engrave')
    rail(B, 'receiver', 'rail', 'steel', (-0.75, 0.75), 3, 4.5, 15, h=0.5, pitch=1.0)
    if not silenced:
        B('receiver', 'sights', 'steel', (-1, 1), (4, 4.5), (5, 7))
        B('receiver', 'sights', 'darksteel', (-1, -0.5), (4.5, 5.5), (5, 7))
        B('receiver', 'sights', 'darksteel', (0.5, 1), (4.5, 5.5), (5, 7))
        B('receiver', 'sights', 'darksteel', (-0.25, 0.25), (4.5, 5.25), (5.5, 6.5), 'dot')
    else:
        B('receiver', 'sights', 'steel', (-0.75, 0.75), (4, 4.75), (5, 7))
        B('receiver', 'sights', 'darksteel', (-0.25, 0.25), (4.75, 5.5), (5.5, 6.5), 'dot')
    B('bolt', 'bolt', 'bright', (-0.75, 0.75), (4, 5), (2, 4.5), 'knurl')
    B('bolt', 'bolt', 'darksteel', (-0.25, 0.25), (3.75, 4), (2.5, 4)) if False else None
    # спуск
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, -4), (6, 10))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4, -2), (9.5, 10))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4, -2), (6, 6.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -2), (8, 8.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -3), (7.5, 8))
    # магазин STANAG
    mm = 'tan' if silenced else 'poly'
    for yb, yt, z0, z1 in ((-4, -2, 10, 14), (-6, -4, 10.25, 14.25), (-8, -6, 10.5, 14.5), (-9.5, -8, 10.75, 14.75)):
        B('magazine', 'magazine', mm, (-1, 1), (yb, yt), (z0, z1), 'rib_h')
    B('magazine', 'base', 'steel', (-1.25, 1.25), (-10.5, -9.5), (10.5, 15.25), 'seam_v')
    B('magazine', 'lip', 'steel', (-1.25, 1.25), (-2.5, -2), (9.75, 14.25), 'seam_v') if False else None
    # приклад
    B('stock', 'stocktube', 'steel', (-0.75, 0.75), (0.5, 2), (-3, 3), 'seam_v')
    B('stock', 'stock', 'poly', (-1.25, 1.25), (-3, 2.5), (-8, -3), 'chk')
    B('stock', 'lever', 'darksteel', (-0.5, 0.5), (-3.5, -3), (-6, -4))
    B('stock', 'buttpad', 'rubber', (-1.25, 1.25), (-3.5, 3), (-9, -8), 'rib_h')
    B('stock', 'cheek', 'poly', (-0.75, 0.75), (2.5, 3), (-7, -4), 'rib_h')
    # рукоять
    for yb, yt, z0, z1 in ((-5, -2, 3, 6), (-7, -5, 2.5, 5.5), (-8, -7, 2, 5)):
        B('pistol_grip', 'grip', 'poly', (-1, 1), (yb, yt), (z0, z1), 'stipple')
    # цевьё
    zf = 23 if silenced else 22
    B('foregrip', 'handguard', hg, (-1.25, 1.25), (-0.5, 3), (15, zf), 'vent' if not silenced else 'rib_h')
    B('foregrip', 'ring', 'steel', (-1.5, 1.5), (-0.75, 3.25), (14.5, 15), 'seam_v') if False else None
    rail(B, 'foregrip', 'hrail', 'steel', (-0.75, 0.75), 3, 15, zf - .5, h=0.5, pitch=1.0)
    B('foregrip', 'rail', 'darksteel', (-0.5, 0.5), (-1, -0.5), (16, zf - 1), 'seam_v')
    if not silenced:
        B('barrel', 'sights', 'steel', (-1, 1), (0.5, 3.5), (22, 24), 'scratch')
        B('barrel', 'sights', 'darksteel', (-0.75, -0.25), (3.5, 5.5), (22.5, 23.5))
        B('barrel', 'sights', 'darksteel', (0.25, 0.75), (3.5, 5.5), (22.5, 23.5))
        B('barrel', 'sights', 'bright', (-0.25, 0.25), (3.5, 5.75), (22.75, 23.25), 'tri')
        B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1, 2.5), (24, 27), 'scratch')
        B('barrel', 'muzzle', 'blued', (-1, 1), (0.75, 2.75), (27, 29.5), 'slits')
        B('barrel', 'muzzle', 'steel', (-1.25, 1.25), (0.5, 3), (26.5, 27)) if False else None
    else:
        B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1, 2.5), (23, 29), 'scratch')
        rod(B, 'silencer', 'silencer', 'darksteel', 0, 1.75, 30, 37, 1.5, 'fin')
        rod(B, 'silencer', 'silencer', 'blued', 0, 1.75, 29, 30, 1.75, 'seam_v')
        rod(B, 'silencer', 'silencer', 'blued', 0, 1.75, 37, 37.5, 1.25)
        B('silencer', 'silencer', 'black', (-0.5, 0.5), (1.25, 2.25), (37.5, 37.75))
    return g
def m4a4(): return _m4('m4a4', G3.m4a4, False)
def m4a1s(): return _m4('m4a1s', G3.m4a1s, True)

# =====================================================================================================
#                                    P90 / AWP / РЕВОЛЬВЕР
# =====================================================================================================
def _fill(B, bone, part, mat, zc, yc, sz_):
    """фаска/скругление отверстия: ромб, повёрнутый на 45 градусов вокруг своего центра (в углу отверстия)"""
    h = sz_ / 2
    B(bone, part, mat, (-1.45, 1.45), (yc - h, yc + h), (zc - h, zc + h), 'scratch', rot=(45, 0, 0), rpiv=(0, yc, zc))

def p90():
    """FN P90 (булл-пап, FDE): сквозное отверстие под большой палец и под палец на спуске, магазин сверху под ручкой-планкой."""
    g = newgun('p90', G2.p90); B = g.box
    X = (-1.5, 1.5)
    # корпус: плитки без перекрытий (сплошной профиль с двумя отверстиями)
    B('stock', 'body', 'tan', X, (-7, 2), (-8, -3.5), 'scratch')                 # затыльник
    B('stock', 'buttpad', 'rubber', X, (-7, 2), (-9, -8), 'rib_h')
    B('receiver', 'body', 'tan', X, (-0.5, 2), (-3.5, 18), 'scratch')            # верхняя перемычка
    B('pistol_grip', 'body', 'tan', X, (-7, -5), (-3.5, 3.5), 'scratch')         # низ под отверстием для большого пальца
    B('pistol_grip', 'grip', 'tan', X, (-7, -0.5), (3.5, 7), 'stipple')         # рукоять между отверстиями
    B('foregrip', 'body', 'tan', X, (-7, -4.5), (7, 11), 'scratch')              # низ под отверстием для пальца
    B('foregrip', 'grip', 'tan', X, (-7, -0.5), (11, 14.5), 'stipple')          # передний «крюк»
    # скругление отверстий
    for zc, yc, s_ in ((-3.5, -0.5, 1.5), (3.5, -0.5, 1.5), (-3.5, -5, 2.5), (3.5, -5, 2.5)):
        _fill(B, 'pistol_grip', 'fill', 'tan', zc, yc, s_)
    for zc, yc, s_ in ((7, -0.5, 1), (11, -0.5, 1), (7, -4.5, 2), (11, -4.5, 2)):
        _fill(B, 'foregrip', 'fill', 'tan', zc, yc, s_)
    # спуск внутри отверстия
    B('trigger', 'trigger', 'darksteel', (-0.25, 0.25), (-3, 0), (8, 8.5))
    B('trigger', 'trigger', 'darksteel', (-0.25, 0.25), (-3, -2.5), (8.5, 9.5))
    # магазин сверху: канал из бортиков, длинный тёмный магазин
    B('magazine', 'magazine', 'smoke', (-1.25, 1.25), (2, 4.5), (-5, 17), 'rounds')
    B('magazine', 'cap', 'steel', (-1.25, 1.25), (2, 4.5), (-5.5, -5), 'seam_v')
    for xr in ((1.25, 1.5), (-1.5, -1.25)):
        B('scope', 'wall', 'tan', xr, (2, 5.5), (8, 9.5), 'scratch')              # задняя стойка ручки
        B('scope', 'wall', 'tan', xr, (2, 5.5), (14, 18), 'scratch')              # передняя стенка ручки
    # ручка для переноски с планкой
    B('scope', 'roof', 'tan', (-1.25, 1.25), (4.5, 5.5), (8, 18), 'scratch')
    rail(B, 'scope', 'rail', 'darksteel', (-1, 1), 5.5, 8.5, 17.5, h=0.5, pitch=1.0)
    B('scope', 'front', 'tan', (-1.25, 1.25), (0, 4.5), (18, 20), 'scratch')
    # ствол и пламегаситель
    B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1.5, 3), (20, 21), 'scratch')
    B('barrel', 'muzzle', 'brass', (-1, 1), (1, 3.5), (21, 23), 'slits')
    B('barrel', 'muzzle', 'black', (-0.5, 0.5), (1.75, 2.75), (23, 23.25))
    # затворная рукоятка сбоку
    B('bolt', 'bolt', 'bright', (1.5, 2.5), (0, 1.5), (12, 14), 'knurl')
    # логотип FN и винты
    sym(B, 'stock', 'logo', 'white', (1.5, 1.75), (-4, -2.5), (-6.5, -5))
    for z_, y_ in ((-6.5, 0.5), (-6.5, -5.5), (5, 0.5), (5, -6), (12.5, -6), (15.5, 0.5)):
        sym(B, 'stock' if z_ < 0 else ('pistol_grip' if z_ < 7 else 'foregrip'), 'screw', 'steel', (1.5, 1.75), (y_, y_ + 0.5), (z_, z_ + 0.5))
    return g

def awp():
    g = newgun('awp', G1.awp); B = g.box
    # ресивер
    B('receiver', 'receiver', 'blued', (-1.25, 1.25), (0.5, 3.5), (2, 15), 'scratch')
    B('receiver', 'receiver', 'darksteel', (-1, 1), (0, 0.5), (3, 14), 'seam')
    B('receiver', 'ridge', 'blued', (-0.75, 0.75), (3.5, 4), (3, 14), 'seam_v')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (1.5, 3), (8, 13), 'port')
    B('receiver', 'engrave', 'steel', (-1.5, -1.25), (1, 3), (6, 13), 'engrave')
    B('receiver', 'screw', 'steel', (-1.5, -1.25), (1, 1.75), (3, 4), 'screw')
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-6.5, -6), (7, 12))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-6, -4), (11.5, 12))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-6, -4), (7, 7.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-5.5, -4), (9, 9.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-5.5, -5), (8.5, 9))
    # затвор
    B('bolt', 'bolt', 'bright', (-0.75, 0.75), (1.5, 3), (-1, 2), 'rib_v')
    B('bolt', 'bolt', 'darksteel', (-0.5, 0.5), (3, 3.5), (-1, 0.5)) if False else None
    B('bolt', 'bolt', 'bright', (1.25, 3.25), (1.5, 2.25), (5.5, 6.5), 'scratch')
    B('bolt', 'bolt', 'rubber', (3.25, 4.5), (0.5, 3), (5, 7), 'knurl')
    # оптика
    rod(B, 'scope', 'scope', 'poly', 0, 6, 7, 19, 1.5, 'scratch')
    rod(B, 'scope', 'scope', 'blued', 0, 6, 19, 22.5, 2.0, 'seam_v')
    B('scope', 'scope', 'blued', (-1.5, 1.5), (4.5, 7.5), (22.5, 23), 'lens')
    rod(B, 'scope', 'scope', 'blued', 0, 6, 5, 7, 1.75)
    B('scope', 'scope', 'blued', (-1.25, 1.25), (4.75, 7.25), (4.5, 5), 'lens')
    for z0 in (9, 15):
        B('scope', 'scopemount', 'steel', (-1, 1), (4, 4.5), (z0, z0 + 2), 'seam')
        rod(B, 'scope', 'scopemount', 'steel', 0, 6, z0, z0 + 2, 1.75)
    B('scope', 'turret', 'bright', (-0.75, 0.75), (7.5, 8.5), (11, 13), 'knurl')
    B('scope', 'turret', 'darksteel', (-0.5, 0.5), (8.5, 9), (11.5, 12.5))
    B('scope', 'turret', 'bright', (1.75, 2.75), (5.5, 6.5), (11, 13), 'knurl')
    B('scope', 'turret', 'bright', (-2.75, -1.75), (5.5, 6.5), (13, 14.5), 'knurl')
    B('scope', 'turret', 'darksteel', (-1.75, -1.5), (5.75, 6.25), (13.25, 14.25)) if False else None
    # магазин
    B('magazine', 'magazine', 'poly', (-1, 1), (-7, -4), (12.25, 15.75), 'stipple')
    B('magazine', 'base', 'bright', (-1.25, 1.25), (-8, -7), (11, 17), 'seam_v')
    # приклад
    B('stock', 'buttpad', 'rubber', (-1.5, 1.5), (-5.5, 4), (-9, -8.5), 'rib_h')
    B('stock', 'stock', 'olive', (-1.5, 1.5), (-5, 3.5), (-8.5, -4), 'scratch')
    B('stock', 'cheek', 'oliveD', (-1.25, 1.25), (3.5, 4.5), (-7, -3), 'rib_h')
    B('stock', 'stock', 'olive', (-1.5, 1.5), (-4, 1), (-4, 16), 'scratch')
    B('stock', 'spacer', 'rubber', (-1.5, 1.5), (-5, 3.5), (-8.5, -8)) if False else None
    B('stock', 'forend', 'oliveD', (-1.5, 1.5), (-3, 1), (16, 27), 'scratch')
    B('stock', 'forend', 'darksteel', (-0.75, 0.75), (-3.5, -3), (17, 26), 'seam_v')
    B('stock', 'swivel', 'steel', (-0.25, 0.25), (-4, -3.25), (4, 5)) if False else None
    # рукоять
    for yb, yt, z0, z1 in ((-6.5, -4, 3.5, 7), (-8.5, -6.5, 3, 6.5), (-10, -8.5, 2.5, 6)):
        B('pistol_grip', 'grip', 'olive', (-1.25, 1.25), (yb, yt), (z0, z1), 'stipple')
    # ствол
    rod(B, 'barrel', 'barrel', 'darksteel', 0, 2, 15, 33, 1.0, 'scratch')
    B('barrel', 'collar', 'steel', (-1.25, 1.25), (0.75, 3.25), (27, 28), 'seam_v')
    B('barrel', 'muzzle', 'blued', (-1.5, 1.5), (0.5, 3.5), (33, 36), 'vent')
    B('barrel', 'muzzle', 'darksteel', (-1.25, 1.25), (0.75, 3.25), (36, 36.25)) if False else None
    return g

def revolver():
    g = newgun('revolver', G3.revolver); B = g.box
    B('frame', 'frame', 'steel', (-1.25, 1.25), (-1, 3.5), (1, 7.5), 'scratch')
    B('frame', 'strap', 'steel', (-1, 1), (3.5, 4.5), (1, 14), 'scratch')
    B('frame', 'lower', 'steel', (-1, 1), (-1, 0), (8, 14), 'seam')
    B('frame', 'plate', 'bright', (1.25, 1.5), (0.5, 3), (2, 7), 'engrave')
    B('frame', 'plate', 'steel', (-1.5, -1.25), (0.5, 3), (2, 7), 'screw')
    B('frame', 'sights', 'darksteel', (-0.75, -0.25), (4.5, 5.5), (1, 2), 'dot')
    B('frame', 'sights', 'darksteel', (0.25, 0.75), (4.5, 5.5), (1, 2), 'dot')
    B('frame', 'sights', 'darksteel', (-0.25, 0.25), (4.5, 5), (1, 2))
    B('hammer', 'hammer', 'darksteel', (-0.5, 0.5), (2, 4.5), (0, 1), 'fin')
    B('hammer', 'hammer', 'darksteel', (-0.5, 0.5), (3.5, 4.25), (-0.5, 0)) if False else None
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-5, -4.5), (5, 10))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-4.5, -1), (5.5, 6))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-4.5, -1), (9.5, 10))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -1), (6, 6.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -3), (6.5, 7))
    # барабан
    rod(B, 'cylinder', 'cylinder', 'bright', 0, 1.75, 8, 13, 1.5, 'fin')
    B('cylinder', 'ratchet', 'darksteel', (-1, 1), (0.75, 2.75), (7.5, 8), 'seam_v')
    for hx, hy in ((0, 1), (1, 0.5), (-1, 0.5), (1, -0.5), (-1, -0.5), (0, -1)):
        B('cylinder', 'holes', 'black', (hx - .25, hx + .25), (1.75 + hy - .25, 1.75 + hy + .25), (13, 13.25))
    B('cylinder', 'pin', 'steel', (-0.25, 0.25), (1.5, 2), (13.25, 13.5)) if False else None
    # ствол
    rod(B, 'barrel', 'barrel', 'blued', 0, 2, 14, 21.5, 1.0, 'scratch')
    B('barrel', 'rib', 'blued', (-0.5, 0.5), (3, 3.5), (14, 21), 'seam_v')
    B('barrel', 'lug', 'steel', (-1, 1), (0, 1), (14, 20), 'seam')
    B('barrel', 'rod', 'bright', (-0.25, 0.25), (-0.5, 0), (14.5, 20)) if False else None
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (3.5, 4.5), (20, 21), 'redtip')
    B('barrel', 'crown', 'darksteel', (-1.25, 1.25), (0.75, 3.25), (21.5, 22), 'seam_v')
    B('barrel', 'crown', 'black', (-0.5, 0.5), (1.5, 2.5), (22, 22.25))
    # рукоять
    for yb, yt, z0, z1 in ((-3, -1, 1.5, 5.5), (-5, -3, 1, 5), (-7, -5, 0.5, 4.5), (-9, -7, 0, 4.5)):
        B('pistol_grip', 'grip', 'wood', (-1.25, 1.25), (yb, yt), (z0, z1), 'grain')
    B('pistol_grip', 'butt', 'steel', (-1.25, 1.25), (-9.5, -9), (-0.5, 4.5), 'seam')
    B('pistol_grip', 'medal', 'gold', (1.25, 1.5), (-4.5, -3.5), (2, 3))
    B('pistol_grip', 'medal', 'gold', (-1.5, -1.25), (-4.5, -3.5), (2, 3))
    return g

# =====================================================================================================
#                                       НОЖИ
# =====================================================================================================
def claw():
    """Керамбит: серповидное лезвие по дуге (сегменты с перекрытием), рукоять, гарда и кольцо - всё сплошное."""
    g = newgun('claw', G3.claw); B = g.box
    # рукоять
    B('handle', 'handle', 'poly', (-0.75, 0.75), (-1, 2), (3, 10), 'rib_h')
    B('handle', 'scales', 'grip', (0.75, 1), (-0.75, 1.75), (3.5, 9.5), 'chk')
    B('handle', 'scales', 'grip', (-1, -0.75), (-0.75, 1.75), (3.5, 9.5), 'chk')
    B('handle', 'spine', 'steel', (-0.6, 0.6), (-1.25, -1), (3, 10), 'seam_v')
    B('handle', 'top', 'steel', (-0.5, 0.5), (2, 2.25), (3.5, 9.5), 'rib_v')
    B('handle', 'guard', 'bright', (-1, 1), (-1.5, 2.5), (9.5, 10.5), 'scratch')
    sym(B, 'handle', 'rivet', 'bright', (1, 1.25), (0.25, 0.75), (5.5, 6))
    sym(B, 'handle', 'rivet', 'bright', (1, 1.25), (0.25, 0.75), (8, 8.5))
    # кольцо: рамка, верхняя планка уходит внутрь рукояти, горизонтали чуть уже - перекрываются со стойками
    B('ring', 'ring', 'bright', (-0.5, 0.5), (-4.5, 0.5), (0, 1), 'edge')      # задняя стойка
    B('ring', 'ring', 'bright', (-0.5, 0.5), (-4.5, 0.5), (3.5, 4.5), 'edge')   # передняя стойка (верх внутри рукояти)
    B('ring', 'ring', 'bright', (-0.45, 0.45), (0, 1), (0.5, 4), 'edge')       # верхняя планка
    B('ring', 'ring', 'bright', (-0.45, 0.45), (-5, -4), (0.5, 4), 'edge')     # нижняя планка
    # серповидное лезвие: спинка - дуга окружности радиуса Rs, сегменты висят внутрь (режущая кромка вогнутая)
    Rs, n, A = 7.5, 12, 104.0
    d = A / n; ch = 2 * Rs * math.sin(math.radians(d / 2))
    sz0, sy0 = 10.25, 2.0
    zc, yc = sz0, sy0 - Rs
    for i in range(n):
        a0 = math.radians(i * d)
        sz, sy = zc + Rs * math.sin(a0), yc + Rs * math.cos(a0)
        h = i * d + d / 2
        f = i / (n - 1)
        T = max(0.5, 4.4 - 3.9 * f ** 1.15)
        hw = 0.52 - 0.012 * i                       # строго убывает: стенки соседей не z-файтятся
        rot = (-h, 0, 0); rp = (0, sy, sz)
        L = ch + (0.15 if i < n - 1 else 0)
        Tb = max(0.5, T * 0.45)                     # фаска у кромки
        B('blade', 'blade', 'bright', (-hw, hw), (sy - T + Tb, sy), (sz, sz + L), 'edge', rot=rot, rpiv=rp)
        B('blade', 'blade', 'bright', (-hw * 0.5, hw * 0.5), (sy - T, sy - T + Tb + 0.05), (sz, sz + L), 'edge', rot=rot, rpiv=rp)
        if T > 1.6:
            B('blade', 'fuller', 'steel', (-hw - 0.08, hw + 0.08), (sy - T * 0.55, sy - T * 0.4), (sz + .1, sz + L - .1), 'seam', rot=rot, rpiv=rp)
    return g

def knife():
    g = newgun('knife', G3.knife); B = g.box
    B('weapon', 'handle', 'poly', (-0.75, 0.75), (-0.5, 2.5), (3, 9.5), 'rib_h')
    B('weapon', 'scales', 'grip', (0.75, 1), (-0.25, 2.25), (3.5, 9), 'chk')
    B('weapon', 'scales', 'grip', (-1, -0.75), (-0.25, 2.25), (3.5, 9), 'chk')
    B('weapon', 'rivet', 'bright', (1, 1.25), (0.5, 1.5), (4.5, 5), None)
    B('weapon', 'rivet', 'bright', (1, 1.25), (0.5, 1.5), (7.5, 8), None)
    B('weapon', 'rivet', 'bright', (-1.25, -1), (0.5, 1.5), (4.5, 5), None)
    B('weapon', 'rivet', 'bright', (-1.25, -1), (0.5, 1.5), (7.5, 8), None)
    B('weapon', 'pommel', 'steel', (-1.25, 1.25), (-0.75, 2.75), (2, 3), 'rib_v')
    B('weapon', 'pommel', 'bright', (-0.5, 0.5), (0.25, 1.75), (1.5, 2), 'seam')
    B('weapon', 'guard', 'bright', (-1.25, 1.25), (-1, 3.5), (9.5, 10.5), 'scratch')
    B('weapon', 'blade', 'bright', (-0.4, 0.4), (0.0, 2.4), (10.5, 17), 'edge')
    B('weapon', 'fuller', 'steel', (-0.5, 0.5), (1.2, 1.8), (11, 16.5), 'seam')
    B('weapon', 'blade', 'bright', (-0.35, 0.35), (0.5, 2.8), (17, 18.5), 'edge')
    B('weapon', 'blade', 'bright', (-0.3, 0.3), (1.0, 2.8), (18.5, 19.5), 'edge')
    B('weapon', 'blade', 'bright', (-0.25, 0.25), (1.8, 2.8), (19.5, 20), 'edge') if False else None
    B('weapon', 'spine', 'steel', (-0.45, 0.45), (2.4, 2.8), (10.6, 16.9), 'seam_v')
    for z in (11, 12, 13, 14, 15):
        B('weapon', 'serr', 'darksteel', (-0.2, 0.2), (2.8, 3.1), (z, z + .5))
    return g


def bayonet():
    """Штык-нож (M9): прямое лезвие с долом и пилой по обуху, массивная гарда, кольцо под ствол."""
    g = newgun('bayonet', G3.knife); B = g.box
    B('weapon', 'handle', 'poly', (-0.75, 0.75), (0.5, 3.5), (2.5, 9.5), 'rib_h')
    B('weapon', 'scales', 'grip', (0.75, 1), (0.75, 3.25), (3.5, 9), 'chk')
    B('weapon', 'scales', 'grip', (-1, -0.75), (0.75, 3.25), (3.5, 9), 'chk')
    for z in (4.5, 7.5):
        sym(B, 'weapon', 'rivet', 'bright', (1, 1.25), (1.5, 2.5), (z, z + 0.5))
    # навершие с отверстием под темляк
    B('weapon', 'pommel', 'steel', (-1.25, 1.25), (0, 4), (2, 3), 'rib_v')
    B('weapon', 'pommel', 'bright', (-0.5, 0.5), (1.5, 2.5), (1.5, 2), 'seam')
    # гарда
    B('weapon', 'guard', 'bright', (-1.25, 1.25), (-0.5, 4.5), (9.5, 10.5), 'scratch')
    # лезвие: прямой обух, клиповая точка
    B('weapon', 'blade', 'bright', (-0.5, 0.5), (1, 4.25), (10, 17.5), 'edge')
    B('weapon', 'blade', 'bright', (-0.45, 0.45), (1.5, 4), (17, 19.5), 'edge')
    B('weapon', 'blade', 'bright', (-0.4, 0.4), (2.5, 3.75), (19, 20.5), 'edge')
    B('weapon', 'blade', 'bright', (-0.3, 0.3), (3, 3.5), (20, 21.5), 'edge')
    B('weapon', 'fuller', 'steel', (-0.6, 0.6), (2.5, 3.25), (11, 18), 'seam')
    # пила по обуху
    for z in (11.5, 12.5, 13.5, 14.5, 15.5):
        B('weapon', 'serr', 'darksteel', (-0.3, 0.3), (4, 4.75), (z, z + 0.5))
    # кольцо под ствол: рамка под клинком (боковины ниже клинка, перемычка уже боковин)
    B('weapon', 'ring', 'bright', (-0.4, 0.4), (-2.5, 1.5), (10.5, 11.5), 'edge')
    B('weapon', 'ring', 'bright', (-0.4, 0.4), (-2.5, 1.5), (13.5, 14.5), 'edge')
    B('weapon', 'ring', 'bright', (-0.35, 0.35), (-3, -2), (11, 14), 'edge')
    return g

# =====================================================================================================
#                                  Famas / MP9 / MP7
# =====================================================================================================
def std_bones(gid, bolt=(0, 1, 13), trig=(0, -2, 9.5), eject=(1.75, 1.2, 9), mag=(0, -2, 13), grip=(0, -2, 5), fore=(0, 0, 19),
              barrel=(0, 1, 26), muz=(0, 1.25, 30)):
    g = Gun(gid, 256); g.bone('weapon', 'root', (0, -1, 6)); g.bone('receiver', 'weapon', (0, 0, 9))
    g.bone('bolt', 'receiver', bolt); g.bone('trigger', 'receiver', trig); g.bone('FX_ejected_case_0', 'receiver', eject)
    g.bone('magazine', 'weapon', mag); g.bone('stock', 'weapon', (0, -1, 0)); g.bone('pistol_grip', 'weapon', grip)
    g.bone('foregrip', 'weapon', fore); g.bone('barrel', 'weapon', barrel); g.bone('FX_muzzle_flash', 'barrel', muz)
    g.fx('FX_muzzle_flash', muz); g.fx('FX_ejected_case_0', eject)
    return g

def famas():
    g = std_bones('famas', bolt=(0, 1, 13), trig=(0, -2, 10), eject=(1.75, 1.2, -1), mag=(0, -2, 2.5), grip=(0, -2, 7), fore=(0, 0, 18),
                  barrel=(0, 1, 25), muz=(0, 1.25, 28.7))
    B = g.box
    # корпус (буллпап)
    B('receiver', 'body', 'olive', (-1.5, 1.5), (-1.5, 2), (-7, 15), 'scratch')
    B('receiver', 'port', 'darksteel', (1.5, 1.75), (0, 1.5), (-3, 1), 'port')
    B('receiver', 'plate', 'steel', (-1.75, -1.5), (-1, 1), (-6, -1), 'engrave')
    B('receiver', 'plate', 'darksteel', (-1.75, -1.5), (-0.5, 1.5), (6, 12), 'vent')
    B('receiver', 'screw', 'steel', (1.5, 1.75), (-1, -0.25), (5, 6), 'screw')
    B('receiver', 'screw', 'steel', (-1.75, -1.5), (-1, -0.25), (13, 14), 'screw')
    # ручка-мостик с прицелом
    B('receiver', 'handle', 'olive', (-1.25, 1.25), (2, 3), (-4, 12), 'seam_v')
    B('receiver', 'handle', 'olive', (-1, 1), (3, 4.5), (-4, -2), 'scratch')
    B('receiver', 'handle', 'olive', (-1, 1), (3, 4.5), (10, 12), 'scratch')
    B('receiver', 'handle', 'oliveD', (-1, 1), (4.5, 5.5), (-4, 12), 'seam')
    B('receiver', 'sights', 'darksteel', (-1, -0.5), (5.5, 6.5), (-3.5, -2.5), 'dot')
    B('receiver', 'sights', 'darksteel', (0.5, 1), (5.5, 6.5), (-3.5, -2.5), 'dot')
    B('receiver', 'sights', 'darksteel', (-0.75, 0.75), (5.5, 6), (10.5, 11.5), 'dot')
    B('receiver', 'sights', 'bright', (-0.25, 0.25), (6, 6.75), (10.75, 11.25), 'tri')
    # затвор / рукоять взведения
    B('bolt', 'bolt', 'bright', (1.5, 2.5), (0.5, 1.25), (12, 14.5), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.5, 3.5), (0, 1.75), (12.5, 14), 'knurl')
    # спуск + скоба
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-5, -4.5), (9, 13))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, -1.5), (12.5, 13))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -1.5), (10, 10.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -3.5), (9.5, 10))
    # магазин (за рукоятью)
    for yb, yt, z0, z1 in ((-4, -1.5, 0.5, 4.5), (-6, -4, 0.75, 4.75), (-8, -6, 1, 5)):
        B('magazine', 'magazine', 'poly', (-1, 1), (yb, yt), (z0, z1), 'rib_h')
    B('magazine', 'base', 'steel', (-1.25, 1.25), (-9, -8), (0.75, 5.5), 'seam_v')
    # рукоять
    for yb, yt, z0, z1 in ((-4.5, -1.5, 6.5, 9.5), (-6.5, -4.5, 6, 9), (-8.5, -6.5, 5.5, 8.5)):
        B('pistol_grip', 'grip', 'olive', (-1, 1), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'cap', 'darksteel', (-1, 1), (-9.5, -8.5), (5.5, 8.5), 'seam')
    # приклад (щека + затылок)
    B('stock', 'buttpad', 'rubber', (-1.25, 1.25), (-4, 3), (-8, -7), 'rib_h')
    B('stock', 'cheek', 'oliveD', (-1, 1), (2, 2.5), (-7, -4.5), 'rib_h')
    B('stock', 'hook', 'rubber', (-1.25, 1.25), (-4.5, -4), (-8, -7)) if False else None
    # цевьё, сошки, мушка
    B('foregrip', 'handguard', 'olive', (-1.25, 1.25), (-1, 1.5), (15, 22), 'vent')
    B('foregrip', 'handguard', 'oliveD', (-1, 1), (1.5, 2.5), (15, 21), 'seam_v')
    B('foregrip', 'bipod', 'steel', (-1, 1), (-1.5, -1), (16, 21), 'seam')
    sym(B, 'foregrip', 'bipod', 'darksteel', (0.75, 1), (-3, -1.5), (17, 17.5))
    B('foregrip', 'ring', 'steel', (-1.5, 1.5), (-1.5, 2), (22, 22.5), 'seam_v')
    B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (0.5, 1.75), (21.5, 26.5), 'scratch')
    B('barrel', 'sights', 'steel', (-1, 1), (1.75, 3), (22.5, 24), 'scratch')
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (3, 4.25), (22.75, 23.25), 'tri')
    B('barrel', 'muzzle', 'blued', (-1, 1), (0.25, 2.25), (27, 29), 'slits')
    B('barrel', 'muzzle', 'steel', (-1.25, 1.25), (0, 2.5), (26.5, 27), 'seam_v')
    return g

def mp9():
    g = std_bones('mp9', bolt=(0, 1, 8), trig=(0, -2, 7.5), eject=(1.5, 2, 8), mag=(0, -2, 4), grip=(0, -2, 4.5), fore=(0, 0, 11),
                  barrel=(0, 1, 13), muz=(0, 1.75, 18.3))
    B = g.box
    B('receiver', 'body', 'poly', (-1.25, 1.25), (-1, 3), (-1, 13), 'scratch')
    B('receiver', 'body', 'poly', (-1, 1), (3, 3.5), (0, 12), 'seam')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (1, 2.75), (6, 10), 'port')
    B('receiver', 'vents', 'darksteel', (1.25, 1.5), (0.25, 1), (1, 5), 'slot_h') if False else None
    B('receiver', 'plate', 'darksteel', (-1.5, -1.25), (0.5, 2.5), (2, 6), 'vent')
    B('receiver', 'screw', 'steel', (-1.5, -1.25), (0, 0.75), (9, 10), 'screw')
    rail(B, 'receiver', 'rail', 'steel', (-0.75, 0.75), 3.5, 0, 12, h=0.5, pitch=1.0)
    B('receiver', 'sights', 'darksteel', (-0.75, -0.25), (4.5, 5.25), (0, 1), 'dot')
    B('receiver', 'sights', 'darksteel', (0.25, 0.75), (4.5, 5.25), (0, 1), 'dot')
    B('receiver', 'sights', 'bright', (-0.25, 0.25), (4.5, 5.5), (12, 12.5), 'tri')
    B('bolt', 'bolt', 'bright', (1.5, 2.25), (1.5, 2.25), (7, 9.5), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.25, 3), (1, 2.75), (7.5, 9), 'knurl')
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-5, -4.5), (6, 11))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, -1), (10.5, 11))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -1), (7.5, 8))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -3), (7, 7.5))
    for yb, yt, z0, z1 in ((-3, -1, 2.5, 6), (-5, -3, 2, 5.5), (-7, -5, 1.5, 5), (-9, -7, 1, 4.5)):
        B('pistol_grip', 'grip', 'poly', (-1, 1), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'panel', 'rubber', (1, 1.25), (-8, -3.5), (1.5, 4.5), 'chk')
    B('pistol_grip', 'panel', 'rubber', (-1.25, -1), (-8, -3.5), (1.5, 4.5), 'chk')
    B('magazine', 'magazine', 'steel', (-0.75, 0.75), (-8.5, -4), (2.25, 4.25), 'rib_h')
    B('magazine', 'base', 'bright', (-1.25, 1.25), (-10, -9), (0.5, 5), 'seam_v')
    # складной приклад
    B('stock', 'strut', 'steel', (-0.5, 0.5), (0.5, 1.5), (-6.5, -1), 'seam_v')
    B('stock', 'strut', 'steel', (-0.5, 0.5), (-1.5, -1), (-6, -1)) if False else None
    B('stock', 'buttpad', 'rubber', (-1.25, 1.25), (-2, 2.5), (-7, -6.5), 'rib_h')
    B('stock', 'brace', 'darksteel', (-0.5, 0.5), (-2, 0.5), (-6.5, -6)) if False else None
    # передняя рукоять, кожух ствола
    B('foregrip', 'grip', 'poly', (-1, 1), (-4.5, -1), (11.5, 14), 'stipple')
    B('foregrip', 'grip', 'rubber', (-1, 1), (-5, -4.5), (11.5, 14), 'rib_h')
    B('foregrip', 'shroud', 'poly', (-1.25, 1.25), (-1, 3), (13, 15), 'vent')
    B('barrel', 'shroud', 'steel', (-1, 1), (0.75, 2.75), (15, 17), 'seam_v')
    B('barrel', 'barrel', 'darksteel', (-0.5, 0.5), (1.25, 2.25), (17, 18), 'scratch')
    B('barrel', 'muzzle', 'black', (-0.25, 0.25), (1.5, 2), (18, 18.25))
    return g

def mp7():
    g = std_bones('mp7', bolt=(0, 1, 9), trig=(0, -2, 8), eject=(1.5, 2, 9), mag=(0, -2, 4), grip=(0, -2, 5), fore=(0, 0, 13),
                  barrel=(0, 1, 16), muz=(0, 1.75, 21.3))
    B = g.box
    B('receiver', 'body', 'tan', (-1.25, 1.25), (-1, 3), (-1, 15), 'scratch')
    B('receiver', 'upper', 'poly', (-1, 1), (3, 3.5), (0, 14), 'seam')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (1, 2.75), (7, 11), 'port')
    B('receiver', 'plate', 'poly', (-1.5, -1.25), (0, 2.5), (1, 6), 'vent')
    B('receiver', 'plate', 'poly', (-1.5, -1.25), (0, 2.5), (8, 12), 'engrave')
    B('receiver', 'screw', 'steel', (1.25, 1.5), (-0.75, 0), (3, 4), 'screw')
    rail(B, 'receiver', 'rail', 'steel', (-0.75, 0.75), 3.5, 1, 14, h=0.5, pitch=1.0)
    B('receiver', 'sights', 'darksteel', (-0.75, -0.25), (4.5, 5.5), (3.5, 4.5), 'dot')
    B('receiver', 'sights', 'darksteel', (0.25, 0.75), (4.5, 5.5), (3.5, 4.5), 'dot')
    B('receiver', 'sights', 'bright', (-0.25, 0.25), (4.5, 5.5), (13.5, 14), 'tri')
    B('bolt', 'bolt', 'bright', (-0.5, 0.5), (4.5, 5), (0, 3), 'knurl')
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-5, -4.5), (7, 12))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, -1), (11.5, 12))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -1), (8, 8.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3.5, -3), (7.5, 8))
    for yb, yt, z0, z1 in ((-3, -1, 3.5, 7), (-5, -3, 3, 6.5), (-7, -5, 2.5, 6), (-9, -7, 2, 5.5)):
        B('pistol_grip', 'grip', 'poly', (-1, 1), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'panel', 'tan', (1, 1.25), (-8, -3.5), (2.5, 5.5), 'chk')
    B('pistol_grip', 'panel', 'tan', (-1.25, -1), (-8, -3.5), (2.5, 5.5), 'chk')
    B('magazine', 'magazine', 'smoke', (-0.75, 0.75), (-8.5, -4), (3.25, 5.25), 'rib_h')
    B('magazine', 'base', 'bright', (-1.25, 1.25), (-10, -9), (1.5, 6), 'seam_v')
    # телескопический приклад
    B('stock', 'tube', 'steel', (-0.5, 0.5), (1, 2), (-7, -1), 'seam_v')
    B('stock', 'tube', 'darksteel', (-0.75, 0.75), (-0.5, 1), (-3, -1), 'fin')
    B('stock', 'buttpad', 'rubber', (-1.25, 1.25), (-3, 3), (-8, -7), 'rib_h')
    B('stock', 'hook', 'poly', (-0.75, 0.75), (-3.5, -3), (-8, -7)) if False else None
    # цевьё и складная рукоять
    B('foregrip', 'handguard', 'tan', (-1.25, 1.25), (-1, 3), (15, 18), 'vent')
    B('foregrip', 'grip', 'poly', (-0.75, 0.75), (-4.5, -1), (12.5, 15), 'stipple')
    B('foregrip', 'grip', 'poly', (-0.75, 0.75), (-5, -4.5), (12.5, 15), 'rib_h')
    B('foregrip', 'rail', 'steel', (-0.5, 0.5), (-0.5, 0), (13.5, 15), 'seam_v') if False else None
    B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1, 2.5), (18, 20), 'scratch')
    B('barrel', 'muzzle', 'blued', (-1, 1), (0.75, 2.75), (20, 21.5), 'slits')
    B('barrel', 'sights', 'darksteel', (-0.5, 0.5), (3, 3.5), (18, 19), None) if False else None
    return g

def scar20():
    g = std_bones('scar20', bolt=(0, 2, 8), trig=(0, -2, 9.5), eject=(1.75, 2.5, 9), mag=(0, -2, 13), grip=(0, -2, 4.5), fore=(0, 0, 20),
                  barrel=(0, 2, 32), muz=(0, 2.25, 43.7))
    g.bone('scope', 'weapon', (0, 6, 12))
    B = g.box
    # ресивер
    B('receiver', 'body', 'poly', (-1.5, 1.5), (0, 3.5), (0, 16), 'scratch')
    B('receiver', 'upper', 'darksteel', (-1.25, 1.25), (3.5, 4), (0, 16), 'seam')
    B('receiver', 'port', 'darksteel', (1.5, 1.75), (1.5, 3), (7, 12), 'port')
    B('receiver', 'plate', 'tan', (-1.75, -1.5), (0.5, 3), (1, 6), 'engrave')
    B('receiver', 'plate', 'tan', (-1.75, -1.5), (0.5, 3), (8, 14), 'vent')
    B('receiver', 'screw', 'steel', (1.5, 1.75), (0.25, 1), (2, 3), 'screw')
    rail(B, 'receiver', 'rail', 'steel', (-0.75, 0.75), 4, 0, 16, h=0.5, pitch=1.0)
    # затвор / рукоять взведения
    B('bolt', 'bolt', 'bright', (1.75, 2.75), (1.5, 2.25), (9, 12), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.75, 3.75), (1, 2.75), (9.5, 11.5), 'knurl')
    # скоба и спуск
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-5, -4.5), (7.5, 13))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, 0), (12.5, 13))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -0.5), (9.5, 10))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -3.5), (9, 9.5))
    # оптика
    rod(B, 'scope', 'scope', 'blued', 0, 6.5, 6, 20, 1.5, 'scratch')
    rod(B, 'scope', 'scope', 'poly', 0, 6.5, 20, 23, 2.0, 'seam_v')
    B('scope', 'scope', 'blued', (-1.5, 1.5), (5, 8), (23, 23.5), 'lens')
    rod(B, 'scope', 'scope', 'poly', 0, 6.5, 4, 6, 1.75)
    B('scope', 'scope', 'blued', (-1.25, 1.25), (5.25, 7.75), (3.5, 4), 'lens')
    for z0 in (8, 15):
        rod(B, 'scope', 'scopemount', 'steel', 0, 6.5, z0, z0 + 2, 1.75)
    B('scope', 'turret', 'bright', (-0.75, 0.75), (8, 9), (11, 13), 'knurl')
    B('scope', 'turret', 'darksteel', (-0.5, 0.5), (9, 9.5), (11.5, 12.5))
    B('scope', 'turret', 'bright', (1.75, 2.75), (6, 7), (11, 13), 'knurl')
    # магазин
    for yb, yt in ((-4, 0), (-6, -4)):
        B('magazine', 'magazine', 'poly', (-1, 1), (yb, yt), (13.5, 17.5), 'rib_h')
    B('magazine', 'magazine', 'poly', (-1, 1), (-8, -6), (13.75, 17.75), 'rib_h')
    B('magazine', 'base', 'steel', (-1.25, 1.25), (-9, -8), (13.5, 18), 'seam_v')
    # рукоять
    for yb, yt, z0, z1 in ((-4.5, 0, 2.5, 6), (-6.5, -4.5, 2, 5.5), (-8.5, -6.5, 1.5, 5)):
        B('pistol_grip', 'grip', 'poly', (-1.25, 1.25), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'panel', 'tan', (1.25, 1.5), (-8, -2), (2, 4.5), 'chk')
    B('pistol_grip', 'panel', 'tan', (-1.5, -1.25), (-8, -2), (2, 4.5), 'chk')
    # приклад с отверстием под большой палец
    B('stock', 'buttpad', 'rubber', (-1.5, 1.5), (-6, 4.5), (-12, -11), 'rib_h')
    B('stock', 'stock', 'tan', (-1.25, 1.25), (0.5, 3.5), (-11, 0), 'scratch')
    B('stock', 'cheek', 'poly', (-1, 1), (3.5, 4.5), (-10, -3), 'rib_h')
    B('stock', 'stock', 'tan', (-1.25, 1.25), (-6, -3.5), (-11, 2), 'scratch')
    B('stock', 'stock', 'tan', (-1.25, 1.25), (-3.5, 0.5), (-11, -9.5), 'seam_v')
    B('stock', 'stock', 'poly', (-1.5, 1.5), (-7, -6.5), (-10.5, 1.5), 'seam')
    # цевьё и ствол
    B('foregrip', 'handguard', 'poly', (-1.25, 1.25), (0.5, 3.5), (16, 28), 'vent')
    B('foregrip', 'handguard', 'tan', (-1, 1), (3.5, 4), (16, 27), 'seam_v')
    B('foregrip', 'handguard', 'tan', (-0.75, 0.75), (-0.5, 0.5), (17, 27), 'seam')
    B('foregrip', 'ring', 'steel', (-1.5, 1.5), (0.25, 3.75), (28, 28.5), 'seam_v')
    rod(B, 'barrel', 'barrel', 'darksteel', 0, 2, 28.5, 41.5, 1.0, 'scratch')
    B('barrel', 'sights', 'steel', (-0.75, 0.75), (3, 4), (36, 37.5), 'scratch')
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (4, 4.75), (36.5, 37), 'tri')
    B('barrel', 'muzzle', 'blued', (-1.5, 1.5), (0.5, 3.5), (42, 46), 'vent')
    B('barrel', 'muzzle', 'steel', (-1.25, 1.25), (0.75, 3.25), (41.5, 42), 'seam_v')
    return g

# ============================== ГРАНАТЫ ==============================
def taser():
    """Тайзер Zeus x27: жёлтый корпус, две контактные иглы, индикатор заряда."""
    g = std_bones('taser', bolt=(0, 2, 4), trig=(0, -1, 5), eject=(1.5, 2, 5), mag=(0, -6, 2.5), grip=(0, -3, 3), fore=(0, 1.5, 9),
                  barrel=(0, 2, 12), muz=(0, 2.5, 15.5))
    B = g.box
    B('receiver', 'body', 'yellow', (-1.5, 1.5), (-1, 4), (-1, 9), 'scratch')
    B('receiver', 'top', 'poly', (-1, 1), (4, 4.5), (0, 8), 'seam')
    for xr in ((1.5, 1.75), (-1.75, -1.5)):
        B('receiver', 'band', 'poly', xr, (0, 1.5), (1, 8), 'seam_v')
        B('receiver', 'label', 'white', xr, (2.25, 3.25), (2, 5), 'engrave')
    B('receiver', 'light', 'green', (-0.5, 0.5), (4.5, 5), (6, 7), None)
    B('receiver', 'port', 'darksteel', (-0.75, 0.75), (-1.5, -1), (6, 9), 'slits')
    # головка с иглами
    B('foregrip', 'head', 'darksteel', (-1.25, 1.25), (0, 3.5), (9, 11), 'vent')
    B('foregrip', 'collar', 'rubber', (-1.5, 1.5), (-0.5, 4), (11, 11.5), 'rib_v')
    for xr in ((0.5, 1.25), (-1.25, -0.5)):
        B('barrel', 'prong', 'bright', xr, (1.25, 2.75), (11.5, 14.5), 'knurl')
        B('barrel', 'tip', 'brass', xr, (1.5, 2.5), (14.5, 15.5), 'seam')
    B('barrel', 'gap', 'glass', (-0.25, 0.25), (1.5, 2.5), (11.5, 13.5), None)
    # рукоять
    B('pistol_grip', 'grip', 'poly', (-1.25, 1.25), (-5, -1), (1.5, 4.5), 'stipple')
    B('pistol_grip', 'grip', 'poly', (-1.25, 1.25), (-7, -5), (1, 4), 'stipple')
    sym(B, 'pistol_grip', 'panel', 'rubber', (1.25, 1.5), (-6.5, -1.5), (1.5, 4), 'chk')
    B('magazine', 'battery', 'steel', (-0.75, 0.75), (-8.5, -6.5), (1.5, 3.5), 'rib_h')
    B('magazine', 'base', 'bright', (-1.25, 1.25), (-9.5, -8.5), (1, 4), 'seam_v')
    # скоба и спуск
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-3.5, -3), (4.5, 8))
    B('receiver', 'guard', 'poly', (-0.4, 0.4), (-3, -1.5), (7.5, 8))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3, -1), (5, 5.5))
    # задняя крышка и ремешок
    B('stock', 'cap', 'darksteel', (-1.5, 1.5), (-1, 4), (-2, -1), 'rib_h')
    B('stock', 'loop', 'rubber', (-0.5, 0.5), (0, 2), (-3.5, -2), 'weave')
    B('bolt', 'switch', 'bright', (1.5, 2.25), (3.5, 4), (6, 7), 'knurl')
    return g

def vrod(B, bone, part, mat, cx, cz, y0, y1, r, pat=None, q=0.5):
    """вертикальный «цилиндр» (восьмигранник из двух боксов)"""
    B(bone, part, mat, (cx - r, cx + r), (y0, y1), (cz - r + q, cz + r - q), pat)
    B(bone, part, mat, (cx - r + q, cx + r - q), (y0 + .25, y1 - .25), (cz - r, cz + r), pat)

def grenade_bones(gid, lever=True):
    g = Gun(gid, 256); g.bone('weapon', 'root', (0, 0, 0)); g.bone('pistol_grip', 'weapon', (0, 0, 0))
    if lever:
        g.bone('lever', 'weapon', (4, 2, 0)); g.bone('pin', 'weapon', (-1.5, 6.25, 0))
    return g

def pin_ring(B, y):
    """кольцо чеки (слева от запала) и сама чека"""
    B('pin', 'pin', 'bright', (-3.5, -1.5), (y, y + .5), (-.25, .25))
    B('pin', 'pin', 'bright', (-4, -3.5), (y - 1, y + 1), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -5), (y - 1, y + 1), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (y + 1, y + 1.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (y - 1.5, y - 1), (-.25, .25))

def grenade_he():
    g = grenade_bones('grenade_he'); B = g.box
    for y0, y1, r in ((-4.5, -3.5, 2), (-3.5, -2, 3), (-2, 2, 3.5), (2, 3.5, 3), (3.5, 4.5, 2)):
        vrod(B, 'pistol_grip', 'body', 'olive', 0, 0, y0, y1, r, 'rib_h')
    vrod(B, 'pistol_grip', 'band', 'oliveD', 0, 0, -.5, .5, 3.75, 'rib_h')
    vrod(B, 'pistol_grip', 'band', 'oliveD', 0, 0, -3, -2.5, 3.25, 'rib_h') if False else None
    B('pistol_grip', 'neck', 'steel', (-1.5, 1.5), (4.5, 5.5), (-1, 1), 'seam')
    vrod(B, 'pistol_grip', 'fuse', 'darksteel', 0, 0, 5.5, 7, 1.5, 'knurl')
    B('pistol_grip', 'label', 'brass', (-1.5, 1.5), (-1.5, 1.5), (-3.75, -3.5), 'stripe_h') if False else None
    B('lever', 'lever', 'steel', (3.75, 4.25), (-2, 6.5), (-1, 1), 'scratch')
    B('lever', 'lever', 'steel', (1.5, 4.25), (6.5, 7), (-1, 1), 'seam')
    B('pin', 'pin', 'bright', (-3.5, -1.5), (6, 6.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-4, -3.5), (5, 7.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -5), (5, 7.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (7.5, 8), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (4.5, 5), (-.25, .25))
    return g

def grenade_flash():
    g = grenade_bones('grenade_flash'); B = g.box
    vrod(B, 'pistol_grip', 'body', 'steelL', 0, 0, -5, 3, 2.5, 'scratch')
    vrod(B, 'pistol_grip', 'band', 'darksteel', 0, 0, -2, 0, 2.75, 'slits')
    vrod(B, 'pistol_grip', 'band', 'darksteel', 0, 0, 1, 2, 2.75, 'slits') if False else None
    vrod(B, 'pistol_grip', 'cap', 'black', 0, 0, -5.5, -5, 2, 'seam')
    vrod(B, 'pistol_grip', 'cap', 'black', 0, 0, 3, 4, 2, 'seam')
    B('pistol_grip', 'neck', 'steel', (-1.5, 1.5), (4, 4.5), (-1, 1), 'seam')
    vrod(B, 'pistol_grip', 'fuse', 'darksteel', 0, 0, 4.5, 6, 1.5, 'knurl')
    B('pistol_grip', 'stripe', 'orange', (-2.75, 2.75), (2, 2.5), (-1.5, 1.5), 'stripe_h') if False else None
    B('lever', 'lever', 'steel', (2.75, 3.25), (-2, 5.5), (-1, 1), 'scratch')
    B('lever', 'lever', 'steel', (1.5, 3.25), (5.5, 6), (-1, 1), 'seam')
    B('pin', 'pin', 'bright', (-3.5, -1.5), (5, 5.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-4, -3.5), (4, 6.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -5), (4, 6.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (6.5, 7), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (3.5, 4), (-.25, .25))
    return g

def grenade_smoke():
    g = grenade_bones('grenade_smoke'); B = g.box
    vrod(B, 'pistol_grip', 'body', 'smoke', 0, 0, -5, 3.5, 2.5, 'scratch')
    vrod(B, 'pistol_grip', 'band', 'white', 0, 0, -1.5, 0, 2.75, 'stripe_h')
    vrod(B, 'pistol_grip', 'cap', 'darksteel', 0, 0, -5.5, -5, 2, 'seam')
    vrod(B, 'pistol_grip', 'cap', 'darksteel', 0, 0, 3.5, 4.5, 2, 'vent')
    B('pistol_grip', 'neck', 'steel', (-1.5, 1.5), (4.5, 5), (-1, 1), 'seam') if False else None
    vrod(B, 'pistol_grip', 'fuse', 'steel', 0, 0, 4.5, 6, 1.5, 'knurl')
    B('lever', 'lever', 'steel', (2.75, 3.25), (-2, 6), (-1, 1), 'scratch')
    B('lever', 'lever', 'steel', (1.5, 3.25), (6, 6.5), (-1, 1), 'seam')
    B('pin', 'pin', 'bright', (-3.5, -1.5), (5.5, 6), (-.25, .25))
    B('pin', 'pin', 'bright', (-4, -3.5), (4.5, 7), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -5), (4.5, 7), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (7, 7.5), (-.25, .25))
    B('pin', 'pin', 'bright', (-5.5, -3.5), (4, 4.5), (-.25, .25))
    return g

MATS['amber'] = (150, 84, 26); NOISE['amber'] = 3
MATS['yellow'] = (238, 196, 32); NOISE['yellow'] = 4
MATS['liquid'] = (214, 150, 44); NOISE['liquid'] = 4

def grenade_molotov():
    g = Gun('grenade_molotov', 256); g.bone('weapon', 'root', (0, 0, 0)); g.bone('pistol_grip', 'weapon', (0, 0, 0))
    g.bone('rag', 'weapon', (0, 6.5, 0)); B = g.box
    vrod(B, 'pistol_grip', 'bottle', 'amber', 0, 0, -5, 1, 3, 'scratch')
    vrod(B, 'pistol_grip', 'bottle', 'amber', 0, 0, 1, 2.5, 2, 'scratch')
    vrod(B, 'pistol_grip', 'bottle', 'amber', 0, 0, 2.5, 5.5, 1, 'scratch')
    vrod(B, 'pistol_grip', 'lip', 'amber', 0, 0, 5.5, 6.5, 1.5, 'seam')
    B('pistol_grip', 'liquid', 'liquid', (-2, 2), (-4, 0), (-3.25, -3), 'glint') if False else None
    B('pistol_grip', 'label', 'white', (-2.5, 2.5), (-3, -0.5), (-3.25, -3), 'engrave')
    B('pistol_grip', 'label', 'red', (-2.5, 2.5), (-1.5, -1), (-3.5, -3.25), 'stripe_h')
    vrod(B, 'pistol_grip', 'tie', 'red', 0, 0, 3.5, 4.5, 1.25, 'weave')
    B('rag', 'rag', 'white', (-.5, .5), (6.5, 9), (-.5, .5), 'weave')
    B('rag', 'rag', 'white', (.5, 1), (6.5, 8), (-.5, .5), 'weave')
    B('rag', 'rag', 'white', (-1, -.5), (6.5, 7.5), (-.5, .5), 'weave')
    B('rag', 'rag', 'orange', (-.5, .5), (9, 9.5), (-.5, .5), 'redtip')
    return g


ALLG = {k: globals()[k] for k in ('ak47', 'awp', 'glock18', 'galil', 'deagle', 'fiveseven', 'p90', 'usp', 'revolver', 'm4a4', 'm4a1s', 'famas', 'mp9', 'mp7', 'scar20', 'grenade_he', 'grenade_flash', 'grenade_smoke', 'grenade_molotov', 'claw', 'knife', 'bayonet', 'taser')}
