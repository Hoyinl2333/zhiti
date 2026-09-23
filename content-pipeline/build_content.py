#!/usr/bin/env python3
"""Build deterministic, signed offline content packs for Zhiti."""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import shutil
import sqlite3
import subprocess
import tempfile
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

UPSTREAM_COMMIT = "84ab93d4b64b61d897bece8a1c0a5bab06b4feb2"
SCHEMA_VERSION = 1
QUESTION_ROOTS = {
    "judgment": "10-真题/判断推理",
    "data-analysis": "10-真题/资料分析",
}
ALLOWED_IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
IMG_RE = re.compile(r"<img\b[^>]*?src=[\"']([^\"']+)[\"'][^>]*>", re.I)
TAG_RE = re.compile(r"<[^>]+>")
SCRIPT_RE = re.compile(r"<(script|style|iframe|object|embed)\b.*?</\1\s*>", re.I | re.S)
SECTION_RE = re.compile(r"^###\s+(.+?)\s*$", re.M)
FRONT_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.S)
OPTION_RE = re.compile(r"^-\s*([A-D])\.\s*(.*)$")


@dataclass
class Audit:
    errors: list[dict] = field(default_factory=list)
    exclusions: list[dict] = field(default_factory=list)
    warnings: list[dict] = field(default_factory=list)

    def add(self, level: str, path: Path | str, reason: str) -> None:
        getattr(self, level).append({"path": str(path), "reason": reason})


def front_matter(text: str) -> dict[str, str]:
    match = FRONT_RE.search(text)
    if not match:
        return {}
    result: dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        value = value.strip().strip('"').strip("'")
        result[key.strip()] = value
    return result


def section(text: str, name: str) -> str:
    pattern = re.compile(
        rf"^###\s+{re.escape(name)}\s*$\n(.*?)(?=^###\s+|\Z)", re.M | re.S
    )
    match = pattern.search(text)
    return match.group(1).strip() if match else ""


def heading_section(text: str, name: str) -> str:
    pattern = re.compile(
        rf"^##\s+{re.escape(name)}\s*$\n(.*?)(?=^##\s+|^---\s*$|\Z)", re.M | re.S
    )
    match = pattern.search(text)
    return match.group(1).strip() if match else ""


def title_from(text: str) -> str:
    match = re.search(r"^#\s+(.+?)\s*$", text, re.M)
    return match.group(1).strip() if match else ""


def clean_text(value: str) -> str:
    value = SCRIPT_RE.sub("", value)
    value = re.sub(r"<br\s*/?>", "\n", value, flags=re.I)
    value = re.sub(r"</p\s*>", "\n", value, flags=re.I)
    value = TAG_RE.sub("", value)
    value = re.sub(r"\[\[.*?\|([^\]]+)]]", r"\1", value)
    value = re.sub(r"\[\[([^\]]+)]]", r"\1", value)
    value = html.unescape(value).replace("\u00a0", " ")
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def resolve_asset(source_file: Path, raw_src: str, source_root: Path) -> Path | None:
    candidate = (source_file.parent / html.unescape(raw_src)).resolve()
    try:
        candidate.relative_to(source_root.resolve())
    except ValueError:
        return None
    if not candidate.is_file() or candidate.suffix.lower() not in ALLOWED_IMAGE_SUFFIXES:
        return None
    return candidate


def content_blocks(
    value: str,
    source_file: Path,
    source_root: Path,
    assets: dict[str, Path],
    audit: Audit,
) -> list[dict]:
    value = SCRIPT_RE.sub("", value)
    blocks: list[dict] = []
    cursor = 0
    for match in IMG_RE.finditer(value):
        before = clean_text(value[cursor : match.start()])
        if before:
            blocks.extend(text_blocks(before))
        resolved = resolve_asset(source_file, match.group(1), source_root)
        if resolved is None:
            audit.add("errors", source_file.relative_to(source_root), f"缺失或越界图片: {match.group(1)}")
        else:
            digest = hashlib.sha256(resolved.read_bytes()).hexdigest()[:16]
            asset_name = f"{digest}{resolved.suffix.lower()}"
            assets[asset_name] = resolved
            blocks.append({"type": "image", "asset": f"assets/{asset_name}", "alt": "题目图片"})
        cursor = match.end()
    tail = clean_text(value[cursor:])
    if tail:
        blocks.extend(text_blocks(tail))
    return blocks


def text_blocks(value: str) -> list[dict]:
    return [
        {"type": "text", "text": paragraph.strip()}
        for paragraph in re.split(r"\n\s*\n", value)
        if paragraph.strip()
    ]


def option_rows(options_text: str) -> tuple[list[tuple[str, str]], list[str]]:
    rows: list[tuple[str, str]] = []
    answers: list[str] = []
    current_key = ""
    current_value: list[str] = []
    for line in options_text.splitlines():
        match = OPTION_RE.match(line)
        if match:
            if current_key:
                rows.append((current_key, "\n".join(current_value).strip()))
            current_key = match.group(1)
            body = match.group(2)
            if "✅" in body:
                answers.append(current_key)
            current_value = [body.replace("✅", "").strip()]
        elif current_key:
            if "✅" in line:
                answers.append(current_key)
            current_value.append(line.replace("✅", ""))
    if current_key:
        rows.append((current_key, "\n".join(current_value).strip()))
    return rows, sorted(set(answers))


def missing_images(value: str, source_file: Path, source_root: Path) -> list[str]:
    return [
        match.group(1)
        for match in IMG_RE.finditer(value)
        if resolve_asset(source_file, match.group(1), source_root) is None
    ]


def material_id(text: str) -> str | None:
    match = re.search(r"材料：\[\[15-材料/资料分析/[^|\]]+\|(\d+)", text)
    return match.group(1) if match else None


def parse_materials(source_root: Path, audit: Audit) -> dict[str, dict]:
    result: dict[str, dict] = {}
    base = source_root / "15-材料" / "资料分析"
    for path in sorted(base.glob("*.md")):
        text = path.read_text(encoding="utf-8")
        meta = front_matter(text)
        mid = meta.get("mid", "")
        original = section(text, "材料原文")
        if not mid or not original:
            audit.add("exclusions", path.relative_to(source_root), "材料缺少 mid 或材料原文")
            continue
        if mid in result:
            audit.add("exclusions", path.relative_to(source_root), f"重复 mid: {mid}")
            continue
        missing = missing_images(original, path, source_root)
        if missing:
            for image in missing:
                audit.add("exclusions", path.relative_to(source_root), f"材料缺失图片: {image}")
            continue
        result[mid] = {
            "mid": mid,
            "title": meta.get("材料主题") or title_from(text),
            "raw": original,
            "path": path,
            "source_path": str(path.relative_to(source_root)),
        }
    return result


def parse_questions(source_root: Path, module: str, materials: dict[str, dict], audit: Audit) -> list[dict]:
    result: list[dict] = []
    root = source_root / QUESTION_ROOTS[module]
    seen: set[str] = set()
    for path in sorted(root.rglob("*.md")):
        text = path.read_text(encoding="utf-8")
        meta = front_matter(text)
        qid = meta.get("qid", "")
        stem = section(text, "题干")
        options_text = section(text, "选项")
        explanation = section(text, "官方解析")
        inline_material = section(text, "给定材料")
        rows, answers = option_rows(options_text)
        if not answers:
            inferred = re.search(r"故正确答案为\s*([A-D]+)", explanation)
            if inferred and len(inferred.group(1)) == 1:
                answers = [inferred.group(1)]
        reasons: list[str] = []
        if not qid:
            reasons.append("缺少 qid")
        if qid in seen:
            reasons.append(f"重复 qid: {qid}")
        if not stem:
            reasons.append("缺少题干")
        if [key for key, _ in rows] != ["A", "B", "C", "D"]:
            reasons.append("选项不是完整且有序的 A-D")
        if len(answers) != 1:
            reasons.append(f"答案标记数量不是 1: {answers}")
        mid = material_id(text) if module == "data-analysis" else None
        if module == "data-analysis" and mid and mid not in materials:
            reasons.append(f"材料关联无效: {mid}")
        if module == "data-analysis" and not mid and not clean_text(inline_material):
            reasons.append("缺少材料关联与内嵌材料")
        raw_parts = [
            stem, options_text, explanation, heading_section(text, "最快解法"),
            heading_section(text, "推理链"), heading_section(text, "易错点"), inline_material,
        ]
        for raw in raw_parts:
            for image in missing_images(raw, path, source_root):
                reasons.append(f"缺失图片: {image}")
        if reasons:
            for reason in reasons:
                audit.add("exclusions", path.relative_to(source_root), reason)
            continue
        seen.add(qid)
        category = path.relative_to(root).parts[0] if path.relative_to(root).parts else "其他"
        result.append(
            {
                "qid": qid,
                "module": module,
                "category": category,
                "year": int(meta["年份"]) if meta.get("年份", "").isdigit() else None,
                "region": meta.get("地区", ""),
                "paper": meta.get("试卷", ""),
                "title": title_from(text),
                "stem_raw": stem,
                "options_raw": rows,
                "answer": answers[0],
                "explanation_raw": explanation,
                "fast_raw": heading_section(text, "最快解法").lstrip("：: "),
                "reasoning_raw": heading_section(text, "推理链"),
                "pitfalls_raw": heading_section(text, "易错点"),
                "material_id": mid,
                "inline_material_raw": inline_material if not mid else "",
                "path": path,
                "source_path": str(path.relative_to(source_root)),
            }
        )
    return result


def compact_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def create_database(
    db_path: Path,
    module: str,
    version: str,
    source_root: Path,
    questions: list[dict],
    materials: dict[str, dict],
    assets: dict[str, Path],
    audit: Audit,
) -> tuple[int, int]:
    schema = (Path(__file__).parent / "schema.sql").read_text(encoding="utf-8")
    connection = sqlite3.connect(db_path)
    connection.executescript(schema)
    connection.executemany(
        "INSERT INTO metadata(key,value) VALUES(?,?)",
        sorted({"schemaVersion": str(SCHEMA_VERSION), "contentVersion": version, "upstreamCommit": UPSTREAM_COMMIT, "packId": module}.items()),
    )
    used_materials = sorted(materials) if module == "data-analysis" else []
    for mid in used_materials:
        item = materials[mid]
        blocks = content_blocks(item["raw"], item["path"], source_root, assets, audit)
        connection.execute(
            "INSERT INTO materials(mid,title,content_json,source_path) VALUES(?,?,?,?)",
            (mid, item["title"], compact_json(blocks), item["source_path"]),
        )
    for q in sorted(questions, key=lambda item: int(item["qid"])):
        blocks = lambda raw: compact_json(content_blocks(raw, q["path"], source_root, assets, audit))
        connection.execute(
            "INSERT INTO questions VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                q["qid"], q["module"], q["category"], q["year"], q["region"], q["paper"], q["title"],
                blocks(q["stem_raw"]), q["answer"], blocks(q["explanation_raw"]), blocks(q["fast_raw"]),
                blocks(q["reasoning_raw"]), blocks(q["pitfalls_raw"]), q["material_id"],
                blocks(q["inline_material_raw"]), q["source_path"],
            ),
        )
        for key, raw in q["options_raw"]:
            connection.execute("INSERT INTO options VALUES(?,?,?)", (q["qid"], key, blocks(raw)))
    connection.commit()
    connection.execute("VACUUM")
    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    connection.close()
    if integrity != "ok":
        raise RuntimeError(f"SQLite integrity_check failed: {integrity}")
    return len(questions), len(used_materials)


def write_deterministic_zip(source_dir: Path, output: Path) -> None:
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(p for p in source_dir.rglob("*") if p.is_file()):
            relative = path.relative_to(source_dir).as_posix()
            info = zipfile.ZipInfo(relative, date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sign_file(path: Path, key: Path, output: Path) -> None:
    subprocess.run(
        ["openssl", "pkeyutl", "-sign", "-rawin", "-inkey", str(key), "-in", str(path), "-out", str(output)],
        check=True,
    )


def validate_source(source_root: Path) -> None:
    commit = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=source_root, text=True, capture_output=True, check=True
    ).stdout.strip()
    if commit != UPSTREAM_COMMIT:
        raise SystemExit(f"上游提交不匹配：需要 {UPSTREAM_COMMIT}，实际 {commit}")


def build(args: argparse.Namespace) -> None:
    source_root = args.source.resolve()
    output = args.output.resolve()
    validate_source(source_root)
    output.mkdir(parents=True, exist_ok=True)
    audit = Audit()
    materials = parse_materials(source_root, audit)
    catalog_packs: list[dict] = []
    counts: dict[str, dict] = {}

    for module in QUESTION_ROOTS:
        questions = parse_questions(source_root, module, materials, audit)
        assets: dict[str, Path] = {}
        with tempfile.TemporaryDirectory(prefix=f"zhiti-{module}-") as temp_name:
            pack_dir = Path(temp_name)
            db_path = pack_dir / "content.sqlite"
            question_count, material_count = create_database(
                db_path, module, args.version, source_root, questions, materials, assets, audit
            )
            assets_dir = pack_dir / "assets"
            assets_dir.mkdir()
            for asset_name, asset_source in sorted(assets.items()):
                shutil.copyfile(asset_source, assets_dir / asset_name)
            pack_metadata = {
                "schemaVersion": SCHEMA_VERSION,
                "contentVersion": args.version,
                "upstreamCommit": UPSTREAM_COMMIT,
                "packId": module,
                "questionCount": question_count,
                "materialCount": material_count,
                "assetCount": len(assets),
            }
            (pack_dir / "pack.json").write_text(compact_json(pack_metadata) + "\n", encoding="utf-8")
            zip_name = f"{module}-{args.version}.zip"
            zip_path = output / zip_name
            write_deterministic_zip(pack_dir, zip_path)
        pack_hash = sha256(zip_path)
        catalog_packs.append(
            {
                **pack_metadata,
                "size": zip_path.stat().st_size,
                "sha256": pack_hash,
                "downloadUrl": f"{args.base_url.rstrip('/')}/{module}/{args.version}",
            }
        )
        counts[module] = pack_metadata

    audit_doc = {
        "upstreamCommit": UPSTREAM_COMMIT,
        "expected": {"judgment": 7713, "data-analysis": 3569, "materials": 717, "images": 21829},
        "built": counts,
        "errors": sorted(audit.errors, key=lambda item: (item["path"], item["reason"])),
        "exclusions": sorted(audit.exclusions, key=lambda item: (item["path"], item["reason"])),
        "warnings": sorted(audit.warnings, key=lambda item: (item["path"], item["reason"])),
    }
    (output / "audit.json").write_text(json.dumps(audit_doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if audit.errors:
        raise SystemExit(f"构建失败：审计报告包含 {len(audit.errors)} 个错误，见 {output / 'audit.json'}")

    catalog = {
        "schemaVersion": SCHEMA_VERSION,
        "contentVersion": args.version,
        "upstreamCommit": UPSTREAM_COMMIT,
        "packs": sorted(catalog_packs, key=lambda item: item["packId"]),
    }
    catalog_path = output / "catalog.json"
    catalog_path.write_text(compact_json(catalog) + "\n", encoding="utf-8")
    if args.signing_key:
        sign_file(catalog_path, args.signing_key.resolve(), output / "catalog.sig")
    print(json.dumps({"catalog": catalog, "audit": str(output / "audit.json")}, ensure_ascii=False, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--signing-key", type=Path)
    return parser.parse_args()


if __name__ == "__main__":
    build(parse_args())
