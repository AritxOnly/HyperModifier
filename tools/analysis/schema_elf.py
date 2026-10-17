"""Minimal offline ELF reader for generating schema ABI contracts."""
import struct,lzma
from pathlib import Path
class Elf:
 def __init__(self,data):
  self.d=data;off=struct.unpack_from('<Q',data,40)[0];es,n,si=struct.unpack_from('<HHH',data,58)
  ss=[struct.unpack_from('<IIQQQQIIQQ',data,off+i*es) for i in range(n)];s=ss[si];ns=data[s[4]:s[4]+s[5]]
  self.ss={ns[s[0]:].split(b'\0',1)[0].decode():s for s in ss};self.syms={};self.byaddr={}
  for key in ['.dynsym','.symtab']:
   if key not in self.ss:continue
   s=self.ss[key];st=ss[s[6]];ns=data[st[4]:st[4]+st[5]]
   for o in range(s[4],s[4]+s[5],24):
    ni,inf,other,sh,addr,size=struct.unpack_from('<IBBHQQ',data,o);name=ns[ni:].split(b'\0',1)[0].decode()
    if sh and inf&15==2 and size:self.syms[name]=(addr,size);self.byaddr[addr]=(name,size)
 def section(self,n):
  s=self.ss[n];return self.d[s[4]:s[4]+s[5]]
 def read(self,addr,n):
  for s in self.ss.values():
   if s[3]<=addr and addr+n<=s[3]+s[5] and s[1]!=8:return self.d[s[4]+addr-s[3]:s[4]+addr-s[3]+n]
  raise Exception(hex(addr))
names=['ActivityInfo_get_package_name','ActivityInfo_get_name','PackageItemInfo_get_package_name','PackageItemInfo_get_name','Bundle_get_boolean','Bundle_contains_key','Bundle_clone','Bundle_drop','ActivityInfo_drop','PackageItemInfo_drop','ActivityInfo_get_meta_data','PackageItemInfo_get_meta_data','Bundle_get_i32','AppWidgetProviderInfo_clone','AppWidgetProviderInfo_drop','ActivityInfo_clone','Intent_get_action','Intent_get_sender_package_name','Intent_get_extras']
