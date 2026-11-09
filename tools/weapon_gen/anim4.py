"""Анимации Famas / MP9 / MP7: производные от Galil (те же имена костей), с подгонкой по времени и силе отдачи."""
import copy
import anim3                        # регистрирует остальные ANIMS
from anim import ANIMS
from anim2 import galil_anims

def _scale_time(a, f):
    a['animation_length'] = round(a['animation_length'] * f, 3)
    for b in a.get('bones', {}).values():
        for ch in b.values():
            old = dict(ch); ch.clear()
            for t, v in old.items(): ch[f'{float(t) * f:.2f}'] = v

def _scale_vals(a, bone, kind, k):
    ch = a['bones'].get(bone, {}).get(kind)
    if not ch: return
    for t, v in ch.items(): ch[t] = [round(x * k, 3) for x in v]

def _derive(gid, reload_len, recoil_k, shoot_len=None):
    out = {}
    for k, v in galil_anims().items():
        out[k.replace('galil', gid)] = copy.deepcopy(v)
    r = out[f'animation.{gid}.reload']; _scale_time(r, reload_len / r['animation_length'])
    s = out[f'animation.{gid}.shoot']
    for kind in ('position', 'rotation'): _scale_vals(s, 'weapon', kind, recoil_k)
    if shoot_len: _scale_time(s, shoot_len / s['animation_length'])
    return out

ANIMS.update({
    'famas': lambda: _derive('famas', 2.7, 0.8),
    'mp9': lambda: _derive('mp9', 2.2, 0.55, 0.2),
    'mp7': lambda: _derive('mp7', 2.5, 0.6, 0.22),
    'scar20': lambda: _derive('scar20', 3.0, 1.5, 0.35),
    'taser': lambda: _derive('taser', 2.4, 1.1, 0.3),
})
