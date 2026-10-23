#!/usr/bin/env python3
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "icons"
TARGET = ROOT / "apps" / "android" / "app" / "src" / "main" / "res" / "drawable"
PREFIX = "pi_"

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{paths}
</vector>
"""

PATH = """    <path
        android:pathData="{data}"
        android:strokeWidth="{width}"
        android:strokeColor="#FF000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />"""


def convert(svg: Path) -> str:
    text = svg.read_text()
    width = re.search(r'stroke-width="([\d.]+)"', text).group(1)
    paths = [PATH.format(data=data, width=width) for data in re.findall(r'<path d="([^"]+)"', text)]
    return TEMPLATE.format(paths="\n".join(paths))


def main() -> None:
    for old in TARGET.glob(f"{PREFIX}*.xml"):
        old.unlink()
    sources = sorted(SOURCE.glob("*.svg"))
    for svg in sources:
        name = PREFIX + svg.stem.replace("-", "_")
        (TARGET / f"{name}.xml").write_text(convert(svg))
    print(f"{len(sources)} icons -> {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
