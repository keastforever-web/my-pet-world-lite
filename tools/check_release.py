"""Offline checks for the publishable Lite source. Requires Python 3.10+ only.

This is a best-effort leak/packaging check, not a rights clearance or security audit.
Only transient build/IDE state is ignored. Credentials in source fail the check.
"""
from pathlib import Path
import gzip
import hashlib
import json
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
TRANSIENT = {"build", ".gradle", ".kotlin", ".idea", ".build-cache", ".git", "__pycache__"}
LOCAL = {"local.properties", ".DS_Store", "Thumbs.db"}
BAD_SUFFIXES = {".apk", ".aab", ".jks", ".keystore", ".key", ".pem", ".p12", ".pfx", ".db", ".sqlite", ".log"}
TEXT_SUFFIXES = {".kt", ".kts", ".java", ".json", ".xml", ".md", ".txt", ".yml", ".yaml", ".properties", ".py", ".sh", ".bat", ".pro", ".gpl"}
SECRET_RULES = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})"),
    "API token": re.compile(r"\bsk-[A-Za-z0-9_-]{24,}"),
    "cloud access key": re.compile(r"\bLTAI[A-Za-z0-9]{16,}"),
    "private workstation path": re.compile(r"[A-Za-z]:[/\\]Users[/\\][^\s/\\]+", re.I),
}

def source_files(root=ROOT):
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if set(relative.parts) & TRANSIENT or path.name in LOCAL:
            continue
        if path.is_symlink():
            raise ValueError(f"Symlink is not a publishable source file: {relative}")
        if path.is_file():
            yield path

def file_issues(relative, data):
    issues = []
    if relative.suffix.lower() in BAD_SUFFIXES or relative.name == "keystore.properties" or relative.name.startswith(".env"):
        issues.append("credential, local data, or generated binary filename")
    if data.startswith(b"SQLite format 3\0"):
        issues.append("local SQLite database")
    if len(data) > 95 * 1024 * 1024:
        issues.append("file exceeds 95 MiB source budget")
    if relative.suffix.lower() in TEXT_SUFFIXES or not relative.suffix:
        content = data.decode("utf-8", errors="replace")
        issues.extend(label for label, pattern in SECRET_RULES.items() if pattern.search(content))
    return issues

def check(root=ROOT):
    files = list(source_files(root)); failures = []
    for path in files:
        for issue in file_issues(path.relative_to(root), path.read_bytes()):
            failures.append(f"{path.relative_to(root).as_posix()}: {issue}")
    assert not failures, "\n".join(failures)
    assert not (root / "feature/park").exists(), "Park module must not be included"
    assert not (root / "app/src/full").exists(), "Full variant must not be included"
    assert not any(p.name == "video-pets-v9.bin" for p in files), "Retired video pack must not be included"
    roster = json.loads((root / "docs/builtin-companions.json").read_text("utf-8"))
    animal_ids = {"builtin-dog", "builtin-snake", "builtin-bunny", "builtin-cat"}
    sprite_ids = {f"pack16-{n}" for n in range(1, 6)} | {f"bear16-{n}" for n in ["honey", "rose", "lilac", "mint", "blue"]}
    assert len(roster) == 14 and {p["id"] for p in roster} == animal_ids | sprite_ids
    assets = root / "core/data/src/main/assets/sprites-v16"
    index = json.loads((assets / "index.json").read_text("utf-8"))
    assert len(index) == 10 and {p["id"] for p in index} == sprite_ids
    actions = frames = 0
    for pet in index:
        assert pet["width"] == pet["height"] == 128 and len(pet["clips"]) == 8
        assert {"idle", "walk"} <= {c["action"] for c in pet["clips"]}
        image = (assets / pet["preview"]).read_bytes()
        assert image[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack_from(">II", image, 16) == (128, 128)
        for clip in pet["clips"]:
            assert len(clip["ms"]) >= 8 and all(0 < t <= 10000 for t in clip["ms"])
            raw = gzip.decompress((assets / clip["file"]).read_bytes())
            assert len(raw) == 128 * 128 * 4 * len(clip["ms"]), clip["file"]
            actions += 1; frames += len(clip["ms"])
    toolchain = json.loads((root / "toolchain.json").read_text("utf-8"))
    wrapper = root / "gradle/wrapper/gradle-wrapper.jar"
    assert hashlib.sha256(wrapper.read_bytes()).hexdigest() == toolchain["wrapper_sha256"]
    properties = (root / "gradle/wrapper/gradle-wrapper.properties").read_text("utf-8")
    assert "distributionSha256Sum=" + toolchain["distribution_sha256"] in properties
    for doc in [p for p in files if p.suffix == ".md"]:
        content = doc.read_text("utf-8")
        targets = re.findall(r"\]\(([^\s)]+)\)", content) + re.findall(r'<img\s+src="([^"]+)"', content)
        for target in targets:
            if re.match(r"[a-zA-Z]+:", target) or target.startswith("#"):
                continue
            assert (doc.parent / target.split("#")[0]).exists(), f"Broken local link: {doc.relative_to(root)} -> {target}"
    print(f"PASS: {len(files)} source files; 14 defaults; {actions} sprite actions; {frames} frames; wrapper hashes; local document links; filename/token scan.")
    print("Excluded local.properties/build/IDE state is never part of the source archive. Review Git staging separately.")
    print("Rights and original-code license remain separate human decisions; this check does not grant distribution rights.")
    return files

if __name__ == "__main__":
    try:
        check()
    except (AssertionError, ValueError, OSError, KeyError) as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
