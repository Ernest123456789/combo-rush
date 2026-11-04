import json, sys, os
import numpy as np
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon
from display import *

FACES = [((0,0,0),(0,1,0),(0,1,1),(0,0,1),-1,0,0),]
def box_faces(lo, hi):
    x0,y0,z0=lo; x1,y1,z1=hi
    v=lambda x,y,z:np.array([x,y,z])
    return [
     ([v(x0,y0,z0),v(x1,y0,z0),v(x1,y1,z0),v(x0,y1,z0)], np.array([0,0,-1])),
     ([v(x0,y0,z1),v(x1,y0,z1),v(x1,y1,z1),v(x0,y1,z1)], np.array([0,0,1])),
     ([v(x0,y0,z0),v(x0,y1,z0),v(x0,y1,z1),v(x0,y0,z1)], np.array([-1,0,0])),
     ([v(x1,y0,z0),v(x1,y1,z0),v(x1,y1,z1),v(x1,y0,z1)], np.array([1,0,0])),
     ([v(x0,y0,z0),v(x1,y0,z0),v(x1,y0,z1),v(x0,y0,z1)], np.array([0,-1,0])),
     ([v(x0,y1,z0),v(x1,y1,z0),v(x1,y1,z1),v(x0,y1,z1)], np.array([0,1,0])),
    ]
def draw_scene(ax, polys, proj, depth, light=np.array([0.3,0.8,0.5]), base=(0.55,0.62,0.75)):
    """polys: list of (verts(list of 3d), normal(3d), color or None) ; proj: 3d->2d ; depth: 3d->float (больше = дальше)"""
    items=[]
    for vs,n,col in polys:
        c=np.mean(vs,0); items.append((depth(c), vs, n, col))
    items.sort(key=lambda t:-t[0])
    for dep,vs,n,col in items:
        shade=0.45+0.55*max(0,np.dot(n/np.linalg.norm(n),light/np.linalg.norm(light)))
        cc=np.array(col if col is not None else base)*shade
        ax.add_patch(Polygon([proj(v) for v in vs],closed=True,fc=np.clip(cc,0,1),ec=(0,0,0,0.35),lw=0.3))

def gun_polys(cs, tf, col=None):
    out=[]
    for n,lo,hi in cs:
        for vs,nrm in box_faces(lo,hi):
            out.append(([tf(v) for v in vs], None, col))
    return out
def gun_polys_n(cs, tf, Lin, col=None):
    out=[]
    for n,lo,hi in cs:
        for vs,nrm in box_faces(lo,hi):
            out.append(([tf(v) for v in vs], Lin@nrm, col))
    return out

def player_boxes():
    # в системе E: x влево игрока, y вверх, z вперёд. (x_min,y_min,z_min),(max)
    P=[]
    P.append(((-4/16,0.75,-2/16),(4/16,1.5,2/16),(0.8,0.6,0.5)))     # торс
    P.append(((-4/16,1.5,-4/16),(4/16,2.0,4/16),(0.9,0.7,0.55)))     # голова (1.5 .. 2.0)
    P.append(((-4/16,0,-2/16),(4/16,0.75,2/16),(0.3,0.3,0.6)))
    return P

def arm_boxes(side):
    ax,ay,az=ARM[side]; L=arm_lin(side)
    piv=np.array([(5 if side=="L" else -5)/16,2/16,0])
    polys=[]
    lo=np.array([-2/16,0,-2/16]); hi=np.array([2/16,12/16,2/16])
    if side=="R": lo[0],hi[0]=-2/16,2/16
    for vs,n in box_faces(lo,hi):
        pts=[]
        for v in vs:
            M=piv+L@(v+ np.array([0,-2/16,0]) + np.array([0,2/16,0])*0)  # рука от плеча (pivot) вниз
            M=piv+L@v
            pts.append(np.array([M[0],1.501-M[1],-M[2]]))
        nrm=np.array([n[0],-n[1],-n[2]])
        polys.append((pts, (FLIP@L@n), (0.9,0.72,0.58)))
    return polys

def tp_panels(fig, axes, n, e):
    A=anchors(n); cs=A["cs"]
    disp=disp_fn(e["tp"]["rot"],e["tp"]["tr"],e["tp"]["sc"])
    R=Rxyz(e["tp"]["rot"])*np.array(e["tp"]["sc"])[0]
    Lm=FLIP@arm_lin("R")@Rx(-np.pi/2)@Ry(np.pi)
    tf=lambda p: tp_point(p,disp,"R")
    polys=gun_polys_n(cs,tf,Lm@Rxyz(e["tp"]["rot"]),(0.35,0.4,0.5))
    body=[]
    for lo,hi,col in [(b[0],b[1],b[2]) for b in player_boxes()]:
        for vs,nrm in box_faces(np.array(lo),np.array(hi)): body.append((vs,nrm,col))
    arms=arm_boxes("R")+arm_boxes("L")
    allp=body+arms+polys
    # side view: смотрим со стороны -x (правая сторона игрока): горизонталь z, вертикаль y
    ax=axes[0]; ax.set_aspect("equal"); 
    draw_scene(ax, allp, lambda v:(v[2],v[1]), lambda c: c[0]*-1+0 )
    # top view
    ax=axes[1]; ax.set_aspect("equal")
    draw_scene(ax, allp, lambda v:(-v[0],v[2]), lambda c: -c[1])
    for a in axes[:2]:
        a.set_xlim(-1.2,2.2) if a is axes[0] else a.set_xlim(-1.3,1.3)
    axes[0].set_xlim(-0.8,2.4); axes[0].set_ylim(0.6,2.0)
    axes[1].set_xlim(-1.2,1.2); axes[1].set_ylim(-0.4,2.4)
    # руки-цели
    hr,hl=tp_hand("R"),tp_hand("L")
    axes[0].plot([hr[2],hl[2]],[hr[1],hl[1]],"r.",ms=4); axes[1].plot([-hr[0],-hl[0]],[hr[2],hl[2]],"r.",ms=4)
    axes[0].set_title(n+" TP side",fontsize=6); axes[1].set_title("top",fontsize=6)

def fp_panel(ax, n, e, fov=70, aspect=16/9):
    A=anchors(n); cs=A["cs"]
    disp=disp_fn(e["fp"]["rot"],e["fp"]["tr"],e["fp"]["sc"])
    Lin=Rxyz(e["fp"]["rot"])
    tf=lambda p: fp_point(p,disp)
    polys=gun_polys_n(cs,tf,Lin,(0.35,0.4,0.5))
    t=np.tan(np.radians(fov/2))
    def proj(v):
        z=min(v[2],-0.05); return (v[0]/(-z)/(t*aspect), v[1]/(-z)/t)
    draw_scene(ax,polys,proj,lambda c:-c[2])
    ax.set_xlim(-1,1); ax.set_ylim(-1,1); ax.set_box_aspect(9/16); ax.set_facecolor((0.7,0.85,0.95))
    ax.set_title(n+" FP",fontsize=6)

def gui_panel(ax,n,e):
    A=anchors(n); cs=A["cs"]
    disp=disp_fn(e["gui"]["rot"],e["gui"]["tr"],e["gui"]["sc"]); Lin=Rxyz(e["gui"]["rot"])
    polys=gun_polys_n(cs,lambda p:disp(p),Lin,(0.35,0.4,0.5))
    draw_scene(ax,polys,lambda v:(v[0],v[1]),lambda c:-c[2])
    ax.add_patch(plt.Rectangle((-0.5,-0.5),1,1,fill=False,ec="gray",lw=0.5))
    ax.set_xlim(-0.7,0.7); ax.set_ylim(-0.7,0.7); ax.set_aspect("equal"); ax.set_title(n+" GUI",fontsize=6)

if __name__=="__main__":
    out=sys.argv[1]; os.makedirs(out,exist_ok=True)
    if sys.argv[2:3]==["fp"]:
        res=json.load(open("display_out.json"))
        fig,axs=plt.subplots(3,5,figsize=(20,9))
        for ax,n in zip(axs.flat,ALL): fp_panel(ax,n,res[n])
        fig.tight_layout(); fig.savefig(f"{out}/fp.png",dpi=80); sys.exit()
    res=json.load(open("display_out.json"))
    names=sys.argv[2:] or ALL
    for grp in range(0,len(names),3):
        sub=names[grp:grp+3]
        fig,axs=plt.subplots(len(sub),4,figsize=(16,3.6*len(sub)))
        axs=np.atleast_2d(axs)
        for i,n in enumerate(sub):
            tp_panels(fig,[axs[i,0],axs[i,1]],n,res[n]); fp_panel(axs[i,2],n,res[n]); gui_panel(axs[i,3],n,res[n])
        fig.tight_layout(); fig.savefig(f"{out}/p{grp//3}.png",dpi=90); plt.close(fig)
