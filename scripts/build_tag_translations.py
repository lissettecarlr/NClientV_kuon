"""
[FORK] Build app/src/main/assets/tag_zh.json (tag name -> Chinese translation) from the
EhTagTranslation database: https://github.com/EhTagTranslation/Database

Usage: python scripts/build_tag_translations.py
"""
import json
import re
import urllib.request
from pathlib import Path

URL = "https://github.com/EhTagTranslation/Database/releases/latest/download/db.text.json"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/tag_zh.json"

# nhentai tag type -> EhTagTranslation namespaces (first one wins)
NAMESPACES = {
    "tag": ["female", "male", "mixed", "other", "location"],
    "parody": ["parody"],
    "character": ["character"],
    "artist": ["artist"],
    "group": ["group"],
    "language": ["language"],
    "category": ["reclass"],
}

MARKDOWN_IMAGE = re.compile(r"!\[[^\]]*]\([^)]*\)")
HTML_TAG = re.compile(r"<[^>]+>")


def clean(name: str) -> str:
    name = MARKDOWN_IMAGE.sub("", name)
    name = HTML_TAG.sub("", name)
    return name.strip()


def main() -> None:
    with urllib.request.urlopen(URL, timeout=120) as response:
        db = json.load(response)
    namespaces = {ns["namespace"]: ns["data"] for ns in db["data"]}

    result = {}
    for tag_type, sources in NAMESPACES.items():
        for source in sources:
            for original, entry in namespaces.get(source, {}).items():
                translated = clean(entry["name"])
                key = f"{tag_type}:{original.lower()}"
                # no use storing a translation identical to the original
                if not translated or translated.lower() == original.lower() or key in result:
                    continue
                result[key] = translated

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    print(f"{len(result)} entries, {OUT.stat().st_size / 1024:.0f} KiB -> {OUT}")


if __name__ == "__main__":
    main()
