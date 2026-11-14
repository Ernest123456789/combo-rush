#!/usr/bin/env python3
"""Экспорт «Медины» в формат карты игры (как dust.map): палитра, зоны-многоугольники, площадки, спавны, точки ботов, колонки RLE.
Над зданиями и дюнами — невидимый барьер до верха, чтобы нельзя было залезть на крыши, кроме отмеченных."""
import sys, os
import numpy as np
import medina as md
from medina import M, NAMES, GW, GH, YMIN, YMAX, Y

BLOCK = {
    'mass_a': 'sandstone', 'mass_b': 'smooth_sandstone', 'mass_c': 'cut_sandstone', 'mass_fill': 'sandstone', 'rock': 'stone', 'dune': 'sand',
    'dome': 'cyan_terracotta', 'minaret': 'smooth_sandstone', 'minaret_top': 'cyan_terracotta', 'arch': 'chiseled_sandstone',
    'f_sand': 'smooth_sandstone', 'f_sand2': 'cut_sandstone', 'f_sand3': 'sandstone', 'f_tsp': 'red_terracotta', 'f_tsp2': 'terracotta',
    'f_ctsp': 'blue_terracotta', 'f_ctsp2': 'light_blue_terracotta', 'f_tile': 'orange_terracotta', 'f_tile2': 'smooth_sandstone',
    'f_cob': 'stone_bricks', 'f_cob2': 'polished_andesite', 'f_cob3': 'cracked_stone_bricks', 'f_siteA': 'orange_terracotta',
    'f_siteB': 'red_sandstone', 'f_siteB2': 'cut_red_sandstone', 'f_tun': 'deepslate_bricks', 'f_tun2': 'deepslate_tiles',
    'f_wood': 'spruce_planks', 'f_wood2': 'dark_oak_planks', 'f_roof': 'red_terracotta', 'f_roof2': 'brown_terracotta',
    'f_grass': 'grass_block[snowy=false]', 'f_path': 'dirt_path', 'f_stone': 'smooth_stone', 'f_stone2': 'stone_bricks', 'f_bridge': 'dark_oak_planks',
    'rail': 'dark_oak_fence', 'water': 'water', 'lamp': 'sea_lantern',
    'prop_crate': 'spruce_planks', 'prop_barrel': 'barrel[facing=up]', 'prop_log': 'jungle_log[axis=y]', 'prop_leaves': 'jungle_leaves[persistent=true]',
    'prop_truck': 'white_concrete', 'prop_truck_cab': 'red_concrete', 'prop_bus': 'yellow_concrete', 'prop_cart': 'oak_planks',
    'prop_stone': 'stone_bricks', 'prop_car': 'blue_concrete', 'prop_wood': 'stripped_spruce_log[axis=x]', 'prop_hedge': 'oak_leaves[persistent=true]',
    'pillar': 'chiseled_sandstone', 'pillar_tun': 'polished_deepslate',
    'awn_red': 'red_wool', 'awn_blue': 'blue_wool', 'awn_green': 'green_wool', 'awn_yellow': 'yellow_wool',
}
FACE = {'e': 'east', 'w': 'west', 'n': 'north', 's': 'south'}
def state(n):
    if n.startswith('stair_'): return 'sandstone_stairs[facing=%s,half=bottom,shape=straight]' % FACE[n[-1]]
    return BLOCK[n]

# ------------------------------------------------------------- точки
SPAWN_T = [(26, 72, 0), (29, 75, 0), (32, 78, 0), (27, 80, 0), (34, 73, 0)]
SPAWN_CT = [(126, 54, 1), (129, 57, 1), (132, 55, 1), (127, 60, 1), (132, 61, 1)]
SITES = [('A', 95, 20, 9, 4), ('B', 103, 100, 9, -2)]
# (x, z, уровень пола, куда смотреть x, z)
HOLD = {
    'A': [(116, 12, 8, 80, 22), (96, 12, 4, 78, 24), (104, 28, 4, 88, 42), (84, 9, 4, 60, 22), (118, 26, 4, 95, 22), (88, 31, 4, 88, 50)],
    'B': [(121, 102, 3, 100, 100), (100, 86, 4, 103, 100), (102, 95, -2, 88, 92), (114, 108, -2, 100, 88), (119, 82, 0, 100, 100), (92, 106, -2, 88, 92)],
    'M': [(91, 66, 4, 65, 74), (107, 64, 1, 70, 72), (96, 77, 4, 70, 74), (100, 68, 0, 70, 74)],
}
ENTRY = {   # места сбора Т перед заходом
    'A': [(52, 30, 2, 78, 24), (64, 44, 7, 70, 24), (82, 72, 0, 88, 58)],
    'B': [(80, 96, -4, 90, 94), (96, 74, 0, 100, 88)],
}
POST = {    # где Т держат площадку после закладки
    'A': [(93, 12, 4, 115, 28), (116, 12, 8, 118, 28), (85, 30, 4, 88, 45), (100, 31, 4, 118, 28)],
    'B': [(99, 95, -2, 118, 85), (95, 108, -2, 100, 86), (108, 106, -2, 118, 100), (88, 90, -2, 100, 100)],
}

def main(out):
    surf, dT, dC, res, zok = md.report()
    reach = set(dT)
    def fix(p):
        x, z, lv = p[:3]
        best = None
        for s in (lv + 1, lv + 2, lv):
            for r in range(0, 5):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        q = (x + dx, z + dz, s)
                        if q in reach and best is None: best = (x + dx, z + dz, s - 1)
                if best: break
            if best: break
        if best is None: raise SystemExit('точка недостижима: %s' % (p,))
        if best[:2] != (x, z) or best[2] != lv: print('  точка сдвинута', p[:3], '→', best)
        return best + tuple(p[3:])
    sp_t = [fix(p) for p in SPAWN_T]; sp_ct = [fix(p) for p in SPAWN_CT]
    hold = {k: [fix(p) for p in v] for k, v in HOLD.items()}
    entry = {k: [fix(p) for p in v] for k, v in ENTRY.items()}
    post = {k: [fix(p) for p in v] for k, v in POST.items()}
    for c, x, z, r, lv in SITES: fix((x, z, lv))

    # колонки, по которым ходят (над остальными — барьер)
    walk = np.zeros((GW, GH), bool)
    for x, z, s in reach: walk[x, z] = True
    for x, z, lv in md.STAIR: walk[x, z] = True

    lines = ['# карта «Медина»: сгенерировано tools/map_gen/medina_export.py', f'BOUNDS 0 {GW - 1} 0 {GH - 1} {YMIN}', 'TITLE Медина']
    used = sorted({int(v) for v in np.unique(M)} - {0})
    pal = {}
    for i, v in enumerate(used): pal[v] = i + 1; lines.append(f'B {i + 1} {state(NAMES[v])}')
    bar = len(used) + 1; lines.append(f'B {bar} barrier')
    # зоны: сначала мелкие и «верхние», чтобы название было точнее
    order = ['Балкон А', 'Галерея', 'Крыши', 'Кухня', 'Склад Б (2 этаж)', 'Склад Б', 'Переход', 'Колодец', 'Лестница', 'Подъём КТ (верх)', 'Подъём КТ',
             'Площадка А', 'Площадка Б', 'Лонг А', 'Рынок', 'Верхняя улица', 'Переулок', 'Нижние тоннели', 'Тоннели', 'Аллея', 'Мид',
             'Ворота КТ', 'Сад', 'База Т', 'База КТ']
    for n in order:
        cl, fl = md.ZONES[n]
        poly = md.ZPOLY.get(n) or md.ZPOLY['Склад Б']
        name = n.replace(' (верх)', '').replace(' (2 этаж)', ', 2 этаж')
        lines.append('ZP %s|%d|%s' % (name, fl, ';'.join('%g,%g' % p for p in poly)))
    for c, x, z, r, lv in SITES: lines.append(f'SITE {c} {x} {z} {r} {lv}')
    for p in sp_t: lines.append('SPAWN T %d %d %d' % p[:3])
    for p in sp_ct: lines.append('SPAWN CT %d %d %d' % p[:3])
    for g, d in (('HOLD', hold), ('ENTRY', entry), ('POST', post)):
        for k, lst in d.items():
            for p in lst: lines.append('%s %s %d %d %d %d %d' % (g, k, *p))
    for x in range(GW):
        for z in range(GH):
            col = [pal.get(int(v), 0) for v in M[x, z]]
            if not walk[x, z]:
                top = max([i for i, v in enumerate(col) if v] or [-1])
                for i in range(top + 1, len(col)): col[i] = bar
            nzi = [i for i, v in enumerate(col) if v]
            if not nzi: continue
            top = nzi[-1]
            runs = []; i = 0
            while i <= top:
                v = col[i]; j = i
                while j <= top and col[j] == v: j += 1
                runs.append(f'{v}:{j - i}'); i = j
            lines.append(f'C {x} {z} ' + ' '.join(runs))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print('записано', out, len(lines), 'строк', os.path.getsize(out) // 1024, 'КБ')

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '../../src/main/resources/data/combo-rush/maps/medina.map')
