#pragma once
#include "clock_glass.h"
#include "clock_glass_profile.h"
#include <cstring>
#include <elf.h>

namespace clock_glass {
template<size_t B>
inline bool hasBuildId(ClockGlassImage image, const unsigned char (&buildId)[B]) {
    if (!image.base || !image.headers) return false;
    const auto *headers = static_cast<const Elf64_Phdr *>(image.headers);
    bool found = false;
    for (size_t i = 0; i < image.count; ++i) {
        const auto &ph = headers[i];
        if (ph.p_type != PT_NOTE) continue;
        const auto *cursor = reinterpret_cast<const unsigned char *>(image.base + ph.p_vaddr);
        size_t remaining = ph.p_memsz;
        while (remaining >= sizeof(Elf64_Nhdr)) {
            Elf64_Nhdr note;
            std::memcpy(&note, cursor, sizeof(note));
            size_t names = (size_t(note.n_namesz) + 3) & ~size_t{3};
            size_t descs = (size_t(note.n_descsz) + 3) & ~size_t{3};
            if (names > remaining - sizeof(note) || descs > remaining - sizeof(note) - names) break;
            if (note.n_type == NT_GNU_BUILD_ID && note.n_namesz == 4 && note.n_descsz == B
                    && !std::memcmp(cursor + sizeof(note), "GNU", 4)
                    && !std::memcmp(cursor + sizeof(note) + names, buildId, B)) found = true;
            size_t bytes = sizeof(note) + names + descs; cursor += bytes; remaining -= bytes;
        }
    }
    return found;
}
template<size_t B, size_t C>
inline bool matches(ClockGlassImage image, const unsigned char (&buildId)[B], const Contract (&contracts)[C]) {
    if (!hasBuildId(image, buildId)) return false;
    const auto *headers = static_cast<const Elf64_Phdr *>(image.headers);
    for (const auto &contract : contracts) {
        bool executable = false;
        for (size_t i = 0; i < image.count; ++i) {
            const auto &ph = headers[i];
            if (ph.p_type == PT_LOAD && (ph.p_flags & (PF_R | PF_X)) == (PF_R | PF_X)
                    && contract.rva >= ph.p_vaddr && contract.rva - ph.p_vaddr <= ph.p_memsz
                    && contract.size <= ph.p_memsz - (contract.rva - ph.p_vaddr)) executable = true;
        }
        if (!executable) return false;
        const auto *bytes = reinterpret_cast<const unsigned char *>(image.base + contract.rva);
        uint64_t hash = UINT64_C(0xcbf29ce484222325);
        for (size_t i = 0; i < contract.size; ++i) hash = (hash ^ bytes[i]) * UINT64_C(0x100000001b3);
        if (hash != contract.hash) return false;
    }
    return true;
}
}
