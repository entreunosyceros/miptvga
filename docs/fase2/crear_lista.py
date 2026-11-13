"""Synthetic local fixtures; no network, no logos/EPG. Does not overwrite files."""
import argparse
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--channels',type=int,required=True)
p.add_argument('--out',type=Path,required=True)
a=p.parse_args()
if not 1 <= a.channels <= 1_000_000:p.error('channels must be 1..1000000; start small on the TV')
groups=min(100,max(1,a.channels//10))
with a.out.open('x',encoding='utf-8',newline='\n') as f:
 f.write('#EXTM3U\n')
 for i in range(a.channels):
  f.write(f'#EXTINF:-1 group-title="Grupo {i%groups:02}",Sintetico {i+1:07}\nfile:///miptvga-navigation-synthetic.mp4\n')
print(f'channels={a.channels} lines={1+2*a.channels} groups={groups} bytes={a.out.stat().st_size}')
