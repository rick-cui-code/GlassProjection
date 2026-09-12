"""Generate original diagnostic art and padded, linear-light Gaussian caches."""
from pathlib import Path
import os
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parents[1]/'projection-lab/src/main/assets'
FONT=os.environ.get('GLASS_DEMO_FONT','C:/Windows/Fonts/segoeui.ttf')
SIGMAS=[0,1.5,3,6,12,24,48,96]
PAD=288

def filter_axis(image, sigma, axis):
    radius=int(np.ceil(sigma*3))
    coord=np.arange(-radius,radius+1)
    kernel=np.exp(-coord*coord/(2*sigma*sigma))
    kernel/=kernel.sum()
    n=image.shape[axis]+kernel.size-1
    n=2**int(np.ceil(np.log2(n)))
    shape=[1,1,1];shape[axis]=n//2+1
    freq=np.fft.rfft(image,n=n,axis=axis)*np.fft.rfft(kernel,n).reshape(shape)
    result=np.fft.irfft(freq,n=n,axis=axis)
    select=[slice(None)]*3;select[axis]=slice(radius,radius+image.shape[axis])
    return result[tuple(select)].astype(np.float32)

def artwork(w,h,inner):
    yy,xx=np.mgrid[0:h,0:w]
    wave=np.exp(-((xx/w-.68)**2+(yy/h-.5)**2)*5)
    rgb=np.stack([20+wave*30,49+wave*82,69+wave*85],axis=-1).astype('uint8')
    im=Image.fromarray(rgb)
    d=ImageDraw.Draw(im)
    font=ImageFont.truetype(FONT,24)
    small=ImageFont.truetype(FONT,17)
    title=ImageFont.truetype(FONT,52)
    d.text((34,30),'09:41',font=title,fill='#f6f5e8')
    d.text((36,98),'INNER  /  FIXED PAPER' if inner else 'COVER  /  LEFT ANCHOR',font=small,fill='#a9d9de')
    cols=8 if inner else 4
    colors=['#65cbbd','#edb96a','#deddd0','#d58c80','#86b0cd','#ad9cc8']
    step=w/cols
    for row in range(3):
        for col in range(cols):
            cx=(col+.5)*step; cy=205+row*145
            color=colors[(col+row*3)%len(colors)]
            d.rounded_rectangle((cx-35,cy-35,cx+35,cy+35),radius=18,fill=color)
            d.text((cx,cy-18),str(1+col+row*cols),font=font,anchor='mt',fill='#163443')
            d.text((cx,cy+46),('I' if inner else 'C')+f' {row+1}.{col+1}',font=small,anchor='mt',fill='#dbe9e8')
    # Fine lines show continuous blur without requiring a noisy texture.
    for i in range(50):
        x=20+i*(w-40)/49
        d.line((x,h-75,x,h-44),fill='#c4ddd9',width=2)
    return im

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for role,w,h in [('inner',1024,724),('cover',512,752)]:
        original=artwork(w,h,role=='inner')
        color=np.array(original,dtype=np.float32)/255
        linear=np.where(color<=.04045,color/12.92,((color+.055)/1.055)**2.4)
        padded=np.pad(linear,((PAD,PAD),(PAD,PAD),(0,0)))
        for i,sigma in enumerate(SIGMAS):
            blurred=padded if sigma==0 else filter_axis(filter_axis(padded,sigma,0),sigma,1)
            blurred=np.maximum(0,blurred)
            srgb=np.where(blurred<=.0031308,12.92*blurred,1.055*blurred**(1/2.4)-.055)
            Image.fromarray(np.round(np.clip(srgb,0,1)*255).astype('uint8')).save(OUT/f'{role}_{i}.png')
        print('BAKED',role,flush=True)
    (OUT/'layers.json').write_text(json.dumps({'sigmas':SIGMAS,'padding':PAD,'art':'Original numbered diagnostic art; no upstream wallpapers','filter':'linear RGB, separable Gaussian, 3 sigma support'}))
if __name__=='__main__':main()
