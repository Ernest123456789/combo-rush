"""Подключение новых стволов: геометрия/анимации/текстуры/скины -> ресурсы, model json, data weapons, lang.
python3 wire.py nova xm1014 ... (после export_all.py)"""
import sys, os, json, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skins as SK
EXP = '/tmp/claude-0/-home-claude/9b4d8123-d2dd-54c5-92d5-c20caffc711b/scratchpad/guns/export_hd'
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
R = ROOT + '/src/main/resources'; A = R + '/assets/combo-rush'; D = R + '/data/combo-rush/weapons'; DOC = ROOT + '/docs/weapons'
NAMES = {'nova': ('Nova', 'Nova'), 'xm1014': ('XM1014', 'XM1014'), 'mag7': ('MAG-7', 'MAG-7'), 'sawedoff': ('Обрез', 'Sawed-Off'), 'mac10': ('MAC-10', 'MAC-10')}
DESC = {'uncommon': ('Обычный скин: красиво, но без особых свойств.', 3, []),
        'rare': ('Скин с шансом вызвать кровотечение.', 5, [{"trigger": "hit", "type": "bleed", "chance": 0.12, "value": 1, "duration": 3}]),
        'epic': ('Скин с кровотечением и критическим уроном.', 8, [{"trigger": "hit", "type": "bleed", "chance": 0.18, "value": 1, "duration": 4},
                                                                  {"trigger": "hit", "type": "crit", "chance": 0.12, "value": 1.0}])}
def wj(p, o): json.dump(o, open(p, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
lang = {l: json.load(open(f'{A}/lang/{l}.json', encoding='utf-8')) for l in ('ru_ru', 'en_us')}
KN = '--knife' in sys.argv
if KN:
    i = sys.argv.index('--knife'); knife = sys.argv[i + 1:i + 4]; sys.argv = sys.argv[:i]
for g in sys.argv[1:]:
    s = f'{EXP}/{g}'
    shutil.copy(f'{s}/{g}.geo.json', f'{A}/geo/item/weapon/{g}.geo.json')
    shutil.copy(f'{s}/{g}.animation.json', f'{A}/animations/item/weapon/{g}.animation.json')
    shutil.copy(f'{s}/{g}.png', f'{A}/textures/item/weapon/{g}.png')
    shutil.copy(f'{s}/{g}_template.png', f'{DOC}/{g}_template.png'); shutil.copy(f'{s}/{g}_uv_layout.json', f'{DOC}/{g}_uv_layout.json')
    wj(f'{A}/models/item/{g}.json', {"parent": "builtin/entity"})
    ru, en = NAMES[g]
    wj(f'{D}/{g}.json', {"name": ru, "description": "Огнестрел: ЛКМ — выстрел. Анимации: R — перезарядка, V — осмотр.",
                         "set_effects": [{"type": "chips_per_action", "value": 4, "source": "KILL"}], "abilities": []})
    lang['ru_ru'][f'item.combo-rush.{g}'] = ru; lang['en_us'][f'item.combo-rush.{g}'] = en
    for sid, sru, sen, tier, *_ in SK.SKINS[g]:
        full = f'{g}_{sid}'
        shutil.copy(f'/tmp/sk/{full}.png', f'{A}/textures/item/weapon/skins/{full}.png')
        wj(f'{A}/models/item/{full}.json', {"parent": "builtin/entity"})
        d, chips, ab = DESC[tier]
        wj(f'{D}/{full}.json', {"name": f'{ru} | {sru}', "description": d, "set_effects": [{"type": "chips_per_action", "value": chips, "source": "KILL"}], "abilities": ab})
        lang['ru_ru'][f'item.combo-rush.{full}'] = f'{ru} | {sru}'; lang['en_us'][f'item.combo-rush.{full}'] = f'{en} | {sen}'
for l, d in lang.items(): wj(f'{A}/lang/{l}.json', d)
if KN:
    g, ru, en = knife
    s = f'{EXP}/{g}'
    shutil.copy(f'{s}/{g}.geo.json', f'{A}/geo/item/weapon/{g}.geo.json'); shutil.copy(f'{s}/{g}.animation.json', f'{A}/animations/item/weapon/{g}.animation.json')
    shutil.copy(f'{s}/{g}.png', f'{A}/textures/item/weapon/{g}.png')
    shutil.copy(f'{s}/{g}_template.png', f'{DOC}/{g}_template.png'); shutil.copy(f'{s}/{g}_uv_layout.json', f'{DOC}/{g}_uv_layout.json')
    wj(f'{A}/models/item/{g}.json', {"parent": "builtin/entity"})
    wj(f'{D}/{g}.json', {"name": ru, "description": "Коллекционный нож. V — осмотр.", "set_effects": [{"type": "chips_per_action", "value": 4, "source": "KILL"}], "abilities": []})
    L = {'ru_ru': {}, 'en_us': {}}
    L['ru_ru'][f'item.combo-rush.{g}'] = ru; L['en_us'][f'item.combo-rush.{g}'] = en
    ab = [{"trigger": "hit", "type": "bleed", "chance": 0.3, "value": 1, "duration": 5}, {"trigger": "hit", "type": "crit", "chance": 0.3, "value": 1.0}]
    for sid, sru, sen, *_ in SK.KNIFE:
        full = f'{g}_{sid}'
        shutil.copy(f'/tmp/sk/{full}.png', f'{A}/textures/item/weapon/skins/{full}.png')
        wj(f'{A}/models/item/{full}.json', {"parent": "builtin/entity"})
        wj(f'{D}/{full}.json', {"name": f'{ru} | {sru}', "description": "Редчайший предмет кейса. Быстрый, режет и кровоточит.",
                                 "set_effects": [{"type": "chips_per_action", "value": 15, "source": "KILL"}, {"type": "mult_times", "value": 1.2}], "abilities": ab})
        L['ru_ru'][f'item.combo-rush.{full}'] = f'{ru} | {sru}'; L['en_us'][f'item.combo-rush.{full}'] = f'{en} | {sen}'
    for l in lang: lang[l].update(L[l]); wj(f'{A}/lang/{l}.json', lang[l])
print('wired', sys.argv[1:])

# ---- ножи-коллекционки (скины из SK.KNIFE): python3 wire.py --knife skeleton "Скелетный нож" "Skeleton Knife"
if False: pass
