from wgen import *
import math

def usp():
    g = Gun('usp', 128)
    g.bone('weapon', 'root', (0, -2, 3.5)); g.bone('slide', 'weapon', (0, 1.5, 9))
    g.bone('FX_ejected_case_0', 'slide', (1.25, 2, 6)); g.bone('frame', 'weapon', (0, -1, 6))
    g.bone('trigger', 'frame', (0, -2, 8)); g.bone('magazine', 'weapon', (0, -6, 3))
    g.bone('barrel', 'weapon', (0, 1.5, 18)); g.bone('silencer', 'weapon', (0, 1.5, 23))
    g.bone('FX_muzzle_flash', 'silencer', (0, 1.5, 27.3)); g.bone('pistol_grip', 'weapon', (0, -5, 3.5))
    B = g.box
    B('slide', 'slide', 'blued', (-1.25, 1.25), (0, 3), (1, 5), 'rib_v')
    B('slide', 'slide', 'blued', (-1.25, 1.25), (0, 3), (5, 14), 'scratch')
    B('slide', 'slide', 'blued', (-1.25, 1.25), (0, 2.5), (14, 17), 'rib_v')
    B('slide', 'sights', 'darksteel', (-0.5, 0.5), (3, 4.5), (15, 16), 'dot')
    B('slide', 'sights', 'darksteel', (-0.75, 0.75), (3, 4.5), (1, 2), 'dot')
    B('barrel', 'barrel', 'bright', (-0.5, 0.5), (1, 2), (17, 19))
    B('silencer', 'silencer', 'darksteel', (-1, 1), (0.5, 2.5), (20, 27), 'rib_v')
    B('silencer', 'silencer', 'blued', (-1.25, 1.25), (0.25, 2.75), (19, 20))
    B('silencer', 'silencer', 'blued', (-0.75, 0.75), (0.75, 2.25), (27, 27.5))
    B('frame', 'hammer', 'darksteel', (-0.5, 0.5), (1, 3), (0, 1))
    B('frame', 'frame', 'steel', (-1, 1), (-1, 0), (2, 10), 'scratch')
    B('frame', 'frame', 'steel', (-1, 1), (-2, 0), (10, 16), 'chk')
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-5, -4), (7, 12))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-4, -2), (11, 12))
    B('trigger', 'trigger', 'bright', (-0.5, 0.5), (-3, -1), (8, 9))
    B('pistol_grip', 'grip', 'poly', (-1, 1), (-5, -1), (2, 7), 'stipple')
    B('pistol_grip', 'grip', 'poly', (-1, 1), (-9, -5), (1, 6), 'stipple')
    B('magazine', 'magazine', 'steel', (-0.75, 0.75), (-8.5, -4), (1.5, 5.5), 'rib_h')
    B('magazine', 'magazine', 'bright', (-1.25, 1.25), (-10, -9), (0.5, 6.5))
    g.fx('FX_muzzle_flash', (0, 1.5, 27.3)); g.fx('FX_ejected_case_0', (1.25, 2, 6))
    return g

def revolver():
    g = Gun('revolver', 128)
    g.bone('weapon', 'root', (0, -3, 3.5)); g.bone('frame', 'weapon', (0, 0, 5))
    g.bone('hammer', 'frame', (0, 3, 1)); g.bone('trigger', 'frame', (0, -2, 6.5))
    g.bone('cylinder', 'weapon', (0, 1.75, 10)); g.bone('FX_ejected_case_0', 'cylinder', (0, 1.75, 10))
    g.bone('barrel', 'weapon', (0, 2, 17)); g.bone('FX_muzzle_flash', 'barrel', (0, 2, 22.3))
    g.bone('pistol_grip', 'weapon', (0, -5, 3))
    B = g.box
    B('frame', 'frame', 'steel', (-1.25, 1.25), (-1, 3.5), (1, 8), 'scratch')
    B('frame', 'frame', 'steel', (-1, 1), (3.5, 4.5), (1, 14), 'scratch')
    B('frame', 'sights', 'darksteel', (-0.5, 0.5), (4.5, 5.5), (1, 2))
    B('hammer', 'hammer', 'darksteel', (-0.5, 0.5), (2, 4.5), (0, 1), 'rib_h')
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-5, -4), (5, 10))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-4, -1), (5, 6))
    B('frame', 'guard', 'steel', (-0.5, 0.5), (-4, 0), (9, 10))
    B('trigger', 'trigger', 'bright', (-0.5, 0.5), (-3.5, -1), (6, 7))
    B('cylinder', 'cylinder', 'bright', (-1.5, 1.5), (0, 3.5), (8, 13), 'rib_v')
    B('barrel', 'barrel', 'blued', (-1, 1), (1, 3), (14, 22), 'scratch')
    B('barrel', 'lug', 'steel', (-1, 1), (0, 1), (14, 20))
    B('barrel', 'sights', 'darksteel', (-0.5, 0.5), (3, 4), (20, 21), 'dot')
    B('pistol_grip', 'grip', 'wood', (-1.25, 1.25), (-5, -1), (1, 5), 'grain')
    B('pistol_grip', 'grip', 'woodd', (-1.25, 1.25), (-9, -5), (0, 4), 'grain')
    B('pistol_grip', 'butt', 'steel', (-1.25, 1.25), (-10, -9), (-0.5, 4.5))
    g.fx('FX_muzzle_flash', (0, 2, 22.3)); g.fx('FX_ejected_case_0', (0, 1.75, 10))
    return g

def _m4(gid, silenced):
    g = Gun(gid, 128)
    g.bone('weapon', 'root', (0, -1, 6)); g.bone('receiver', 'weapon', (0, 1, 9))
    g.bone('bolt', 'receiver', (0, 3.5, 3.5)); g.bone('trigger', 'receiver', (0, -2, 8))
    g.bone('FX_ejected_case_0', 'receiver', (1.75, 1.5, 8)); g.bone('magazine', 'weapon', (0, -2, 12))
    g.bone('stock', 'weapon', (0, 0, -3)); g.bone('pistol_grip', 'weapon', (0, -3, 4.5))
    g.bone('foregrip', 'weapon', (0, 1, 19)); g.bone('barrel', 'weapon', (0, 1.75, 25))
    if silenced:
        g.bone('silencer', 'weapon', (0, 1.75, 29)); mz = 37.2; par = 'silencer'
    else:
        mz = 29.7; par = 'barrel'
    g.bone('FX_muzzle_flash', par, (0, 1.75, mz))
    B = g.box
    B('receiver', 'receiver', 'blued', (-1.5, 1.5), (0, 3), (3, 15), 'scratch')
    B('receiver', 'receiver', 'darksteel', (1.5, 1.75), (1, 2.5), (7, 11), 'port')
    B('receiver', 'lower', 'poly', (-1.5, 1.5), (-2, 0), (3, 15), 'scratch')
    B('receiver', 'rail', 'steel', (-0.75, 0.75), (3, 3.75), (4, 15), 'chk')
    if not silenced:
        B('receiver', 'sights', 'steel', (-1, 1), (3.75, 4.75), (5, 7))
        B('receiver', 'sights', 'darksteel', (-0.5, 0.5), (4.75, 5.5), (5.5, 6.5), 'dot')
    else:
        B('receiver', 'sights', 'steel', (-0.75, 0.75), (3.75, 4.5), (5, 7))
    B('bolt', 'bolt', 'bright', (-0.75, 0.75), (3.75, 4.75), (2, 5))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, -3.5), (6, 10))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-3.5, -2), (6, 7))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-3.5, -2), (9, 10))
    B('trigger', 'trigger', 'bright', (-0.5, 0.5), (-3.5, -2), (7.5, 8.5))
    B('magazine', 'magazine', 'poly' if not silenced else 'tan', (-1, 1), (-6, -2), (10, 14), 'rib_h')
    B('magazine', 'magazine', 'poly' if not silenced else 'tan', (-1, 1), (-9, -6), (10.5, 14.5), 'rib_h')
    B('magazine', 'magazine', 'steel', (-1.25, 1.25), (-10, -9), (10, 15))
    B('stock', 'stocktube', 'steel', (-0.75, 0.75), (0.5, 2), (-3, 3))
    B('stock', 'stock', 'poly', (-1.25, 1.25), (-3, 2.5), (-8, -3), 'chk')
    B('stock', 'buttpad', 'rubber', (-1.25, 1.25), (-3.5, 3), (-9, -8), 'rib_h')
    B('pistol_grip', 'grip', 'poly', (-1, 1), (-5, -2), (3, 6), 'stipple')
    B('pistol_grip', 'grip', 'poly', (-1, 1), (-8, -5), (2, 5), 'stipple')
    hg = 'poly' if not silenced else 'tan'
    B('foregrip', 'handguard', hg, (-1.25, 1.25), (-0.5, 3), (15, 23 if silenced else 22), 'rib_h' if silenced else 'chk')
    if not silenced:
        B('foregrip', 'handguard', 'steel', (-1, 1), (3, 3.75), (15, 22), 'chk')
        B('barrel', 'sights', 'steel', (-1, 1), (0.5, 3.5), (22, 24))
        B('barrel', 'sights', 'darksteel', (-0.5, 0.5), (3.5, 5.5), (22.5, 23.5))
        B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1, 2.5), (24, 27))
        B('barrel', 'muzzle', 'blued', (-1, 1), (0.75, 2.75), (27, 29.5), 'slits')
    else:
        B('barrel', 'barrel', 'darksteel', (-0.75, 0.75), (1, 2.5), (23, 29))
        B('silencer', 'silencer', 'darksteel', (-1.25, 1.25), (0.5, 3), (30, 37), 'rib_v')
        B('silencer', 'silencer', 'blued', (-1.5, 1.5), (0.25, 3.25), (29, 30))
        B('silencer', 'silencer', 'blued', (-1, 1), (0.75, 2.75), (37, 37.5))
        mz = 37.7
    g.fx('FX_muzzle_flash', (0, 1.75, mz)); g.fx('FX_ejected_case_0', (1.75, 1.5, 8))
    return g
def m4a4(): return _m4('m4a4', False)
def m4a1s(): return _m4('m4a1s', True)

def claw():
    g = Gun('claw', 128)
    RP = (0, -2.5, 2)
    g.bone('weapon', 'root', RP)
    g.bone('handle', 'weapon', (0, 0.5, 7)); g.bone('ring', 'weapon', RP)
    g.bone('blade', 'weapon', (0, 1.5, 10))
    B = g.box
    B('handle', 'handle', 'poly', (-1, 1), (-1, 2), (4, 10), 'rib_h')
    B('handle', 'handle', 'steel', (-0.75, 0.75), (-1.25, -1), (4, 10))
    B('handle', 'guard', 'bright', (-0.75, 0.75), (-0.5, 3.5), (10, 10.5))
    B('handle', 'handlecap', 'steel', (-1.25, 1.25), (-1, 2.5), (3, 4), 'rib_v')
    # finger ring
    B('ring', 'ring', 'bright', (-0.5, 0.5), (-5, -1), (0, 1))
    B('ring', 'ring', 'bright', (-0.5, 0.5), (-5, -1), (3, 4))
    B('ring', 'ring', 'bright', (-0.5, 0.5), (-5, -4), (1, 3))
    B('ring', 'ring', 'bright', (-0.5, 0.5), (-2, -1), (1, 3))
    # crescent blade: chain of rotated segments turning downward
    sy, sz, th = 1.5, 10.5, 0.0
    L = 3.2
    thick = [3.8, 3.5, 3.0, 2.4, 1.6]
    half = [0.5, 0.45, 0.4, 0.35, 0.3]
    for i in range(5):
        T = thick[i]
        B('blade', 'blade', 'bright', (-half[i], half[i]), (sy - T / 2, sy + T / 2), (sz, sz + L), 'edge',
          rot=(th, 0, 0) if th else None, rpiv=(0, sy, sz) if th else None)
        sz += L * math.cos(math.radians(th)); sy += L * math.sin(math.radians(th))
        th -= 24
    return g

def knife():
    """Стандартный боевой нож (Т / КТ отличаются только текстурой)"""
    g = Gun('knife', 128)
    RP = (0, 0.5, 6)
    g.bone('weapon', 'root', RP)
    B = g.box
    B('weapon', 'handle', 'poly', (-1, 1), (-0.5, 2.5), (3, 9.5), 'rib_h')
    B('weapon', 'pommel', 'steel', (-1.25, 1.25), (-0.75, 2.75), (2, 3), 'rib_v')
    B('weapon', 'guard', 'bright', (-1.25, 1.25), (-1, 3.5), (9.5, 10.5))
    B('weapon', 'blade', 'bright', (-0.4, 0.4), (0.0, 2.4), (10.5, 17), 'edge')
    B('weapon', 'blade', 'bright', (-0.35, 0.35), (0.5, 2.8), (17, 18.5), 'edge')
    B('weapon', 'blade', 'bright', (-0.3, 0.3), (1.3, 2.8), (18.5, 19.5), 'edge')
    B('weapon', 'spine', 'steel', (-0.45, 0.45), (2.4, 2.8), (10.6, 16.9))
    g.fx('FX_muzzle_flash', (0, 1.5, 19.5)); g.fx('FX_ejected_case_0', (0, 1.5, 8))
    return g
