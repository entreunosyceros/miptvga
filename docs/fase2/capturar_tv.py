"""Collect only MiptvgaNav logs. Never installs, clears logs, starts or stops the app."""
import argparse,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--adb',default='adb')
p.add_argument('--serial')
p.add_argument('--seconds',type=int,default=120)
p.add_argument('--out',type=Path,required=True)
a=p.parse_args()
if not 10 <= a.seconds <= 3600:p.error('seconds must be 10..3600')
base=[a.adb]+(['-s',a.serial] if a.serial else [])
subprocess.run(base+['get-state'],check=True,stdout=subprocess.DEVNULL)
with a.out.open('x',encoding='utf-8') as f:
 for prop in ['ro.product.manufacturer','ro.product.model','ro.build.version.sdk','ro.product.cpu.abi']:
  result=subprocess.run(base+['shell','getprop',prop],check=True,capture_output=True,text=True,timeout=10)
  f.write('# '+prop+'='+result.stdout.strip()+'\n')
 f.flush()
 process=subprocess.Popen(base+['logcat','-v','epoch','-T','1','-s','MiptvgaNav:D','*:S'],stdout=f,stderr=subprocess.PIPE)
 print('Recording only numeric MiptvgaNav diagnostics. Alternate small group / All; then press Down 10 times. Ctrl+C ends early.',flush=True)
 try:
  process.wait(timeout=a.seconds)
 except (subprocess.TimeoutExpired,KeyboardInterrupt):
  process.terminate()
  try:process.wait(timeout=5)
  except subprocess.TimeoutExpired:process.kill();process.wait()
 # Termination applies solely to the local logcat subprocess, never to the app.
 if process.returncode not in (0,-15,-9):
  raise SystemExit('adb logcat failed: '+process.stderr.read().decode(errors='replace'))
print('Saved',a.out)
