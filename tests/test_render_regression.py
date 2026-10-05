"""Exercise unlimited export with a synthetic image that exposed rotation rounding.

Run from the project root with a full JDK 17+ and the Python image dependencies:
    python tests/test_render_regression.py
PORTRAIT_JAVA can specify the full path to java.exe.
"""
from pathlib import Path
import os
import subprocess
import tempfile
import zipfile
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="portrait-render-regression-") as temporary:
    folder = Path(temporary)
    y, x = np.mgrid[:800, :600]
    colors = np.stack([
        120 + 65 * np.sin(x / 29) + 45 * np.cos(y / 35),
        110 + 50 * np.sin(y / 33) + 50 * np.cos((x + y) / 40),
        120 + 65 * np.cos(x / 34) + 40 * np.sin(y / 40),
    ], axis=2)
    image = folder / "reference.png"
    Image.fromarray(colors.clip(0, 255).astype("uint8")).save(image)
    target = folder / "RegressionPortrait.java"
    completed = subprocess.run([
        os.environ.get("PORTRAIT_JAVA", "java"), "-jar", str(root / "PortraitStudio.jar"),
        "--generate", str(image), str(target), "1", "0", "0", "false", "false",
    ], check=True, capture_output=True, text=True)
    assert "PNG matches preview pixel-for-pixel" in completed.stdout
    assert target.is_file()
    with zipfile.ZipFile(target.with_name("RegressionPortrait_assets.zip")) as archive:
        assert len(archive.namelist()) == 5
        assert archive.testzip() is None
print("PASS: unlimited export compiles, executes, and matches preview pixel-for-pixel.")
