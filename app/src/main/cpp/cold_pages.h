#pragma once
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <limits>

// Sorted, unique page snapshot. Cold reclamation only; no animation callback.
// Forward every unprotected span to the original OEM helper, preserving its
// own pin range, byte accounting, errors and other modules' memory policy.
template<class Release>
bool releaseColdSpans(uintptr_t begin,size_t size,const uintptr_t *pages,unsigned count,
                      size_t pageSize,Release release,size_t &kept) {
    kept=0;
    if(!size || !pageSize || size>std::numeric_limits<uintptr_t>::max()-begin)
        return release(begin,size);
    uintptr_t end=begin+size,cursor=begin;
    for(unsigned i=0;i<count;++i) {
        uintptr_t page=pages[i];
        if(page>=end) break;
        uintptr_t after=page>std::numeric_limits<uintptr_t>::max()-pageSize
            ?std::numeric_limits<uintptr_t>::max():page+pageSize;
        if(after<=cursor) continue;
        if(page>cursor && !release(cursor,page-cursor)) return false;
        uintptr_t next=std::min(end,after);
        kept+=next-std::max(cursor,page);cursor=next;
    }
    return cursor==end || release(cursor,end-cursor);
}
