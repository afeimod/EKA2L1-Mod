"""Reproducible handset assets, controls and pressed-key textures.

N73, N86, QD and touch-shell artwork uses the supplied reference renders stored
in phone_materials/reference, with seeded fine matte grain outside the LCD
window. All models export matching Android hit maps and physically shaded
key-press atlases.
Requires Pillow, numpy and scipy.
"""
from pathlib import Path
import json
import math
import sys
import numpy as np
from scipy import ndimage
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
from scipy.ndimage import distance_transform_edt, gaussian_filter, shift

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/emu/android/app/src/main/assets/phone'
OUT.mkdir(parents=True, exist_ok=True)
S = 3
FONT = Path('C:/Windows/Fonts')
RNG = np.random.default_rng(73)

class Front:
    def __init__(self, name, w, h, screen, base=(30,31,31)):
        self.name,self.w,self.h,self.screen = name,w,h,screen
        self.size=(round(w*S),round(h*S)); self.keys=[]; self.key_masks=[];self.nav=None
        self.im=Image.new('RGB',self.size,base)
        self.yy,self.xx=np.mgrid[0:self.size[1],0:self.size[0]].astype(np.float32)/S
        self.grain=RNG.normal(0,1,self.size[::-1]).astype(np.float32)
        self.grain=gaussian_filter(self.grain,S*.17)
        self.grain/=self.grain.std()
        coarse=gaussian_filter(self.grain,S*2.4)
        self.grain=.96*self.grain+.04*coarse/max(.001,coarse.std())
        self.micro_y,self.micro_x=np.gradient(self.grain)
        material=Image.open(ROOT/'tools/phone_materials/polymer.png').convert('L')
        tile=np.asarray(material,dtype=np.float32)-128
        tile=np.block([[tile,tile[:,::-1]],[tile[::-1,:],tile[::-1,::-1]]])
        self.polymer=np.tile(tile,(math.ceil(self.size[1]/tile.shape[0]),math.ceil(self.size[0]/tile.shape[1])))[:self.size[1],:self.size[0]]
        self.polymer/=self.polymer.std()
        self.satin={}
        for name in ('anodized','qd-satin'):
            tile=np.asarray(Image.open(ROOT/'tools/phone_materials'/(name+'.png')),dtype=np.float32)-128
            tile=np.block([[tile,tile[:,::-1]],[tile[::-1,:],tile[::-1,::-1]]])
            tiled=np.tile(tile,(math.ceil(self.size[1]/tile.shape[0]),math.ceil(self.size[0]/tile.shape[1])))[:self.size[1],:self.size[0]]
            self.satin[name]=tiled/max(1,tiled.std())
    def mask(self,box=None,r=0,points=None):
        m=Image.new('L',self.size);d=ImageDraw.Draw(m)
        if points:
            if len(points)>4:
                for _ in range(3 if len(points)>10 else 2):
                    smooth=[]
                    for i,p in enumerate(points):
                        q=points[(i+1)%len(points)];cut=.15 if len(points)>10 else .045;smooth.extend([((1-cut)*p[0]+cut*q[0],(1-cut)*p[1]+cut*q[1]),(cut*p[0]+(1-cut)*q[0],cut*p[1]+(1-cut)*q[1])])
                    points=smooth
            d.polygon([(round(x*S),round(y*S)) for x,y in points],fill=255)
        else:d.rounded_rectangle(tuple(round(v*S) for v in box),radius=round(r*S),fill=255)
        return m
    def part(self,box=None,r=0,color=(80,82,83),bevel=3,metal=.1,rough=1.2,points=None,bulge=0,material=None):
        m=self.mask(box,r,points);a=np.asarray(m)>0
        if not a.any(): return
        ys,xs=np.where(a);x0=max(0,xs.min()-int(8*S));x1=min(self.size[0],xs.max()+int(8*S)+1);y0=max(0,ys.min()-int(8*S));y1=min(self.size[1],ys.max()+int(8*S)+1)
        a=a[y0:y1,x0:x1];am=m.crop((x0,y0,x1,y1));
        dist=distance_transform_edt(np.pad(a,1))[1:-1,1:-1].astype(np.float32)/S
        t=np.clip(dist/max(.15,bevel),0,1)
        z=np.sin(t*np.pi/2)*bevel*.6
        xx=self.xx[y0:y1,x0:x1];yy=self.yy[y0:y1,x0:x1]
        if bulge:
            cx=(xs.min()+xs.max())/(2*S);cy=(ys.min()+ys.max())/(2*S)
            rx=max(1,(xs.max()-xs.min())/(2*S));ry=max(1,(ys.max()-ys.min())/(2*S))
            dome=np.maximum(0,1-((xx-cx)/rx)**2-((yy-cy)/ry)**2)
            z+=gaussian_filter(dome,1.2*S)*bulge*t
        dy,dx=np.gradient(gaussian_filter(z,.6*S));dx*=S;dy*=S
        # Roughness changes microscopic normals as well as pigment. The grain is
        # resolved in handset coordinates, so it survives asset downsampling.
        dx+=self.micro_x[y0:y1,x0:x1]*min(.08,rough*.0035)
        dy+=self.micro_y[y0:y1,x0:x1]*min(.08,rough*.0035)
        norm=np.sqrt(dx*dx+dy*dy+1);nx=-dx/norm;ny=-dy/norm;nz=1/norm
        diffuse=np.maximum(0,(-.43*nx-.57*ny+.70*nz))
        # Separate powder coat, sandblasted aluminium, pearl paint and chrome.
        # Broad reflected cards describe the curved surface; a narrow strip is
        # reserved for polished edges instead of making every surface mirror-like.
        material=material or ('chrome' if metal>.78 else 'satin' if metal>.32 else 'polymer')
        spec=np.exp(-((nx+.22)**2+(ny+.30)**2)/(.12 if material in ('satin','anodized') else .045))
        strip=np.exp(-((nx-.45)**2+(ny-.3)**2)/.009)
        card=.92+.06*np.cos(xx/self.w*4.2-.6)+.025*np.cos(yy/self.h*3.8)
        if material=='chrome':
            intensity=(.32+.65*diffuse)*card
            reflected=spec*.78+strip*.43
        elif material in ('satin','anodized'):
            intensity=(.54+.60*diffuse)*card
            reflected=spec*.22+strip*.09
            if material=='anodized':
                # A continuous rounded aluminium extrusion. The broad side
                # falloff remains visible across the top and bottom shoulders.
                midpoint=(xs.min()+xs.max())/(2*S);half=max(1,(xs.max()-xs.min())/(2*S))
                lateral=np.clip(np.abs(xx-midpoint)/half,0,1)
                intensity*=1.06-.53*lateral**4
        elif material=='pearl':
            intensity=(.73+.29*diffuse)*card
            reflected=spec*.12+strip*.065
        else:
            intensity=(.30+.82*diffuse)*card
            reflected=spec*.07+strip*.015
        rgb=np.asarray(color,dtype=np.float32)[None,None,:]*intensity[:,:,None]+255*reflected[:,:,None]
        polymer=max(0,1-metal*1.8)
        pigment=(self.grain[y0:y1,x0:x1]*(1-polymer)+self.polymer[y0:y1,x0:x1]*polymer)
        if material in ('satin','anodized'):
            sample='qd-satin' if self.name=='qd' else 'anodized'
            pigment=.42*self.satin[sample][y0:y1,x0:x1]+.58*self.grain[y0:y1,x0:x1]
        rgb+=pigment[:,:,None]*rough
        # Microscopic horizontal brushed streaks; polymer keeps isotropic grain.
        if material=='chrome':rgb+=np.sin(yy*19)[:,:,None]*.35
        # The shallow lip casts contact occlusion around a key, without drawing
        # a graphic outline over its face.
        rgb-=np.exp(-dist/.42)[:,:,None]*(7 if material=='polymer' else 4)
        rgb=np.uint8(np.clip(rgb,0,255))
        shadow=m.crop((x0,y0,x1,y1))
        shadow=ImageChops.offset(shadow,0,round(1.1*S)).filter(ImageFilter.GaussianBlur(2.2*S))
        shadow=shadow.point(lambda value:round(value*.48))
        patch=self.im.crop((x0,y0,x1,y1));patch.paste((3,4,5),mask=shadow);patch.paste(Image.fromarray(rgb),mask=am)
        self.im.paste(patch,(x0,y0))
    def line(self,pts,fill,width=1):
        ImageDraw.Draw(self.im).line([(x*S,y*S) for x,y in pts],fill=fill,width=max(1,round(width*S)),joint='curve')
    def text(self,t,x,y,size=16,color='#ececea',bold=False,anchor='mm',rotate=0):
        f=ImageFont.truetype(str(FONT/('arialbd.ttf' if bold else 'arial.ttf')),max(1,round(size*S)))
        if rotate:
            layer=Image.new('RGBA',(round(size*S*len(t)),round(size*S*2)))
            ImageDraw.Draw(layer).text((layer.width/2,layer.height/2),t,font=f,fill=color,anchor='mm')
            layer=layer.rotate(rotate,expand=True,resample=Image.Resampling.BICUBIC)
            self.im.paste(layer,(round(x*S-layer.width/2),round(y*S-layer.height/2)),layer)
        else:ImageDraw.Draw(self.im).text((x*S,y*S),t,font=f,fill=color,anchor=anchor)
    def decal(self,name,box,color='#e7ebe7'):
        mask=Image.open(ROOT/'tools/phone_materials/legends'/(name+'.png')).convert('L')
        x0,y0,x1,y1=box;scale=min((x1-x0)*S/mask.width,(y1-y0)*S/mask.height)
        mask=mask.resize((max(1,round(mask.width*scale)),max(1,round(mask.height*scale))),Image.Resampling.LANCZOS)
        x=round((x0+x1)*S/2-mask.width/2);y=round((y0+y1)*S/2-mask.height/2)
        # Pigment sits inside an engraved recess; the lettering silhouette comes
        # directly from the reference, including its special symbols.
        self.im.paste('#080d0f',(x,y),mask.filter(ImageFilter.MaxFilter(3)))
        self.im.paste(color,(x,y),mask)
    def nokia(self,x,y,w,h,color='#eeefea',rotate=0):
        # Traced geometric wordmark proportions, rather than an Arial substitute.
        layer=Image.new('RGBA',(round(w*S),round(h*S)))
        mask=Image.new('L',(round(w*S),round(h*S)));d=ImageDraw.Draw(mask)
        def poly(points):d.polygon([(a/92*w*S,b/16*h*S) for a,b in points],fill=255)
        poly([(0,0),(4,0),(13,10),(13,0),(17,0),(17,16),(13,16),(4,6),(4,16),(0,16)])
        d.rounded_rectangle((20/92*w*S,0,39/92*w*S,16/16*h*S),radius=4/92*w*S,fill=255)
        d.rounded_rectangle((24/92*w*S,4/16*h*S,35/92*w*S,12/16*h*S),radius=1.3/92*w*S,fill=0)
        poly([(42,0),(46,0),(46,6),(54,0),(60,0),(50,8),(60,16),(54,16),(46,10),(46,16),(42,16)])
        poly([(63,0),(68,0),(68,16),(63,16)])
        poly([(71,16),(80,0),(84,0),(92,16),(87,16),(82,5),(76,16)])
        poly([(77,10),(87,10),(88,13),(75,13)])
        layer.paste(color,(0,0),mask)
        if rotate:layer=layer.rotate(rotate,expand=True,resample=Image.Resampling.BICUBIC)
        self.im.paste(layer,(round(x*S),round(y*S)),layer)
    def lens(self,x,y,r):
        # Radial lens bowl with glass coatings and an off-axis softbox reflection.
        box=(round((x-r)*S),round((y-r)*S),round((x+r)*S),round((y+r)*S))
        yy,xx=np.mgrid[0:box[3]-box[1],0:box[2]-box[0]].astype(np.float32)
        xx=(xx/S-r)/r;yy=(yy/S-r)/r;rho=np.sqrt(xx*xx+yy*yy)
        shade=8+13*np.exp(-((rho-.68)/.13)**2)+10*np.exp(-((rho-.34)/.08)**2)
        glow=np.exp(-((xx+.35)**2+(yy+.37)**2)/.07)*16
        rgb=np.stack((shade+glow*.65,shade+glow*.9,shade+glow),axis=2)
        mask=Image.fromarray(np.uint8((rho<=1)*255));self.im.paste(Image.fromarray(np.uint8(rgb)),box[:2],mask)
    def key(self,box,code,label='',metal=False,r=5,points=None,size=17,color='#f0f1ef',bulge=1.2,gloss=None):
        self.hit(box,code,points=points,r=r)
        self.keys[-1]['label']=label
        self.part(box,r,(232,235,230) if metal else (35,38,39),2.3 if self.name=='n73' else 4.0,(.69 if metal else .07) if gloss is None else gloss,1.15 if metal else 4.6 if self.name=='n73' else 3.2,points,bulge*1.8)
        if label:self.text(label,(box[0]+box[2])/2,(box[1]+box[3])/2,size,color)
    def hit(self,box,code,points=None,r=0,mask=None,texture_mask=None,tilt=None):
        self.keys.append(dict(bounds=list(box),code=code,label=''))
        self.key_masks.append((mask or self.mask(box,r,points),texture_mask,tilt))
    def navigation(self,base,mask,hit_bounds,center,deadzone,joystick=False,confirm=False,hit_polygon=None):
        self.nav=(base,mask,hit_bounds,center,deadzone,joystick,confirm,hit_polygon)
    def finish_navigation(self):
        if self.nav is None:
            (OUT/(self.name+'-nav.json')).write_text('{}',encoding='utf-8');return
        base,mask,hit_bounds,center,deadzone,joystick,confirm,hit_polygon=self.nav
        x0,y0,x1,y1=mask.getbbox();pad=round(8*S)
        bx=(max(0,x0-pad),max(0,y0-pad),min(self.im.width,x1+pad),min(self.im.height,y1+pad))
        alpha=mask.crop(bx);moving=self.im.crop(bx).convert('RGBA');moving.putalpha(alpha)
        rest=self.im.crop(bx);rest.paste(base.crop(bx),mask=alpha)
        w,h=rest.size;atlas=Image.new('RGB',(w*3,h*3));states=[]
        # Project the complete keycap as one rigid plane rotating on a short
        # stem. All eight directions retain engraving, grain and the silhouette.
        corners=np.array([[0,0],[w,0],[w,h],[0,h]],dtype=float)
        for dy in (-1,0,1):
            for dx in (-1,0,1):
                ax=math.radians(-dy*16);ay=math.radians(dx*16)
                projected=[]
                for x,y in corners:
                    u=x-w/2;v=y-h/2
                    px=u*math.cos(ay);z=-u*math.sin(ay)
                    py=v*math.cos(ax)-z*math.sin(ax);z=v*math.sin(ax)+z*math.cos(ax)
                    perspective=max(w,h)*3/(max(w,h)*3-z)
                    projected.append((w/2+px*perspective+dx*S*1.4,h/2+py*perspective+dy*S*1.4+(1 if dx==dy==0 else 0)*S*.75))
                equations=[];target=[]
                for (x,y),(u,v) in zip(projected,corners):
                    equations.extend([[x,y,1,0,0,0,-u*x,-u*y],[0,0,0,x,y,1,-v*x,-v*y]]);target.extend([u,v])
                coefficients=np.linalg.solve(np.array(equations),np.array(target))
                tilted=moving.transform((w,h),Image.Transform.PERSPECTIVE,coefficients,Image.Resampling.BICUBIC)
                rgba=np.asarray(tilted,dtype=np.float32).copy();yy,xx=np.mgrid[0:h,0:w]
                slope=((xx-w/2)*dx/w+(yy-h/2)*dy/h)
                rgba[:,:,:3]*=(.92+slope[:,:,None]*.22) if dx or dy else .94
                tilted=Image.fromarray(np.uint8(np.clip(rgba,0,255)))
                contact=tilted.getchannel('A').filter(ImageFilter.GaussianBlur(S*1.1))
                patch=rest.copy();patch.paste((3,6,8),mask=contact);patch.paste(tilted,mask=tilted.getchannel('A'))
                tx=(dx+1)*w;ty=(dy+1)*h;atlas.paste(patch,(tx,ty));states.append([tx,ty,tx+w,ty+h])
        atlas.save(OUT/(self.name+'-nav.webp'),lossless=True,method=6)
        data=dict(bounds=[v/S for v in bx],hit_bounds=hit_bounds,hit_polygon=hit_polygon,
                  center=center,deadzone=deadzone,joystick=joystick,confirm=confirm,states=states)
        (OUT/(self.name+'-nav.json')).write_text(json.dumps(data,indent=2),encoding='utf-8')
    def lcd(self,glass=False):
        x,y,w,h=self.screen
        if glass:
            self.part((x-6,y-6,x+w+6,y+h+6),5,(6,9,11),1,.12,.2)
            self.line([(x-1,y+h+1),(x-1,y-1),(x+w+1,y-1)],(27,33,35),.45)
        else:
            self.part((x-10,y-10,x+w+10,y+h+10),12,(12,14,15),3,.25,.6)
            self.part((x-6,y-6,x+w+6,y+h+6),7,(137,143,145),2,.9,.5)
            self.part((x-3,y-3,x+w+3,y+h+3),3,(4,6,9),1,.1,.3)
        ImageDraw.Draw(self.im).rectangle((x*S,y*S,(x+w)*S,(y+h)*S),fill=(8,11,14))
    def phone(self,x,y,red=False,angle=0,size=23):
        # Engraved, curved call/end glyph rather than a Unicode substitute.
        color=(175,39,34) if red else (42,109,84)
        pts=[(-.55,-.12),(-.55,.18),(-.4,.34),(.33,.34),(.5,.19)]
        if red:pts=[(-.5,-.12),(.38,-.12),(.55,.03),(.55,.28),(.38,.40),(-.43,.40)]
        a=math.radians(angle)
        self.line([(x+(u*math.cos(a)-v*math.sin(a))*size,y+(u*math.sin(a)+v*math.cos(a))*size) for u,v in pts],(6,16,19),2.9)
        self.line([(x+(u*math.cos(a)-v*math.sin(a))*size,y+(u*math.sin(a)+v*math.cos(a))*size-1) for u,v in pts],color,1.8)
    def finish(self):
        # Chassis and overlays must share exact pixels, otherwise a lossy base
        # makes the boundary of a pressed atlas tile flash as a rectangle.
        self.im.save(OUT/(self.name+'.webp'),lossless=True,method=6)
        self.finish_navigation()
        # Real sculpted key silhouettes, engraved legends and the same material
        # are retained in each depressed texture. There is no rectangular tint.
        tiles=[];atlas_w=2048;cx=cy=row_h=0
        for item,(mask,texture_mask,tilt) in zip(self.keys,self.key_masks):
            hit_box=mask.getbbox()
            if hit_box is None:raise ValueError(item)
            x0,y0,x1,y1=hit_box
            hit=mask.crop(hit_box)
            item['hit_bounds']=[v/S for v in hit_box]
            if cx+hit.width+4>atlas_w:cx=0;cy+=row_h+4;row_h=0
            item['hit_atlas']=[cx,cy,cx+hit.width,cy+hit.height]
            tiles.append((Image.merge('RGBA',(hit,hit,hit,hit)),cx,cy))
            cx+=hit.width+4;row_h=max(row_h,hit.height)
            visible=texture_mask if texture_mask is not None else mask
            bx=visible.getbbox();x0,y0,x1,y1=bx
            alpha=np.asarray(visible.crop(bx),dtype=np.float32)/255
            rgb=np.asarray(self.im.crop(bx),dtype=np.float32)
            dist=distance_transform_edt(np.pad(alpha>0,1))[1:-1,1:-1]/S
            if tilt:
                # A single joystick cap rocks around its stem, never a second
                # button in the centre of a drawn direction pad.
                dx,dy=tilt
                rgb=shift(rgb,(dy*S*.85,dx*S*.85,0),order=1,mode='nearest')
                yy,xx=np.mgrid[0:rgb.shape[0],0:rgb.shape[1]].astype(np.float32)
                rocking=((xx-rgb.shape[1]/2)*dx+(yy-rgb.shape[0]/2)*dy)/max(rgb.shape[:2])
                rgb=rgb*(.92+rocking[:,:,None]*.27)
                rgba=Image.fromarray(np.uint8(np.clip(np.dstack((rgb,alpha*255)),0,255)))
            else:
                # Turn the photographed cap into a recessed socket, then move
                # the cap itself down as one smaller rigid piece. The original
                # alpha outline keeps every handset's individual key profile;
                # this creates a real bevel around it rather than a flat tint.
                inset_depth=2.8 if self.name=='qd' else 2.1
                depth=np.clip(dist/inset_depth,0,1)
                rim=np.exp(-dist/(.8 if self.name=='qd' else .68))
                edge_y=np.gradient(alpha)[0]
                socket_rgb=rgb*(.54+.27*depth)[:,:,None]
                socket_rgb-=rim[:,:,None]*(18 if self.name=='qd' else 14)
                socket_rgb+=np.maximum(0,-edge_y)[:,:,None]*(12 if self.name=='qd' else 10)
                socket=Image.fromarray(np.uint8(np.clip(np.dstack((socket_rgb,alpha*255)),0,255)))

                cap=Image.fromarray(np.uint8(np.clip(rgb*(.91 if self.name=='qd' else .94),0,255))).convert('RGBA')
                cap.putalpha(Image.fromarray(np.uint8(alpha*255)))
                scale=.974 if min(cap.size)>round(S*24) else .95
                inset_size=(max(1,round(cap.width*scale)),max(1,round(cap.height*scale)))
                cap=cap.resize(inset_size,Image.Resampling.LANCZOS)
                inset=Image.new('RGBA',socket.size,(0,0,0,0))
                inset.alpha_composite(cap,((socket.width-cap.width)//2,
                                           (socket.height-cap.height)//2+round(S*.72)))
                rgba=Image.alpha_composite(socket,inset)
                rgba.putalpha(Image.fromarray(np.uint8(alpha*255)))
            if cx+rgba.width+4>atlas_w:cx=0;cy+=row_h+4;row_h=0
            item['pressed_bounds']=[v/S for v in bx]
            item['pressed_atlas']=[cx,cy,cx+rgba.width,cy+rgba.height]
            tiles.append((rgba,cx,cy));cx+=rgba.width+4;row_h=max(row_h,rgba.height)
        atlas=Image.new('RGBA',(atlas_w,cy+row_h+4))
        for tile,x,y in tiles:atlas.paste(tile,(x,y))
        atlas.save(OUT/(self.name+'-keys.webp'),lossless=True,method=6)
        (OUT/(self.name+'.json')).write_text(json.dumps(self.keys,indent=2),encoding='utf-8')
        preview=self.im.copy();preview.thumbnail((1000,1100));preview.save(OUT/(self.name+'-preview.png'))
        print(self.name,self.im.size,len(self.keys))

# Key numbers are the Symbian scan codes used by Keycode.java.
UP,DOWN,LEFT,RIGHT,FIRE=0x10,0x11,0x0e,0x0f,0xa7
SOFTL,SOFTR,CALL,END,MENU,CLEAR,EDIT=0xa4,0xa5,0xb4,0xb5,0x94,1,0x12
MEDIA_PLAY,MEDIA_STOP,VOLUME_UP,VOLUME_DOWN=0x9c,0x9d,0xa2,0xa3

def n73():
    # The N73's native portrait LCD is 240:320. Leave a little more headroom
    # above the glass so the top camera assembly is fully framed.
    f=Front('n73',430,932,(41,80,345,485))
    f.part((0,-8,430,942),43,(49,52,52),10,.2,2.7)
    f.part((6,-5,424,939),39,(112,117,117),6,.48,1.4)
    f.part((13,-5,417,939),34,(39,41,42),8,.13,16)
    f.nokia(41,13,101,18);f.text('N73',117,45,20)
    f.part((174,20,254,42),10,(9,12,13),3,.4,.6)
    f.part((182,26,246,37),5,(166,172,171),3,.9,.5)
    f.line([(188,30),(239,30)],'#e0e6df',.7)
    f.line([(189,33),(239,33)],'#7d8989',.45)
    f.part((264,22,286,41),6,(64,66,67),2,.3,1)
    f.part((268,26,281,37),4,(96,104,106),2,.5,.5)
    f.part((298,4,356,61),14,(209,213,211),3,.9,.4,material='anodized')
    f.part((302,8,352,57),12,(8,9,10),3,.3,.5)
    for d,col in [(10,(29,32,34)),(17,(44,47,49)),(23,(11,14,17))]:
        f.part((305+d/2,11+d/2,349-d/2,54-d/2),20,col,2,.15,.3)
    f.lens(327,33,14)
    f.lcd()
    # Silver softkey plate and a raised joystick on its short dark stem.
    f.part((28,600,402,927),41,(5,7,8),3,.1,1)
    f.part((31,602,400,716),30,(14,17,18),5,.4,2)
    f.part((43,616,388,715),27,(242,244,239),8,.95,.9,bulge=1.8)
    f.part((61,627,371,706),21,(216,221,217),7,.8,1.8,bulge=-6)
    f.part((152,632,279,702),17,(24,28,29),4,.75,.7)
    f.part((156,635,275,700),15,(219,226,219),6,1,.7,bulge=-3)
    f.part((178,634,253,703),26,(11,13,14),4,.6,.5)
    f.part((182,637,249,700),24,(238,241,235),4,1,.5,bulge=1)
    f.part((188,643,243,694),19,(9,12,13),3,.4,.4)
    stick_base=f.im.copy()
    f.part((194,649,237,690),14,(221,229,224),6,.9,.6,bulge=3.5)
    f.part((198,652,233,687),11,(246,248,241),5,.45,.7,bulge=1.8)
    f.navigation(stick_base,f.mask((194,649,238,691),14),[194,649,238,691],[215.5,669.5],26,joystick=True,confirm=False)
    f.line([(44,667),(156,667)],(6,8,8),2);f.line([(276,667),(388,667)],(5,7,8),2)
    f.line([(45,669),(156,669)],(228,234,230),.65);f.line([(276,669),(387,669)],(224,230,225),.65)
    f.decal('n73-softl',(80,634,113,650),'#3d6776');f.decal('n73-softr',(315,634,348,650),'#3d6776')
    f.decal('n73-call',(80,677,114,697),'#318a70');f.decal('n73-end',(315,677,350,697),'#c44135')
    # Four separate sculpted caps follow the same slanted, tapered outline as
    # the marked right softkey; their common plate and centre seam stay visible.
    for points,code in [
        ([(46,632),(64,619),(150,620),(150,661),(46,662)],SOFTL),
        ([(280,620),(365,619),(384,632),(384,662),(280,661)],SOFTR),
        ([(46,673),(150,674),(150,708),(69,708),(46,696)],CALL),
        ([(280,674),(384,673),(384,696),(361,708),(280,708)],END)]:
        xs,ys=zip(*points);f.hit((min(xs),min(ys),max(xs),max(ys)),code,points=points)
    # The yellow-marked N73 cap is one analog-style joystick, with no centre
    # select switch. Direction keys are emitted by dragging this single cap.
    labels=[('1','@'),('2','abc'),('def','3'),('4','ghi'),('5','jkl'),('mno','6'),('7','pqrs'),('8','tuv'),('wxyz','9'),('*','+'),('0',''),('','#')]
    codes=[49,50,51,52,53,54,55,56,57,42,48,127]
    for i,((a,b),code) in enumerate(zip(labels,codes)):
        row,col=divmod(i,3);x=73+col*96;y=722+row*48
        if row==3:
            points=[(x,y),(x+89,y-1),(x+89,y+39),(x+79,y+44),(x+12,y+43),(x+2,y+37)]
        else:
            points=[(x,y),(x+89,y+1),(x+88,y+42),(x+49,y+44),(x+1,y+41)]
        f.key((x,y-1,x+89,y+44),code,r=2,points=points,bulge=.65)
        if row>0:
            f.part(points=[(x+10,y-3),(x+83,y-3),(x+80,y),(x+14,y+1)],color=(185,195,193),bevel=1.1,metal=.85,rough=1)
        if i==4:
            f.line([(x+3,y+12),(x+3,y+31)],'#929d9c',1.2);f.line([(x+86,y+12),(x+86,y+31)],'#929d9c',1.2)
        f.decal('n73-'+str(i),(x+18,y+10,x+76,y+34))
    # The physical key immediately left of 1/4 is the N73 centre/select key.
    # Joystick movement itself supplies directions only.
    f.key((33,720,69,807),FIRE,r=2,bulge=.7)
    f.decal('n73-menu',(40,750,64,781),'#b9d0d6')
    f.key((33,812,69,904),EDIT,r=2,points=[(33,812),(69,813),(69,904),(47,902),(35,886)]);f.decal('n73-edit',(41,851,63,875))
    f.key((365,720,399,807),65,r=3);f.decal('n73-diamond',(372,756,392,777))
    f.key((365,812,399,904),CLEAR,r=3,points=[(365,813),(399,812),(397,885),(389,902),(365,904)]);f.decal('n73-clear',(373,849,392,877))
    f.finish()

def n86():
    # Match the landscape body's reference proportions and the native 4:3 LCD.
    f=Front('n86',1100,490,(337,57,510,387),base=(97,104,105))
    f.part((0,-1,1100,491),76,(51,59,63),9,.7,1)
    f.part((6,5,1095,485),71,(220,225,224),7,.9,.5,material='anodized')
    f.part((15,9,1093,481),67,(57,64,68),4,.9,.7)
    f.part((24,17,1082,472),58,(242,243,239),5,.62,1.1,material='anodized')
    f.part((28,23,967,468),58,(39,44,47),4,.9,.5)
    f.part((34,29,956,463),53,(242,243,239),5,.62,1.1,material='anodized')
    f.lcd()
    f.nokia(864,72,112,21,color='#929899',rotate=-90)
    f.text('N86 8MP',862,390,19,color='#8c9294',rotate=-90)
    f.part((903,219,918,276),7,(174,184,165),3,.6,.8,material='anodized')
    f.part((901,38,918,55),8,(163,169,167),3,.4,.8,material='anodized')
    f.part((900,374,923,397),11,(153,160,162),3,.3,.7,material='anodized')
    f.part((95,191,226,320),35,(103,111,114),2,.65,.7)
    pad_base=f.im.copy()
    f.part((99,195,222,317),33,(234,239,236),2.5,.9,.7)
    f.part((104,200,217,312),29,(70,77,79),1.5,.7,.6)
    f.part((108,204,213,308),27,(247,248,244),2,.65,.8)
    f.part((114,210,207,302),24,(226,231,227),4,.4,1.4,bulge=1)
    f.navigation(pad_base,f.mask((99,195,222,317),33),[95,191,226,320],[160.5,255.5],36,confirm=True)
    pad=f.mask((102,198,218,313),29)
    center=f.mask((124,218,197,292),18)
    ring=np.minimum(np.asarray(pad),255-np.asarray(center))
    for box,code in [((102,191,218,218),UP),((102,292,218,320),DOWN),((95,218,124,292),LEFT),((197,218,226,292),RIGHT)]:
        shape=np.minimum(ring,np.asarray(f.mask(box)))
        f.hit(box,code,mask=Image.fromarray(shape))
    f.hit((124,218,197,292),FIRE,r=18)
    # The rotated handset has an oblique capsule here, not a square key.
    capsule=[];angle=math.radians(49)
    for end,start in [(20,-90),(-20,90)]:
        for a in np.linspace(start,start+180,13):
            u=end+9*math.cos(math.radians(a));v=9*math.sin(math.radians(a))
            capsule.append((91+u*math.cos(angle)-v*math.sin(angle),117+u*math.sin(angle)+v*math.cos(angle)))
    xs,ys=zip(*capsule)
    f.key((min(xs),min(ys),max(xs),max(ys)),MENU,metal=True,points=capsule,bulge=1.7)
    f.key((248,88,266,143),SOFTL,metal=True,r=8)
    f.key((248,357,266,412),SOFTR,metal=True,r=8)
    f.key((70,357,86,414),CLEAR,metal=True,r=7)
    f.key((138,61,200,76),CALL,metal=True,r=7);f.line([(146,68),(192,68)],'#74a48f',4)
    f.key((138,431,201,446),END,metal=True,r=7);f.line([(145,438),(194,438)],'#bb5060',4)
    f.text('C',113,386,19,color='#b4bab9',rotate=-90)
    for i,(a,b,code) in enumerate([('▶▶','−',54),('■','○',53),('Ⅱ◀','◧',52),('◀◀','+',50)]):
        y=98+i*78;f.key((986,y,1057,y+69),code,metal=True,r=23)
        f.decal('n86-media-'+str(i),(1007,y+17,1033,y+31),'#4c5557')
        f.decal('n86-symbol-'+str(i),(1006,y+38,1032,y+57),'#00628a')
    f.line([(970,95),(970,420)],'#777e80',.8)
    f.finish()

def qd_procedural_legacy():
    f=Front('qd',1000,580,(345,94,300,357),base=(29,31,31))
    outer=[(0,135),(8,76),(31,47),(113,27),(258,10),(502,0),(746,11),(899,38),(964,67),(993,111),(1000,203),(995,324),(975,413),(930,483),(850,529),(707,563),(514,580),(288,568),(133,540),(55,497),(14,421),(0,320)]
    f.part(points=outer,color=(48,52,51),bevel=12,metal=.2,rough=7,bulge=2)
    inner=[(17,134),(28,83),(76,62),(267,27),(507,18),(742,29),(911,60),(966,96),(978,166),(975,288),(955,397),(908,465),(829,506),(694,541),(515,558),(298,547),(150,520),(75,480),(35,410),(18,309)]
    f.part(points=inner,color=(139,141,137),bevel=18,metal=.35,rough=5.8,bulge=4,material='satin')
    f.part((66,53,944,518),176,(190,193,187),12,.7,2.2,bulge=2)
    f.part((103,62,899,514),162,(30,38,41),9,.3,6.5,bulge=2)
    f.lcd();f.nokia(447,64,99,16)
    f.decal('qd-n',(409,466,455,491),'#bf3e32');f.decal('qd-gage',(461,466,589,491),'#d9dedb')
    f.part(points=[(327,99),(305,100),(280,124),(267,191),(270,374),(294,435),(323,449)],color=(198,201,195),bevel=7,metal=.65,rough=1)
    f.part(points=[(668,101),(688,107),(710,141),(723,214),(719,367),(693,436),(667,449)],color=(205,209,201),bevel=6,metal=.65,rough=1)
    f.line([(309,143),(305,182),(306,377),(312,409)],'#e0e2df',1.8)
    f.line([(683,149),(688,187),(688,371),(684,409)],'#eef0eb',1.8)
    f.part((123,137,277,292),76,(6,11,14),6,.8,1)
    pad_base=f.im.copy()
    f.part((130,144,270,285),70,(91,107,111),7,.85,2.5,bulge=1.5)
    # Sculpted cross with lobed tips.
    cross=[(182,153),(214,153),(223,168),(224,184),(241,195),(257,197),(260,226),(243,237),(227,238),(215,254),(213,272),(183,272),(176,253),(164,240),(145,238),(137,218),(144,199),(164,192),(177,181),(179,164)]
    f.part(points=cross,color=(207,214,205),bevel=7,metal=.85,rough=2.4,bulge=3)
    # The four directions are engraved into one continuous eight-way keycap.
    # The QD's separate confirm key is the round key immediately to its right.
    for points in [
        [(199,160),(192,174),(196,174),(196,180),(202,180),(202,174),(206,174)],
        [(199,265),(192,251),(196,251),(196,245),(202,245),(202,251),(206,251)],
        [(143,212),(157,205),(157,209),(163,209),(163,215),(157,215),(157,219)],
        [(255,212),(241,205),(241,209),(235,209),(235,215),(241,215),(241,219)],
    ]:
        f.part(points=points,color=(101,116,117),bevel=1.3,metal=.25,rough=.8)
        f.part(points=[(x,y-.8) for x,y in points],color=(236,239,232),bevel=.55,metal=.55,rough=.65)
    f.navigation(pad_base,f.mask((130,144,270,285),70),[130,144,270,285],[199,212],18)
    for box,code in [((174,151,224,193),UP),((173,238,225,276),DOWN),((137,192,176,239),LEFT),((225,192,262,239),RIGHT)]:
        crop=f.mask(box,8);shape=np.minimum(np.asarray(crop),np.asarray(f.mask(points=cross)))
        f.hit(box,code,mask=Image.fromarray(shape))
    f.part((249,283,304,339),27,(116,131,132),4,.7,.6);f.part((254,288,299,334),23,(218,221,213),3,.65,.7)
    f.line([(264,310),(275,321),(290,299)],'#576466',3);f.hit((249,283,304,339),FIRE,r=27)
    f.key((167,285,242,353),SOFTL,points=[(148,278),(178,291),(230,287),(243,349),(172,353)],r=5)
    f.key((179,357,264,419),MENU,points=[(174,362),(239,356),(263,409),(226,418),(201,405)],color='#ed6246')
    f.decal('qd-menu',(205,372,231,401),'#dd5037')
    f.key((232,420,300,455),EDIT,points=[(233,425),(266,418),(300,447),(269,459)],color='#ef6547');f.decal('qd-edit',(251,430,283,447),'#d94b32')
    f.key((179,432,265,479),CALL,metal=True,points=[(179,446),(223,432),(265,464),(232,479)],r=10);f.decal('qd-call',(203,444,239,469),'#4b9445')
    # Curved, staggered keypad. Columns 1/3 use satin silver, centre black.
    rows=[
        [[(709,148),(749,137),(780,210),(714,220)],[(754,135),(803,126),(846,203),(784,211)],[(808,126),(849,119),(882,146),(909,197),(851,203)]],
        [[(714,222),(780,214),(787,278),(716,282)],[(783,215),(848,207),(862,276),(791,278)],[(852,207),(911,200),(929,237),(933,275),(865,277)]],
        [[(715,285),(787,283),(782,348),(712,345)],[(791,283),(864,282),(856,354),(786,350)],[(867,282),(933,280),(929,323),(918,354),(860,354)]],
        [[(711,349),(779,353),(754,414),(706,404)],[(783,355),(854,359),(823,425),(759,416)],[(858,358),(917,358),(898,397),(871,433),(828,426)]]
    ]
    legends=[('1','oo'),('2','abc'),('3','def'),('4','ghi'),('5','jkl'),('6','mno'),('7','pqrs'),('8','tuv'),('9','wxyz'),('*','+'),('0','o'),('#','^')]
    for i,((a,b),code) in enumerate(zip(legends,[49,50,51,52,53,54,55,56,57,42,48,127])):
        row,col=divmod(i,3);points=rows[row][col];xs,ys=zip(*points);box=(min(xs),min(ys),max(xs),max(ys));x0,y0,x1,y1=box
        f.key(box,code,metal=col!=1,points=points,bulge=4.8,gloss=.85 if col==1 else .7)
        # Offset legends follow the staggered columns rather than a square grid.
        cx=sum(xs)/len(xs);cy=sum(ys)/len(ys)
        # Draw a clean red foreground legend. The old masks were cut from a
        # perspective photo and left pale source pixels around several keys.
        f.text(a,cx,cy-5,19 if i<9 else 17,'#cf422d',True)
        f.text(b,cx,cy+13,11,'#cf422d',True)
    f.key((733,74,859,121),CLEAR,metal=True,points=[(741,74),(793,84),(858,112),(806,124),(735,94)],bulge=3);f.decal('qd-clear',(784,83,807,104),'#ce412b')
    f.key((708,99,803,140),65,points=[(722,101),(747,108),(803,131),(753,141),(710,120)]);f.decal('qd-pencil',(735,108,766,130),'#d64b35')
    f.key((716,425,794,454),SOFTR,r=6);f.decal('qd-softr',(739,431,772,449),'#dc5138')
    f.key((787,432,887,482),END,metal=True,points=[(819,432),(887,438),(861,460),(796,482),(784,460)],bulge=3);f.decal('qd-end',(817,444,854,469),'#ce412e')
    for i in range(3):f.part((922+i*10,128+i*30,929+i*10,144+i*30),4,(19,24,24),2,.1,.4)
    f.finish()


def qd():
    """Rebuild the QD chassis from the user's latest render and map its controls."""
    size=(2468,1428)
    screen=(873,238,770,909)
    f=reference_shell('qd','qd-reference.webp',1000,579,screen,894)

    # The user-marked render supplies the visible QD keycaps as well as their
    # hit and pressed silhouettes. Previously only the pressed atlas used the
    # new masks, leaving the old square caps visible at rest.
    marked=Image.open(ROOT/'tools/phone_materials/reference/qd-marked-controls.png').convert('RGB')
    marked_pixels=np.asarray(marked,dtype=np.float32)
    mr,mg,mb=marked_pixels[:,:,0],marked_pixels[:,:,1],marked_pixels[:,:,2]
    red_caps=(mr>65)&(mr>mg*1.22)&(mr>mb*1.1)
    arrow_softkey_rois={
        (340,700,660,980),   # upper blue arrow: blank left soft key
        (590,1140,790,1270), # lower blue arrow: slanted left soft key
        (1970,1165,2205,1260), # red arrow: horizontal right/back key
    }

    def marked_key(roi,code,old_outline=None):
        if old_outline and roi in arrow_softkey_rois:
            # Hand-traced caps are expressed in marked-controls image pixels,
            # which has a different resolution from the clean chassis source.
            # Normalize against the marked image itself or every cap shifts.
            outline=reference_polygon(f,old_outline,marked.size)
            xs,ys=zip(*outline)
            mask=f.mask(points=outline)
            f.hit((min(xs),min(ys),max(xs),max(ys)),code,mask=mask,texture_mask=mask)
            return
        x0,y0,x1,y1=roi
        cap=red_caps[y0:y1,x0:x1]
        cap=ndimage.binary_closing(cap,structure=np.ones((5,5)))
        cap=ndimage.binary_fill_holes(cap)
        labels,count=ndimage.label(cap)
        sizes=np.bincount(labels.ravel());sizes[0]=0
        if not sizes.any():raise ValueError(f'No marked QD cap for key {code} in {roi}')
        cap=ndimage.binary_dilation(labels==int(sizes.argmax()),iterations=2)
        source_mask=Image.new('L',marked.size)
        source_mask.paste(Image.fromarray(np.uint8(cap)*255),(x0,y0))
        mask=source_mask.resize(f.size,Image.Resampling.NEAREST)
        bx=mask.getbbox()
        f.hit(tuple(v/S for v in bx),code,mask=mask,texture_mask=mask)

    def hit_polygon(points,code):
        xs,ys=zip(*points)
        reference_hit(f,(min(xs),min(ys),max(xs),max(ys)),size,code,points=points)

    # The silver eight-way D-pad is one continuous moving cap. Its four
    # directional scan-code zones all feed the same nine-state tilted sprite.
    cross=[
        (407,350),(421,338),(509,338),(526,355),(530,425),(548,448),
        (578,462),(618,468),(641,487),(645,557),(625,579),(580,584),
        (550,600),(529,622),(526,699),(509,717),(420,717),(402,699),
        (398,630),(379,607),(349,589),(311,584),(291,565),(289,494),
        (310,475),(350,463),(380,447),(400,425)
    ]
    nav_base=f.im.copy()
    nav_mask=f.mask(points=reference_polygon(f,cross,size))
    nav_bounds=reference_box(f,(289,338,645,717),size)
    nav_center=(468*1000/size[0],538*579/size[1])
    f.navigation(nav_base,nav_mask,nav_bounds,nav_center,14,joystick=False,confirm=False,
                 hit_polygon=reference_polygon(f,cross,size))
    # The cross is a single navigation assembly, not four ordinary keycaps.
    # Sector selection is handled by its polygonal navigation hit region.

    # Red-marked physical caps: joystick confirm and left-side controls,
    # followed by the twelve keypad caps and the top/bottom utility keys.
    marked_controls=[
        ((610,740,820,960),FIRE,[(637,734),(666,716),(711,724),(744,750),(756,786),(748,822),(722,851),(685,866),(650,852),(624,826),(614,791),(620,758)]),
        ((340,700,660,980),SOFTL,[(397,770),(568,775),(591,901),(578,927),(425,923),(396,895)]),
        ((400,940,730,1160),MENU,[(405,895),(618,882),(645,1048),(617,1090),(430,1102),(397,1062)]),
        ((400,1110,700,1370),CALL,[(405,1090),(615,1080),(635,1158),(610,1200),(455,1208),(420,1188)]),
        ((1900,350,2160,610),49,[(1816,352),(1983,340),(2010,546),(1840,565)]),
        ((2070,330,2370,600),50,[(1987,336),(2168,315),(2197,535),(2010,548)]),
        ((2250,290,2590,570),51,[(2172,310),(2335,290),(2360,525),(2198,540)]),
        ((1880,550,2210,810),52,[(1822,560),(2008,548),(2018,735),(1815,747)]),
        ((2100,540,2430,820),53,[(2011,550),(2197,538),(2204,735),(2017,744)]),
        ((2310,520,2656,830),54,[(2199,538),(2360,523),(2367,715),(2202,736)]),
        ((1840,740,2200,980),55,[(1814,755),(2018,741),(2005,925),(1804,942)]),
        ((2110,730,2420,1000),56,[(2020,744),(2207,735),(2194,923),(2005,932)]),
        ((2310,700,2656,1010),57,[(2209,733),(2365,713),(2350,907),(2194,923)]),
        ((1800,900,2200,1160),42,[(1802,930),(2004,920),(1978,1080),(1785,1090)]),
        ((2050,920,2420,1210),48,[(2008,920),(2195,910),(2175,1074),(1978,1082)]),
        ((2250,900,2600,1230),127,[(2199,910),(2350,895),(2325,1068),(2175,1078)]),
        ((1980,180,2390,360),CLEAR,[(1900,188),(2140,188),(2220,230),(2140,306),(1960,300),(1850,255)]),
        ((1850,240,2200,410),EDIT,[(1830,250),(1860,238),(1980,270),(2030,315),(1980,355),(1820,320)]),
        # Both blue-arrow caps are left soft keys. The lower slanted cap is
        # separate from the adjacent green call key.
        ((590,1140,790,1270),SOFTL,[(611,1162),(740,1151),(771,1227),(751,1257),(626,1226),(601,1195)]),
        # Red arrow: the whole horizontal cap below '*' is right soft/back.
        ((1970,1165,2205,1260),SOFTR,[(1990,1187),(2149,1175),(2191,1193),(2184,1225),(2135,1249),(1995,1230),(1979,1210)]),
        ((2070,1130,2450,1380),END,[(2045,1091),(2238,1095),(2291,1131),(2244,1218),(2197,1244),(2055,1175),(2010,1134)]),
    ]
    for roi,code,old_outline in marked_controls:
        marked_key(roi,code,old_outline)
    f.finish()


def n8():
    f=Front('n8',430,850,(35,110,360,640),base=(123,128,127))
    outline=[(0,103),(7,45),(23,-8),(407,-8),(423,45),(430,103),(430,747),(422,807),(407,858),(23,858),(7,807),(0,747)]
    f.part(points=outline,color=(153,157,155),bevel=14,metal=.7,rough=1.8,bulge=4,material='anodized')
    inner=[(5,103),(11,48),(27,-8),(403,-8),(419,48),(425,103),(425,747),(418,804),(403,858),(27,858),(11,804),(5,747)]
    f.part(points=inner,color=(230,233,230),bevel=11,metal=.62,rough=1.4,bulge=5.5,material='anodized')
    f.part((12,57,418,792),42,(41,46,47),5,.8,.4)
    f.part((17,61,413,788),36,(221,224,219),5,.9,.5)
    f.part((24,68,406,782),30,(4,6,8),4,.3,.25)
    # Black glass optical layer leaves the native LCD completely clear.
    f.part((28,72,402,778),28,(9,12,14),2,.2,.25)
    f.nokia(181,84,100,17,color='#c2c7c5');f.text('N8',62,96,12)
    for x,r in [(360,11),(325,8)]:
        f.part((x-r,99-r,x+r,99+r),r,(19,23,25),2,.2,.4)
        f.part((x-r/2,99-r/2,x+r/2,99+r/2),r/2,(28,33,34),1,.12,.3)
    # Hairline seam, hardware menu button and the lower shell's tapered edges.
    # Continuous silver rails remain visible beside the glass, as on the N8
    # front shell, instead of breaking into two isolated hairlines.
    f.line([(12,108),(12,744)],'#737b7a',1.1);f.line([(17,108),(17,744)],'#f0f2ef',.7)
    f.line([(413,108),(413,744)],'#f2f4f0',.7);f.line([(418,108),(418,744)],'#747c7b',1.1)
    f.line([(4,103),(12,103)],'#676e6e',1);f.line([(418,103),(427,103)],'#707878',1)
    f.line([(4,748),(14,748)],'#676e6e',1);f.line([(417,747),(427,747)],'#707878',1)
    f.key((58,798,142,832),MENU,metal=True,r=12,bulge=2.6)
    f.part((300,789,309,795),3,(86,94,90),2,.3,.5)
    f.lcd(glass=True);f.finish()


def reference_shell(name, source_name, width, height, screen, seed):
    """Use a supplied complete handset render, adding only a fine matte grain.

    The liquid crystal window stays untouched so emulator pixels remain clear.
    Shell artwork is stored in the repository, making the export reproducible.
    """
    f=Front(name,width,height,screen)
    source=Image.open(ROOT/'tools/phone_materials/reference'/source_name).convert('RGB')
    base=source.resize((width,height),Image.Resampling.LANCZOS)
    rgb=np.asarray(base,dtype=np.float32).copy()
    rng=np.random.default_rng(seed)
    fine=gaussian_filter(rng.normal(size=(height,width)).astype(np.float32),.62)
    fine/=max(.001,float(fine.std()))
    broad=gaussian_filter(rng.normal(size=(height,width)).astype(np.float32),1.8)
    broad/=max(.001,float(broad.std()))
    grain=(fine*.76+broad*.54)*1.35
    keep=Image.new('L',(width,height),255)
    ImageDraw.Draw(keep).rectangle(
        (round(screen[0]),round(screen[1]),round(screen[0]+screen[2]-1),round(screen[1]+screen[3]-1)),
        fill=0)
    textured=rgb+grain[:,:,None]*np.array([1.,.98,.94],dtype=np.float32)[None,None,:]
    textured=np.where((np.asarray(keep)>0)[:,:,None],textured,rgb)
    matte=Image.fromarray(np.uint8(np.clip(textured,0,255)))
    f.im=matte.resize(f.size,Image.Resampling.LANCZOS)
    return f


def reference_box(f,box,source_size):
    sx=f.w/source_size[0];sy=f.h/source_size[1]
    return tuple(value*scale for value,scale in zip(box,(sx,sy,sx,sy)))


def reference_polygon(f,points,source_size):
    sx=f.w/source_size[0];sy=f.h/source_size[1]
    return [(x*sx,y*sy) for x,y in points]


def reference_hit(f,box,source_size,code,r=2,points=None):
    transformed=reference_box(f,box,source_size)
    if points is None:
        f.hit(transformed,code,r=r)
    else:
        f.hit(transformed,code,points=reference_polygon(f,points,source_size))


def reference_n73():
    size=(852,1846)
    screen=(47,85,332,436)
    f=reference_shell('n73','n73-reference.webp',430,932,screen,731)
    # The raised centre cap rocks as one joystick; its surrounding collar also
    # accepts the eight cardinal/diagonal directions.
    base=f.im.copy()
    # User's yellow outline marks only the movable yellow cap. Keep both the
    # hit region and the pressed sprite off the broad silver joystick bezel.
    joystick_cap=[(390,1272),(397,1267),(408,1265),(445,1265),(455,1270),
                  (462,1278),(465,1288),(465,1326),(461,1335),(454,1342),
                  (445,1347),(409,1347),(399,1343),(391,1336),(388,1327),(388,1286)]
    cap_bounds=(min(x for x,y in joystick_cap),min(y for x,y in joystick_cap),
                max(x for x,y in joystick_cap),max(y for x,y in joystick_cap))
    cap_poly=reference_polygon(f,joystick_cap,size)
    f.navigation(base,f.mask(points=cap_poly),list(reference_box(f,cap_bounds,size)),
        [404*430/852,1310*932/1846],13,joystick=True,confirm=False,hit_polygon=cap_poly)
    # Soft, call and side controls follow each photographed cap silhouette.
    def hit_poly(points,code):
        xs,ys=zip(*points)
        reference_hit(f,(min(xs),min(ys),max(xs),max(ys)),size,code,points=points)
    # The colored reference marks the actual four cap faces.  They share the
    # same compact rounded/slanted outline; the surrounding silver strip is
    # deliberately excluded from both hit testing and press feedback.
    for points,code in [
        ([(163,1230),(294,1230),(302,1238),(302,1290),(294,1298),(160,1298),(152,1290),(152,1238)],SOFTL),
        ([(163,1300),(294,1300),(302,1308),(302,1360),(294,1368),(160,1368),(152,1360),(152,1308)],CALL),
        ([(561,1230),(692,1230),(700,1238),(700,1290),(692,1298),(558,1298),(550,1290),(550,1238)],SOFTR),
        ([(561,1300),(692,1300),(700,1308),(700,1360),(692,1368),(558,1368),(550,1360),(550,1308)],END),
        # The tall physical key alongside the 1/4 column is centre/select.
        ([(78,1405),(151,1400),(153,1572),(140,1586),(79,1577)],FIRE),
        ([(78,1588),(151,1592),(150,1757),(136,1775),(78,1764)],EDIT),
        ([(701,1401),(775,1399),(774,1574),(762,1585),(701,1578)],MENU),
        ([(700,1588),(775,1586),(773,1755),(758,1775),(699,1765)],CLEAR),
    ]:
        hit_poly(points,code)

    # Twelve separate keycaps. The masks trace the bevelled, subtly bowed
    # corners of the photographed buttons, so hit and pressed layers keep the
    # original cap and legend silhouette instead of a rectangular overlay.
    codes=[49,50,51,52,53,54,55,56,57,42,48,127]
    key_polygons=[
        [(155,1401),(329,1402),(332,1484),(322,1494),(160,1494),(154,1485)],
        [(336,1401),(513,1402),(515,1485),(504,1494),(337,1494)],
        [(518,1400),(697,1399),(696,1485),(684,1495),(519,1494)],
        [(154,1498),(330,1497),(332,1579),(321,1589),(158,1588),(153,1578)],
        [(336,1498),(514,1498),(515,1579),(503,1589),(337,1588)],
        [(519,1497),(696,1496),(696,1579),(684,1589),(519,1588)],
        [(155,1592),(331,1592),(332,1673),(320,1683),(158,1682),(153,1673)],
        [(336,1592),(514,1592),(514,1674),(503,1683),(337,1682)],
        [(519,1591),(696,1590),(695,1674),(684,1683),(519,1682)],
        [(155,1686),(331,1685),(332,1760),(319,1774),(163,1774),(154,1760)],
        [(336,1686),(514,1685),(514,1761),(502,1774),(337,1774)],
        [(519,1685),(696,1684),(695,1760),(683,1774),(519,1773)],
    ]
    for points,code in zip(key_polygons,codes):
        hit_poly(points,code)
    f.finish()


def reference_n86():
    size=(1844,853)
    screen=(315,49,506,381)
    f=reference_shell('n86','n86-reference.webp',1060,490,screen,862)
    base=f.im.copy()
    # The moving surface is the visible rounded-square D-pad, rather than the
    # much larger silver surround. This keeps both its press relief and input
    # area on the photographed cap.
    pad_outline=[(181,308),(318,308),(335,314),(348,327),(356,344),(359,362),
                 (359,487),(355,506),(345,520),(329,531),(312,537),(184,537),
                 (167,532),(152,520),(144,505),(140,487),(140,361),(144,343),
                 (153,327),(167,314)]
    pad_box=(140,308,359,537)
    pad_poly=reference_polygon(f,pad_outline,size)
    f.navigation(base,f.mask(points=pad_poly),list(reference_box(f,pad_box,size)),
        [249*1060/1844,422*490/853],12,joystick=True,confirm=True,hit_polygon=pad_poly)

    def capsule(box,code,rx,ry):
        x0,y0,x1,y1=box
        points=[]
        for cx,cy,start in ((x1-rx,y0+ry,-90),(x1-rx,y1-ry,0),
                            (x0+rx,y1-ry,90),(x0+rx,y0+ry,180)):
            for angle in np.linspace(start,start+90,7):
                a=math.radians(angle)
                points.append((cx+rx*math.cos(a),cy+ry*math.sin(a)))
        reference_hit(f,box,size,code,points=points)

    # Trace each individual cap. In the landscape render the left soft key is
    # the upper inner capsule; the lower-left capsule is marked C (Clear).
    slanted_menu=[(82,141),(88,133),(100,129),(111,132),(179,215),
                  (187,226),(186,237),(179,246),(168,249),(158,245)]
    reference_hit(f,(75,128,190,247),size,MENU,points=slanted_menu)
    capsule((204,88,316,125),CALL,17,17)
    capsule((397,137,433,245),SOFTL,17,17)
    capsule((204,707,316,749),END,19,19)
    capsule((81,594,121,702),CLEAR,19,19)
    capsule((397,594,433,702),SOFTR,17,17)

    # The right-edge four caps are media/volume controls, not digits. Their
    # separate traced contours also keep each depressed texture on its own key.
    media_caps=[
        ([(1674,160),(1684,154),(1775,154),(1788,162),(1794,174),(1794,252),
          (1789,265),(1777,273),(1685,273),(1673,265),(1667,252),(1667,178)],VOLUME_DOWN),
        ([(1674,300),(1684,291),(1775,291),(1788,299),(1794,312),(1794,389),
          (1789,402),(1777,411),(1685,411),(1673,402),(1667,389),(1667,314)],MEDIA_STOP),
        ([(1674,437),(1684,426),(1775,426),(1788,434),(1794,447),(1794,523),
          (1789,537),(1777,546),(1685,546),(1673,537),(1667,523),(1667,450)],MEDIA_PLAY),
        ([(1674,575),(1684,563),(1775,563),(1788,571),(1794,585),(1794,660),
          (1789,675),(1777,684),(1685,684),(1673,675),(1667,660),(1667,587)],VOLUME_UP),
    ]
    for points,code in media_caps:
        xs,ys=zip(*points)
        reference_hit(f,(min(xs),min(ys),max(xs),max(ys)),size,code,points=points)
    f.finish()


def reference_n8():
    size=(1569,3080)
    screen=(53,132,324,586)
    f=reference_shell('n8','n8-reference.webp',430,850,screen,873)
    # The T7/N8 has one physical key.  Keep its hit mask inside the
    # photographed pill-shaped cap instead of making the whole bounding box
    # clickable; the rounded corners are visible in the supplied outline.
    # Match the single red-outlined N8/T7 button; the surrounding lower glass
    # and bezel are not part of the control.
    x0,y0,x1,y1=228,2926,500,2997
    radius=25
    points=[]
    for cx,cy,start in ((x1-radius,y0+radius,-90),
                        (x1-radius,y1-radius,0),
                        (x0+radius,y1-radius,90),
                        (x0+radius,y0+radius,180)):
        for angle in np.linspace(start,start+90,9):
            a=math.radians(angle)
            points.append((cx+radius*math.cos(a),cy+radius*math.sin(a)))
    reference_hit(f,(x0,y0,x1,y1),size,MENU,points=points)
    f.finish()


if __name__ == '__main__':
    for name in (sys.argv[1:] or ['n73','n86','qd','n8']):
        {'n73':reference_n73,'n86':reference_n86,'qd':qd,'n8':reference_n8}[name]()
