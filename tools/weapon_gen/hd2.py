"""Дробовики и MAC-10: Nova, XM1014, MAG-7, Обрез (sawedoff), MAC-10. Кости как у std_bones (foregrip у помп — подвижное цевьё)."""
from hd import *
import hd

def _trig(B, grip_mat='poly', z0=7.0, z1=12.0, y0=-4.0):
    B('receiver', 'guard', grip_mat, (-0.5, 0.5), (y0, y0 + .5), (z0, z1))
    B('receiver', 'guard', grip_mat, (-0.5, 0.5), (y0 + .5, -1.5), (z1 - .5, z1))
    B('receiver', 'guard', grip_mat, (-0.5, 0.5), (y0 + .5, -1.5), (z0, z0 + .5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3, -1.5), (z0 + 2, z0 + 2.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-3, -2.5), (z0 + 1.5, z0 + 2))

def _grip(B, mat='poly', z=4.0, wid=1.0, rows=((-4.5, -1.0, 0), (-6.5, -4.5, -.5), (-8.5, -6.5, -1))):
    for yb, yt, dz in rows:
        B('pistol_grip', 'grip', mat, (-wid, wid), (yb, yt), (z + dz, z + dz + 3.5), 'stipple')
    B('pistol_grip', 'cap', 'darksteel', (-wid, wid), (rows[-1][0] - .5, rows[-1][0]), (z + rows[-1][2], z + rows[-1][2] + 3.5), 'seam')

def _tube_barrel(B, z_bar0, z_bar1, z_tube1, cy=1.25, tcy=-0.5, bm='darksteel', r=1.0):
    rod(B, 'barrel', 'barrel', bm, 0, cy, z_bar0, z_bar1, r, 'scratch')
    B('barrel', 'tube', 'darksteel', (-1, 1), (tcy - .5, tcy + .5), (z_bar0 + .5, z_tube1), 'scratch')
    B('barrel', 'tube', 'darksteel', (-0.75, 0.75), (tcy - 1, tcy + 1), (z_bar0 + .75, z_tube1 - .25), 'scratch')
    B('barrel', 'cap', 'steel', (-1.5, 1.5), (tcy - 1, tcy + 1), (z_tube1, z_tube1 + .5), 'seam_v')

# ================================================================== NOVA
def nova():
    g = std_bones('nova', bolt=(0, 1, 9), trig=(0, -2, 9.5), eject=(1.5, 1.5, 9), mag=(0, -2, 12), grip=(0, -2, 5), fore=(0, -1, 20),
                  barrel=(0, 1, 28), muz=(0, 1.25, 38.5))
    B = g.box
    # ресивер
    B('receiver', 'body', 'poly', (-1.25, 1.25), (-1.5, 2.5), (2, 15), 'scratch')
    B('receiver', 'top', 'darksteel', (-0.75, 0.75), (2.5, 3), (3, 14), 'seam_v')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (0.25, 2), (6, 12), 'port')
    B('receiver', 'plate', 'darksteel', (-1.5, -1.25), (0, 2), (4, 10), 'engrave')
    B('receiver', 'screw', 'steel', (1.25, 1.5), (-1, -0.5), (4, 5), 'screw')
    B('receiver', 'screw', 'steel', (-1.5, -1.25), (-1, -0.5), (12, 13), 'screw')
    B('receiver', 'sights', 'darksteel', (-0.75, 0.75), (3, 3.5), (3, 4), 'dot')
    # затвор-ручка (двигается с цевьём)
    B('bolt', 'bolt', 'bright', (1.5, 2.25), (0.5, 1.5), (8, 11), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.25, 3), (0.25, 1.75), (8.5, 10.5), 'knurl')
    _trig(B)
    _grip(B, 'poly', 4.5)
    # цельный приклад с «горбом»
    B('stock', 'buttpad', 'rubber', (-1.25, 1.25), (-4.5, 2.5), (-12.5, -12), 'rib_h')
    for yb, yt, z0, z1, hw in ((-3.5, 2.5, -12, -8, 1.25), (-3, 3, -8, -4, 1.25), (-2.5, 3, -4, 0, 1.25), (-2, 2, 0, 2.5, 1)):
        B('stock', 'stock', 'poly', (-hw, hw), (yb, yt), (z0, z1), 'scratch')
    B('stock', 'cheek', 'darksteel', (-1, 1), (3, 3.5), (-8, -1), 'seam')
    sym(B, 'stock', 'screw', 'steel', (1.25, 1.5), (-0.5, 0), (-6, -5.5), 'screw')
    # ствол + магазинная труба
    _tube_barrel(B, 14.5, 38, 31)
    B('barrel', 'rib', 'darksteel', (-0.5, 0.5), (2.25, 2.75), (16, 37), 'seam_v')
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (2.75, 3.5), (36.5, 37), 'tri')
    B('barrel', 'muzzle', 'blued', (-1.25, 1.25), (0, 2.5), (38, 38.5), 'seam_v')
    # подвижное цевьё
    B('foregrip', 'pump', 'poly', (-1.5, 1.5), (-2.5, 1), (16, 25), 'rib_v')
    B('foregrip', 'ring', 'darksteel', (-1.5, 1.5), (-2.5, 1), (25, 25.5), 'seam_v')
    B('foregrip', 'ring', 'darksteel', (-1.5, 1.5), (-2.5, 1), (15.5, 16), 'seam_v')
    return g

# ================================================================== XM1014
def xm1014():
    g = std_bones('xm1014', bolt=(0, 1, 9), trig=(0, -2, 9.5), eject=(1.5, 1.5, 9), mag=(0, -2, 12), grip=(0, -2, 5), fore=(0, -1, 21),
                  barrel=(0, 1, 28), muz=(0, 1.25, 37.5))
    B = g.box
    B('receiver', 'body', 'poly', (-1.25, 1.25), (-1.5, 2.5), (2, 16), 'scratch')
    B('receiver', 'top', 'darksteel', (-1, 1), (2.5, 3), (3, 15), 'seam')
    rail(B, 'receiver', 'rail', 'steel', (-0.75, 0.75), 3, 3, 16, h=0.5, pitch=1.0)
    B('receiver', 'ghost', 'darksteel', (-0.75, -0.25), (4, 5), (3, 4), 'dot')
    B('receiver', 'ghost', 'darksteel', (0.25, 0.75), (4, 5), (3, 4), 'dot')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (0.25, 2), (6, 12), 'port')
    B('receiver', 'plate', 'olive', (-1.5, -1.25), (0, 2), (4, 12), 'engrave')
    B('receiver', 'screw', 'steel', (1.25, 1.5), (-1, -0.5), (4, 5), 'screw')
    B('bolt', 'bolt', 'bright', (1.5, 2.25), (0.5, 1.5), (7, 10), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.25, 3), (0.25, 1.75), (7.5, 9.5), 'knurl')
    _trig(B)
    _grip(B, 'poly', 4.5)
    # выдвижной приклад: две штанги + затылок
    B('stock', 'strut', 'steel', (-1, -0.5), (0.5, 1.5), (-9, 2), 'seam_v')
    B('stock', 'strut', 'steel', (0.5, 1), (0.5, 1.5), (-9, 2), 'seam_v')
    B('stock', 'strut', 'steel', (-1, 1), (-1.5, -1), (-9, -1), 'seam_v')
    B('stock', 'cheek', 'poly', (-1, 1), (1.5, 2.5), (-9, -2), 'rib_h')
    B('stock', 'butt', 'poly', (-1.25, 1.25), (-3.5, 2.5), (-11.5, -9), 'scratch')
    B('stock', 'butt', 'rubber', (-1.25, 1.25), (-4, 3), (-12, -11.5), 'rib_h')
    # неподвижное цевьё
    B('foregrip', 'handguard', 'poly', (-1.5, 1.5), (-2.5, 1), (16.5, 27), 'vent')
    B('foregrip', 'ring', 'steel', (-1.5, 1.5), (-2.5, 1), (27, 27.5), 'seam_v')
    _tube_barrel(B, 15.5, 37, 29, bm='blued')
    B('barrel', 'sights', 'darksteel', (-0.5, 0.5), (2.25, 3.25), (31, 32), 'dot')
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (3.25, 4), (31.25, 31.75), 'tri')
    B('barrel', 'muzzle', 'darksteel', (-1.25, 1.25), (0, 2.5), (37, 38), 'slits')
    return g

# ================================================================== MAG-7
def mag7():
    g = std_bones('mag7', bolt=(0, 1, 8), trig=(0, -2, 9), eject=(1.5, 1.5, 8), mag=(0, -2, 13), grip=(0, -2, 5), fore=(0, -1, 17),
                  barrel=(0, 1, 22), muz=(0, 1.25, 29.5))
    B = g.box
    B('receiver', 'body', 'steel', (-1.25, 1.25), (-1.5, 2.5), (-1, 14), 'scratch')
    B('receiver', 'top', 'darksteel', (-1, 1), (2.5, 3), (0, 14), 'seam')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (0.25, 2), (5, 11), 'port')
    B('receiver', 'plate', 'tan', (-1.5, -1.25), (-1, 2), (1, 6), 'engrave')
    B('receiver', 'screw', 'steel', (1.25, 1.5), (-1, -0.5), (3, 4), 'screw')
    B('receiver', 'sights', 'darksteel', (-0.75, -0.25), (3, 4), (0, 1), 'dot')
    B('receiver', 'sights', 'darksteel', (0.25, 0.75), (3, 4), (0, 1), 'dot')
    B('receiver', 'sights', 'bright', (-0.25, 0.25), (3, 4), (13, 13.5), 'tri')
    B('bolt', 'bolt', 'bright', (1.5, 2.25), (0.5, 1.5), (6, 9), 'scratch')
    B('bolt', 'bolt', 'rubber', (2.25, 3), (0.25, 1.75), (6.5, 8.5), 'knurl')
    _trig(B, 'poly', 6.0, 11.0)
    # пистолетная рукоять (без приклада) + затыльник
    _grip(B, 'tan', 3.5, 1.0)
    B('stock', 'rear', 'steel', (-1.25, 1.25), (-2, 2.5), (-3, -1), 'seam_v')
    B('stock', 'pad', 'rubber', (-1.25, 1.25), (-2.5, 3), (-3.5, -3), 'rib_h')
    # коробчатый магазин
    B('magazine', 'magazine', 'poly', (-1, 1), (-6.5, -1.5), (11.5, 15.5), 'rib_h')
    B('magazine', 'magazine', 'steel', (-1.25, 1.25), (-7, -6.5), (11, 16), 'seam_v')
    # ствол с кожухом + цевьё
    rod(B, 'barrel', 'barrel', 'darksteel', 0, 1.25, 14, 29, 1.0, 'scratch')
    B('barrel', 'shield', 'blued', (-1.25, 1.25), (-0.5, 2.5), (18, 24), 'vent') if False else None
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (2.25, 3.25), (27.5, 28), 'tri')
    B('barrel', 'muzzle', 'blued', (-1.25, 1.25), (0, 2.5), (29, 30), 'slits')
    B('foregrip', 'pump', 'tan', (-1.5, 1.5), (-2.5, 1), (14.5, 22), 'rib_v')
    B('foregrip', 'ring', 'darksteel', (-1.5, 1.5), (-2.5, 1), (22, 22.5), 'seam_v')
    return g

# ================================================================== ОБРЕЗ (Sawed-Off)
def sawedoff():
    g = std_bones('sawedoff', bolt=(0, 1, 8), trig=(0, -2, 9), eject=(1.5, 1.5, 8), mag=(0, -2, 12), grip=(0, -2, 4), fore=(0, -1, 15),
                  barrel=(0, 1, 20), muz=(0, 1.25, 26.5))
    B = g.box
    B('receiver', 'body', 'steel', (-1.25, 1.25), (-1, 2.5), (1, 13), 'scratch')
    B('receiver', 'body', 'darksteel', (-1, 1), (2.5, 3), (2, 12), 'seam')
    B('receiver', 'port', 'darksteel', (1.25, 1.5), (0.25, 2), (5, 11), 'port')
    B('receiver', 'plate', 'bright', (-1.5, -1.25), (0, 2), (3, 7), 'engrave')
    B('receiver', 'screw', 'bright', (1.25, 1.5), (-0.5, 0), (3, 4), 'screw')
    B('receiver', 'screw', 'bright', (-1.5, -1.25), (-0.5, 0), (10, 11), 'screw')
    B('bolt', 'bolt', 'bright', (1.5, 2.25), (0.5, 1.5), (6, 9), 'scratch')
    B('bolt', 'bolt', 'woodd', (2.25, 3), (0.25, 1.75), (6.5, 8.5), 'grain')
    _trig(B, 'darksteel', 6.0, 11.0)
    # обрезанный приклад — только рукоять
    for yb, yt, z0, z1 in ((-4, -1, 2, 5.5), (-6, -4, 1.5, 5), (-8, -6, 1, 4.5), (-9.5, -8, 0.5, 4)):
        B('pistol_grip', 'grip', 'wood', (-1.25, 1.25), (yb, yt), (z0, z1), 'grain')
    B('pistol_grip', 'cap', 'darksteel', (-1.25, 1.25), (-10, -9.5), (0.5, 4), 'seam')
    sym(B, 'pistol_grip', 'screw', 'bright', (1.25, 1.5), (-5.5, -5), (3, 4), 'screw')
    B('stock', 'stub', 'woodd', (-1.25, 1.25), (-2, 2), (-3, 1), 'grain')
    B('stock', 'stub', 'darksteel', (-1.25, 1.25), (-2.5, 2.5), (-3.5, -3), 'seam')
    _tube_barrel(B, 13, 26, 21, bm='darksteel')
    B('barrel', 'sights', 'bright', (-0.25, 0.25), (2.25, 3), (24.5, 25), 'tri')
    B('barrel', 'muzzle', 'steel', (-1.25, 1.25), (0, 2.5), (26, 26.5), 'seam_v')
    B('foregrip', 'pump', 'wood', (-1.5, 1.5), (-2.5, 1), (14, 19.5), 'grain')
    B('foregrip', 'ring', 'darksteel', (-1.5, 1.5), (-2.5, 1), (19.5, 20), 'seam_v')
    return g

# ================================================================== MAC-10
def mac10():
    g = std_bones('mac10', bolt=(0, 3, 7), trig=(0, -2, 7.5), eject=(1.5, 2, 6), mag=(0, -2, 4), grip=(0, -2, 4.5), fore=(0, 0, 11),
                  barrel=(0, 1, 13), muz=(0, 1.75, 17.3))
    B = g.box
    B('receiver', 'body', 'darksteel', (-1.5, 1.5), (-1, 3.5), (-1, 12), 'scratch')
    B('receiver', 'top', 'poly', (-1.25, 1.25), (3.5, 4), (0, 11), 'seam')
    B('receiver', 'port', 'black', (1.5, 1.75), (1, 3), (5, 9), 'port')
    B('receiver', 'ribs', 'poly', (-1.75, -1.5), (0, 3), (2, 10), 'vent')
    B('receiver', 'screw', 'steel', (-1.75, -1.5), (3, 3.5), (1, 1.5), 'screw') if False else None
    B('receiver', 'sights', 'darksteel', (-0.75, -0.25), (4, 5), (0, 1), 'dot')
    B('receiver', 'sights', 'darksteel', (0.25, 0.75), (4, 5), (0, 1), 'dot')
    B('receiver', 'sights', 'bright', (-0.25, 0.25), (4, 5), (11, 11.5), 'tri')
    B('bolt', 'knob', 'bright', (-0.75, 0.75), (4, 5), (5.5, 7.5), 'knurl')
    B('bolt', 'knob', 'rubber', (-0.5, 0.5), (5, 5.5), (6, 7), 'rib_h')
    _trig(B, 'poly', 6.5, 11.5)
    for yb, yt, z0, z1 in ((-3, -1, 2.5, 6), (-5, -3, 2, 5.5), (-7, -5, 1.5, 5), (-9, -7, 1, 4.5)):
        B('pistol_grip', 'grip', 'poly', (-1, 1), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'panel', 'rubber', (1, 1.25), (-8, -3.5), (1.5, 4.5), 'chk')
    B('pistol_grip', 'panel', 'rubber', (-1.25, -1), (-8, -3.5), (1.5, 4.5), 'chk')
    B('magazine', 'magazine', 'steel', (-0.75, 0.75), (-8.5, -4), (2.25, 4.25), 'rib_h')
    B('magazine', 'base', 'bright', (-1.25, 1.25), (-10, -9), (0.5, 5), 'seam_v')
    # проволочный приклад
    B('stock', 'wire', 'steel', (-1.25, -0.75), (1, 1.5), (-9, -1), 'seam_v')
    B('stock', 'wire', 'steel', (0.75, 1.25), (1, 1.5), (-9, -1), 'seam_v')
    B('stock', 'wire', 'steel', (-1.25, -0.75), (-3, 1.5), (-9.5, -9), 'seam_v')
    B('stock', 'wire', 'steel', (0.75, 1.25), (-3, 1.5), (-9.5, -9), 'seam_v')
    B('stock', 'wire', 'steel', (-1.25, 1.25), (-3.5, -3), (-9.5, -9), 'seam_v')
    # ствол с цевьём и передняя ручка
    B('foregrip', 'grip', 'poly', (-1, 1), (-4.5, -1), (10.5, 13), 'stipple')
    B('foregrip', 'grip', 'rubber', (-1, 1), (-5, -4.5), (10.5, 13), 'rib_h')
    B('foregrip', 'shroud', 'darksteel', (-1.25, 1.25), (-0.5, 3), (12, 14), 'vent')
    B('barrel', 'barrel', 'blued', (-1, 1), (0.75, 2.75), (14, 16), 'seam_v')
    B('barrel', 'barrel', 'darksteel', (-0.5, 0.5), (1.25, 2.25), (16, 17.5), 'scratch')
    B('barrel', 'muzzle', 'black', (-0.25, 0.25), (1.5, 2), (17.5, 17.75))
    return g

hd.ALLG.update({k: globals()[k] for k in ('nova', 'xm1014', 'mag7', 'sawedoff', 'mac10')})

# ================================================================== СКЕЛЕТНЫЙ НОЖ
def skeleton():
    """Скелетный нож: цельнометаллический «скелет» рукояти с окнами, обмотка паракордом, кольцо в навершии, клинок с каплевидной точкой."""
    g = newgun('skeleton', hd.G3.knife); B = g.box
    # рамка рукояти: верхняя и нижняя шины + поперечные перемычки (между ними — сквозные окна)
    B('weapon', 'frame', 'darksteel', (-0.5, 0.5), (2.5, 3.5), (2.5, 10), 'scratch')
    B('weapon', 'frame', 'darksteel', (-0.5, 0.5), (0, 1), (2.5, 10), 'scratch')
    for z0 in (2.5, 6.5, 9.5):
        B('weapon', 'strut', 'steel', (-0.5, 0.5), (1, 2.5), (z0, z0 + 0.5), 'seam_v')
    # паракорд: две обмотки поверх шин (окна между ними остаются открытыми)
    for z0 in (3.5, 8.0):
        B('weapon', 'cord', 'grip', (-0.75, 0.75), (-0.5, 4.0), (z0, z0 + 1.5), 'weave')
    # навершие-кольцо
    B('weapon', 'pommel', 'steel', (-0.75, 0.75), (-0.5, 4), (0.5, 1), 'seam')
    B('weapon', 'pommel', 'steel', (-0.75, 0.75), (-0.5, 0.5), (1, 2.5), 'rib_v')
    B('weapon', 'pommel', 'steel', (-0.75, 0.75), (3, 4), (1, 2.5), 'rib_v')
    # гарда
    B('weapon', 'guard', 'bright', (-1.25, 1.25), (-0.5, 4.5), (10, 11), 'scratch')
    # клинок: «капля» — обух плавно спускается к острию, лезвие идёт брюшком вверх
    for z0, z1, yb, yt in ((11, 17, 1.5, 4.75), (17, 19.5, 1.75, 4.5), (19.5, 21, 2.25, 4.0), (21, 22, 2.75, 3.75), (22, 22.5, 3.25, 3.5)):
        B('weapon', 'blade', 'bright', (-0.45, 0.45), (max(yb, 2.75) if z0 < 21 else yb, yt), (z0, z1), 'edge')
        if yb < 2.75 and z0 < 21:
            B('weapon', 'edge', 'bright', (-0.2, 0.2), (yb, 2.75), (z0 + 0.25, z1 - 0.25), 'edge')
    B('weapon', 'fuller', 'steel', (-0.55, 0.55), (3.5, 4.0), (11.5, 18.5), 'seam')
    return g

hd.ALLG['skeleton'] = skeleton
