#!/usr/bin/env python3
"""Production ABI/probe regression tests. Optional ROM/schema and launcher fixtures
are supplied explicitly; no private user paths are embedded in the repository.
"""
import argparse, pathlib, struct, subprocess, tempfile, zipfile
import sys
sys.dont_write_bytecode = True
root = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / 'tools/analysis'))
from schema_elf import Elf
p = argparse.ArgumentParser()
p.add_argument('--schema', action='append', default=[])
p.add_argument('--launcher', action='append', default=[])
a = p.parse_args()
with tempfile.TemporaryDirectory(prefix='mhm-widget-compat-') as tmp:
 work = pathlib.Path(tmp)
 def compile_run(name, source, args=()):
  path = work / (name + '.cpp'); path.write_text(source)
  subprocess.run(['clang++','-std=c++17','-O1','-Wall','-Wextra','-Werror','-fsanitize=address,undefined',
                  '-I',str(root/'app/src/main/cpp'),str(path),'-o',str(work/name)],check=True)
  subprocess.run([str(work/name),*map(str,args)],check=True)
 if a.schema:
  schema_paths=[]
  for path in a.schema:
   file=pathlib.Path(path);data=file.read_bytes();e=Elf(data);schema_paths += ['1',file]
   # All changed ABI/semantic edges must reject, even with identical exports.
   for tag,mutate in [
    ('return',lambda b,o:struct.pack_into('<I',b,o+76,0xd503201f)),
    ('entry',lambda b,o:struct.pack_into('<I',b,o,0xd503201f)),
   ]:
    b=bytearray(data);addr,size=e.syms['Bundle_get_boolean'];s=e.ss['.text'];offset=s[4]+addr-s[3]
    mutate(b,offset);out=work/(file.name+str(len(schema_paths))+tag);out.write_bytes(b);schema_paths += ['0',out]
   # Internal helper mutation is rejected too.
   branch=e.syms['Bundle_clone'][0]+20;s=e.ss['.text'];o=s[4]+branch-s[3];w=struct.unpack_from('<I',data,o)[0]
   delta=w&0x3ffffff;delta=delta-(1<<26) if delta&(1<<25) else delta
   helper=branch+delta*4;b=bytearray(data);struct.pack_into('<I',b,s[4]+helper-s[3],0xd503201f)
   out=work/(file.name+str(len(schema_paths))+'helper');out.write_bytes(b);schema_paths += ['0',out]
   b=bytearray(data);needle=b'contains_keyeE';pos=b.find(needle);assert pos>=0;b[pos]=ord('X')
   out=work/(file.name+str(len(schema_paths))+'call');out.write_bytes(b);schema_paths += ['0',out]
   # Unrelated build metadata may change without requiring adaptation.
   b=bytearray(data);s=e.ss['.note.gnu.build-id'];b[s[4]+s[5]-1]^=1
   out=work/(file.name+str(len(schema_paths))+'build');out.write_bytes(b);schema_paths += ['1',out]
   out=work/(file.name+str(len(schema_paths))+'truncated');out.write_bytes(data[:80]);schema_paths += ['0',out]
  compile_run('schema',r'''
#include "schema_abi.h"
#include <cassert>
#include <iostream>
int main(int argc,char **argv) {
 for(int i=1;i+1<argc;i+=2) {
  std::vector<uint8_t> bytes; std::string reason;
  bool loaded=schema_abi::load(argv[i+1],bytes); schema_abi::Image image(bytes);
  bool result=loaded && image.init() && image.compatible(reason);
  if(result!=(argv[i][0]=='1')) {std::cerr<<argv[i+1]<<": "<<reason<<"\n";return 1;}
 }
 std::cout<<"Schema: reviewed ROMs, return/entry/helper/call mutation, build-only change and truncation passed\n";
}
''',schema_paths)
 # Exercise one structural contract under future pool/table/call relocations.
 compile_run('picker',r'''
#include "widget_picker_probe.h"
#include <cassert>
#include <iostream>
#include <algorithm>
int main() {
 std::vector<uint32_t> words(4096,0xd503201f);
 auto address=reinterpret_cast<uintptr_t>(words.data());
 std::vector<widget_picker::Code> ranges{{address,words.size()*4}};
 constexpr size_t site=512;
 auto set=[&](int delta,uint32_t word) {words[site+delta/4]=word;};
 auto pool=[&](int delta,unsigned reg,uint32_t offset) {
  set(delta,0x91400000|(27<<5)|reg|((offset/4096)<<10));
  set(delta+4,0xf9400000|(reg<<5)|reg|(((offset%4096)/8)<<10));
 };
 auto fixture=[&](unsigned variant) {
  std::fill(words.begin(),words.end(),0xd503201f);
  for(unsigned i=0;i<32;++i) {
   auto w=widget_picker::context[i];
   if(widget_picker::mask(i)==0xfc000000) w=0x94000000|((3000-(site-16+i))&0x3ffffff);
   words[site-16+i]=w;
  }
  set(-0x124,0x9100c2c4); set(0x140,0xaa0203e1); set(0x144,0xf9403f40);
  set(-0x80,0xf9403f40);set(-0x7c,0xf9400000|((1024+variant)<<10));
  set(0x10,words[site-0x7c/4]);
  uint32_t logger=0x12000+variant*4088,debug=0x600008+variant*16;
  pool(-0x24,3,logger);pool(0x120,3,logger);
  pool(-0x54,16,logger+40);pool(0xf0,16,logger+48);
  pool(0x3c,16,debug);pool(0x50,16,debug+8);
 };
 for(unsigned variant=0;variant<128;++variant) {
  fixture(variant);assert(widget_picker::find(ranges)==address+site*4);
 }
 fixture(0);
 for(unsigned i=0;i<32;++i) if(widget_picker::mask(i)==0xffffffff) {
  words[site-16+i]^=1;assert(!widget_picker::find(ranges));words[site-16+i]^=1;
 }
 for(int delta:{-0x124,-0x7c,0x10,0x124,0xf4,0x54,0x140}) {
  fixture(0);words[site+delta/4]^=1<<10;assert(!widget_picker::find(ranges));
 }
 fixture(0);set(4,0x97ffffff); // executable but loops into the classification block
 assert(!widget_picker::find(ranges));
 set(4,0x95000000);assert(!widget_picker::find(ranges)); // BL points outside executable mappings
 fixture(0);
 std::copy(words.begin()+site-0x124/4,words.begin()+site+0x14c/4,words.begin()+site+512-0x124/4);
 assert(!widget_picker::find(ranges));
 assert(!widget_picker::find({{address,100}}));
 assert(!widget_picker::find({{address,65*1024*1024}}));
 assert(!widget_picker::find({{UINTPTR_MAX-100,200}}));
 std::cout<<"Picker: 128 relocations incl. pool-page boundaries; register/ABI/operand/branch mutations, ambiguity, overflow and bounds passed\n";
}
''')
 launchers=[next((root/'reference').glob('*7722*.APK')),*map(pathlib.Path,a.launcher)]
 args=[]
 for index,file in enumerate(launchers):
  data=zipfile.ZipFile(file).read('lib/arm64-v8a/libapp.so');off=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
  ranges=[]
  for j in range(count):
   typ,flags,offset,va,pa,fs,ms,align=struct.unpack_from('<IIQQQQQQ',data,off+j*size)
   if typ==1 and flags&5==5:ranges.append((offset,fs))
  out=work/str(index);out.write_bytes(data)
  args += [out, ','.join(f'{x}:{y}' for x,y in ranges)]
 compile_run('launchers',r'''
#include "widget_picker_probe.h"
#include <fstream>
#include <iterator>
#include <iostream>
#include <sstream>
int main(int argc,char**argv) {
 for(int i=1;i+1<argc;i+=2) {
  std::ifstream f(argv[i],std::ios::binary);std::vector<char>b((std::istreambuf_iterator<char>(f)),{});
  std::vector<widget_picker::Code> ranges;std::stringstream ss(argv[i+1]);std::string entry;
  while(std::getline(ss,entry,',')) {auto colon=entry.find(':');size_t off=std::stoull(entry.substr(0,colon)),size=std::stoull(entry.substr(colon+1));
   if(off>b.size()||size>b.size()-off)return 2;
   ranges.push_back({reinterpret_cast<uintptr_t>(b.data()+off),size});}
  auto site=widget_picker::find(ranges);
  std::cout<<"Launcher fixture "<<((i-1)/2)<<": "<<(site?"unique compatible site":"safe rejection")<<"\n";
  if(!site)return 1;
 }
}
''',args)
print('Widget compatibility regression checks passed')
