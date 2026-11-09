from anim2 import *
import copy

def _rename(d, a, b):
    out = {}
    for k, v in d.items(): out[k.replace(a, b)] = copy.deepcopy(v)
    return out

def m4a4_anims():
    out = _rename(ak47_anims(), 'ak47', 'm4a4')
    r = out['animation.m4a4.reload']
    for t in r['bones']['magazine']['rotation']: r['bones']['magazine']['rotation'][t][0] *= 0.5
    out['animation.m4a4.shoot']['animation_length'] = 0.22
    return out

def m4a1s_anims():
    out = _rename(ak47_anims(), 'ak47', 'm4a1s')
    r = out['animation.m4a1s.reload']
    for t in r['bones']['magazine']['rotation']: r['bones']['magazine']['rotation'][t][0] *= 0.5
    # silenced: softer kick
    s = out['animation.m4a1s.shoot']['bones']['weapon']
    for t in s['position']: s['position'][t][2] *= 0.7
    for t in s['rotation']: s['rotation'][t][0] *= 0.7
    return out

def usp_anims():
    out = _rename(glock_anims(), 'glock18', 'usp')
    s = out['animation.usp.shoot']['bones']['weapon']
    for t in s['rotation']: s['rotation'][t][0] *= 0.8
    return out

def sil_toggle(gid, pivot_note=''):
    out = {}
    a = An(gid, 'sil_off', 1.3)
    a.pos('weapon', [(0, Z), (0.25, (-1, 0.5, -2)), (1.0, (-1, 0.5, -2)), (1.3, Z)])
    a.rot('weapon', [(0, Z), (0.25, (8, 35, -10)), (1.0, (8, 35, -10)), (1.3, Z)])
    a.rot('silencer', [(0, Z), (0.3, Z), (0.9, (0, 0, -720))])
    a.pos('silencer', [(0, Z), (0.3, Z), (0.9, (0, 0, 4)), (1.05, (0, -2, 9))])
    a.scl('silencer', [(0, (1, 1, 1)), (1.0, (1, 1, 1)), (1.06, (0.001, 0.001, 0.001)), (1.3, (0.001, 0.001, 0.001))])
    out[a.key] = a.d
    a = An(gid, 'sil_on', 1.3)
    a.pos('weapon', [(0, Z), (0.25, (-1, 0.5, -2)), (1.05, (-1, 0.5, -2)), (1.3, Z)])
    a.rot('weapon', [(0, Z), (0.25, (8, 35, -10)), (1.05, (8, 35, -10)), (1.3, Z)])
    a.scl('silencer', [(0, (0.001, 0.001, 0.001)), (0.35, (0.001, 0.001, 0.001)), (0.4, (1, 1, 1)), (1.3, (1, 1, 1))])
    a.pos('silencer', [(0, (0, -2, 9)), (0.4, (0, -2, 9)), (0.6, (0, 0, 4)), (1.0, Z)])
    a.rot('silencer', [(0, (0, 0, 720)), (0.6, (0, 0, 720)), (1.0, Z)])
    out[a.key] = a.d
    return out

def with_silencer_states(d, gid):
    """добавляет варианты *_ns (глушитель снят: масштаб кости silencer = 0), idle_ns и анимации снятия/установки"""
    out = dict(d)
    for k, v in list(d.items()):
        v2 = copy.deepcopy(v)
        L = v2['animation_length']
        v2['bones'].setdefault('silencer', {})['scale'] = {'0.00': [0.001] * 3, f'{L:.2f}': [0.001] * 3}
        out[k + '_ns'] = v2
    out[f'animation.{gid}.idle_ns'] = {'animation_length': 1.0, 'loop': True,
        'bones': {'silencer': {'scale': {'0.00': [0.001] * 3, '1.00': [0.001] * 3}}}}
    out.update(sil_toggle(gid))
    return out

def revolver_anims():
    out = {}
    a = An('revolver', 'draw', 0.8)
    a.pos('weapon', [(0, (2, -13, -5)), (0.3, (0.5, -4, -1.5)), (0.5, (0, 1.0, 0.5)), (0.65, (0, -0.4, 0)), (0.8, Z)])
    a.rot('weapon', [(0, (-65, 22, 22)), (0.3, (-15, 6, 7)), (0.5, (6, -2, -2)), (0.65, (-1.5, 0, 0)), (0.8, Z)])
    a.rot('cylinder', [(0, Z), (0.5, Z), (0.8, (0, 0, 120))])
    a.rot('hammer', [(0, Z), (0.55, Z), (0.62, (-25, 0, 0)), (0.75, Z)])
    out[a.key] = a.d
    a = An('revolver', 'shoot', 0.5)
    a.pos('weapon', [(0, Z), (0.03, (0, 0.6, -2.4)), (0.18, (0, 0.2, -1.0)), (0.5, Z)])
    a.rot('weapon', [(0, Z), (0.03, (15, 0, 1.5)), (0.2, (7, 0, 0.6)), (0.5, Z)])
    a.rot('hammer', [(0, (-40, 0, 0)), (0.02, Z), (0.06, Z), (0.2, (-40, 0, 0)), (0.3, (-40, 0, 0)), (0.5, Z)])
    a.rot('cylinder', [(0, Z), (0.2, Z), (0.3, (0, 0, 60)), (0.5, (0, 0, 60))])
    a.rot('trigger', [(0, Z), (0.02, (-18, 0, 0)), (0.12, Z)])
    out[a.key] = a.d
    a = An('revolver', 'reload', 3.4)
    a.pos('weapon', [(0, Z), (0.3, (0, 0.4, -1.5)), (2.8, (0, 0.4, -1.5)), (3.1, (0, -0.2, -0.5)), (3.4, Z)])
    a.rot('weapon', [(0, Z), (0.3, (8, -6, 12)), (0.8, (8, -6, 12)), (1.0, (12, -4, 20)), (2.2, (12, -4, 20)),
                     (2.5, (8, -6, 12)), (3.4, Z)])
    # cylinder swings out to the left, shakes, swings back
    a.rot('cylinder', [(0, Z), (0.5, Z), (0.9, (0, 0, -70)), (1.1, (0, 0, -70)), (1.3, (0, 0, -66)), (1.5, (0, 0, -70)),
                       (2.1, (0, 0, -70)), (2.3, (0, 0, -70)), (2.6, Z)])
    a.pos('cylinder', [(0, Z), (0.5, Z), (0.9, (-2, 0.5, 0)), (2.1, (-2, 0.5, 0)), (2.6, Z)])
    a.pos('FX_ejected_case_0', [(0, Z)])
    a.rot('hammer', [(0, Z), (2.7, Z), (2.8, (-35, 0, 0)), (3.0, (-35, 0, 0)), (3.1, Z)])
    out[a.key] = a.d
    a = An('revolver', 'inspect', 4.4)
    a.pos('weapon', [(0, Z), (0.5, (-1.5, 0.5, -3)), (1.1, (-2, 0.5, -3.5)), (1.8, (-2, 0.5, -3.5)), (2.5, (-1, 0.5, -3)),
                     (3.4, (-1, 0.5, -3)), (4.0, (0, 0, -0.5)), (4.4, Z)])
    a.rot('weapon', [(0, Z), (0.5, (-10, 22, -8)), (1.1, (5, 70, -12)), (1.8, (8, 62, -5)), (2.5, (0, -30, 8)),
                     (3.4, (-6, -65, 10)), (4.0, (0, -10, 2)), (4.4, Z)])
    a.rot('cylinder', [(0, Z), (1.2, Z), (1.8, (0, 0, 360)), (4.4, (0, 0, 360))])
    a.rot('hammer', [(0, Z), (2.6, Z), (2.7, (-40, 0, 0)), (3.0, (-40, 0, 0)), (3.1, Z)])
    out[a.key] = a.d
    return out

def claw_anims():
    out = {}
    a = An('claw', 'draw', 0.8)
    a.pos('weapon', [(0, (2, -12, -4)), (0.3, (0.5, -4, -1)), (0.5, (0, 1.0, 0.4)), (0.65, (0, -0.3, 0)), (0.8, Z)])
    a.rot('weapon', [(0, (-70, 30, 30)), (0.3, (-20, 10, 10)), (0.5, (8, -3, -3)), (0.65, (-1.5, 0, 0)), (0.8, Z)])
    a.rot('ring', [(0, (0, 0, 0)), (0.2, (0, 0, 0)), (0.7, (360, 0, 0)), (0.8, (360, 0, 0))])
    out[a.key] = a.d
    a = An('claw', 'attack', 0.4)
    a.pos('weapon', [(0, Z), (0.08, (1, 1, -2)), (0.18, (-1, -1, 4)), (0.4, Z)])
    a.rot('weapon', [(0, Z), (0.08, (40, -25, 12)), (0.18, (-65, 28, -18)), (0.4, Z)])
    out[a.key] = a.d
    a = An('claw', 'attack2', 0.42)
    a.pos('weapon', [(0, Z), (0.08, (-1, 1, -2)), (0.18, (1.5, -1, 4)), (0.42, Z)])
    a.rot('weapon', [(0, Z), (0.08, (30, 30, -40)), (0.18, (-55, -25, 35)), (0.42, Z)])
    out[a.key] = a.d
    a = An('claw', 'inspect', 4.0)
    a.pos('weapon', [(0, Z), (0.5, (-1, 0.5, -3)), (3.2, (-1, 0.5, -3)), (3.7, (0, 0, -0.5)), (4.0, Z)])
    a.rot('weapon', [(0, Z), (0.5, (-10, 20, -10)), (1.0, (10, 50, -20)), (1.6, (10, 50, 160)), (2.0, (10, 50, 340)),
                     (2.4, (0, -30, 360)), (2.8, (-10, -50, 380)), (3.2, (0, -20, 360)), (3.7, (0, 0, 360)), (4.0, (0, 0, 360))])
    # spin the knife on the finger ring
    a.rot('ring', [(0, Z), (2.2, Z), (2.8, (720, 0, 0)), (4.0, (720, 0, 0))])
    out[a.key] = a.d
    return out

ANIMS.update({'m4a4': m4a4_anims, 'm4a1s': lambda: with_silencer_states(m4a1s_anims(), 'm4a1s'),
              'usp': lambda: with_silencer_states(usp_anims(), 'usp'), 'revolver': revolver_anims, 'claw': claw_anims})

def knife_anims():
    out = {}
    a = An('knife', 'draw', 0.7)
    a.pos('weapon', [(0, (2, -12, -4)), (0.3, (0.5, -4, -1)), (0.5, (0, 1.0, 0.4)), (0.7, Z)])
    a.rot('weapon', [(0, (-70, 30, 30)), (0.3, (-20, 10, 10)), (0.5, (8, -3, -3)), (0.7, Z)])
    out[a.key] = a.d
    a = An('knife', 'attack', 0.4)
    a.pos('weapon', [(0, Z), (0.08, (1, 1, -2)), (0.18, (-1, -1, 4)), (0.4, Z)])
    a.rot('weapon', [(0, Z), (0.08, (40, -25, 12)), (0.18, (-65, 28, -18)), (0.4, Z)])
    out[a.key] = a.d
    a = An('knife', 'attack2', 0.42)
    a.pos('weapon', [(0, Z), (0.08, (-1, 1, -2)), (0.2, (1.5, -1, 4)), (0.42, Z)])
    a.rot('weapon', [(0, Z), (0.08, (30, 30, -40)), (0.2, (-55, -25, 35)), (0.42, Z)])
    out[a.key] = a.d
    a = An('knife', 'inspect', 3.6)
    a.pos('weapon', [(0, Z), (0.5, (-1, 0.5, -3)), (2.9, (-1, 0.5, -3)), (3.3, (0, 0, -0.5)), (3.6, Z)])
    a.rot('weapon', [(0, Z), (0.5, (-10, 20, -10)), (1.1, (10, 50, -20)), (1.7, (10, 50, 160)), (2.2, (0, -30, 360)),
                     (2.6, (-10, -50, 400)), (2.9, (0, -20, 360)), (3.3, (0, 0, 360)), (3.6, (0, 0, 360))])
    out[a.key] = a.d
    return out
ANIMS['knife'] = knife_anims


def bayonet_anims():
    """Штык: те же доставание/осмотр, что у ножа, но выпады колющие"""
    out = {k.replace('animation.knife.', 'animation.bayonet.'): v for k, v in knife_anims().items()}
    a = An('bayonet', 'attack', 0.4)
    a.pos('weapon', [(0, Z), (0.1, (0.5, 0.5, -3)), (0.2, (0, -0.5, 6)), (0.4, Z)])
    a.rot('weapon', [(0, Z), (0.1, (12, -8, 6)), (0.2, (-8, 6, -4)), (0.4, Z)])
    out[a.key] = a.d
    return out
ANIMS['bayonet'] = bayonet_anims
