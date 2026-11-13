"""Isolated JVM navigation costs, not DPAD/Compose/Android measurements.
Run from any directory: python3 docs/fase2/medir_navegacion.py
Compiles exact phase-1/filter candidate methods with current Models, no Android or network.
"""
from pathlib import Path
import subprocess,tempfile,json,platform,os
root=Path(__file__).resolve().parents[2]
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
def jar(g,a,v):return next((cache/g/a/v).glob(f'*/{a}-{v}.jar'))
cp=':'.join(str(jar(*v)) for v in [('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.2.10'),('org.jetbrains.kotlin','kotlin-stdlib','2.2.10'),('org.jetbrains.kotlin','kotlin-script-runtime','2.2.10'),('org.jetbrains','annotations','13.0'),('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.9.0')])
stdlib=str(jar('org.jetbrains.kotlin','kotlin-stdlib','2.2.10'))
source=(root/'app/src/main/java/com/toigo/miptvga/MainViewModel.kt').read_text()
a=source.index('    private data class FilterResult(');b=source.index('    private data class PreparedChannels(',a)
result=source[a:b].replace('private data class','data class')
a=source.index('    private fun computeFilterResult(');body=source.index('): FilterResult {',a)+len('): FilterResult ');b=body+1;depth=1
while depth:
 if source[b]=='{':depth+=1
 elif source[b]=='}':depth-=1
 b+=1
candidate='package com.toigo.miptvga\ninternal class FiltroFase2 {\n'+result+source[a:b].replace('private fun','fun',1)+'\n}\n'
with tempfile.TemporaryDirectory(prefix='miptvga-nav-') as directory:
 p=Path(directory)
 (p/'Models.kt').write_text((root/'app/src/main/java/com/toigo/miptvga/Models.kt').read_text())
 (p/'Immutable.kt').write_text('package androidx.compose.runtime\nannotation class Immutable\n')
 (p/'FiltroFase1.kt').write_text((root/'docs/fase2/FiltroFase1.kt').read_text())
 (p/'FiltroFase2.kt').write_text(candidate)
 (p/'Benchmark.kt').write_text(r'''package com.toigo.miptvga
import java.lang.management.ManagementFactory
import kotlin.system.measureNanoTime
@Volatile var consumed: Any? = null
val allocated = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
fun bytes() = allocated.getThreadAllocatedBytes(Thread.currentThread().id)
fun report(n:Int, count:Int, operation:String, version:String, block:()->Any?) {
 val before=bytes(); val cold=measureNanoTime { consumed=block() }; val coldBytes=bytes()-before
 repeat(3) { consumed=block() }
 val times=ArrayList<Long>();val allocations=ArrayList<Long>()
 repeat(9) { val a=bytes();times+=measureNanoTime { consumed=block() };allocations+=bytes()-a }
 times.sort();allocations.sort()
 println(listOf(n,count,operation,version,cold/1e6,coldBytes,times[4]/1e6,times[8]/1e6,allocations[4]).joinToString(","))
 consumed=null
 System.gc()
}
fun main(args:Array<String>) {
 val n=args[0].toInt()
 val channel=Channel("Synthetic", "https://example.invalid/synthetic")
 val ids=List(100) { "group$it" }
 val entries=List(n) { ChannelListEntry(it,channel,"Synthetic",ids[it%100],"synthetic") }
 val byGroup=buildChannelsByGroupId(entries)
 val groups=buildChannelGroups(entries,emptySet(),emptySet())
 val small=byGroup.getValue(ids[0])
 val old=FiltroFase1(); val candidate=FiltroFase2()
 if(n<=1000) {
  var comparisons=0
  for (id in listOf(AllChannelsGroupId,FavoriteChannelsGroupId,ids[0],"missing"))
   for (query in listOf("","Synthetic","no-match"))
    for (favorites in listOf(emptySet<String>(),setOf("synthetic")))
     for (favoriteGroups in listOf(emptySet<String>(),setOf(ids[0])))
      for (selected in listOf(-1,0,n-1,n+1))
       for (preserve in listOf(false,true)) {
        val expected=old.computeFilterResult(entries,byGroup,id,query,favorites,favoriteGroups,selected,groups,preserve)
        val actual=candidate.computeFilterResult(entries,byGroup,id,query,favorites,favoriteGroups,selected,groups,preserve)
        check(expected.filteredChannels==actual.filteredChannels)
        check(expected.selectedIndex==actual.selectedIndex)
        check(expected.selectedVisibleIndex==actual.selectedVisibleIndex)
        check(expected.groups==actual.groups)
        comparisons++
       }
  System.err.println("Equivalent filter cases n=$n: $comparisons PASS")
 }
 for ((label,list) in listOf("all" to entries,"group" to small)) {
  for ((position,target) in listOf("first" to list.first().originalIndex,"last" to list.last().originalIndex,"absent" to n+1)) {
   report(n,list.size,"header.$label.$position","phase1") { list.firstOrNull { it.originalIndex==target } }
   report(n,list.size,"header.$label.$position","phase2") { list.getOrNull(findVisibleChannelIndex(list,target)) }
  }
  val groupId=if(label=="all") AllChannelsGroupId else ids[0]
  report(n,list.size,"enter.$label","phase1") { old.computeFilterResult(entries,byGroup,groupId,"",emptySet(),emptySet(),list.last().originalIndex,groups,true) }
  report(n,list.size,"enter.$label","phase2") { candidate.computeFilterResult(entries,byGroup,groupId,"",emptySet(),emptySet(),list.last().originalIndex,groups,true) }
 }
}
''')
 subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',stdlib,'-d',str(p/'out')]+[str(f) for f in p.glob('*.kt')],check=True)
 print('catalogue_count,result_count,operation,version,first_call_ms,first_alloc_bytes,median_ms,max_ms,median_alloc_bytes',flush=True)
 for n in [100,1000,10000,100000,1000000]:
  subprocess.run(['java','-Xms128m','-Xmx1024m','-cp',str(p/'out')+':'+stdlib,'com.toigo.miptvga.BenchmarkKt',str(n)],check=True)
