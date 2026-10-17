package com.aritxonly.myhypermodifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** One-time, name-independent resolution from retained Main bytecode. No Android dependency. */
final class HyperMusicCoverDexSymbols {
    static final String MAIN = "Lcom/os4/musiccover/Main;";
    static final class Member {
        final String owner, name, descriptor;
        Member(String owner, String name, String descriptor) {
            this.owner = owner; this.name = name; this.descriptor = descriptor;
        }
        public String toString() { return owner + "->" + name + (descriptor.startsWith("(") ? "" : ":") + descriptor; }
        public boolean equals(Object o) {
            if (!(o instanceof Member)) return false;
            Member m = (Member) o;
            return owner.equals(m.owner) && name.equals(m.name) && descriptor.equals(m.descriptor);
        }
        public int hashCode() { return (owner.hashCode() * 31 + name.hashCode()) * 31 + descriptor.hashCode(); }
    }
    final Map<String, Member> members = new LinkedHashMap<>();
    Member get(String role) throws IOException {
        Member m = members.get(role);
        if (m == null) throw new IOException("Missing HMC role: " + role);
        return m;
    }
    static HyperMusicCoverDexSymbols read(String apk) throws IOException {
        List<Dex> dexes = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) {
                    if (entry.getSize() < 112 || entry.getSize() > 64 * 1024 * 1024)
                        throw new IOException("Invalid HMC DEX size");
                    try (java.io.InputStream in = zip.getInputStream(entry)) { dexes.add(new Dex(in.readAllBytes())); }
                }
            }
        } catch (RuntimeException badDex) { throw new IOException("Invalid HMC DEX", badDex); }
        if (dexes.isEmpty()) throw new IOException("No HMC DEX");
        try { return resolve(new Index(dexes)); }
        catch (RuntimeException badDex) { throw new IOException("Invalid HMC DEX references", badDex); }
    }
    private static HyperMusicCoverDexSymbols resolve(Index index) throws IOException {
        HyperMusicCoverDexSymbols out = new HyperMusicCoverDexSymbols();
        for (String[] signature : new String[][]{
                {"clockRoots", "()[Landroid/view/View;"}, {"clockTarget", "(Landroid/view/View;)Landroid/view/View;"},
                {"clockHeld", "()Z"}, {"keyguardLocked", "()Z"}, {"onKeyguardNow", "()Z"},
                {"controlCenterShown", "()Z"}, {"screenOn", "()Z"}}) {
            if (!index.named(MAIN, signature[0], signature[1]).isStatic()) throw new IOException("HMC retained API is no longer static");
        }
        if (index.methods(MAIN).stream().anyMatch(m -> m.member.name.equals("glyphUnit")
                && m.member.descriptor.equals("(Landroid/graphics/RectF;)F"))) return legacy(index);
        Def full = index.named(MAIN, "clockFullRatio", "()F");
        Member glyph = one(full.calls("(Landroid/graphics/RectF;)F"), "glyphUnit");
        out.members.put("glyphUnit", glyph);
        Member collapseRatio = one(full.calls("(F)F"), "clock collapse ratio");
        if (index.methods(collapseRatio.owner).stream().noneMatch(m -> m.calls(glyph.descriptor).contains(glyph)))
            throw new IOException("HMC glyphUnit was inlined in clock placement");
        // R8 may inline cardProgress and horizontally merge MediaCard with ClockRoom.
        Set<Member> progress = index.named(MAIN, "enterCoverMode", "(Z)V").fields(true, "F");
        progress.retainAll(index.named(MAIN, "exitCoverMode", "(Z)V").fields(true, "F"));
        progress.retainAll(index.named(MAIN, "onClockReleased", "()V").fields(true, "F"));
        progress.removeIf(m -> MAIN.equals(m.owner));
        out.members.put("progress", one(progress, "card progress field"));
        Set<Member> modes = new LinkedHashSet<>();
        for (Def method : index.methods(MAIN)) {
            if (method.member.name.startsWith("lambda$static$")
                    && method.strings().stream().anyMatch(s -> s.contains("notification bound mode=")))
                modes.addAll(method.fields(false, "I"));
        }
        modes.removeIf(m -> MAIN.equals(m.owner));
        Member mode = one(modes, "fingerprint avoidance");
        out.members.put("fpAvoid", mode);
        Set<Member> hide = new LinkedHashSet<>();
        for (Def method : index.methods(mode.owner)) {
            boolean afterLogAnchor = false;
            for (Use use : method.uses()) {
                if (use.string != null && use.string.contains("fingerprint setting read early: hide=")) afterLogAnchor = true;
                if (afterLogAnchor && use.op == 0x63 && mode.owner.equals(use.member.owner)) hide.add(use.member);
            }
        }
        Member hidden = one(hide, "hide fingerprint flag");
        out.members.put("hideFp", hidden);
        Set<Member> apply = new LinkedHashSet<>(), forKeyguard = new LinkedHashSet<>();
        for (Def method : index.methods(mode.owner)) {
            if (!method.isStatic()) continue;
            if ("(Ljava/lang/Object;)Z".equals(method.member.descriptor)) forKeyguard.add(method.member);
            if ("()V".equals(method.member.descriptor) && method.fields(false, "Z").contains(hidden)
                    && method.calls("()V").stream().anyMatch(m -> "Landroid/view/View;".equals(m.owner) && "invalidate".equals(m.name)))
                apply.add(method.member);
        }
        out.members.put("applyHide", one(apply, "fingerprint redraw"));
        out.members.put("fodForKeyguard", one(forKeyguard, "fingerprint keyguard scope"));
        Member raw = one(index.named(MAIN, "easeRaw", "()F").fields(false, "F"), "clock room raw");
        out.members.put("roomRaw", raw);
        Set<Member> room = new LinkedHashSet<>();
        for (Def method : index.methods(MAIN))
            if (method.member.name.startsWith("lambda$hookNotifStateChange$")
                    || method.member.name.startsWith("lambda$hookClockInteractor$"))
                for (Member call : method.calls("(F)F")) if (raw.owner.equals(call.owner)) room.add(call);
        Member roomForRows = one(room, "roomForRows");
        out.members.put("roomForRows", roomForRows);
        Def roomDef = index.definition(roomForRows);
        Set<Member> now = roomDef.calls("(F)F");
        now.removeIf(m -> !raw.owner.equals(m.owner));
        out.members.put("roomForRowsNow", one(now, "roomForRowsNow"));
        Set<Member> floats = roomDef.fields(true, "F");
        floats.removeIf(m -> !raw.owner.equals(m.owner));
        if (floats.size() != 3 || !floats.contains(raw)) throw new IOException("Unknown HMC room easing layout");
        // The immediate/unobstructed branch writes raw then shown, and zeroes soft last.
        floats.remove(raw);
        Member shown = floats.iterator().next(); floats.remove(shown);
        out.members.put("roomShown", shown);
        out.members.put("roomSoft", one(floats, "room soft start"));
        Set<Member> callbacks = roomDef.fields(false, null);
        Set<Member> easing = new LinkedHashSet<>(), reassert = new LinkedHashSet<>();
        for (Member field : callbacks) {
            if (!raw.owner.equals(field.owner) || !index.implementsType(field.descriptor, "Landroid/view/Choreographer$FrameCallback;")) continue;
            Def frame = index.named(field.descriptor, "doFrame", "(J)V");
            for (Member f : frame.fields(false, "Z")) if (raw.owner.equals(f.owner)) easing.add(f);
            for (Member m : frame.calls("()V")) if (raw.owner.equals(m.owner)) reassert.add(m);
        }
        out.members.put("roomEasing", one(easing, "clock room frame gate"));
        out.members.put("reassertClockRoom", one(reassert, "clock room frame callback"));
        validate(index, out);
        return out;
    }
    private static HyperMusicCoverDexSymbols legacy(Index index) throws IOException {
        HyperMusicCoverDexSymbols out = new HyperMusicCoverDexSymbols();
        for (String[] role : new String[][]{
                {"glyphUnit", "glyphUnit", "(Landroid/graphics/RectF;)F"}, {"progress", "cardProgress", "()F"},
                {"applyHide", "applyHideFp", "()V"}, {"fodForKeyguard", "fodForKeyguard", "(Ljava/lang/Object;)Z"},
                {"roomForRows", "roomForRows", "(F)F"}, {"roomForRowsNow", "roomForRowsNow", "(F)F"},
                {"reassertClockRoom", "reassertClockRoom", "()V"},
                {"fpAvoid", "sFpAvoid", "I"}, {"hideFp", "sHideFp", "Z"}, {"roomRaw", "sRoomRaw", "F"},
                {"roomShown", "sRoomShown", "F"}, {"roomSoft", "sRoomSoft", "F"}, {"roomEasing", "sRoomEasing", "Z"}})
            out.members.put(role[0], new Member(MAIN, role[1], role[2]));
        validate(index, out); return out;
    }
    private static void validate(Index index, HyperMusicCoverDexSymbols symbols) throws IOException {
        for (Member m : symbols.members.values()) {
            if (m.descriptor.startsWith("(")) {
                if (!index.definition(m).isStatic()) throw new IOException("HMC method not static: " + m);
            } else index.requireStaticField(m);
        }
        index.requireStaticField(new Member(MAIN, "sContainer", "Landroid/view/View;"));
        index.requireStaticField(new Member(MAIN, "sHoldY", "Ljava/lang/Float;"));
        index.requireStaticField(new Member(MAIN, "sSelfDriving", "Z"));
    }
    private static Member one(Set<Member> matches, String role) throws IOException {
        if (matches.size() != 1) throw new IOException("HMC " + role + ": expected one match, found " + matches.size());
        return matches.iterator().next();
    }
    private static final class Index {
        final List<Dex> dexes;
        Index(List<Dex> dexes) { this.dexes = dexes; }
        List<Def> methods(String owner) {
            List<Def> found = new ArrayList<>();
            for (Dex dex : dexes) for (int i = 0; i < dex.codes.length; i++)
                if (dex.codes[i] > 0 && owner.equals(dex.methodOwner(i))) found.add(new Def(dex, i));
            return found;
        }
        Def named(String owner, String name, String proto) throws IOException { return definition(new Member(owner, name, proto)); }
        Def definition(Member member) throws IOException {
            for (Def def : methods(member.owner)) if (def.member.equals(member)) return def;
            throw new IOException("Missing HMC method " + member);
        }
        void requireStaticField(Member member) throws IOException {
            for (Dex dex : dexes) for (int i = 0; i < dex.fieldFlags.length; i++) {
                if ((dex.fieldFlags[i] & 8) != 0 && member.owner.equals(dex.type(dex.u16(dex.fieldsAt + i * 8)))
                        && member.equals(dex.field(i))) return;
            }
            throw new IOException("Missing static HMC field " + member);
        }
        boolean implementsType(String owner, String type) {
            for (Dex dex : dexes) if (dex.interfaces.getOrDefault(owner, Collections.emptySet()).contains(type)) return true;
            return false;
        }
    }
    private static final class Use {
        final int op; final Member member; final String string;
        Use(int op, Member member, String string) { this.op = op; this.member = member; this.string = string; }
    }
    private static final class Def {
        final Dex dex; final int id; final Member member;
        List<Use> uses;
        Def(Dex dex, int id) { this.dex = dex; this.id = id; member = dex.method(id); }
        boolean isStatic() { return (dex.flags[id] & 8) != 0; }
        List<Use> uses() {
            if (uses == null) uses = dex.uses(id);
            return uses;
        }
        Set<Member> calls(String proto) {
            Set<Member> result = new LinkedHashSet<>();
            for (Use u : uses()) if (u.op >= 0x6e && u.op <= 0x78 && u.member != null && proto.equals(u.member.descriptor)) result.add(u.member);
            return result;
        }
        Set<Member> fields(boolean write, String type) {
            Set<Member> result = new LinkedHashSet<>();
            for (Use u : uses()) if ((write ? u.op >= 0x67 && u.op <= 0x6d : u.op >= 0x60 && u.op <= 0x66)
                    && (type == null || type.equals(u.member.descriptor))) result.add(u.member);
            return result;
        }
        List<String> strings() {
            List<String> result = new ArrayList<>();
            for (Use u : uses()) if (u.string != null) result.add(u.string);
            return result;
        }
    }
    private static final class Dex {
        final byte[] bytes; final ByteBuffer data;
        final int stringsAt, typesAt, protosAt, fieldsAt, methodsAt;
        final String[] strings, types; final int[] codes, flags, fieldFlags;
        final Map<String, Set<String>> interfaces = new HashMap<>();
        Dex(byte[] bytes) throws IOException {
            this.bytes = bytes; data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.length < 112 || bytes[0] != 'd' || bytes[1] != 'e' || bytes[2] != 'x'
                    || data.getInt(0x24) != 112 || data.getInt(0x28) != 0x12345678)
                throw new IOException("Unsupported HMC DEX header");
            strings = new String[data.getInt(0x38)]; stringsAt = data.getInt(0x3c);
            types = new String[data.getInt(0x40)]; typesAt = data.getInt(0x44);
            fieldFlags = new int[data.getInt(0x50)];
            protosAt = data.getInt(0x4c); fieldsAt = data.getInt(0x54); methodsAt = data.getInt(0x5c);
            codes = new int[data.getInt(0x58)]; flags = new int[codes.length];
            int count = data.getInt(0x60), at = data.getInt(0x64);
            for (int i = 0; i < count; i++, at += 32) {
                String owner = type(data.getInt(at));
                int list = data.getInt(at + 12);
                if (list != 0) {
                    Set<String> implemented = new HashSet<>();
                    for (int n = data.getInt(list), j = 0; j < n; j++) implemented.add(type(u16(list + 4 + j * 2)));
                    interfaces.put(owner, implemented);
                }
                int c = data.getInt(at + 24);
                if (c == 0) continue;
                int[] cursor = {c};
                int staticFields = leb(cursor), instanceFields = leb(cursor), direct = leb(cursor), virtual = leb(cursor);
                for (int kind = 0; kind < 2; kind++) {
                    int id = 0, n = kind == 0 ? staticFields : instanceFields;
                    for (int j = 0; j < n; j++) { id += leb(cursor); fieldFlags[id] = leb(cursor); }
                }
                for (int kind = 0; kind < 2; kind++) {
                    int id = 0, n = kind == 0 ? direct : virtual;
                    for (int j = 0; j < n; j++) { id += leb(cursor); flags[id] = leb(cursor); codes[id] = leb(cursor); }
                }
            }
        }
        int u16(int at) { return data.getShort(at) & 0xffff; }
        int leb(int[] at) {
            int value = 0, shift = 0, b;
            do { b = bytes[at[0]++] & 255; value |= (b & 127) << shift; shift += 7; if (shift > 35) throw new IllegalArgumentException("DEX uleb overflow"); } while ((b & 128) != 0);
            return value;
        }
        String string(int id) {
            if (strings[id] == null) {
                int[] at = {data.getInt(stringsAt + id * 4)}; leb(at);
                int end = at[0]; while (bytes[end] != 0) end++;
                // All member descriptors and semantic anchors used here are ASCII.
                strings[id] = new String(bytes, at[0], end - at[0], StandardCharsets.UTF_8);
            }
            return strings[id];
        }
        String type(int id) { if (types[id] == null) types[id] = string(data.getInt(typesAt + id * 4)); return types[id]; }
        String proto(int id) {
            int at = protosAt + id * 12, args = data.getInt(at + 8);
            StringBuilder s = new StringBuilder("(");
            if (args != 0) for (int n = data.getInt(args), i = 0; i < n; i++) s.append(type(u16(args + 4 + i * 2)));
            return s.append(')').append(type(data.getInt(at + 4))).toString();
        }
        String methodOwner(int id) { return type(u16(methodsAt + id * 8)); }
        Member method(int id) {
            int at = methodsAt + id * 8;
            return new Member(type(u16(at)), string(data.getInt(at + 4)), proto(u16(at + 2)));
        }
        Member field(int id) {
            int at = fieldsAt + id * 8;
            return new Member(type(u16(at)), string(data.getInt(at + 4)), type(u16(at + 2)));
        }
        List<Use> uses(int id) {
            List<Use> result = new ArrayList<>();
            int at = codes[id] + 16, end = at + data.getInt(codes[id] + 12) * 2;
            while (at < end) {
                int word = u16(at), op = word & 255, width = width(op);
                if (op == 0 && word != 0) {
                    if (word == 0x100) width = 4 + u16(at + 2) * 2;
                    else if (word == 0x200) width = 2 + u16(at + 2) * 4;
                    else if (word == 0x300) width = 4 + (int) (((long) u16(at + 2) * data.getInt(at + 4) + 1) / 2);
                    else throw new IllegalArgumentException("Unknown DEX payload");
                } else if (op >= 0x60 && op <= 0x6d) result.add(new Use(op, field(u16(at + 2)), null));
                else if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)) result.add(new Use(op, method(u16(at + 2)), null));
                else if (op == 0x1a || op == 0x1b) result.add(new Use(op, null, string(op == 0x1a ? u16(at + 2) : data.getInt(at + 2))));
                if (width <= 0 || (long) at + width * 2L > end) throw new IllegalArgumentException("Invalid DEX instruction span");
                at += width * 2;
            }
            return result;
        }
        static int width(int op) {
            if (op == 0x18) return 5;
            if (op == 0xfa || op == 0xfb) return 4;
            if (op == 3 || op == 6 || op == 9 || op == 0x14 || op == 0x17 || op == 0x1b
                    || op >= 0x24 && op <= 0x26 || op >= 0x2a && op <= 0x2c
                    || op >= 0x6e && op <= 0x72 || op >= 0x74 && op <= 0x78 || op == 0xfc || op == 0xfd) return 3;
            if (op == 2 || op == 5 || op == 8 || op == 0x13 || op == 0x15 || op == 0x16
                    || op == 0x19 || op == 0x1a || op == 0x1c || op == 0x1f || op == 0x20
                    || op == 0x22 || op == 0x23 || op == 0x29 || op >= 0x2d && op <= 0x3d
                    || op >= 0x44 && op <= 0x6d || op >= 0x90 && op <= 0xaf
                    || op >= 0xd0 && op <= 0xe2 || op == 0xfe || op == 0xff) return 2;
            return 1;
        }
    }
}
