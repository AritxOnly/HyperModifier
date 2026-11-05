#include "cold_pages.h"
#include <cassert>
#include <cstdio>
#include <vector>
struct Span {uintptr_t begin;size_t size;};
int main() {
    // Every subrange, hole arrangement and supported Android page size. Kept
    // pages must never reach OEM MADV_DONTNEED; all other bytes must reach it.
    for(size_t pageSize:{4096,16384}) for(unsigned mask=0;mask<256;++mask) {
        uintptr_t pages[8]{};unsigned n=0;
        for(unsigned i=0;i<8;++i) if(mask&(1u<<i)) pages[n++]=(i+1)*pageSize;
        for(unsigned lo=0;lo<10;++lo) for(unsigned hi=lo+1;hi<=10;++hi) {
            std::vector<Span> spans;size_t kept=0;
            assert(releaseColdSpans(lo*pageSize,(hi-lo)*pageSize,pages,n,pageSize,
                [&](uintptr_t at,size_t size){spans.push_back({at,size});return true;},kept));
            size_t total=kept,expected=0;
            for(unsigned i=lo;i<hi;++i) if(i && i<=8 && (mask&(1u<<(i-1)))) expected+=pageSize;
            assert(kept==expected);
            for(auto span:spans) {
                assert(span.size && span.begin%pageSize==0 && span.size%pageSize==0);
                total+=span.size;
                for(unsigned i=0;i<n;++i) assert(span.begin+span.size<=pages[i] || span.begin>=pages[i]+pageSize);
            }
            assert(total==(hi-lo)*pageSize);
        }
    }
    uintptr_t pages[]{4096,8192,12288};size_t kept=0;unsigned called=0;
    assert(!releaseColdSpans(0,20480,pages,3,4096,[&](uintptr_t,size_t){++called;return false;},kept));
    assert(called==1 && kept==0); // First OEM error stops the release, not masked.
    assert(releaseColdSpans(4096,12288,pages,3,4096,[&](uintptr_t,size_t){assert(false);return true;},kept));
    assert(kept==12288);
    assert(releaseColdSpans(UINTPTR_MAX-3,8,pages,3,4096,[&](uintptr_t at,size_t bytes){return at==UINTPTR_MAX-3 && bytes==8;},kept));
    std::puts("Cold pages: 28,160 span/4K/16K cases, protected holes, accounting, OEM errors and overflow passed");
}
