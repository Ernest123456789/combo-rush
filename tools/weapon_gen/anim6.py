"""Анимации дробовиков и MAC-10. draw/inspect — производные от Galil; shoot/reload дробовиков свои:
помпа — цевьё (foregrip) и затвор (bolt) ходят назад-вперёд; XM1014 — полуавтомат (только затвор);
перезарядка — вкладывание патронов по одному (покачивание ствола), у MAG-7 — смена коробчатого магазина."""
import copy
import anim4
from anim import An, ANIMS, Z
from anim4 import _derive

def _shotgun(gid, reload_len, pump=True, kick=1.0, tube_reload=True, draw_from='galil'):
    out = _derive(gid, 2.7, 1.0)                    # draw / inspect / shoot / reload от Galil
    # --- shoot
    cyc = 0.55 if pump else 0.12
    a = An(gid, 'shoot', 0.75 if pump else 0.28)
    a.pos('weapon', [(0, Z), (0.04, (0, 0.3 * kick, -2.4 * kick)), (0.16, (0, 0.1, -0.9 * kick)), (0.75 if pump else 0.28, Z)])
    a.rot('weapon', [(0, Z), (0.04, (8 * kick, 0.5, 1.0)), (0.2, (3 * kick, 0.2, 0.4)), (0.75 if pump else 0.28, Z)])
    if pump:
        a.pos('foregrip', [(0, Z), (0.22, Z), (0.34, (0, 0, -5.5)), (0.44, (0, 0, -5.5)), (0.58, Z)])
        a.pos('bolt', [(0, Z), (0.22, Z), (0.34, (0, 0, -4.5)), (0.44, (0, 0, -4.5)), (0.58, Z)])
        a.rot('weapon', [(0, Z), (0.04, (8 * kick, 0.5, 1.0)), (0.2, (3 * kick, 0.2, 0.4)), (0.34, (2, 0, 0.6)), (0.5, (0.5, 0, 0.2)), (0.75, Z)])
    else:
        a.pos('bolt', [(0, Z), (0.03, (0, 0, -3.5)), (0.12, Z)])
    a.pos('trigger', [(0, Z), (0.02, (0, 0, -0.6)), (0.14, Z)])
    out[a.key] = a.d
    # --- reload
    L = reload_len
    a = An(gid, 'reload', L)
    if tube_reload:
        a.pos('weapon', [(0, Z), (0.35, (0, -0.8, -1.0)), (L - 0.7, (0, -0.8, -1.0)), (L - 0.45, (0, -0.4, -0.6)), (L - 0.2, (0, 0, -0.3)), (L, Z)])
        a.rot('weapon', [(0, Z), (0.35, (14, -12, 22)), (L - 0.7, (14, -12, 22)), (L - 0.45, (8, -6, 10)), (L - 0.2, (3, 0, 3)), (L, Z)])
        t, k = 0.55, []
        while t < L - 0.9:                              # каждый патрон — короткий «толчок» оружия
            k += [(t, (14, -12, 22)), (t + 0.1, (16.5, -12, 23.5)), (t + 0.24, (14, -12, 22))]
            t += 0.5
        if k:
            a.rot('weapon', [(0, Z), (0.35, (14, -12, 22))] + k + [(L - 0.7, (14, -12, 22)), (L - 0.45, (8, -6, 10)), (L - 0.2, (3, 0, 3)), (L, Z)])
        if pump:
            a.pos('foregrip', [(0, Z), (L - 0.65, Z), (L - 0.5, (0, 0, -5.5)), (L - 0.34, (0, 0, -5.5)), (L - 0.2, Z)])
            a.pos('bolt', [(0, Z), (L - 0.65, Z), (L - 0.5, (0, 0, -4.5)), (L - 0.34, (0, 0, -4.5)), (L - 0.2, Z)])
        else:
            a.pos('bolt', [(0, Z), (L - 0.6, Z), (L - 0.5, (0, 0, -3.5)), (L - 0.38, Z)])
        out[a.key] = a.d
    else:
        r = out[f'animation.{gid}.reload']
        sc = L / r['animation_length']
        r['animation_length'] = L
        for b in r.get('bones', {}).values():
            for ch in b.values():
                old = dict(ch); ch.clear()
                for t_, v in old.items(): ch[f'{float(t_) * sc:.2f}'] = v
        # после смены магазина — передёрнуть помпу
        if pump:
            a.pos('foregrip', [])
        if pump:
            r.setdefault('bones', {})['foregrip'] = {'position': {'0.00': [0, 0, 0], f'{L - 0.6:.2f}': [0, 0, 0], f'{L - 0.45:.2f}': [0, 0, -5.5], f'{L - 0.3:.2f}': [0, 0, -5.5], f'{L - 0.15:.2f}': [0, 0, 0]}}
    for an in out.values():
        for b in an.get('bones', {}).values():
            for kind, ch in b.items():
                items = sorted(ch.items(), key=lambda kv: float(kv[0]))
                ch.clear(); ch.update(items)
    return out

ANIMS.update({
    'nova': lambda: _shotgun('nova', 3.6),
    'xm1014': lambda: _shotgun('xm1014', 3.4, pump=False, kick=0.85),
    'sawedoff': lambda: _shotgun('sawedoff', 3.8, kick=1.25),
    'mag7': lambda: _shotgun('mag7', 2.9, tube_reload=False, kick=1.0),
    'mac10': lambda: _derive('mac10', 2.2, 0.6, 0.14),
})


def _skeleton_anims():
    """Скелетный нож: доставание/осмотр/замахи как у обычного ножа"""
    from anim3 import knife_anims
    return {k.replace('animation.knife.', 'animation.skeleton.'): v for k, v in knife_anims().items()}
ANIMS['skeleton'] = _skeleton_anims
