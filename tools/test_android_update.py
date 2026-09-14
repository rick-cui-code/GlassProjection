"""Verify GitHub APK download on the connected phone without installing the downloaded APK."""
import argparse
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    sdk = Path(os.environ["ANDROID_HOME"])
    jdk = Path(os.environ["JAVA_HOME"]) / "bin"
    suffix = ".exe" if os.name == "nt" else ""
    classes = ROOT / "projection-lab/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
    apksig = next((Path.home() / ".gradle/caches/modules-2/files-2.1/com.android.tools.build/apksig/8.6.1").glob("*/*.jar"))
    android = sdk / "platforms/android-35/android.jar"
    adb = [args.adb, "-s", args.serial]
    with tempfile.TemporaryDirectory(prefix="update-android-", dir=ROOT / "build") as temp:
        work = Path(temp)
        subprocess.run([str(jdk / ("javac" + suffix)), "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, [android, classes, apksig])), "-d", str(work), str(ROOT / "tools/android-tests/UpdateSmoke.java")], check=True)
        subprocess.run([str(jdk / ("java" + suffix)), "-cp", str(sdk / "build-tools/35.0.0/lib/d8.jar"), "com.android.tools.r8.D8", "--min-api", "33", "--lib", str(android), "--classpath", str(classes), "--output", str(work), *map(str, work.rglob("*.class"))], check=True)
        remote = "/data/local/tmp/glass-update-smoke.dex"
        subprocess.run([*adb, "shell", "rm", "-f", remote], check=True)
        subprocess.run([*adb, "push", str(work / "classes.dex"), remote], check=True)
        subprocess.run([*adb, "shell", "chmod", "444", remote], check=True)
        installed = subprocess.check_output([*adb, "shell", "pm", "path", "io.github.sixzleo.tabfold.projection"], text=True).strip().removeprefix("package:")
        if not installed.startswith("/data/app/") or not installed.endswith("/base.apk") or "\n" in installed:
            raise SystemExit("Unexpected installed APK path")
        command = "CLASSPATH=" + shlex.quote(remote + ":" + installed) + " app_process /system/bin io.github.sixzleo.tabfold.projection.UpdateSmoke /data/local/tmp/glass-update-smoke"
        subprocess.run([*adb, "shell", command], check=True)


if __name__ == "__main__":
    main()
