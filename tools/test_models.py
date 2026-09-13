"""Run existing geometry, display-direction and scene-gating tests with JDK 17."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SUFFIX = ".exe" if os.name == "nt" else ""


def main():
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        raise SystemExit("Set JAVA_HOME to JDK 17.")
    java = Path(java_home) / "bin" / ("java" + SUFFIX)
    javac = Path(java_home) / "bin" / ("javac" + SUFFIX)
    package = "io.github.sixzleo.tabfold.projection"
    source = ROOT / "projection-lab/src/main/java" / package.replace(".", "/")
    files = [source / f"{name}.java" for name in ("ProjectionMath", "FrameGate", "LockScreenGate", "FoldHoldGate", "FingerSwipeGate", "FoldPose")]
    files += [ROOT / "tools/helpers/EarlyDisplayModel.java", ROOT / "tools/helpers/FoldReturnMotion.java"]
    files += sorted((ROOT / "tools/tests").glob("*.java"))
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="models-", dir=build) as output:
        subprocess.run([str(javac), "-encoding", "UTF-8", "-d", output, *map(str, files)], check=True)
        tests = [package + "." + name for name in ("ProjectionMathTest", "FrameGateTest", "LockScreenGateTest", "FoldHoldGateTest", "FingerSwipeGateTest", "FoldPoseTest")]
        tests += ["io.github.sixzleo.tabfold.probe.EarlyDisplayModelTest", "io.github.sixzleo.tabfold.probe.FoldReturnMotionTest"]
        for test in tests:
            subprocess.run([str(java), "-cp", output, test], check=True)


if __name__ == "__main__":
    main()
