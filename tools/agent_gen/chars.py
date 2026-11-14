#!/usr/bin/env python3
from agents import *

def eyes_fn(a, c, w, h, col):
    """глаза в прорези балаклавы (грань лица 32×32: a — слева направо, c — сверху вниз)"""
    skin = hexc('#b98a68')
    # прорезь: c 11..17, a 3..28
    if not (3 <= a <= 28 and 11 <= c <= 17): return None
    k = 1.0
    if c == 11 or a in (3, 28): k = 0.78                      # тень от края ткани
    if c == 12: k = 0.86
    if 14 <= a <= 17: k *= 1.06                               # переносица
    base = mul(skin, k * (1 + (hsh(a, c, 9) - 0.5) * 0.05))
    for ex in (6, 19):                                        # левый и правый глаз (по 7 px)
        if ex <= a <= ex + 6:
            if c == 13: return hexc('#3b2a20') if ex < a < ex + 6 else mul(skin, 0.7)       # верхнее веко/ресницы
            if c == 14:
                if a in (ex, ex + 6): return mul(skin, 0.75)
                if ex + 2 <= a <= ex + 4: return hexc('#20160f') if a == ex + 3 else hexc('#5a3d24')
                return hexc('#e8e2d8')
            if c == 15:
                if ex + 1 <= a <= ex + 5:
                    if ex + 2 <= a <= ex + 4: return hexc('#4a3220')
                    return hexc('#cfc8bc')
                return mul(skin, 0.8)
            if c == 16: return mul(skin, 0.86)                 # тень под глазом
    return base

def lens_fn(a, c, w, h, col):
    """очки КТ: две янтарные линзы с бликом (грань 36×8)"""
    if c in (0, h - 1) or a in (0, w - 1): return None
    left = 2 <= a <= 16; right = 19 <= a <= 33
    if not (left or right) or not (1 <= c <= 6): return None
    lx = a - (2 if left else 19)
    if c in (1, 6) and lx in (0, 14): return None             # скруглённые углы
    t = (c - 1) / 5
    lc = mix(hexc('#f0b450'), hexc('#6a3c12'), t)
    if (lx - c * 2) % 13 in (2, 3) and c < 5: lc = mix(lc, (255, 245, 220), 0.55)
    if c == 1: lc = mix(lc, (255, 240, 200), 0.35)
    return lc

def laces(a, c, w, h, col):
    """шнурки на передней грани ботинка: только верх (над носком)"""
    if c > 6 or c < 1: return None
    mid = w // 2
    if a in (mid - 3, mid + 2) and c % 2 == 1: return hexc('#8d939a')       # люверсы
    if mid - 2 <= a <= mid + 1 and (a + c) % 3 == 0: return hexc('#1a1a1a')
    return None

# ====================================================================== КТ
def ct():
    for n, c, k in [('uni', '#334063', 'ripstop'), ('uni_d', '#28324c', 'ripstop'), ('pants', '#414b60', 'ripstop'), ('pants_d', '#343c4e', 'ripstop'),
                    ('gear', '#303236', 'cordura'), ('gear_l', '#42454b', 'cordura'), ('helm', '#363b42', 'poly'), ('helm_d', '#24272b', 'poly'),
                    ('bala', '#1c1e21', 'knit'), ('frame', '#17181a', 'poly'), ('olive', '#575c43', 'poly'), ('olive_d', '#3f432f', 'poly'),
                    ('rubber', '#1d1e20', 'rubber'), ('glove', '#2c2d30', 'leather'), ('glove_d', '#1d1e21', 'rubber'), ('boot', '#25231f', 'leather'),
                    ('sole', '#121212', 'rubber'), ('metal', '#8d939a', 'metal'), ('steel_d', '#474c52', 'metal'), ('mag', '#28292b', 'poly'),
                    ('velcro', '#5d6167', 'velcro'), ('blue', '#2f62b8', 'cordura'), ('tq', '#2a2a2a', 'cordura'), ('red', '#9a2b25', 'cordura')]:
        M(n, c, k)
    m = Model('ct')
    B = m.box
    # ---------------- голова
    h = B('head', (-4, 4), (-8, 0), (-4, 4), 'bala', 0.92, name='head')
    h.d('n', 'shade', (-0.5, 0.5), (-3.5, -2.0), 1.12)                       # нос под тканью
    h.d('n', 'shade', (-1.5, 1.5), (-1.75, -0.75), 0.7)                      # дыхательная сетка
    for i in range(5): h.d('n', 'px', -1.25 + i * 0.5 + 0.25, -1.25, '#3a3d42')
    h.d('n', 'shade', (-4, 4), (-0.5, 0), 0.85)
    g = B('head', (-4.5, 4.5), (-5.5, -3.5), (-4.75, -4.0), 'frame', 0.7, name='goggles')
    g.d('n', 'fn', lens_fn)
    g.d('u', 'shade', (-4.5, 4.5), (-4.75, -4.0), 1.2)
    B('head', (-4.25, 4.25), (-5.25, -4.25), (-4.25, 4.25), 'gear', 0.8, name='gstrap').d('w', 'hline', (-4.25, 4.25), -4.75, 0.75, 2).d('e', 'hline', (-4.25, 4.25), -4.75, 0.75, 2)
    sh = B('head', (-4.5, 4.5), (-9, -5.75), (-4.5, 4.75), 'helm', 0.72, name='shell')
    sh.d('n', 'hline', (-4.5, 4.5), -6.0, 0.6).d('n', 'shade', (-4.5, 4.5), (-6.0, -5.75), 0.8)
    for f in ('w', 'e'):
        sh.d(f, 'fill', (-2.5, 2.5), (-8.75, -8.0), 'velcro', True, 0.7)
        sh.d(f, 'px', -3.5, -6.25, '#9aa0a8').d(f, 'px', 3.25, -6.25, '#9aa0a8')
    sh.d('s', 'hline', (-4.5, 4.5), -6.0, 0.7)
    d = B('head', (-3.75, 3.75), (-9.5, -9), (-3.75, 4.0), 'helm', 0.8, name='dome')
    d.d('u', 'fill', (-1.75, 1.75), (-1.25, 2.25), 'velcro', True, 0.7)
    d.d('u', 'fill', (-0.75, 0.75), (-3.25, -2.25), 'velcro', True, 0.7)
    B('head', (-4.5, 4.5), (-5.75, -4.0), (1.25, 4.75), 'helm', 0.72, name='skirt').d('s', 'shade', (-4.5, 4.5), (-4.5, -4.0), 0.75)
    r = B('head', (4.5, 5.0), (-7.75, -6.75), (-2.75, 2.75), 'helm_d', 0.75, name='rail')
    r.d('e', 'fn', lambda a, c, w, hh, col: mul(col, 0.45) if a % 4 in (1, 2) and c in (1, 2) else None)
    m.mirror(r, 'head')
    s = B('head', (-1.25, 1.25), (-8.25, -6.75), (-5.0, -4.5), 'steel_d', 0.7, name='shroud')
    s.d('n', 'color', (-0.5, 0.5), (-7.75, -7.25), '#121314').d('n', 'px', -1.0, -8.0, '#b8bec6').d('n', 'px', 0.75, -8.0, '#b8bec6')
    s.d('u', 'color', (-0.25, 0.25), (-4.75, -4.5), '#b8bec6')
    cup = B('head', (3.75, 5.25), (-5.5, -2.5), (-1.5, 1.5), 'olive', 0.72, name='cup')
    cup.d('e', 'fill', (-1.0, 1.0), (-5.0, -3.0), 'olive_d', True, 0.75).d('e', 'px', -0.25, -4.25, '#8b9070')
    m.mirror(cup, 'head')
    arm = B('head', (4.5, 5.0), (-6.75, -5.5), (-0.5, 0.5), 'gear', 0.8, name='cuparm')
    m.mirror(arm, 'head')
    B('head', (5.25, 5.75), (-3.25, -2.75), (-4.25, -0.5), 'gear', 0.75, name='boom')
    B('head', (1.25, 5.75), (-3.25, -2.75), (-4.75, -4.25), 'gear', 0.75, name='boom2')
    B('head', (0.5, 1.5), (-3.5, -2.25), (-5.0, -4.5), 'rubber', 0.8, name='mic').d('n', 'shade', (0.5, 1.5), (-3.5, -2.25), 1.25)
    cw = B('head', (-2.25, 2.25), (-8.0, -5.75), (4.75, 5.5), 'gear', 0.7, name='cw')
    cw.d('s', 'molle', (-2.25, 2.25), (-8.0, -5.75)).d('s', 'stitch', (-2.25, 2.25), (-8.0, -5.75), 0.7)
    st = B('head', (0.75, 1.75), (-10.0, -9.5), (1.75, 2.75), 'rubber', 0.8, name='strobe')
    st.d('u', 'color', (1.0, 1.5), (2.0, 2.5), '#3c7a3c')

    # ---------------- торс
    t = B('body', (-4, 4), (0, 12), (-2, 2), 'uni', 0.92, name='torso')
    t.d('n', 'fill', (-2.0, 2.0), (0, 0.75), 'uni_d', True, 0.8)
    t.d('s', 'fill', (-2.0, 2.0), (0, 0.75), 'uni_d', True, 0.8)
    for f in ('n', 's'): t.d(f, 'fill', (-4, 4), (11.5, 12), 'pants', False)
    for f in ('w', 'e'): t.d(f, 'fill', (-2, 2), (11.5, 12), 'pants', False)
    t.d('n', 'zip', -0.25, (0.75, 9.75))
    pf = B('body', (-3.75, 3.75), (1.0, 9.0), (-3.0, -2.0), 'gear', 0.7, name='plate_f')
    pf.d('n', 'molle', (-3.5, 3.5), (4.5, 8.75)).d('n', 'stitch', (-3.75, 3.75), (1.0, 9.0), 0.72)
    pf.d('n', 'hline', (-3.75, 3.75), 1.25, 1.2)
    pb = B('body', (-3.75, 3.75), (1.0, 8.75), (2.0, 3.0), 'gear', 0.7, name='plate_b')
    pb.d('s', 'molle', (-3.5, 3.5), (1.5, 8.5)).d('s', 'stitch', (-3.75, 3.75), (1.0, 8.75), 0.72)
    cm = B('body', (-4.25, 4.25), (5.5, 8.5), (-2.5, 2.5), 'gear', 0.75, name='cumm')
    for f in ('w', 'e'): cm.d(f, 'molle', (-2.5, 2.5), (5.75, 8.5))
    sp = B('body', (1.5, 3.5), (-0.25, 1.25), (-2.75, 2.75), 'gear', 0.75, name='strap')
    sp.d('n', 'color', (2.0, 3.0), (0.25, 0.75), '#55595f').d('u', 'stitch', (1.5, 3.5), (-2.75, 2.75), 0.75)
    m.mirror(sp, 'body')
    for i, (x0, x1) in enumerate([(-3.5, -1.25), (-1.0, 1.0), (1.25, 3.5)]):
        p = B('body', (x0, x1), (4.75, 8.75), (-4.0, -3.0), 'gear', 0.7, name='pouch%d' % i)
        p.d('n', 'stitch', (x0, x1), (4.75, 8.75), 0.72).d('n', 'fill', (x0, x1), (5.25, 5.75), 'rubber', True, 0.7)
        p.d('n', 'shade', (x0, x1), (8.25, 8.75), 0.8)
        mg = B('body', (x0 + 0.5, x1 - 0.5), (3.5, 4.75), (-3.75, -3.25), 'mag', 0.7, name='mag%d' % i)
        mg.d('n', 'hline', (x0 + 0.5, x1 - 0.5), 3.75, 1.3).d('u', 'shade', (x0 + 0.5, x1 - 0.5), (-3.75, -3.25), 1.25)
    ad = B('body', (-2.0, 2.0), (1.75, 4.5), (-3.5, -3.0), 'gear', 0.7, name='admin')
    ad.d('n', 'fill', (-1.75, 1.75), (2.0, 3.5), 'velcro', True, 0.75)
    ad.d('n', 'fill', (-1.5, 1.5), (2.25, 3.25), 'blue', True, 0.75)
    ad.d('n', 'text', -1.0, 2.5, 'КТ', '#f2f2f2')
    ad.d('n', 'zip', -1.75, (3.75, 4.25), '#6d7178').d('n', 'hline', (-1.75, 1.75), 3.875, 0.6)
    tq = B('body', (-3.5, -2.25), (1.25, 3.5), (-3.5, -3.0), 'tq', 0.7, name='tq')
    tq.d('n', 'fill', (-3.5, -2.25), (2.25, 2.75), 'red', True, 0.75)
    pt = B('body', (2.25, 3.5), (1.5, 3.0), (-3.5, -3.0), 'olive', 0.7, name='ptt')
    pt.d('n', 'color', (2.5, 3.25), (1.75, 2.5), '#24261c').d('n', 'px', 2.75, 2.0, '#8b9070')
    pk = B('body', (-3.25, 1.75), (1.5, 8.25), (3.0, 4.25), 'gear', 0.7, name='pack')
    pk.d('s', 'molle', (-3.0, 1.5), (3.0, 8.0)).d('s', 'stitch', (-3.25, 1.75), (1.5, 8.25), 0.72)
    pk.d('s', 'zip', 1.0, (1.75, 8.0), '#6d7178')
    B('body', (2.0, 3.5), (2.5, 6.5), (3.0, 5.0), 'gear', 0.7, name='rpouch').d('s', 'stitch', (2.0, 3.5), (2.5, 6.5), 0.72)
    rd = B('body', (2.25, 3.25), (2.0, 2.5), (3.75, 4.75), 'steel_d', 0.7, name='radio')
    rd.d('u', 'px', 2.5, 4.25, '#c23a2a')
    B('body', (2.5, 3.0), (-3.5, 2.0), (4.0, 4.5), 'rubber', 0.85, name='ant')
    B('body', (2.25, 3.25), (-4.0, -3.5), (3.75, 4.75), 'rubber', 0.8, name='antk')
    bl = B('body', (-4.25, 4.25), (10.0, 11.5), (-2.5, 2.5), 'gear', 0.75, name='belt')
    for f in ('n', 's'): bl.d(f, 'hline', (-4.25, 4.25), 10.25, 0.75, 2).d(f, 'hline', (-4.25, 4.25), 11.0, 0.75, 2)
    for f in ('w', 'e'): bl.d(f, 'hline', (-2.5, 2.5), 10.25, 0.75, 2).d(f, 'hline', (-2.5, 2.5), 11.0, 0.75, 2)
    bk = B('body', (-1.0, 1.0), (10.25, 11.25), (-2.75, -2.5), 'metal', 0.6, name='buckle')
    bk.d('n', 'color', (-0.5, 0.5), (10.5, 11.0), '#3a3d41')
    B('body', (2.25, 3.75), (9.25, 11.0), (-3.0, -2.25), 'gear', 0.7, name='pmag').d('n', 'stitch', (2.25, 3.75), (9.25, 11.0), 0.72)
    ik = B('body', (-1.75, 1.75), (9.5, 11.75), (2.25, 3.25), 'gear', 0.7, name='ifak')
    ik.d('s', 'stitch', (-1.75, 1.75), (9.5, 11.75), 0.72).d('s', 'fill', (-1.75, 1.75), (9.5, 10.0), 'red', True, 0.75)

    # ---------------- руки (левая, правая — зеркально)
    a = B('left_arm', (-1, 3), (-2, 10), (-2, 2), 'uni', 0.92, name='arm')
    a.d('e', 'hline', (-2, 2), 4.5, 0.75, 2).d('s', 'hline', (-1, 3), 4.5, 0.75, 2)
    a.d('u', 'shade', (-1, 3), (-2, 2), 1.05)
    pc = B('left_arm', (3.0, 3.5), (-1.0, 3.25), (-1.75, 1.75), 'uni_d', 0.75, name='spocket')
    pc.d('e', 'fill', (-1.5, 1.5), (-0.75, 1.0), 'velcro', True, 0.75)
    pc.d('e', 'fill', (-1.25, 1.25), (-0.5, 0.75), 'blue', True, 0.75)
    pc.d('e', 'stitch', (-1.75, 1.75), (1.5, 3.25), 0.72)
    el = B('left_arm', (-0.75, 3.25), (3.5, 6.25), (-1.25, 2.5), 'rubber', 0.72, name='elbow')
    for f, hr in (('e', (-1.25, 2.5)), ('s', (-0.75, 3.25))):
        el.d(f, 'fill', hr, (4.0, 5.75), 'gear_l', True, 0.75)
    cf = B('left_arm', (-1.5, 3.5), (6.75, 7.75), (-2.5, 2.5), 'glove_d', 0.75, name='cuff')
    gl = B('left_arm', (-1.25, 3.25), (7.75, 10.25), (-2.25, 2.25), 'glove', 0.82, name='glove')
    for zz in (-0.75, 0.25, 1.25): gl.d('e', 'vline', zz, (9.25, 10.25), 0.6)
    gl.d('n', 'fill', (-1.0, 0.75), (8.25, 10.0), 'glove_d', True, 0.7)          # большой палец
    gl.d('d', 'shade', (-1.25, 3.25), (-2.25, 2.25), 0.8)
    kn = B('left_arm', (3.25, 3.5), (8.0, 9.25), (-1.75, 1.75), 'glove_d', 0.75, name='knuck')
    for bb in (a, pc, el, cf, gl, kn): m.mirror(bb, 'right_arm')
    pc.d('e', 'text', -1.0, -0.4, 'КТ', '#f2f2f2')
    m.boxes[-5].d('w', 'text', 1.0, -0.4, 'КТ', '#f2f2f2')

    # ---------------- ноги (левая, правая — зеркально)
    lg = B('left_leg', (-2, 2), (0, 9), (-2, 2), 'pants', 0.92, name='leg')
    lg.d('e', 'vline', 0, (0, 9), 0.8, 2).d('n', 'hline', (-2, 2), 0.5, 0.8, 2)
    for f, hr in (('s', (-2, 2)), ('e', (-2, 2)), ('w', (-2, 2))): lg.d(f, 'fill', hr, (5.25, 6.0), 'gear', True, 0.75)
    lg.d('n', 'shade', (-2, 2), (3.5, 4.0), 0.85)
    kp = B('left_leg', (-1.5, 2.25), (4.0, 7.25), (-2.75, -1.75), 'rubber', 0.7, name='knee')
    kp.d('n', 'fill', (-1.25, 2.0), (4.25, 6.75), 'gear_l', True, 0.7).d('n', 'hline', (-1.25, 2.0), 5.5, 0.7)
    bu = B('left_leg', (-1.75, 2.5), (8.5, 9.25), (-2.5, 2.5), 'pants_d', 0.8, name='blouse')
    bo = B('left_leg', (-2.0, 2.25), (9.0, 11.5), (-2.25, 2.25), 'boot', 0.82, name='boot')
    bo.d('n', 'fn', laces).d('s', 'fill', (-2.0, 2.25), (9.0, 10.25), 'glove_d', True, 0.75)
    to = B('left_leg', (-2.0, 2.25), (10.5, 11.5), (-2.75, -2.25), 'boot', 0.75, name='toe')
    to.d('u', 'shade', (-2.0, 2.25), (-2.75, -2.25), 1.15)
    so = B('left_leg', (-1.75, 2.5), (11.5, 12.0), (-3.0, 2.5), 'sole', 0.8, name='sole')
    for bb in (lg, kp, bu, bo, to, so): m.mirror(bb, 'right_leg')
    cg = B('left_leg', (1.75, 2.5), (1.25, 5.0), (-1.5, 1.25), 'pants', 0.75, name='cargo')
    cg.d('e', 'fill', (-1.5, 1.25), (1.25, 2.25), 'pants_d', True, 0.7).d('e', 'stitch', (-1.5, 1.25), (2.25, 5.0), 0.75)
    ho = B('right_leg', (-2.75, -2.0), (0.5, 5.0), (-1.5, 1.25), 'gear', 0.7, name='holster')
    ho.d('w', 'stitch', (-1.5, 1.25), (0.5, 5.0), 0.72).d('w', 'fill', (-1.25, 1.0), (1.0, 2.0), 'velcro', True, 0.75)
    B('right_leg', (-2.75, -2.25), (-1.25, 0.5), (-0.25, 1.25), 'mag', 0.75, name='pgrip').d('w', 'hline', (-0.25, 1.25), -0.5, 1.3)
    ls = B('right_leg', (-2.25, 1.75), (3.25, 3.75), (-2.25, 2.25), 'gear', 0.75, name='lstrap')
    return m

# ====================================================================== Т
def tt():
    for n, c, k in [('jacket', '#8a7656', 'canvas'), ('jacket_d', '#6c5b40', 'canvas'), ('hoodie', '#3c3a39', 'twill'),
                    ('rig', '#5a5a3c', 'canvas'), ('rig_d', '#46462f', 'canvas'), ('akmag', '#8c4b22', 'poly'),
                    ('pants', '#4e493c', 'twill'), ('pants_d', '#3d392f', 'twill'), ('dboot', '#6b4a2c', 'leather'), ('dsole', '#2a221a', 'rubber'),
                    ('beanie', '#242424', 'rib'), ('cuffk', '#282828', 'knit'), ('bala', '#2b2b2c', 'knit'), ('skin', '#b98a68', 'skin'),
                    ('shem', '#9e2a26', 'shemagh'), ('glove', '#1f1f1f', 'leather'), ('band', '#b02a22', 'cordura'), ('belt', '#4a3322', 'leather'),
                    ('metal', '#8d939a', 'metal'), ('knee', '#2e2e2b', 'rubber')]:
        M(n, c, k)
    m = Model('t')
    B = m.box
    # ---------------- голова
    h = B('head', (-4, 4), (-8, 0), (-4, 4), 'bala', 0.92, name='head')
    h.d('n', 'fn', eyes_fn)
    h.d('n', 'shade', (-4, 4), (-3.5, -3.25), 0.8)
    be = B('head', (-4.25, 4.25), (-8.5, -6.0), (-4.25, 4.25), 'beanie', 0.85, name='beanie')
    for f in ('n', 's'): be.d(f, 'shade', (-4.25, 4.25), (-8.5, -8.0), 1.1)
    cu = B('head', (-4.5, 4.5), (-6.5, -5.5), (-4.5, 4.5), 'cuffk', 0.72, name='bcuff')
    cu.d('n', 'fill', (2.25, 3.5), (-6.25, -5.75), 'rig_d', True, 0.8)        # нашивка-ярлык
    B('head', (-3.5, 3.5), (-8.75, -8.5), (-3.5, 3.5), 'beanie', 0.85, name='btop')
    sc = B('head', (-4.5, 4.5), (-3.25, 0.5), (-4.5, 4.5), 'shem', 0.8, name='scarf')
    for f, hr in (('n', (-4.5, 4.5)), ('s', (-4.5, 4.5)), ('w', (-4.5, 4.5)), ('e', (-4.5, 4.5))):
        sc.d(f, 'hline', hr, -2.75, 0.72, 3).d(f, 'hline', hr, -0.25, 0.72, 3)
        sc.d(f, 'shade', hr, (-3.25, -3.0), 1.12)
    sc.d('n', 'shade', (-1.0, 1.0), (-3.0, -2.0), 1.08)                       # нос под платком
    s2 = B('head', (-4.75, 4.75), (-1.75, -0.75), (-4.75, 4.75), 'shem', 0.72, name='scarf2')
    s2.d('w', 'fill', (-1.5, 0.5), (-1.75, 0.0), 'shem', True, 0.7)           # узел сбоку

    # ---------------- торс
    t = B('body', (-4, 4), (0, 12), (-2, 2), 'jacket', 0.92, name='torso')
    t.d('n', 'zip', -0.25, (0.5, 12), '#7a7a72')
    for x0 in (-3.5, 1.5):
        t.d('n', 'fill', (x0, x0 + 2.0), (1.0, 1.75), 'jacket_d', True, 0.75)
        t.d('n', 'stitch', (x0, x0 + 2.0), (1.75, 3.5), 0.78)
    t.d('n', 'hline', (-4, 4), 11.5, 0.75, 2).d('s', 'hline', (-4, 4), 11.5, 0.75, 2)
    t.d('s', 'vline', -0.25, (0, 12), 0.82, 2)
    t.d('s', 'shade', (-4, 4), (0, 0.5), 0.85)
    for f in ('w', 'e'): t.d(f, 'vline', 0, (0, 12), 0.8, 2)
    ho = B('body', (-3.0, 3.0), (-0.5, 2.5), (2.0, 3.25), 'hoodie', 0.8, name='hood')
    ho.d('s', 'hline', (-3.0, 3.0), 1.25, 0.75).d('s', 'shade', (-3.0, 3.0), (2.0, 2.5), 0.8)
    ho.d('u', 'color', (-2.25, 2.25), (2.25, 3.0), '#1b1b1b')
    for (x0, x1, y0, y1) in ((-2.5, 2.5, 0.5, 1.75), (-1.5, 1.5, 1.75, 2.75), (-0.5, 0.5, 2.75, 3.5)):
        dr = B('body', (x0, x1), (y0, y1), (-2.75, -2.0), 'shem', 0.75, name='drape')
    B('body', (0.0, 1.75), (0.5, 4.0), (3.25, 3.75), 'shem', 0.75, name='tail').d('s', 'hline', (0.0, 1.75), 3.5, 0.7)
    rg = B('body', (-3.75, 3.75), (3.5, 8.25), (-2.75, -2.0), 'rig', 0.75, name='rig')
    rg.d('n', 'stitch', (-3.75, 3.75), (3.5, 8.25), 0.75)
    st = B('body', (-3.25, -2.0), (-0.25, 3.5), (-2.5, 2.25), 'rig', 0.78, name='rstrap')
    st.d('n', 'color', (-3.0, -2.25), (2.0, 2.75), '#2a2a22').d('n', 'px', -2.75, 2.25, '#8d939a')
    m.mirror(st, 'body')
    B('body', (-0.5, 0.5), (0.5, 8.5), (2.0, 2.5), 'rig', 0.78, rot=((0, 4.5, 0), (0, 0, 0.6435)), name='x1')
    B('body', (-0.5, 0.5), (0.5, 8.5), (2.0, 2.75), 'rig', 0.78, rot=((0, 4.5, 0), (0, 0, -0.6435)), name='x2')
    B('body', (-4.25, 4.25), (6.5, 7.25), (-2.25, 2.25), 'rig_d', 0.75, name='rband')
    for i, (x0, x1) in enumerate([(-3.5, -1.5), (-1.0, 1.0), (1.5, 3.5)]):
        p = B('body', (x0, x1), (4.75, 8.25), (-3.75, -2.75), 'rig', 0.7, name='tp%d' % i)
        p.d('n', 'stitch', (x0, x1), (4.75, 8.25), 0.72).d('n', 'vline', (x0 + x1) / 2 - 0.125, (5.5, 8.25), 0.8, 2)
        p.d('n', 'shade', (x0, x1), (7.75, 8.25), 0.8)
        if i != 1:
            fl = B('body', (x0 - 0.25, x1 + 0.25), (4.0, 5.25), (-4.0, -3.0), 'rig_d', 0.7, name='flap%d' % i)
            fl.d('n', 'px', (x0 + x1) / 2 - 0.125, 4.75, '#8d939a').d('n', 'stitch', (x0 - 0.25, x1 + 0.25), (4.0, 5.25), 0.75)
    mg = B('body', (-0.75, 0.75), (2.75, 4.75), (-3.5, -3.0), 'akmag', 0.72, name='akmag')
    for yy in (3.25, 3.75, 4.25): mg.d('n', 'hline', (-0.75, 0.75), yy, 0.75)
    m2 = B('body', (-0.75, 0.75), (2.0, 2.75), (-3.75, -3.25), 'akmag', 0.72, name='akmag2')
    m2.d('u', 'shade', (-0.75, 0.75), (-3.75, -3.25), 1.2).d('u', 'color', (-0.25, 0.25), (-3.75, -3.5), '#c9a43a')

    def bullets(a, c, w, hh, col):
        if c % 3 == 2: return None
        if 1 <= a <= w - 3: return mul(hexc('#c9a43a'), 1.15 if c % 3 == 0 else 0.85)
        if a == w - 2: return mul(hexc('#b0683a'), 1.1 if c % 3 == 0 else 0.8)
        return None
    bd = B('body', (-0.75, 0.75), (-1.0, 12.0), (-2.25, 2.25), 'belt', 0.75, rot=((0, 5.5, 0), (0, 0, 0.53)), name='bando')
    bd.d('n', 'fn', bullets).d('s', 'fn', bullets)
    bl = B('body', (-4.25, 4.25), (9.75, 11.0), (-2.5, 2.5), 'belt', 0.75, name='belt')
    for f in ('n', 's'): bl.d(f, 'hline', (-4.25, 4.25), 10.0, 0.8, 2)
    bk = B('body', (-0.75, 0.75), (9.5, 11.25), (-2.75, -2.5), 'metal', 0.6, name='buckle')
    bk.d('n', 'color', (-0.5, 0.5), (9.75, 11.0), '#5a5f66').d('n', 'color', (-0.5, 0.5), (10.25, 10.5), '#b8bec6')
    bp = B('body', (-2.75, 1.25), (8.75, 11.5), (2.25, 3.5), 'rig', 0.7, name='butt')
    bp.d('s', 'fill', (-2.75, 1.25), (8.75, 9.75), 'rig_d', True, 0.75).d('s', 'stitch', (-2.75, 1.25), (9.75, 11.5), 0.72)
    bp.d('s', 'px', -0.875, 9.5, '#8d939a')

    # ---------------- руки
    a = B('left_arm', (-1, 3), (-2, 10), (-2, 2), 'jacket', 0.92, name='arm')
    for f, hr in (('n', (-1, 3)), ('s', (-1, 3)), ('w', (-2, 2)), ('e', (-2, 2))):
        a.d(f, 'fill', hr, (8.75, 10), 'skin', False)
        a.d(f, 'hline', hr, -1.75, 0.8, 2)
    a.d('d', 'fill', (-1, 3), (-2, 2), 'skin', False)
    for zz in (-1.0, 0.0, 1.0): a.d('e', 'vline', zz, (8.75, 10), 0.75)
    for xx in (0.0, 1.0, 2.0): a.d('n', 'vline', xx, (8.75, 10), 0.75)
    a.d('e', 'fill', (-1.5, 1.5), (3.5, 5.5), 'jacket_d', True, 0.8).d('e', 'stitch', (-1.5, 1.5), (3.5, 5.5), 0.75)
    cf = B('left_arm', (-1.25, 3.25), (6.0, 6.75), (-2.25, 2.25), 'jacket_d', 0.75, name='cuff')
    gl = B('left_arm', (-1.25, 3.25), (6.75, 8.75), (-2.25, 2.25), 'glove', 0.82, name='glove')
    gl.d('e', 'hline', (-2.25, 2.25), 8.0, 0.7).d('e', 'fill', (-1.5, 1.5), (7.0, 7.75), 'knee', True, 0.7)
    for bb in (a, cf, gl): m.mirror(bb, 'right_arm')
    ab = B('left_arm', (-1.25, 3.25), (0.5, 1.75), (-2.25, 2.25), 'band', 0.75, name='armband')
    ab.d('e', 'stitch', (-2.25, 2.25), (0.5, 1.75), 0.72).d('e', 'px', 0.0, 1.0, '#c9c9c9').d('e', 'px', 0.25, 1.0, '#c9c9c9')

    # ---------------- ноги
    lg = B('left_leg', (-2, 2), (0, 9.25), (-2, 2), 'pants', 0.92, name='leg')
    lg.d('n', 'fill', (-1.5, 1.5), (4.25, 6.75), 'pants_d', True, 0.8).d('n', 'stitch', (-1.5, 1.5), (4.25, 6.75), 0.72)
    lg.d('e', 'vline', 0, (0, 9.25), 0.8, 2).d('w', 'vline', 0, (0, 9.25), 0.8, 2)
    lg.d('n', 'shade', (-2, 2), (2.0, 2.5), 0.88)
    cg = B('left_leg', (1.75, 2.5), (2.0, 5.5), (-1.5, 1.25), 'pants', 0.75, name='cargo')
    cg.d('e', 'fill', (-1.5, 1.25), (2.0, 3.0), 'pants_d', True, 0.7).d('e', 'px', -0.25, 2.5, '#5a554a').d('e', 'stitch', (-1.5, 1.25), (3.0, 5.5), 0.75)
    pcf = B('left_leg', (-1.75, 2.5), (8.75, 9.5), (-2.5, 2.5), 'pants_d', 0.8, name='pcuff')
    bo = B('left_leg', (-2.0, 2.25), (9.25, 11.5), (-2.25, 2.25), 'dboot', 0.82, name='boot')
    bo.d('n', 'fn', laces)
    to = B('left_leg', (-2.0, 2.25), (10.5, 11.5), (-2.75, -2.25), 'dboot', 0.75, name='toe')
    to.d('u', 'shade', (-2.0, 2.25), (-2.75, -2.25), 1.15)
    so = B('left_leg', (-1.75, 2.5), (11.5, 12.0), (-3.0, 2.5), 'dsole', 0.8, name='sole')
    for bb in (lg, cg, pcf, bo, to, so): m.mirror(bb, 'right_leg')
    kp = B('right_leg', (-2.25, 1.5), (4.25, 7.0), (-2.75, -1.75), 'knee', 0.7, name='kneepad')
    kp.d('n', 'hline', (-2.25, 1.5), 5.5, 0.7).d('n', 'shade', (-2.25, 1.5), (4.25, 4.75), 1.2)
    return m

if __name__ == '__main__':
    root = sys.argv[1] if len(sys.argv) > 1 else '../../src/main/resources/assets/combo-rush'
    for fn in (ct, tt):
        MATS.clear()
        mdl = fn()
        bad = check(mdl)
        if bad: print('совпадающие грани:', bad)
        build(mdl, os.path.join(root, 'agents'), os.path.join(root, 'textures/entity'))
