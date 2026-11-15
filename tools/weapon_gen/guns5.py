"""G3SG1 — автоматическая снайперская винтовка стороны Т (аналог SCAR-20 у спецназа):
штампованная стальная коробка с рёбрами, барабанный целик, трубка взвода над стволом с рукояткой слева,
узкое цевьё с круглыми окнами, прямой магазин на 20, приклад с регулируемым щеком, оптика на «когтевом» кронштейне,
щелевой пламегаситель."""
from wgen import *
import hd
from hd import std_bones, rod, rail, sym


def g3sg1():
    g = std_bones('g3sg1', bolt=(0, 3.5, 22), trig=(0, -2, 9.5), eject=(1.75, 2.5, 10), mag=(0, -2, 13.5), grip=(0, -2, 4.5),
                  fore=(0, 0, 22), barrel=(0, 2, 31), muz=(0, 2, 45.3))
    g.bone('scope', 'weapon', (0, 7, 11))
    B = g.box
    # ---- ствольная коробка (штамповка): корпус, рёбра жёсткости, заклёпки
    B('receiver', 'body', 'darksteel', (-1.5, 1.5), (0, 3.5), (0, 18), 'scratch')
    B('receiver', 'top', 'darksteel', (-1.25, 1.25), (3.5, 4.25), (0.5, 17.5), 'seam')
    sym(B, 'receiver', 'rib', 'blued', (1.5, 1.75), (2.25, 2.75), (1, 17))
    B('receiver', 'port', 'black', (1.5, 1.75), (1, 2), (9, 13), 'port')
    sym(B, 'receiver', 'rivet', 'steel', (1.5, 1.7), (0.5, 1.0), (1.5, 2.0))
    sym(B, 'receiver', 'rivet', 'steel', (1.5, 1.7), (0.5, 1.0), (16, 16.5))
    # ударно-спусковой короб (полимер) и скоба
    B('receiver', 'lower', 'poly', (-1.25, 1.25), (-1.5, 0), (2, 13), 'seam')
    B('receiver', 'selector', 'steel', (-1.5, -1.25), (-1.25, -0.5), (3, 5), 'engrave')
    B('receiver', 'selector', 'bright', (-1.75, -1.5), (-1.0, -0.75), (3, 4))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-5, -4.5), (7.5, 12.5))
    B('receiver', 'guard', 'poly', (-0.5, 0.5), (-4.5, -1.5), (12, 12.5))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -1.5), (9.5, 10))
    B('trigger', 'trigger', 'bright', (-0.25, 0.25), (-4, -3.5), (9, 9.5))
    # барабанный целик
    rod(B, 'receiver', 'sight', 'darksteel', 0, 5.0, 1.0, 2.5, 0.85)
    B('receiver', 'sight', 'steel', (-0.5, 0.5), (4.25, 4.5), (1.3, 2.2))
    # ---- трубка взвода над стволом и рукоятка
    rod(B, 'foregrip', 'tube', 'blued', 0, 4.25, 18, 33, 0.75, 'seam_v')
    B('bolt', 'handle', 'steel', (-2.5, -0.75), (3.75, 4.5), (22, 22.75), 'scratch')
    B('bolt', 'handle', 'rubber', (-3.25, -2.5), (3.5, 4.75), (21.75, 23), 'knurl')
    # ---- цевьё с окнами
    B('foregrip', 'handguard', 'poly', (-1.5, 1.5), (-1.25, 3.0), (18.5, 30), 'vent')
    B('foregrip', 'handguard', 'poly', (-1.25, 1.25), (-1.75, -1.25), (19, 29.5), 'rib_h')
    B('foregrip', 'cap', 'darksteel', (-1.25, 1.25), (-0.75, 3.25), (30, 31), 'seam_v')
    # ---- ствол, мушка, пламегаситель
    rod(B, 'barrel', 'barrel', 'darksteel', 0, 2, 31, 41.5, 0.9, 'scratch')
    B('barrel', 'sight', 'darksteel', (-0.75, 0.75), (2.75, 5.0), (37.5, 38.5), 'scratch')
    B('barrel', 'sight', 'bright', (-0.25, 0.25), (5.0, 5.75), (37.75, 38.25), 'tri')
    rod(B, 'barrel', 'muzzle', 'blued', 0, 2, 41.5, 45, 1.15, 'slits')
    B('barrel', 'muzzle', 'black', (-0.5, 0.5), (1.5, 2.5), (45, 45.25))
    # ---- магазин (прямой, стальной, с рёбрами)
    B('magazine', 'magazine', 'steel', (-1.0, 1.0), (-8.5, 0), (13.5, 16.5), 'rib_h')
    B('magazine', 'base', 'darksteel', (-1.25, 1.25), (-9.25, -8.5), (13.25, 16.75), 'seam_v')
    B('magazine', 'release', 'bright', (-0.5, 0.5), (-1.25, -0.25), (12.75, 13.5))
    # ---- пистолетная рукоять
    for yb, yt, z0, z1 in ((-3.5, -1.5, 3.5, 7), (-5.5, -3.5, 3, 6.5), (-7.5, -5.5, 2.5, 6), (-9, -7.5, 2.25, 5.75)):
        B('pistol_grip', 'grip', 'poly', (-1.1, 1.1), (yb, yt), (z0, z1), 'stipple')
    B('pistol_grip', 'grip', 'rubber', (-1.0, 1.0), (-9.5, -9), (2.25, 5.75), 'rib_v')
    # ---- приклад с регулируемым щеком
    B('stock', 'stock', 'poly', (-1.25, 1.25), (-2.5, 3.0), (-6, 0), 'scratch')
    B('stock', 'stock', 'poly', (-1.25, 1.25), (-5.0, 3.0), (-12, -6), 'scratch')
    B('stock', 'cheek', 'black', (-1.0, 1.0), (3.0, 4.5), (-11, -4), 'rib_h')
    B('stock', 'cheek', 'steel', (-1.5, -1.25), (3.25, 4.25), (-8, -7), 'knurl')
    B('stock', 'butt', 'rubber', (-1.5, 1.5), (-5.5, 4.0), (-13, -12), 'rib_h')
    B('stock', 'swivel', 'steel', (-0.25, 0.25), (-5.75, -5.0), (-11, -10))
    # ---- оптика на кронштейне
    rod(B, 'scope', 'scope', 'black', 0, 7.5, 4, 19, 1.4, 'scratch')
    rod(B, 'scope', 'scope', 'poly', 0, 7.5, 19, 23, 2.0, 'seam_v')
    B('scope', 'scope', 'blued', (-1.5, 1.5), (6.0, 9.0), (23, 23.5), 'lens')
    rod(B, 'scope', 'scope', 'poly', 0, 7.5, 1.5, 4, 1.75)
    B('scope', 'scope', 'blued', (-1.25, 1.25), (6.25, 8.75), (1.0, 1.5), 'lens')
    B('scope', 'turret', 'bright', (-0.75, 0.75), (9, 10), (10.5, 12.5), 'knurl')
    B('scope', 'turret', 'bright', (1.5, 2.5), (7.0, 8.0), (10.5, 12.5), 'knurl')
    for z0 in (6, 14):                                                     # «когтевой» кронштейн
        B('scope', 'mount', 'steel', (-1.25, 1.25), (4.25, 6.0), (z0, z0 + 1.5), 'scratch')
        sym(B, 'scope', 'mount', 'darksteel', (1.25, 1.5), (4.25, 5.25), (z0 + 0.25, z0 + 1.25))
    return g


hd.ALLG['g3sg1'] = g3sg1

import anim4                                   # noqa: E402
from anim import ANIMS                         # noqa: E402
ANIMS['g3sg1'] = lambda: anim4._derive('g3sg1', 3.2, 1.6, 0.38)
