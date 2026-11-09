import sys, os, json; sys.path.insert(0,'.')
import hd, hd2
import anim3, anim4, anim5, anim6
from anim import anim_file
import skins as SK
EXP='/tmp/claude-0/-home-claude/9b4d8123-d2dd-54c5-92d5-c20caffc711b/scratchpad/guns/export_hd'
ALLG=hd.ALLG
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
            SK.apply(GUNS[gid],m,fu,sparkle={'epic':.012,'legendary':.02}.get(tier,0)).save(f'/tmp/sk/{gid}_{sid}.png')
    for sid,ru,en,m,fu in SK.STD:
        SK.apply(GUNS['knife'],m,fu).save(f'/tmp/sk/knife_{sid}.png')
    for sid,ru,en,m,fu in SK.KNIFE:
        SK.apply(GUNS['claw'],m,fu,sparkle=.025).save(f'/tmp/sk/claw_{sid}.png')
        SK.apply(GUNS['bayonet'],m,fu,sparkle=.025).save(f'/tmp/sk/bayonet_{sid}.png')
        SK.apply(GUNS['skeleton'],m,fu,sparkle=.025).save(f'/tmp/sk/skeleton_{sid}.png')
if __name__=='__main__':
    build(); print({k:(g.tw,g.th,len(g.cubes)) for k,g in GUNS.items()})
