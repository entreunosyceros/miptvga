"""Microbenchmark JVM aislado. No modifica fuentes ni accede a red.
Usa compilador Kotlin 2.2.10 de la caché local; no sustituye Gradle/Android.
Ejecutar desde la raíz: python3 docs/auditoria/medir_listas.py
"""
from pathlib import Path
import subprocess, tempfile, shutil, sys
root = Path.cwd()
cache = Path.home()/'.gradle/caches/modules-2/files-2.1'
def jar(group, artifact, version):
    return next((cache/group/artifact/version).glob(f'*/{artifact}-{version}.jar'))
cp = ':'.join(str(jar(*x)) for x in [
 ('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.2.10'),
 ('org.jetbrains.kotlin','kotlin-stdlib','2.2.10'),
 ('org.jetbrains.kotlin','kotlin-script-runtime','2.2.10'),
 ('org.jetbrains','annotations','13.0'),
 ('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.9.0')])
stdlib = str(jar('org.jetbrains.kotlin','kotlin-stdlib','2.2.10')) + ':' + str(jar('net.sf.kxml','kxml2','2.3.0'))
with tempfile.TemporaryDirectory(prefix='miptvga-audit-') as d:
 p=Path(d)
 for f in ['M3uParser.kt','Models.kt','XmltvParser.kt']:
  shutil.copyfile(root/'app/src/main/java/com/toigo/miptvga'/f,p/f)
 (p/'Immutable.kt').write_text('package androidx.compose.runtime\nannotation class Immutable\n')
 (p/'Measure.kt').write_text(r'''package com.toigo.miptvga
import java.io.Reader
import kotlin.system.measureNanoTime
class SyntheticReader(val count: Int): Reader() {
 var i=0; var line=""; var pos=0
 override fun read(c: CharArray, off: Int, len: Int): Int {
  if(pos==line.length) {
   if(i>=count) return -1
   val n=i++
   line="#EXTINF:-1 tvg-id=\"id$n\" group-title=\"Grupo ${n%100}\",Canal $n\nhttps://example.invalid/live/$n.m3u8\n"; pos=0
  }
  val n=minOf(len,line.length-pos); line.toCharArray(c,off,pos,pos+n); pos+=n; return n
 }
 override fun close() {}
}
fun heap():Long { System.gc(); Thread.sleep(100); return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory() }
fun main(args:Array<String>) {
 val parser=M3uParser()
 if(args[0]=="cases") {
  check(parser.parse("").isEmpty())
  check(parser.parse("#EXTINF:-1,Uno\nhttps://example.invalid/1\n").single().name=="Uno")
  println("empty/basic: PASS")
  println("comma-name="+parser.parse("#EXTINF:-1,Noticias, España\nhttps://example.invalid/1\n").single().name)
  println("unquoted-group="+parser.parse("#EXTINF:-1 group-title=Noticias,Uno\nhttps://example.invalid/1\n").single().group)
  println("malformed-url-accepted="+parser.parse("#EXTINF:-1,Uno\nnot a URL\n").single().playbackUrl)
  for (zone in listOf("+0100","+01:00","")) {
   val xml="<tv><channel id=\"id1\"><display-name>Uno</display-name></channel><programme channel=\"id1\" start=\"20261113180000 $zone\" stop=\"20261113190000 $zone\"><title>Programa</title></programme></tv>"
   val result=XmltvParser().parseProgramTimelines(xml.reader(),EpgChannelInterests(setOf("id1"),setOf("uno")),1794592800000L)
   println("xmltv-zone=$zone timelines="+result.size)
  }
  return
 }
 val n=args[0].toInt()
 parser.parse(SyntheticReader(1000).buffered())
 val base=heap()
 lateinit var channels:List<Channel>
 val parse=measureNanoTime { channels=parser.parse(SyntheticReader(n).buffered()) }
 val parsedHeap=heap()-base
 lateinit var entries:List<ChannelListEntry>
 lateinit var groups:Map<String,List<ChannelListEntry>>
 val prep=measureNanoTime { entries=channels.mapIndexed { i,c -> ChannelListEntry(i,c) }; groups=buildChannelsByGroupId(entries); buildChannelGroups(entries,emptySet(),emptySet()) }
 val indexedHeap=heap()-base
 var found=0
 val search=measureNanoTime { val matches=entries.filter { it.channel.name.contains("999",true)||it.groupTitle.contains("999",true) }; val index=HashMap<Int,Int>(matches.size.coerceAtLeast(16)); matches.forEachIndexed { i,e -> index[e.originalIndex]=i }; found=index.size }
 var size=0
 val group=measureNanoTime { val list=groups["grupo 1"].orEmpty(); val index=HashMap<Int,Int>(list.size.coerceAtLeast(16)); list.forEachIndexed { i,e -> index[e.originalIndex]=i }; size=index.size }
 val all=measureNanoTime { val index=HashMap<Int,Int>(entries.size.coerceAtLeast(16)); entries.forEachIndexed { i,e -> index[e.originalIndex]=i }; check(index.size==n) }
 check(channels.size==n); check(entries.last().originalIndex==n-1)
 println(listOf(n,parse/1e6,prep/1e6,parsedHeap/1048576.0,indexedHeap/1048576.0,search/1e6,group/1e6,all/1e6,found,size).joinToString(","))
}
''')
 subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',stdlib,'-d',str(p/'out')]+[str(f) for f in p.glob('*.kt')],check=True)
 runtime=str(p/'out')+':'+stdlib
 subprocess.run(['java','-Xmx1536m','-cp',runtime,'com.toigo.miptvga.MeasureKt','cases'],check=True)
 print('n,parse_ms,prepare_ms,parser_heap_MiB,indexed_heap_MiB,search_ms,group_ms,all_index_ms,matches,group_count',flush=True)
 for n in ([] if '--cases' in sys.argv else [1000,10000,100000,500000,1000000]):
  subprocess.run(['java','-Xmx1536m','-cp',runtime,'com.toigo.miptvga.MeasureKt',str(n)],check=True)
