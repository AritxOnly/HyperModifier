#!/usr/bin/env python3
"""Run production install/config against the real 6325 ELF, with a fake detour backend."""
from pathlib import Path
import struct, subprocess, sys, tempfile
root=Path(__file__).resolve().parents[2]
if len(sys.argv)!=2: raise SystemExit('usage: fold-dual-page-install-test.py libapp.so')
platform='''#pragma once
#include <cstdint>
#include <cstddef>
struct Elf64_Phdr { uint32_t p_type,p_flags; uint64_t p_offset,p_vaddr,p_paddr,p_filesz,p_memsz,p_align; };
struct Elf64_Nhdr { uint32_t n_namesz,n_descsz,n_type; };
constexpr int PT_LOAD=1,PT_NOTE=4,PF_R=4,PF_X=1,NT_GNU_BUILD_ID=3;
'''
driver=r'''
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>
extern "C" void mhm_fold_dual_orientation_entry() {}
extern "C" void mhm_fold_dual_grid_entry() {}
unsigned hooks=0,undos=0,failAt=0,nullAt=0;
bool disableDuringInstall=false;
int backend(void *,void *,void **out) {
    hooks++;
    if(hooks==failAt) return 1;
    *out=hooks==nullAt?nullptr:reinterpret_cast<void *>(0x1234);
    assert(!mhm_fold_dual_enabled.load());
    if(disableDuringInstall) configureFoldDualPage(false);
    return 0;
}
int undo(void*) { undos++; return 0; }
void reset() {
    requested=true;ready=false;attempted=false;mhm_fold_dual_enabled=0;
    mhm_fold_dual_orientation_original=mhm_fold_dual_grid_original=nullptr;
    hooks=undos=failAt=nullAt=0;disableDuringInstall=false;
}
int main(int argc,char**argv) {
    assert(argc==2);
    std::ifstream file(argv[1],std::ios::binary);
    std::vector<unsigned char> bytes((std::istreambuf_iterator<char>(file)),{});
    ClockGlassImage image{reinterpret_cast<uintptr_t>(bytes.data()),headers,sizeof(headers)/sizeof(*headers)};
    assert(!requested && !ready && !mhm_fold_dual_enabled.load());
    installFoldDualPage(image,backend,undo); assert(!attempted && !hooks);
    using namespace fold_dual_page;
    assert(clock_glass::matches(image,buildId,contracts));
    reset();installFoldDualPage({},backend,undo);assert(!attempted);
    reset();installFoldDualPage(image,backend,nullptr);assert(attempted && !hooks);
    for(auto c:contracts) {
        reset();bytes[c.rva]^=1;installFoldDualPage(image,backend,undo);
        assert(attempted && !hooks && !ready && !mhm_fold_dual_enabled.load());bytes[c.rva]^=1;
    }
    for(auto &h:headers) {
        if(h.p_type!=PT_NOTE) continue;
        reset(); bytes[h.p_vaddr+16]^=1;installFoldDualPage(image,backend,undo);
        assert(attempted && !hooks);bytes[h.p_vaddr+16]^=1;
    }
    for(unsigned fail:{1u,2u}) {
        reset();failAt=fail;installFoldDualPage(image,backend,undo);
        assert(hooks==fail && undos==fail-1 && !ready && !mhm_fold_dual_enabled.load());
        for(int i=0;i<10000;i++) installFoldDualPage(image,backend,undo);
        assert(hooks==fail);
        reset();nullAt=fail;installFoldDualPage(image,backend,undo);
        assert(hooks==fail && undos==fail && !ready && !mhm_fold_dual_enabled.load());
    }
    reset();disableDuringInstall=true;installFoldDualPage(image,backend,undo);
    assert(ready && hooks==2 && !mhm_fold_dual_enabled.load());
    reset();installFoldDualPage(image,backend,undo);
    assert(ready && hooks==2 && !undos && mhm_fold_dual_enabled.load());
    assert(mhm_fold_dual_grid_target==image.base+dualGrid);
    assert(mhm_fold_dual_mode_return==image.base+modeReturn);
    assert(mhm_fold_dual_editing_return==image.base+editingReturn);
    configureFoldDualPage(false);assert(!mhm_fold_dual_enabled.load());
    configureFoldDualPage(true);assert(mhm_fold_dual_enabled.load());
    installFoldDualPage(image,backend,undo);assert(hooks==2);
    std::cout<<"Real ELF contracts, code/build mutations, off default, rollback, terminal failure, disable during install and re-enable passed\n";
}
'''
with tempfile.TemporaryDirectory() as temp:
    temp=Path(temp);(temp/'android').mkdir();(temp/'sys').mkdir()
    (temp/'elf.h').write_text(platform)
    (temp/'android/log.h').write_text('constexpr int ANDROID_LOG_INFO=4,ANDROID_LOG_WARN=5;\ninline int __android_log_print(int,const char*,const char*,...){return 0;}\ninline int __android_log_write(int,const char*,const char*){return 0;}')
    (temp/'sys/system_properties.h').write_text('#include <cstring>\nconstexpr int PROP_VALUE_MAX=92;\ninline int __system_property_get(const char*,char* out){std::strcpy(out,"lhasa");return 5;}')
    data=Path(sys.argv[1]).read_bytes(); assert data[:6]==b'\x7fELF\x02\x01'
    off=struct.unpack_from('<Q',data,32)[0]; size,count=struct.unpack_from('<HH',data,54)
    ph=[struct.unpack_from('<IIQQQQQQ',data,off+i*size) for i in range(count)]
    mapped=bytearray(max(p[3]+p[6] for p in ph if p[0]==1))
    for p in ph:
        if p[0]==1: mapped[p[3]:p[3]+p[5]]=data[p[2]:p[2]+p[5]]
    (temp/'dart').write_bytes(mapped)
    array='Elf64_Phdr headers[]={'+','.join('{'+','.join(map(str,p))+'}' for p in ph)+'};'
    (temp/'test.cpp').write_text('#include "fold_dual_page.cpp"\n'+array+'\n'+driver)
    subprocess.run(['clang++','-std=c++17','-O1','-Wall','-Wextra','-Werror','-fsanitize=address,undefined','-I',str(temp),'-I',str(root/'app/src/main/cpp'),str(temp/'test.cpp'),'-o',str(temp/'test')],check=True)
    subprocess.run([str(temp/'test'),str(temp/'dart')],check=True,timeout=30)
