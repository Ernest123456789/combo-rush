"""Экспорт ножей v3 в ресурсы мода: геометрия, анимации, текстуры, скины, шаблоны UV; новый нож «Кукри» (gurkha) — модели,
данные, перевод. Затем: python3 display.py --write --only knife,claw,bayonet,skeleton,gurkha"""
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import knives as KN, kanims as KA, skins as SK
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
A = ROOT + '/src/main/resources/assets/combo-rush'; D = ROOT + '/src/main/resources/data/combo-rush/weapons'; DOC = ROOT + '/docs/weapons'
def wj(p, o): json.dump(o, open(p, 'w', encoding='utf-8'), indent=2 if '/lang/' in p or '/weapons/' in p or '/models/' in p else 1, ensure_ascii=False)

G = {}
for gid, f in KN.ALL.items():
    g = f(); g.paint()
    vis = [c for c in g.cubes if c.part != 'hand']
    keep = g.cubes; g.cubes = vis; bad = g.coplanar(); g.cubes = keep
    assert not bad, (gid, bad[:3])
    G[gid] = g
    wj(f'{A}/geo/item/weapon/{gid}.geo.json', g.geo())
    wj(f'{A}/animations/item/weapon/{gid}.animation.json', KA.anim_file(gid))
    g.img.save(f'{A}/textures/item/weapon/{gid}.png')
    g.template().convert('RGB').save(f'{DOC}/{gid}_template.png')
    json.dump(g.uv_layout(), open(f'{DOC}/{gid}_uv_layout.json', 'w'), indent=1)
    print(gid, len(g.cubes), g.tw, g.th)
for gid in ('claw', 'bayonet', 'skeleton', 'gurkha', 'butterfly'):
    for sid, ru, en, m, fu in SK.KNIFE:
        SK.apply(G[gid], m, fu, sparkle=.025).save(f'{A}/textures/item/weapon/skins/{gid}_{sid}.png')
for sid, ru, en, m, fu in SK.STD:
    SK.apply(G['knife'], m, fu).save(f'{A}/textures/item/weapon/skins/knife_{sid}.png')

# ---- новые ножи: модели предметов, данные, перевод
lang = {l: json.load(open(f'{A}/lang/{l}.json', encoding='utf-8')) for l in ('ru_ru', 'en_us')}
ab = [{"trigger": "hit", "type": "bleed", "chance": 0.3, "value": 1, "duration": 5}, {"trigger": "hit", "type": "crit", "chance": 0.3, "value": 1.0}]
for gid, (RU, EN, CASE) in {'gurkha': ('Кукри', 'Kukri', 'Кобура'), 'butterfly': ('Нож-бабочка', 'Butterfly Knife', 'Граффити')}.items():
    if not os.path.exists(f'{A}/models/item/{gid}.json'): wj(f'{A}/models/item/{gid}.json', {"parent": "builtin/entity"})
    wj(f'{D}/{gid}.json', {"name": RU, "description": f"Коллекционный нож из кейса «{CASE}». V — осмотр.",
                           "set_effects": [{"type": "chips_per_action", "value": 15, "source": "KILL"}, {"type": "mult_times", "value": 1.2}], "abilities": ab})
    lang['ru_ru'][f'item.combo-rush.{gid}'] = RU; lang['en_us'][f'item.combo-rush.{gid}'] = EN
    for sid, sru, sen, *_ in SK.KNIFE:
        full = f'{gid}_{sid}'
        if not os.path.exists(f'{A}/models/item/{full}.json'): wj(f'{A}/models/item/{full}.json', {"parent": "builtin/entity"})
        wj(f'{D}/{full}.json', {"name": f'{RU} | {sru}', "description": "Редчайший предмет кейса. Быстрый, режет и кровоточит.",
                                "set_effects": [{"type": "chips_per_action", "value": 15, "source": "KILL"}, {"type": "mult_times", "value": 1.2}], "abilities": ab})
        lang['ru_ru'][f'item.combo-rush.{full}'] = f'{RU} | {sru}'; lang['en_us'][f'item.combo-rush.{full}'] = f'{EN} | {sen}'
for l, d in lang.items(): wj(f'{A}/lang/{l}.json', d)
print('ok')
