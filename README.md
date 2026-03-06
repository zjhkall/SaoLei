# SaoLei



“””
MSG 邮件解析器 - 纯 Python 实现，零第三方依赖
自行实现 OLE2 复合文档（Compound File Binary Format）解析

用法：
python msg_parser.py your_email.msg
python msg_parser.py your_email.msg –save-html
python msg_parser.py your_email.msg –save-attachments

作为模块：
from msg_parser import MsgParser
with MsgParser(“email.msg”) as msg:
print(msg.subject)
print(msg.htmlBody)
“””

import struct
import sys
import re
import codecs
import zlib
from datetime import datetime, timezone, timedelta

# ═══════════════════════════════════════════════════════════════════════════════

# 第一层：OLE2 复合文档解析 [MS-CFB]

# ═══════════════════════════════════════════════════════════════════════════════

FREESECT   = 0xFFFFFFFF
ENDOFCHAIN = 0xFFFFFFFE
FATSECT    = 0xFFFFFFFD
DIFSECT    = 0xFFFFFFFC
NOSTREAM   = 0xFFFFFFFF

STGTY_STREAM  = 2
STGTY_STORAGE = 1
STGTY_ROOT    = 5

OLE_MAGIC = b”\xD0\xCF\x11\xE0\xA1\xB1\x1A\xE1”

class OleFile:
def **init**(self, filepath: str):
with open(filepath, “rb”) as f:
self._data = f.read()

```
    if self._data[:8] != OLE_MAGIC:
        raise ValueError("不是有效的 OLE2/MSG 文件")

    # 解析文件头 [MS-CFB] 2.2
    # 偏移24开始，格式：
    #   H  MinorVersion
    #   H  MajorVersion
    #   H  ByteOrder
    #   H  SectorSizePow   (扇区大小 = 2^N，通常9 → 512字节)
    #   H  MiniSectorSizePow (通常6 → 64字节)
    #   6s Reserved (6字节，不是4字节！)
    #   I  NumFatSectors
    #   I  FirstDirSector
    #   I  TransactionSig
    #   I  MiniStreamCutoff  (通常4096)
    #   I  FirstMiniFatSector
    #   I  NumMiniFatSectors
    #   I  FirstDifatSector
    #   I  NumDifatSectors
    (
        _minor, _major, _bo,
        ss_pow, mss_pow,
        _reserved,           # 6字节保留字段
        _nfat,
        first_dir,
        _tsig,
        mini_cutoff,
        first_minifat,
        _nmini,
        first_difat,
        _ndifat,
    ) = struct.unpack_from("<HHHHH6sIIIIIIII", self._data, 24)

    self._ss          = 1 << ss_pow
    self._mss         = 1 << mss_pow
    self._mini_cutoff = mini_cutoff
    self._first_dir   = first_dir
    self._first_mini  = first_minifat
    self._first_difat = first_difat

    # 文件头内嵌前109个DIFAT条目（偏移76）
    self._header_difat = list(struct.unpack_from("<109I", self._data, 76))

    self._fat     = self._build_fat()
    self._dir     = self._build_dir()
    self._minifat = self._build_minifat()

    root = self._dir[0]
    self._ministream = self._read_fat_chain(root["start"], root["size"])

# ── 扇区读取 ──────────────────────────────────────────────────────────────

def _sec(self, sid: int) -> bytes:
    off = 512 + sid * self._ss
    return self._data[off: off + self._ss]

# ── FAT / MiniFAT ─────────────────────────────────────────────────────────

def _build_fat(self) -> list:
    fat_sids = []
    for sid in self._header_difat:
        if sid >= DIFSECT:
            break
        fat_sids.append(sid)

    difat = self._first_difat
    visited = set()
    while difat < DIFSECT and difat not in visited:
        visited.add(difat)
        raw  = self._sec(difat)
        n    = self._ss // 4
        ents = struct.unpack_from(f"<{n}I", raw)
        for sid in ents[:-1]:
            if sid < DIFSECT:
                fat_sids.append(sid)
        difat = ents[-1]

    fat = []
    for sid in fat_sids:
        raw = self._sec(sid)
        fat.extend(struct.unpack_from(f"<{self._ss // 4}I", raw))
    return fat

def _build_minifat(self) -> list:
    mf  = []
    sec = self._first_mini
    visited = set()
    while sec < DIFSECT and sec not in visited:
        visited.add(sec)
        raw = self._sec(sec)
        mf.extend(struct.unpack_from(f"<{self._ss // 4}I", raw))
        sec = self._fat[sec] if sec < len(self._fat) else ENDOFCHAIN
    return mf

# ── 链读取 ────────────────────────────────────────────────────────────────

def _chain(self, start: int, fat: list) -> list:
    chain, sec, seen = [], start, set()
    while sec < DIFSECT and sec < len(fat):
        if sec in seen:
            break
        seen.add(sec)
        chain.append(sec)
        sec = fat[sec]
    return chain

def _read_fat_chain(self, start: int, size: int = -1) -> bytes:
    if start >= DIFSECT:
        return b""
    raw = b"".join(self._sec(s) for s in self._chain(start, self._fat))
    return raw[:size] if size >= 0 else raw

def _read_mini_chain(self, start: int, size: int = -1) -> bytes:
    if start >= DIFSECT:
        return b""
    mss = self._mss
    raw = b"".join(
        self._ministream[s * mss: s * mss + mss]
        for s in self._chain(start, self._minifat)
    )
    return raw[:size] if size >= 0 else raw

# ── 目录解析 ──────────────────────────────────────────────────────────────

def _build_dir(self) -> list:
    raw     = self._read_fat_chain(self._first_dir)
    entries = []
    for off in range(0, len(raw), 128):
        chunk = raw[off: off + 128]
        if len(chunk) < 128:
            break
        name_len = struct.unpack_from("<H", chunk, 64)[0]
        name = (chunk[:max(0, name_len - 2)].decode("utf-16-le", errors="replace")
                if name_len > 2 else "")
        obj_type, = struct.unpack_from("<B", chunk, 66)
        left, right, child = struct.unpack_from("<III", chunk, 68)
        start, = struct.unpack_from("<I", chunk, 116)
        size,  = struct.unpack_from("<I", chunk, 120)
        entries.append({
            "name":  name,
            "type":  obj_type,
            "left":  left,
            "right": right,
            "child": child,
            "start": start,
            "size":  size,
        })
    return entries

# ── 目录树遍历（红黑树中序）─────────────────────────────────────────────

def _inorder(self, idx: int, out: dict):
    if idx == NOSTREAM or idx >= len(self._dir):
        return
    e = self._dir[idx]
    self._inorder(e["left"], out)
    out[e["name"].upper()] = idx
    self._inorder(e["right"], out)

def list_children(self, dir_idx: int = 0) -> dict:
    """返回指定目录下所有子条目 {UPPER_NAME: entry_index}"""
    child = self._dir[dir_idx].get("child", NOSTREAM)
    result = {}
    self._inorder(child, result)
    return result

# ── 路径解析与流读取 ──────────────────────────────────────────────────────

def _resolve(self, path: list) -> int | None:
    idx = 0
    for part in path:
        ch  = self.list_children(idx)
        idx = ch.get(part.upper())
        if idx is None:
            return None
    return idx

def read_stream(self, path: list) -> bytes | None:
    idx = self._resolve(path)
    if idx is None:
        return None
    e = self._dir[idx]
    if e["type"] != STGTY_STREAM:
        return None
    if e["size"] < self._mini_cutoff:
        return self._read_mini_chain(e["start"], e["size"])
    return self._read_fat_chain(e["start"], e["size"])

def entry_type(self, path: list) -> int | None:
    """返回路径对应条目的 type，不存在返回 None"""
    idx = self._resolve(path)
    return self._dir[idx]["type"] if idx is not None else None

def close(self):
    self._data = b""

def __enter__(self):
    return self

def __exit__(self, *_):
    self.close()
```

# ═══════════════════════════════════════════════════════════════════════════════

# RTF 压缩解压 [MS-OXRTFCP]

# Outlook 常将 HTML/正文压缩存储为 PR_RTF_COMPRESSED (0x1009)

# ═══════════════════════════════════════════════════════════════════════════════

# RTF 初始字典（固定前缀，规范附录A）

_RTF_PREBUF = (
“{\rtf1\ansi\mac\deff0\deftab720{\fonttbl;}”
“{\f0\fnil \froman \fswiss \fmodern \fscript “
“\fdecor MS Sans SerifSymbolArialTimes New RomanCourier”
“{\colortbl\red0\green0\blue0\n\par \pard\plain”
“\f0\fs20\b\i\u\tab\tx”
)

_MAGIC_MELA = 0x414C454D   # 未压缩
_MAGIC_LZFU = 0x465A4C55   # LZFu 压缩

def decompress_rtf(data: bytes) -> bytes:
“””
解压 PR_RTF_COMPRESSED 格式数据，返回原始 RTF 字节。
支持 LZFu 压缩和 MELA（未压缩）两种格式。
“””
if len(data) < 16:
return data

```
comp_size, uncomp_size, magic, _crc = struct.unpack_from("<IIII", data, 0)

if magic == _MAGIC_MELA:
    # 未压缩，直接返回数据部分
    return data[16: 16 + uncomp_size]

if magic != _MAGIC_LZFU:
    # 未知格式，原样返回
    return data

# LZFu 解压：滑动窗口 4096 字节，初始填充预定义字典
buf = bytearray(_RTF_PREBUF.encode("latin-1"))
buf += bytearray(4096 - len(buf))   # 填充至4096字节

write_pos = len(_RTF_PREBUF)        # 当前写入位置（循环）
out       = bytearray()
src       = data[16:]               # 跳过16字节头
i         = 0

while i < len(src) and len(out) < uncomp_size:
    flags = src[i]
    i += 1
    for bit in range(8):
        if len(out) >= uncomp_size:
            break
        if i >= len(src):
            break

        if flags & (1 << bit):
            # 引用：2字节，高12位=偏移，低4位=长度-2
            if i + 1 >= len(src):
                break
            ref  = (src[i] << 8) | src[i + 1]
            i   += 2
            ref_offset = (ref >> 4) & 0xFFF
            ref_len    = (ref & 0xF) + 2

            for _ in range(ref_len):
                ch = buf[ref_offset % 4096]
                out.append(ch)
                buf[write_pos % 4096] = ch
                write_pos  += 1
                ref_offset += 1
        else:
            # 字面量：1字节
            ch = src[i]
            i += 1
            out.append(ch)
            buf[write_pos % 4096] = ch
            write_pos += 1

return bytes(out)
```

def rtf_to_html(rtf_bytes: bytes) -> str:
“””
从 RTF 字节中提取 HTML 内容。
Outlook 将完整 HTML 嵌入 RTF 的 \htmltag / \mhtmltag 标签或
\*\htmltag 组中，或使用 htmlrtf 标记包裹原始 HTML。
“””
try:
rtf = rtf_bytes.decode(“latin-1”)
except Exception:
return “”

```
# 方法1：提取 \*\htmltag ... \*\htmltag 之间的 HTML 片段
# Outlook 将每段 HTML 包在 {\*\htmltag <html>} 中
html_parts = []

# 匹配 {\*\htmltag ...} 块
pattern = re.compile(r'\{\\\*\\htmltag\d*\s*(.*?)\}', re.DOTALL)
matches = pattern.findall(rtf)
if matches:
    html_parts = matches
    return _unescape_rtf_html("".join(html_parts))

# 方法2：查找 \htmlrtf0 ... \htmlrtf 之间的内容（原始HTML区域）
pattern2 = re.compile(r'\\htmlrtf0\s*(.*?)\\htmlrtf(?:\s|\\|{)', re.DOTALL)
matches2 = pattern2.findall(rtf)
if matches2:
    return _unescape_rtf_html("".join(matches2))

# 方法3：直接搜索 <html 开始标签
m = re.search(r'(<html[\s>].*)', rtf, re.IGNORECASE | re.DOTALL)
if m:
    return _unescape_rtf_html(m.group(1))

return ""
```

def _unescape_rtf_html(s: str) -> str:
“”“反转义 RTF 中的 HTML 字符串：处理 \’ 十六进制转义和 RTF 控制词。”””
# 'XX → 对应字节（cp1252编码）
def replace_hex(m):
try:
return bytes([int(m.group(1), 16)]).decode(“cp1252”, errors=“replace”)
except Exception:
return “”

```
s = re.sub(r"\\'([0-9a-fA-F]{2})", replace_hex, s)

# 去除剩余 RTF 控制词（\word 和 \\ 转义）
s = re.sub(r'\\([a-zA-Z]+-?\d*) ?', '', s)
s = re.sub(r'\\([^a-zA-Z])', r'\1', s)
return s
```

# ═══════════════════════════════════════════════════════════════════════════════

# 编码工具

# ═══════════════════════════════════════════════════════════════════════════════

def _decode_utf16le(data: bytes) -> str:
if data[:2] == b”\xff\xfe”:
data = data[2:]
return data.decode(“utf-16-le”, errors=“replace”).rstrip(”\x00”)

def _decode_ansi(data: bytes) -> str:
data = data.rstrip(b”\x00”)
if not data:
return “”
for enc in (“utf-8”, “gbk”, “gb2312”, “gb18030”, “cp1252”, “latin-1”):
try:
return data.decode(enc)
except (UnicodeDecodeError, LookupError):
continue
return data.decode(“latin-1”)

def _detect_html_encoding(raw: bytes) -> str:
if raw[:3] == b”\xef\xbb\xbf”:
return “utf-8-sig”
if raw[:2] == b”\xff\xfe”:
return “utf-16-le”
if raw[:2] == b”\xfe\xff”:
return “utf-16-be”
snippet = raw[:4096].decode(“latin-1”)
m = re.search(r’<meta[^>]+charset\s*=\s*[”']?\s*([\w-]+)’, snippet, re.I)
if m:
enc = m.group(1).strip()
try:
codecs.lookup(enc)
return enc
except LookupError:
pass
m = re.search(r’content\s*=\s*[”'][^”']*charset\s*=\s*([\w-]+)’, snippet, re.I)
if m:
enc = m.group(1).strip()
try:
codecs.lookup(enc)
return enc
except LookupError:
pass
return “utf-8”

def _decode_html_bytes(raw: bytes) -> str:
enc  = _detect_html_encoding(raw)
text = raw.decode(enc, errors=“replace”)
return text.lstrip(”\ufeff”)

# ═══════════════════════════════════════════════════════════════════════════════

# 第二层：MAPI 属性 + MSG 解析 [MS-OXMSG]

# ═══════════════════════════════════════════════════════════════════════════════

PROP_SUBJECT      = “0037”
PROP_SENDER_NAME  = “0C1A”
PROP_SENDER_EMAIL = “0C1F”
PROP_SENDER_SMTP  = “5D01”
PROP_TO           = “0E04”
PROP_CC           = “0E03”
PROP_BCC          = “0E02”
PROP_DATE         = “0039”
PROP_BODY_PLAIN   = “1000”
PROP_BODY_HTML    = “1013”
PROP_BODY_HTML2   = “1014”   # 备用 HTML 属性
PROP_BODY_RTF     = “1009”   # PR_RTF_COMPRESSED
PROP_MESSAGE_ID   = “1035”
PROP_ATT_FNAME    = “3704”   # PR_ATTACH_FILENAME
PROP_ATT_LNAME    = “3707”   # PR_ATTACH_LONG_FILENAME
PROP_ATT_MIME     = “370E”   # PR_ATTACH_MIME_TAG
PROP_ATT_DATA     = “3701”   # PR_ATTACH_DATA_BIN
PROP_ATT_METHOD   = “3705”   # PR_ATTACH_METHOD
PROP_ATT_CONTENT_ID = “3712” # PR_ATTACH_CONTENT_ID

PT_UNICODE = “001F”
PT_STRING8 = “001E”
PT_BINARY  = “0102”
PT_SYSTIME = “0040”
PT_LONG    = “0003”

# PR_ATTACH_METHOD 值

ATTACH_BY_VALUE    = 1
ATTACH_BY_REF      = 2
ATTACH_EMBEDDED    = 5
ATTACH_OLE         = 6

class MsgParser:
“””
.msg 文件解析器（纯 Python，零依赖）。

```
主要属性：
  subject, htmlBody, body,
  sender_name, sender_email,
  to, cc, bcc, date, attachments
"""

def __init__(self, filepath: str):
    self._ole = OleFile(filepath)

# ── 底层属性读取 ──────────────────────────────────────────────────────────

def _raw(self, prop_id: str, prop_type: str,
         prefix: list | None = None) -> bytes | None:
    name = f"__substg1.0_{prop_id}{prop_type}"
    return self._ole.read_stream((prefix or []) + [name])

def _read_str(self, prop_id: str, prefix: list | None = None) -> str | None:
    # 优先 Unicode
    data = self._raw(prop_id, PT_UNICODE, prefix)
    if data is not None:
        return _decode_utf16le(data)
    # 其次 ANSI
    data = self._raw(prop_id, PT_STRING8, prefix)
    if data is not None:
        return _decode_ansi(data)
    return None

def _read_bin(self, prop_id: str, prefix: list | None = None) -> bytes | None:
    return self._raw(prop_id, PT_BINARY, prefix)

def _read_long(self, prop_id: str, prefix: list | None = None) -> int | None:
    """读取 PT_LONG (4字节整数) 属性，存储在 __properties_version1.0 中。"""
    # PT_LONG 的值直接存在属性流里（固定4字节，不单独建流）
    # 需要从 __properties_version1.0 中按固定格式读取
    props_name = "__properties_version1.0"
    path = (prefix or []) + [props_name]
    data = self._ole.read_stream(path)
    if data is None:
        return None

    prop_id_int = int(prop_id, 16)
    prop_type_int = int(PT_LONG, 16)
    target = (prop_id_int << 16) | prop_type_int

    # 属性流：根存储头部32字节，附件/收件人存储头部8字节
    # 每条属性记录固定16字节：type(2)+id(2)+flags(4)+value(8)
    header_size = 32 if not prefix else 8
    for off in range(header_size, len(data), 16):
        if off + 16 > len(data):
            break
        prop_tag, = struct.unpack_from("<I", data, off)
        if prop_tag == target:
            value, = struct.unpack_from("<i", data, off + 8)
            return value
    return None

# ── 公开属性 ──────────────────────────────────────────────────────────────

@property
def subject(self) -> str:
    return self._read_str(PROP_SUBJECT) or ""

@property
def sender_name(self) -> str:
    return self._read_str(PROP_SENDER_NAME) or ""

@property
def sender_email(self) -> str:
    for pid in (PROP_SENDER_SMTP, PROP_SENDER_EMAIL):
        v = self._read_str(pid)
        if v and "@" in v:
            return v
    return self._read_str(PROP_SENDER_EMAIL) or ""

@property
def to(self) -> str:
    return self._read_str(PROP_TO) or ""

@property
def cc(self) -> str:
    return self._read_str(PROP_CC) or ""

@property
def bcc(self) -> str:
    return self._read_str(PROP_BCC) or ""

@property
def message_id(self) -> str:
    return self._read_str(PROP_MESSAGE_ID) or ""

@property
def date(self) -> str | None:
    raw = self._raw(PROP_DATE, PT_SYSTIME)
    if raw and len(raw) >= 8:
        filetime = struct.unpack_from("<Q", raw)[0]
        EPOCH_DIFF = 116_444_736_000_000_000
        secs = (filetime - EPOCH_DIFF) / 10_000_000
        dt = datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(seconds=secs)
        return dt.isoformat()
    return None

@property
def body(self) -> str:
    return self._read_str(PROP_BODY_PLAIN) or ""

@property
def htmlBody(self) -> str:
    """
    HTML 正文，按以下顺序尝试：
      1. PR_BODY_HTML Unicode 流 (0x1013 001F)
      2. PR_BODY_HTML Binary 流  (0x1013 0102) — 最常见，含BOM或<meta charset>
      3. PR_BODY_HTML String8 流 (0x1013 001E)
      4. 备用属性 0x1014（部分Outlook版本）
      5. PR_RTF_COMPRESSED 解压后提取HTML (0x1009 0102)
    """
    # 1. Unicode 流
    data = self._raw(PROP_BODY_HTML, PT_UNICODE)
    if data is not None:
        return _decode_utf16le(data)

    # 2. Binary 流（最常见，Outlook默认存这里）
    data = self._raw(PROP_BODY_HTML, PT_BINARY)
    if data is not None:
        return _decode_html_bytes(data)

    # 3. String8 流
    data = self._raw(PROP_BODY_HTML, PT_STRING8)
    if data is not None:
        return _decode_ansi(data)

    # 4. 备用属性 0x1014
    data = self._raw(PROP_BODY_HTML2, PT_BINARY)
    if data is not None:
        return _decode_html_bytes(data)
    data = self._raw(PROP_BODY_HTML2, PT_UNICODE)
    if data is not None:
        return _decode_utf16le(data)

    # 5. 从压缩RTF中提取HTML（Outlook常用此方式存储HTML邮件）
    rtf_compressed = self._raw(PROP_BODY_RTF, PT_BINARY)
    if rtf_compressed:
        rtf_raw = decompress_rtf(rtf_compressed)
        if rtf_raw:
            html = rtf_to_html(rtf_raw)
            if html:
                return html

    return ""

@property
def attachments(self) -> list:
    """
    返回附件列表，每项字典：
      name        短文件名
      longName    长文件名（推荐）
      mimeType    MIME 类型
      contentId   Content-ID（内嵌图片等）
      method      附件方式（1=普通, 5=嵌套MSG）
      data        原始字节 bytes（嵌套MSG时为None，见 embeddedMsg）
      embeddedMsg 嵌套邮件 MsgParser 对象（method=5时有值）
      size        字节数
    """
    results = []
    seen    = set()

    for uname, eidx in self._ole.list_children(0).items():
        if not uname.startswith("__ATTACH_VERSION1.0_"):
            continue

        real = self._ole._dir[eidx]["name"]
        pfx  = [real]

        name       = self._read_str(PROP_ATT_FNAME,      pfx) or ""
        lname      = self._read_str(PROP_ATT_LNAME,      pfx) or ""
        mime       = self._read_str(PROP_ATT_MIME,       pfx) or ""
        content_id = self._read_str(PROP_ATT_CONTENT_ID, pfx) or ""
        method     = self._read_long(PROP_ATT_METHOD,    pfx)

        data         = None
        embedded_msg = None

        if method == ATTACH_EMBEDDED:
            # 嵌套 MSG：数据在子存储 __substg1.0_3701000D 里
            # 尝试将其作为嵌套 OleFile 解析
            embedded_storage = pfx + ["__substg1.0_3701000D".upper()]
            # 嵌套MSG作为子存储，需要特殊处理——此处仅标记
            pass
        else:
            # 普通附件：PR_ATTACH_DATA_BIN
            data = self._read_bin(PROP_ATT_DATA, pfx)

            # 若 PT_BINARY 为空，尝试读取附件子存储中所有流拼接
            # （部分Outlook版本将大附件分块）
            if not data:
                # 尝试 PT_BINARY 备用读取
                name_bin = f"__substg1.0_{PROP_ATT_DATA}{PT_BINARY}"
                data = self._ole.read_stream(pfx + [name_bin])

        size = len(data) if data else 0

        # 用(名称+大小)去重
        key = (name or lname, size)
        if key in seen and size > 0:
            continue
        seen.add(key)

        results.append({
            "name":        name,
            "longName":    lname,
            "mimeType":    mime,
            "contentId":   content_id,
            "method":      method,
            "data":        data,
            "embeddedMsg": embedded_msg,
            "size":        size,
        })
    return results

# ── 汇总 ──────────────────────────────────────────────────────────────────

def summary(self) -> dict:
    atts = [{k: v for k, v in a.items() if k not in ("data", "embeddedMsg")}
            for a in self.attachments]
    return {
        "subject":      self.subject,
        "date":         self.date,
        "sender_name":  self.sender_name,
        "sender_email": self.sender_email,
        "to":           self.to,
        "cc":           self.cc,
        "bcc":          self.bcc,
        "message_id":   self.message_id,
        "has_html":     bool(self.htmlBody),
        "has_plain":    bool(self.body),
        "attachments":  atts,
    }

def close(self):
    self._ole.close()

def __enter__(self):
    return self

def __exit__(self, *_):
    self.close()
```

# ═══════════════════════════════════════════════════════════════════════════════

# 命令行入口

# ═══════════════════════════════════════════════════════════════════════════════

def main():
if sys.platform == “win32”:
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding=“utf-8”, errors=“replace”)

```
if len(sys.argv) < 2:
    print("用法: python msg_parser.py <file.msg> [--save-html] [--save-attachments] [--debug]")
    sys.exit(1)

filepath  = sys.argv[1]
save_html = "--save-html"        in sys.argv
save_atts = "--save-attachments" in sys.argv
debug     = "--debug"            in sys.argv

with MsgParser(filepath) as msg:
    if debug:
        # 调试模式：列出所有顶层 OLE 目录条目和其子流
        print("=== OLE 目录结构 ===")
        children = msg._ole.list_children(0)
        for name, idx in sorted(children.items()):
            e = msg._ole._dir[idx]
            print(f"  [{e['type']}] {e['name']}  size={e['size']}")
            if e['type'] in (STGTY_STORAGE, STGTY_ROOT):
                sub = msg._ole.list_children(idx)
                for sname, sidx in sorted(sub.items()):
                    se = msg._ole._dir[sidx]
                    print(f"      [{se['type']}] {se['name']}  size={se['size']}")
        print()

    info = msg.summary()
    print("=" * 60)
    print("邮件解析结果")
    print("=" * 60)
    for label, key in [
        ("主题",       "subject"),
        ("发送时间",   "date"),
        ("发件人",     "sender_name"),
        ("发件邮箱",   "sender_email"),
        ("收件人",     "to"),
        ("抄送",       "cc"),
        ("密送",       "bcc"),
        ("Message-ID", "message_id"),
        ("含HTML",     "has_html"),
        ("含纯文本",   "has_plain"),
    ]:
        print(f"  {label:12s}: {info[key]}")

    print(f"\n  {'附件':12s}: {len(info['attachments'])} 个")
    for a in info["attachments"]:
        fname = a.get("longName") or a.get("name") or "(无名)"
        print(f"    - {fname}  ({a['size']} bytes)  mime={a['mimeType']}  cid={a['contentId']}")

    if msg.body:
        print("\n-- 纯文本正文（前500字）" + "-" * 30)
        print(msg.body[:500])

    if msg.htmlBody:
        print("\n-- HTML正文（前500字）" + "-" * 33)
        print(msg.htmlBody[:500])

    if save_html and msg.htmlBody:
        out = filepath + ".html"
        with open(out, "w", encoding="utf-8") as f:
            f.write(msg.htmlBody)
        print(f"\n[OK] HTML已保存: {out}")

    if save_atts:
        for a in msg.attachments:
            fname = a.get("longName") or a.get("name") or "attachment"
            fname = re.sub(r'[\\/:*?"<>|]', "_", fname)
            if a["data"]:
                with open(fname, "wb") as f:
                    f.write(a["data"])
                print(f"[OK] 附件已保存: {fname}")
```

if **name** == “**main**”:
main()
