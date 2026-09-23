#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import tempfile
import zipfile
from pathlib import Path


def safe_members(archive: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    members = archive.infolist()
    for member in members:
        path = Path(member.filename)
        if path.is_absolute() or ".." in path.parts:
            raise ValueError(f"危险 ZIP 路径: {member.filename}")
    return members


def verify(pack: Path) -> dict:
    with tempfile.TemporaryDirectory(prefix="zhiti-verify-") as temp_name:
        destination = Path(temp_name)
        with zipfile.ZipFile(pack) as archive:
            archive.extractall(destination, members=safe_members(archive))
        metadata = json.loads((destination / "pack.json").read_text(encoding="utf-8"))
        db = sqlite3.connect(destination / "content.sqlite")
        result = {
            "packId": metadata["packId"],
            "questions": db.execute("SELECT COUNT(*) FROM questions").fetchone()[0],
            "materials": db.execute("SELECT COUNT(*) FROM materials").fetchone()[0],
            "options": db.execute("SELECT COUNT(*) FROM options").fetchone()[0],
            "integrity": db.execute("PRAGMA integrity_check").fetchone()[0],
            "sha256": hashlib.sha256(pack.read_bytes()).hexdigest(),
        }
        db.close()
        if result["questions"] != metadata["questionCount"] or result["materials"] != metadata["materialCount"]:
            raise ValueError("pack.json 与数据库计数不一致")
        if result["options"] != result["questions"] * 4 or result["integrity"] != "ok":
            raise ValueError("数据库完整性检查失败")
        return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("pack", type=Path)
    print(json.dumps(verify(parser.parse_args().pack), ensure_ascii=False, indent=2))

