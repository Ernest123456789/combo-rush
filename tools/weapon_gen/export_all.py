import sys, os, json; sys.path.insert(0,'.')
from guns import ALL
from guns2 import ALL2
from guns3 import usp, revolver, m4a4, m4a1s, claw, knife
import anim3
from anim import anim_file
import skins as SK
EXP='/tmp/claude-0/-home-claude/9b4d8123-d2dd-54c5-92d5-c20caffc711b/scratchpad/guns/export'
ALLG={**ALL, **ALL2, 'usp':usp,'revolver':revolver,'m4a4':m4a4,'m4a1s':m4a1s,'claw':claw,'knife':knife}
GUNS={}
def build():
    os.makedirs(EXP,exist_ok=True); os.makedirs('/tmp/sk',exist_ok=True)
    for gid,f in ALLG.items():
        g=f(); g.paint(); bad=g.coplanar(); assert not bad,(gid,bad)
        GUNS[gid]=g
        d=f'{EXP}/{gid}'; os.makedirs(d,exist_ok=True)
        json.dump(g.geo(),open(f'{d}/{gid}.geo.json','w'),indent=1)
        g.img.save(f'{d}/{gid}.png'); g.img.save(f'/tmp/{gid}.png')
        json.dump(g.geo(),open(f'/tmp/{gid}.geo.json','w'),indent=1)
        an=anim_file(gid); json.dump(an,open(f'{d}/{gid}.animation.json','w'),indent=1); json.dump(an,open(f'/tmp/{gid}.anim.json','w'),indent=1)
        g.template().convert('RGB').save(f'{d}/{gid}_template.png')
        json.dump(g.uv_layout(),open(f'{d}/{gid}_uv_layout.json','w'),indent=1)
    # skins
    for gid,lst in SK.SKINS.items():
        for sid,ru,en,tier,m,fu in lst:
            SK.apply(GUNS[gid],m,fu).save(f'/tmp/sk/{gid}_{sid}.png')
    for sid,ru,en,m,fu in SK.STD:
        SK.apply(GUNS['knife'],m,fu).save(f'/tmp/sk/knife_{sid}.png')
    for sid,ru,en,m,fu in SK.KNIFE:
        SK.apply(GUNS['claw'],m,fu).save(f'/tmp/sk/claw_{sid}.png')
if __name__=='__main__':
    build(); print({k:(g.tw,g.th,len(g.cubes)) for k,g in GUNS.items()})
