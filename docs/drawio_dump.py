#!/usr/bin/env python3
"""Превращает UI.drawio (сжатый или нет) в детерминированный текстовый дамп.

Использование:
    python tools/drawio_dump.py docs/UI.drawio -o docs/ui-dump/UI.dump.txt

Дамп не содержит координат (чтобы перемещение блоков не давало шум в diff).
Рабочий процесс при обновлении UI: см. AGENTS.md §9.
"""
import argparse
import base64
import html
import re
import sys
import urllib.parse
import xml.etree.ElementTree as ET
import zlib


def clean(value):
    v = html.unescape(value or "")
    v = re.sub(r"<br\s*/?>", "\n", v)
    v = re.sub(r"</(p|div|li)>", "\n", v)
    v = re.sub(r"<[^>]+>", "", v)
    v = html.unescape(v).strip()
    return re.sub(r"\s*\n\s*", " | ", v)


def page_model(diagram):
    model = diagram.find("mxGraphModel")
    if model is not None:
        return model
    text = (diagram.text or "").strip()
    xml = urllib.parse.unquote(zlib.decompress(base64.b64decode(text), -15).decode("utf-8"))
    return ET.fromstring(xml)


def dump(path):
    root = ET.parse(path).getroot()
    diagrams = [root] if root.tag == "diagram" else root.findall("diagram")
    lines = []
    for d in diagrams:
        model = page_model(d)
        cells = {c.get("id"): c for c in model.iter("mxCell")}
        lines.append(f"##### PAGE: {d.get('name')}")
        for c in model.iter("mxCell"):
            if c.get("edge") == "1":
                s, t = cells.get(c.get("source")), cells.get(c.get("target"))
                sv = clean(s.get("value"))[:50] if s is not None else "?"
                tv = clean(t.get("value"))[:50] if t is not None else "?"
                lines.append(f"EDGE [{c.get('id')}] '{clean(c.get('value'))}': "
                             f"{c.get('source')}<{sv}> -> {c.get('target')}<{tv}>")
            else:
                v = clean(c.get("value"))
                if v:
                    lines.append(f"NODE [{c.get('id')}] parent={c.get('parent')}: {v}")
        lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("drawio")
    ap.add_argument("-o", "--out")
    a = ap.parse_args()
    text = dump(a.drawio)
    if a.out:
        with open(a.out, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    else:
        sys.stdout.write(text)
