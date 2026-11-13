"""Summarize one diagnostic session. No upload or external connection."""
import argparse,collections,re,statistics
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('log',type=Path);a=p.parse_args()
segments={};samples=collections.defaultdict(list);seen=collections.Counter()
for line in a.log.read_text().splitlines():
 values=dict(re.findall(r'([A-Za-z][A-Za-z0-9]*)=([^\s]+)',line))
 if 'catalogue segment=' in line:
  segment=values['segment']
  if segment in segments:raise SystemExit('Repeated segment: capture one app launch per file; do not concatenate logs.')
  segments[segment]=(values['all'],values['count']);continue
 if values.get('segment') not in segments:continue
 kind,count=segments[values['segment']];position='first' if values.get('ordinal')=='1' else 'later'
 for field in ['dispatchNs','mainCpuNs','dispatchToFocusCallbackNs','dispatchToFrameCallbackNs','keyEventAgeMs']:
  if field in values:
   number=float(values[field])/(1_000_000 if field.endswith('Ns') else 1)
   samples[(kind,count,position,field)].append(number)
   seen[(kind,count,position)]+=int(field=='dispatchNs')
print('all,result_count,press,metric,n,median_ms,p95_ms,max_ms')
for key,values in sorted(samples.items()):
 values.sort();q=values[max(0,(95*len(values)+99)//100-1)]
 print(','.join(key)+f',{len(values)},{statistics.median(values):.6f},{q:.6f},{max(values):.6f}')
if not samples:print('# No valid samples: enable debug diagnostic_navigation, start capture, then switch groups to record segment metadata.')
print('# Frame callback is a proxy, not pixel presentation. Missing focus/frame samples are not zero-latency events.')
