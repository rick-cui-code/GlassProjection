"""Rebuild the bundled shell helpers with JDK 17 and Android Build Tools 35.0.0.

No third-party Python packages required. --check compares without changing assets.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    java_home = os.environ.get("JAVA_HOME")
    sdk_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not java_home or not sdk_home:
        parser.error("Set JAVA_HOME (JDK 17) and ANDROID_HOME (Android SDK).")
    suffix = ".exe" if os.name == "nt" else ""
    java = Path(java_home) / "bin" / ("java" + suffix)
    javac = Path(java_home) / "bin" / ("javac" + suffix)
    sdk = Path(sdk_home)
    android = sdk / "platforms/android-35/android.jar"
    d8 = sdk / "build-tools/35.0.0/lib/d8.jar"
    for file in (java, javac, android, d8):
        if not file.is_file():
            parser.error(f"Missing required tool: {file}")
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    assets = ROOT / "projection-lab/src/main/assets/helpers"
    assets.mkdir(parents=True, exist_ok=True)
    groups = {
        "live": ["LiveMirrorWindowProbe", "LiveBlurPyramid", "FoldReturnMotion", "OutputOwnerGuard"],
        "controller": ["EarlyDisplayHelper", "EarlyDisplayModel"],
    }
    with tempfile.TemporaryDirectory(prefix="helpers-", dir=build) as temp:
        for name, classes in groups.items():
            work = Path(temp) / name
            compiled, dex = work / "classes", work / "dex"
            compiled.mkdir(parents=True)
            dex.mkdir()
            sources = [ROOT / "tools/helpers" / (c + ".java") for c in classes]
            run(javac, "-encoding", "UTF-8", "-cp", android, "-d", compiled, *sources)
            run(java, "-cp", d8, "com.android.tools.r8.D8", "--min-api", "33",
                "--output", dex, *sorted(compiled.rglob("*.class")))
            generated, bundled = dex / "classes.dex", assets / (name + ".dex")
            if args.check:
                if not bundled.is_file() or digest(generated) != digest(bundled):
                    raise SystemExit(f"MISMATCH: {name}.dex; rebuild after reviewing helper changes.")
                print(f"MATCH {name}.dex {digest(generated)}", flush=True)
            else:
                shutil.copyfile(generated, bundled)
                print(f"BUILT {name}.dex {digest(generated)}", flush=True)


if __name__ == "__main__":
    main()
