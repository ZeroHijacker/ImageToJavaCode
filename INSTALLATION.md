# Installation Guide

## Requirements

Use 64-bit Windows 10 or Windows 11. Allow approximately 1 GB of free disk space for installation and temporary work, plus room for your images and exports. A display of at least 1300 by 950 logical pixels is recommended. Other operating systems and Windows ARM64 have not been release-tested.

## Install and launch

1. Extract the release ZIP to a local folder. Keep the accompanying notices and source archive if you redistribute the release.
2. Open `PortraitStudio-0.2.1-Windows-x64.msi`.
3. Read the license, select an installation directory when offered, and complete the wizard. This is a per-user installation.
4. Open **Portrait Studio** from the Start menu or desktop shortcut.

The installer includes a private Java runtime with compiler support and a private Python interpreter with its image libraries. It does not change the system Java or Python installation, associate image files, or add those runtimes to PATH. The program needs no network connection for conversion.

The package has no code-signing certificate. Windows may show an unknown-publisher warning. Verify the download source and SHA-256 checksum; do not disable operating-system protections. An organization's policy may require a signed installer.

## Verify the download

Open PowerShell in the extracted folder and run:

```powershell
Get-FileHash .\PortraitStudio-0.2.1-Windows-x64.msi -Algorithm SHA256
```

Compare the result with `SHA256SUMS.txt` supplied with the release. A checksum detects a changed file; it does not independently authenticate the publisher.

## Update, repair, and remove

Close Portrait Studio before changing the installation. For this first installer release, uninstall an earlier copy before installing it in a different directory. Future installers should preserve the documented upgrade identifier. Repair or remove the installation from Windows **Settings > Apps > Installed apps**, or rerun the MSI.

Uninstalling removes installed application files. It does not intentionally remove portraits or archives that you saved elsewhere. Keep exports in a documents folder, outside the installation directory. A source ZIP is a developer distribution and does not need installation; see `BUILDING.md`.

