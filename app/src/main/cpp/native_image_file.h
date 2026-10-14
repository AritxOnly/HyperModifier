#pragma once
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

// Android may map an uncompressed library directly from base.apk!/lib/...
// Fingerprint the ZIP entry bytes, never the surrounding APK or a virtual path.
inline bool matchingNativeImageFile(const char *path, size_t expectedSize, uint64_t expectedHash) {
    if (!path) return false;
    std::string filename(path);
    size_t separator = filename.find("!/");
    std::string entryName;
    if (separator != std::string::npos) {
        entryName = filename.substr(separator + 2);
        filename.resize(separator);
    }
    FILE *file = std::fopen(filename.c_str(), "rb");
    if (!file) return false;
    struct Close { FILE *file; ~Close() { std::fclose(file); } } close{file};
    if (std::fseek(file, 0, SEEK_END) != 0) return false;
    long length = std::ftell(file);
    if (length < 0) return false;
    uint64_t start = 0, bytes = static_cast<uint64_t>(length);
    auto u16 = [](const unsigned char *p) -> uint16_t { return p[0] | (uint16_t(p[1]) << 8); };
    auto u32 = [](const unsigned char *p) -> uint32_t {
        return p[0] | (uint32_t(p[1]) << 8) | (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24);
    };
    if (!entryName.empty()) {
        size_t tailSize = bytes < 65557 ? static_cast<size_t>(bytes) : 65557;
        if (tailSize < 22) return false;
        std::vector<unsigned char> tail(tailSize);
        if (std::fseek(file, length - static_cast<long>(tailSize), SEEK_SET) != 0
                || std::fread(tail.data(), 1, tail.size(), file) != tail.size()) return false;
        size_t end = tailSize;
        for (size_t i = tailSize - 22;; --i) {
            if (u32(tail.data() + i) == 0x06054b50
                    && i + 22 + u16(tail.data() + i + 20) == tailSize) { end = i; break; }
            if (i == 0) break;
        }
        if (end == tailSize) return false;
        const auto *directory = tail.data() + end;
        if (u16(directory + 4) || u16(directory + 6)
                || u16(directory + 8) != u16(directory + 10)) return false;
        uint64_t position = u32(directory + 16), limit = position + u32(directory + 12);
        if (limit > bytes) return false;
        bool found = false;
        for (unsigned index = 0; index < u16(directory + 10); ++index) {
            unsigned char header[46];
            if (position + sizeof(header) > limit || std::fseek(file, static_cast<long>(position), SEEK_SET)
                    || std::fread(header, 1, sizeof(header), file) != sizeof(header)
                    || u32(header) != 0x02014b50) return false;
            size_t nameSize = u16(header + 28);
            uint64_t next = position + 46 + nameSize + u16(header + 30) + u16(header + 32);
            if (next > limit) return false;
            std::string name(nameSize, '\0');
            if (std::fread(name.data(), 1, nameSize, file) != nameSize) return false;
            if (name == entryName) {
                // Direct Android mappings require stored, unencrypted entries.
                if ((u16(header + 8) & 1) || u16(header + 10)
                        || u32(header + 20) != expectedSize || u32(header + 24) != expectedSize) return false;
                uint64_t local = u32(header + 42);
                unsigned char localHeader[30];
                if (local + sizeof(localHeader) > bytes
                        || std::fseek(file, static_cast<long>(local), SEEK_SET)
                        || std::fread(localHeader, 1, sizeof(localHeader), file) != sizeof(localHeader)
                        || u32(localHeader) != 0x04034b50 || u16(localHeader + 8)
                        || (u16(localHeader + 6) & 1)) return false;
                start = local + 30 + u16(localHeader + 26) + u16(localHeader + 28);
                bytes = expectedSize;
                if (start + bytes > static_cast<uint64_t>(length)) return false;
                found = true;
                break;
            }
            position = next;
        }
        if (!found) return false;
    }
    if (bytes != expectedSize || std::fseek(file, static_cast<long>(start), SEEK_SET)) return false;
    uint64_t fingerprint = 0xcbf29ce484222325ULL;
    unsigned char buffer[8192];
    while (bytes) {
        size_t count = bytes < sizeof(buffer) ? static_cast<size_t>(bytes) : sizeof(buffer);
        if (std::fread(buffer, 1, count, file) != count) return false;
        for (size_t i = 0; i < count; ++i) fingerprint = (fingerprint ^ buffer[i]) * 0x100000001b3ULL;
        bytes -= count;
    }
    return fingerprint == expectedHash;
}
