"""Extract only grain and lettering masks from the five supplied references.

No photographed keycaps, chassis, screen content or backgrounds are exported.
Run with an optional directory containing the original user-supplied images.
"""
from pathlib import Path
import sys
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, label

SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('C:/Users/Administrator/Downloads')
OUT = Path(__file__).resolve().parent / 'phone_materials'
(OUT / 'legends').mkdir(parents=True, exist_ok=True)

def grain(filename, box, name):
    a = np.asarray(Image.open(SOURCE / filename).convert('L').crop(box), dtype=np.float32)
    a -= gaussian_filter(a, 5)
    a -= a.mean(axis=0, keepdims=True)
    a -= a.mean(axis=1, keepdims=True)
    a *= 14 / max(1, a.std())
    Image.fromarray(np.uint8(np.clip(a + 128, 0, 255))).save(OUT / (name + '.png'))

def legend(im, box, name, channel='white'):
    rgb = np.asarray(im.crop(box).convert('RGB'), dtype=np.float32)
    r, g, b = rgb.transpose(2, 0, 1)
    if channel == 'red': a = np.clip((r - np.maximum(g, b) - 15) / 90, 0, 1)
    elif channel == 'green': a = np.clip((g - r - 5) / 40, 0, 1)
    elif channel == 'teal': a = np.clip((np.minimum(g, b) - r - 3) / 29, 0, 1)
    elif channel == 'dark': a = np.clip((160 - np.maximum(np.maximum(r, g), b)) / 75, 0, 1)
    elif channel == 'blue': a = np.clip((b - r - 8) / 70, 0, 1)
    else: a = np.clip((np.minimum(np.minimum(r, g), b) - 120) / 100, 0, 1)
    components, count = label(a > .12)
    sizes = np.bincount(components.ravel())
    if count: a[sizes[components] < 5] = 0
    image = Image.fromarray(np.uint8(a * 255))
    bounds = image.getbbox()
    if bounds is None: raise ValueError(name)
    image.crop(bounds).save(OUT / 'legends' / (name + '.png'))

grain('81tw12k35hL.jpg', (360, 190, 970, 280), 'anodized')
grain('f88b59ded710989f.jpg', (950, 177, 1190, 207), 'qd-satin')
im = Image.open(SOURCE / '1795526410057.png')
boxes = [(294,2270,440,2365),(590,2270,751,2365),(882,2265,1060,2360),
         (290,2450,459,2527),(590,2450,752,2527),(878,2450,1065,2527),
         (291,2600,475,2680),(590,2600,760,2680),(878,2600,1082,2680),
         (309,2765,455,2850),(595,2760,754,2850),(890,2763,1020,2840)]
for i, box in enumerate(boxes): legend(im, box, 'n73-' + str(i))
for name, box in [('menu',(120,2350,190,2455)),('edit',(125,2665,187,2730)),
                  ('diamond',(1140,2365,1210,2440)),('clear',(1140,2655,1210,2730))]:
    legend(im, box, 'n73-' + name)
for name, box, channel in [('softl',(235,1970,405,2085),'teal'),
                           ('softr',(925,1970,1110,2085),'teal'),
                           ('call',(235,2090,410,2220),'green'),
                           ('end',(925,2090,1120,2220),'red')]:
    legend(im, box, 'n73-' + name, channel)
im = Image.open(SOURCE / 'f88b59ded710989f.jpg')
boxes = [(1225,340,1300,421),(1328,337,1405,414),(1424,307,1506,397),
         (1230,461,1310,530),(1350,443,1420,530),(1470,444,1560,525),
         (1225,558,1320,641),(1350,573,1433,646),(1470,568,1560,648),
         (1224,679,1278,751),(1328,683,1410,757),(1440,690,1505,770)]
for i, box in enumerate(boxes): legend(im, box, 'qd-' + str(i), 'red')
for name,box,channel in [('n',(747,834,818,874),'red'),('gage',(821,834,1038,875),'white'),
                         ('menu',(389,690,443,746),'red'),('edit',(458,790,516,831),'red'),
                         ('call',(377,807,453,861),'green'),('end',(1320,815,1399,868),'red'),
                         ('softr',(1255,784,1326,829),'red'),('clear',(1335,241,1379,277),'red'),
                         ('pencil',(1259,266,1314,309),'red')]: legend(im,box,'qd-'+name,channel)
im=Image.open(SOURCE/'816OCzcF21L.jpg')
for i,(top,bottom) in enumerate([((2280,252,2376,296),(2250,302,2350,356)),
                                ((2310,426,2374,476),(2265,479,2330,538)),
                                ((2285,609,2376,665),(2265,660,2340,713)),
                                ((2280,788,2376,838),(2250,837,2350,894))]):
    legend(im,top,'n86-media-'+str(i),'dark');legend(im,bottom,'n86-symbol-'+str(i),'blue')
print('Prepared reference grain and lettering masks')
