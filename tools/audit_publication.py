"""Check tracked publication files and compare the tested APK with the local build."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Tested release APK to compare with the local build")
    args = parser.parse_args()
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    patterns = [
        rb"gh[pousr]_[A-Za-z0-9]{20,}",
        rb"github_pat_[A-Za-z0-9_]{20,}",
        rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
        rb"192\.168\.\d{1,3}\.\d{1,3}",
        rb"adb-[A-Za-z0-9]{8,}",
    ]
    for relative in filter(None, tracked):
        path = ROOT / relative
        if any(part in ("build", "dist", ".gradle") for part in path.relative_to(ROOT).parts):
            raise SystemExit(f"Unexpected generated file: {relative}")
        if path.suffix in (".apk", ".jks", ".keystore", ".pftrace", ".log") or path.name == "local.properties":
            raise SystemExit(f"Unexpected local file: {relative}")
        content = path.read_bytes()
        for pattern in patterns:
            if re.search(pattern, content):
                raise SystemExit(f"Review possible private data in {relative}")
    print(f"Publication scan passed: {len(list(filter(None, tracked)))} tracked files.")
    if args.apk is None:
        return
    original = args.apk if args.apk.is_absolute() else ROOT / args.apk
    rebuilt = ROOT / "projection-lab/build/outputs/apk/debug/projection-lab-debug.apk"
    if not original.is_file() or not rebuilt.is_file():
        raise SystemExit("Both the tested APK and local build are required.")
    if original.exists() and rebuilt.exists():
        with zipfile.ZipFile(original) as a, zipfile.ZipFile(rebuilt) as b:
            names = {n for n in a.namelist() if n.endswith(".dex") or n.startswith("assets/") or n in ("AndroidManifest.xml", "resources.arsc")}
            other = {n for n in b.namelist() if n.endswith(".dex") or n.startswith("assets/") or n in ("AndroidManifest.xml", "resources.arsc")}
            if names != other:
                raise SystemExit("APK runtime entry lists differ.")
            mismatches = [n for n in sorted(names) if a.read(n) != b.read(n)]
            if mismatches:
                raise SystemExit("APK runtime entries differ: " + ", ".join(mismatches))
            print(f"All {len(names)} APK code/assets/manifest/resource-table entries match the tested APK.")


if __name__ == "__main__":
    main()
