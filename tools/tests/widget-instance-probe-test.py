#!/usr/bin/env python3
"""Run the production SDK ABI probe against rebased real ELF images.
Only the two import GOT slots are bound to inert test addresses; no ARM64 code
is executed on the host. Inputs are APK files, including optional new versions.
"""
import argparse,pathlib,struct,subprocess,tempfile,zipfile,sys
sys.dont_write_bytecode=True
root=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'tools/analysis'))
from schema_elf import Elf
p=argparse.ArgumentParser();p.add_argument('--launcher',action='append',default=[]);a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='mhm-widget-sdk-') as temp:
 work=pathlib.Path(temp);args=[]
 for index,path in enumerate([next((root/'reference').glob('*7722*.APK')),*map(pathlib.Path,a.launcher)]):
  raw=zipfile.ZipFile(path).read('lib/arm64-v8a/libnative_widget_sdk.so');e=Elf(raw)
  off=struct.unpack_from('<Q',raw,32)[0];size,count=struct.unpack_from('<HH',raw,54);segments=[]
  for j in range(count):
   typ,flags,offset,va,pa,fs,ms,align=struct.unpack_from('<IIQQQQQQ',raw,off+j*size)
   if typ==1 and flags&4:segments.append((va,ms,bool(flags&1),offset,fs))
  image=bytearray(max(va+ms for va,ms,*_ in segments))
  for va,ms,execute,offset,fs in segments:image[va:va+fs]=raw[offset:offset+fs]
  imports={'AppWidgetManager_get_app_widget_info':0x12340000,'AppWidgetProviderInfo_get_providerInfo':0x12350000}
  ds=e.ss['.dynsym'];strings=e.section('.dynstr');found=set()
  for got,info,addend in struct.iter_unpack('<QQq',e.section('.rela.plt')):
   no=struct.unpack_from('<I',raw,ds[4]+(info>>32)*24)[0];name=strings[no:].split(b'\0',1)[0].decode()
   if name in imports:struct.pack_into('<Q',image,got,imports[name]);found.add(name)
  assert found==imports.keys()
  f=work/str(index);f.write_bytes(image);args += [str(f),','.join(f'{va}:{ms}:{int(execute)}' for va,ms,execute,*_ in segments)]
 source=work/'test.cpp';source.write_text(r'''
#include "widget_instance_probe.h"
#include <fstream>
#include <iterator>
#include <sstream>
#include <iostream>
#include <cassert>
#include <algorithm>
int main(int argc,char**argv) {
 for(int index=1;index+1<argc;index+=2) {
  std::ifstream file(argv[index],std::ios::binary);
  std::vector<uint8_t> raw((std::istreambuf_iterator<char>(file)),{}),memory(raw.size()+4095);
  auto base=(reinterpret_cast<uintptr_t>(memory.data())+4095)&~uintptr_t{4095};
  std::memcpy(reinterpret_cast<void*>(base),raw.data(),raw.size());
  std::vector<widget_instance::Segment> segments;
  std::stringstream stream(argv[index+1]);std::string row;
  while(std::getline(stream,row,',')) {auto first=row.find(':'),second=row.find(':',first+1);
   segments.push_back({base+std::stoull(row.substr(0,first)),std::stoull(row.substr(first+1,second-first-1)),row.substr(second+1)=="1"});}
  assert(widget_instance::compatible(segments,0x12340000,0x12350000));
  assert(!widget_instance::compatible(segments,0x12360000,0x12350000));
  assert(!widget_instance::compatible(segments,0x12340000,0x12360000));
  assert(!widget_instance::compatible(segments,0,0x12350000));
  // Corrupt every reviewed tag test. Other SDK functions and import symbols remain.
  bool changed=false;
  for(const auto&s:segments) if(s.executable) for(size_t o=0;o+40<=s.size;o+=4) {
   auto pc=s.address+o;
   if(widget_instance::matches(pc,widget_instance::infoContext,2,0x12340000,segments)) {
    uint32_t word=widget_instance::wordAt(pc+12)^(1<<19);std::memcpy(reinterpret_cast<void*>(pc+12),&word,4);changed=true;
   }
  }
  assert(changed && !widget_instance::compatible(segments,0x12340000,0x12350000));
  std::memcpy(reinterpret_cast<void*>(base),raw.data(),raw.size());
  changed=false;
  for(const auto&s:segments) if(s.executable) for(size_t o=0;o+40<=s.size;o+=4) {
   auto pc=s.address+o;
   if(widget_instance::matches(pc,widget_instance::providerContext,1,0x12350000,segments)) {
    uint32_t word=0xaa0103f4;std::memcpy(reinterpret_cast<void*>(pc+12),&word,4);changed=true;
   }
  }
  assert(changed && !widget_instance::compatible(segments,0x12340000,0x12350000));
  assert(!widget_instance::compatible({{base,17*1024*1024,true}},0x12340000,0x12350000));
  std::cout<<"SDK fixture "<<(index-1)/2<<": ABI verified; changed imports and tag layout safely rejected\n";
 }
}
''')
 subprocess.run(['clang++','-std=c++17','-O1','-Wall','-Wextra','-Werror','-fsanitize=address,undefined','-I',str(root/'app/src/main/cpp'),str(source),'-o',str(work/'test')],check=True)
 subprocess.run([str(work/'test'),*args],check=True)
