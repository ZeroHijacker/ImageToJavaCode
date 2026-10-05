# Build and Package Guide

## Build the application

Install a full JDK 17 or newer and Python 3.10 or newer. In this source directory, run `Build.bat`, followed by `Setup automatic converter.bat`. The latter creates a local virtual environment and installs the packages in `requirements.txt`; it requires internet access. Run `Launch Portrait Studio.bat` to start the application. No user photographs are required to build it.

The installed distribution uses Java 17.0.20.1+1, CPython 3.13.16, NumPy 2.5.3, OpenCV Python headless 4.14.0.94, and Pillow 12.3.0. `requirements-release.txt` records the exact Python versions. General source setup accepts the compatible ranges in `requirements.txt`.

## Prepare the Windows runtime

1. Obtain the Windows x64 Temurin JDK and matching full source archive from the same release. Verify the publisher checksums. Obtain WiX 3.14.1 and the CPython Windows x64 embeddable ZIP. Exact artifact names and observed hashes are listed in `DEPENDENCIES.json`.
2. Extract the CPython ZIP into a staging directory. Download wheels using `python -m pip download --only-binary=:all: --platform win_amd64 --python-version 313 --implementation cp --abi cp313 -r requirements-release.txt -d wheels`.
3. Extract the wheels into the embedded interpreter's `Lib/site-packages` directory, preserving all package data, dist-info directories, and license files. Add `Lib/site-packages` and `import site` to `python313._pth`, after `python313.zip` and `.`.
4. Omit `cv2/opencv_videoio_ffmpeg4140_64.dll`; the release does not use video input. Preserve the other native libraries and all notices. Confirm that this Python interpreter imports `cv2`, `numpy`, and `PIL` before packaging.
5. Run `Package-Windows.ps1` with the JDK, prepared Python, and WiX directories. The script builds the Java sources, creates the private runtime with jlink, and produces a per-user MSI. Its staging directory is retained for inspection.

```powershell
.\Package-Windows.ps1 -JdkHome C:\Tools\jdk-17 -PythonHome C:\Tools\portrait-python -WixHome C:\Tools\wix314 -Destination .\dist
```

The runtime module roots are `java.desktop`, `java.logging`, `java.compiler`, `jdk.compiler`, `jdk.unsupported`, `jdk.zipfs`, and `jdk.charsets`; jlink includes their dependencies. Compiler support is required because preview generation compiles and executes the exported source. The build uses `--release 17` for application and exported code.

The MSI upgrade identifier is `7c5328de-b660-4cb0-9358-e8e575da3e75`. Preserve it across versions and advance the application version when preparing an update. The installer is unsigned; signing requires the distributor's own certificate and release process.

## Assemble the release

Keep the MSI, original application source ZIP, documentation, matching OpenJDK source archive, and SHA-256 checksums together. Include the complete dependency notices. `Package-Windows.ps1` creates the MSI only; it does not download dependencies, sign, publish, or create a final source bundle.

Before publishing, test installation, launch, conversion, export execution, and uninstall on supported Windows systems. Inspect the public source and examples for private paths and images. Update the verification record with observed results and unresolved limitations. Do not claim clean-machine or upgrade testing unless it was performed.
