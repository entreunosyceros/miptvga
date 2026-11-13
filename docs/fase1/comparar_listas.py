"""Comparación JVM con 1.1. Requiere caché Kotlin/kxml del harness; sin red."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
script=root/'docs/auditoria/medir_listas.py'
with tempfile.TemporaryDirectory(prefix='miptvga-baseline-') as d:
 p=Path(d)/'app/src/main/java/com/toigo/miptvga';p.mkdir(parents=True)
 for name in ['M3uParser.kt','Models.kt','XmltvParser.kt']:
  text=subprocess.check_output(['git','show','8a2e87f:app/src/main/java/com/toigo/miptvga/'+name],cwd=root)
  (p/name).write_bytes(text)
 with (root/'docs/fase1/benchmark_base_jvm.txt').open('w') as out:
  subprocess.run(['python3',str(script)],cwd=d,stdout=out,stderr=subprocess.STDOUT,check=True)
with (root/'docs/fase1/benchmark_fase1_jvm.txt').open('w') as out:
 subprocess.run(['python3',str(script)],cwd=root,stdout=out,stderr=subprocess.STDOUT,check=True)
