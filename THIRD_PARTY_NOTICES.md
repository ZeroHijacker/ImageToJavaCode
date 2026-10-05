# Third-Party Components

The MIT License in this directory covers original Portrait Studio code. It does not replace the terms of bundled runtimes, libraries, or user-supplied images. Upstream copyright statements and full license texts are retained with their components.

| Component | Bundled version | Notice location in the installation |
| --- | --- | --- |
| Eclipse Temurin / OpenJDK | 17.0.20.1+1 | `runtime/legal/` and `app/licenses/Java-LICENSE` |
| CPython | 3.13.16 | `app/python/LICENSE.txt` |
| NumPy and bundled native libraries | 2.5.3 | `app/python/Lib/site-packages/numpy-2.5.3.dist-info/licenses/` |
| OpenCV Python headless | 4.14.0.94 | `app/python/Lib/site-packages/cv2/LICENSE.txt`, `LICENSE-3RD-PARTY.txt`, and its dist-info licenses |
| Pillow and bundled native libraries | 12.3.0 | `app/python/Lib/site-packages/pillow-12.3.0.dist-info/licenses/` |
| WiX installer tooling | 3.14.1 | `app/licenses/WiX-LICENSE.txt` |

OpenJDK is distributed under GPL version 2 with the Classpath Exception and applicable additional notices. Its runtime is produced with jlink without modifying upstream source. The matching full OpenJDK source archive is included in the release ZIP under `third-party-sources/`. Redistributors should provide that archive alongside the installer and preserve the runtime notices. `BUILDING.md` identifies the runtime modules and build steps. The archive comes from the same Temurin release as the binaries; publisher SHA-256 checksums were verified.

CPython, NumPy, OpenCV, Pillow, and their bundled native dependencies have separate permissive and other component-specific terms. Consult the complete retained texts for the applicable component. The optional OpenCV FFmpeg video plugin is omitted because this application processes still images; upstream notices remain intact. WiX is used to construct the MSI. Its matching source archive is included under `third-party-sources/` in the release ZIP, together with its notice for installer components. Preserve that source when redistributing the installer.

Official projects: https://adoptium.net/ ; https://www.python.org/ ; https://numpy.org/ ; https://github.com/opencv/opencv-python ; https://python-pillow.org/ ; https://github.com/wixtoolset/wix3 . Exact downloaded artifact names and hashes appear in `DEPENDENCIES.json`.

Generated portrait geometry depends on the supplied image. This application does not grant rights to that image. The application license does not require the user's portrait exports to adopt MIT.
