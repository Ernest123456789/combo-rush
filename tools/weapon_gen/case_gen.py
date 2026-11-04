import json, math, sys
from PIL import Image, ImageDraw
sys.path.insert(0,'.')
from wgen import hsh
def mix(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
def tex():
    im=Image.new('RGBA',(64,64),(0,0,0,0)); d=ImageDraw.Draw(im); px=im.load()
    def panel(x0,y0,w,h,top,bot,border,rivet=None,noise=4):
        for y in range(h):
            for x in range(w):
                c=mix(top,bot,y/(h-1)); n=int((hsh(x+x0,y+y0,3)-.5)*2*noise)
                c=tuple(max(0,min(255,v+n)) for v in c)
                if x in (0,w-1) or y in (0,h-1): c=border
                elif x in (1,w-2) or y in (1,h-2): c=mix(c,(255,255,255),.08)
                px[x0+x,y0+y]=c+(255,)
        if rivet:
            for (rx,ry) in ((3,3),(w-4,3),(3,h-4),(w-4,h-4)): px[x0+rx,y0+ry]=rivet+(255,); px[x0+rx+1,y0+ry]=mix(rivet,(0,0,0),.4)+(255,)
    # BODY navy with ribs
    panel(0,0,32,16,(44,62,104),(22,32,60),(12,16,32),(150,170,210))
    for x in range(6,28,5):
        for y in range(4,13): px[x,y]=(18,26,50,255); px[x+1,y]=(70,92,140,255)
    # LID navy-lighter with orange stripe + chevrons
    panel(0,16,32,16,(58,80,128),(30,44,80),(12,16,32),(150,170,210))
    for y in range(6,10):
        for x in range(2,30): px[x,16+y]=(236,138,40,255) if y in (7,8) else (160,86,20,255)
    for k in range(5):
        cx=6+k*5
        for t in range(3):
            px[cx+t,16+3+t]=(255,200,90,255); px[cx+t,16+12-t]=(255,200,90,255)
    # METAL
    panel(32,0,16,16,(150,156,170),(86,92,108),(40,44,54),None,noise=6)
    for y in range(2,14,2):
        for x in range(2,14): px[32+x,y]=mix(px[32+x,y][:3],(255,255,255),.08)+(255,)
    # GOLD
    panel(48,0,16,16,(255,222,110),(170,118,28),(96,60,10),None,noise=5)
    # EMBLEM: dark plate, gold ring + crosshair + orange chevrons
    panel(32,16,16,16,(28,32,48),(16,18,30),(230,170,60),None,noise=2)
    cx,cy=39.5,23.5
    for y in range(16):
        for x in range(16):
            dd=math.hypot(32+x-cx-0.5+0.5,16+y-cy-0.5+0.5)
            X,Y=32+x,16+y
            if 3.4<=dd<=4.6: px[X,Y]=(255,196,70,255)
            if dd<1.2: px[X,Y]=(255,120,40,255)
            if (abs(X-cx)<.9 and 2<dd<7) or (abs(Y-cy)<.9 and 2<dd<7): px[X,Y]=(255,196,70,255)
    # DARK rubber
    panel(48,16,16,16,(40,40,46),(22,22,26),(10,10,12),None,noise=3)
    # GLOW neon strip
    for y in range(16):
        for x in range(32):
            t=1-abs(y-7.5)/8
            c=mix((10,60,90),(120,255,255),max(0,t)**1.5)
            px[x,32+y]=c+(255,)
    return im
R={'body':(0,0,8,4),'lid':(0,4,8,8),'metal':(8,0,12,4),'gold':(12,0,16,4),'emb':(8,4,12,8),'dark':(12,4,16,8),'glow':(0,8,8,12)}
E=[]
def el(a,b,reg,faces=None,name=''):
    E.append({'from':a,'to':b,'reg':reg,'name':name})
el([1,1,4],[15,6,12],'body')
el([0.5,6,3.5],[15.5,8.5,12.5],'lid')
el([0.5,0,3.5],[15.5,1,12.5],'metal')
for xa,xb in ((0.5,2.2),(13.8,15.5)):
    for za,zb in ((3.5,5.2),(10.8,12.5)):
        el([xa,1,za],[xb,8.5,zb],'metal')
el([2.5,6.5,12.5],[13.5,7.7,12.62],'glow'); el([2.5,6.5,3.38],[13.5,7.7,3.5],'glow')
el([5.5,1.6,12.5],[10.5,5.6,12.75],'emb'); el([5.5,1.6,3.25],[10.5,5.6,3.5],'emb')
for x in (3.0,12.0):
    el([x,4.2,12.5],[x+1.0,7.8,12.9],'gold'); el([x,4.2,3.1],[x+1.0,7.8,3.5],'gold')
el([6,8.5,7],[7,10,9],'metal'); el([9,8.5,7],[10,10,9],'metal'); el([6,10,7],[10,11,9],'dark')
el([2,8.5,5.5],[5,8.75,10.5],'emb'); el([11,8.5,5.5],[14,8.75,10.5],'emb')
el([0.2,2,6],[0.5,6,10],'gold'); el([15.5,2,6],[15.8,6,10],'gold')
def model():
    els=[]
    for e in E:
        u=R[e['reg']]; f={k:{'uv':list(u),'texture':'#0'} for k in ('north','east','south','west','up','down')}
        els.append({'from':e['from'],'to':e['to'],'faces':f})
    return {'textures':{'0':'combo-rush:item/case/arsenal_0','particle':'combo-rush:item/case/arsenal_0'},'elements':els,
      'display':{
        'gui':{'rotation':[30,225,0],'translation':[0,-0.3,0],'scale':[0.78,0.78,0.78]},
        'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.35,0.35,0.35]},
        'fixed':{'rotation':[0,180,0],'translation':[0,-0.5,0],'scale':[0.7,0.7,0.7]},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.3,0.3,0.3]},
        'thirdperson_lefthand':{'rotation':[75,225,0],'translation':[0,2.5,0],'scale':[0.3,0.3,0.3]},
        'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0.5,0],'scale':[0.45,0.45,0.45]},
        'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,0.5,0],'scale':[0.45,0.45,0.45]},
        'head':{'rotation':[0,180,0],'translation':[0,13,0],'scale':[0.8,0.8,0.8]}}}
if __name__=='__main__':
    tex().save('/tmp/arsenal_0.png'); json.dump(model(),open('/tmp/case_arsenal.json','w'),indent=1)
    print(len(E),'elements')
