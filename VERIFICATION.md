# Release Verification

## Version 0.2.1 regression checks

The previous release failed exact render verification on a detailed synthetic image and the locally supplied failing image with Balanced quality, unlimited lines and shapes, Original style, no recoloring, and both layer options disabled. After aligning rotation construction, both complete conversions pass compilation, execution, and pixel-for-pixel comparison. No comparison tolerance was added.

A reproducible synthetic test is included at `tests/test_render_regression.py`. No supplied image or derived drawing data is included in this test or release.

## Version 0.2.0 baseline checks

Version 0.2.0 was checked on the Windows release workstation. These are local validation results, not certification or clean-machine testing.

| Check | Result |
| --- | --- |
| Application source compiled with Java 17 target | Passed |
| Single-image file-drop flow and retained settings | Passed through Swing TransferHandler integration tests |
| Busy state, multiple images, directory, unsupported type, text/URL rejection | Passed |
| Canceled destination preserves the current selection | Passed |
| Java code selection/copy handler preserved | Passed |
| Empty author fields and no retained private session at startup | Passed |
| Updated dark interface rendered offscreen and visually inspected | Passed |
| Bundled Python imports after omission of the optional video plugin | Passed through complete conversion |
| Native launcher with system Java/Python excluded from PATH | Passed |
| Export compilation, execution, and pixel comparison with preview | Passed |
| Synthetic 12-shape, 600-line export with layer comments and viewer | 358 lines; 10 visible technique families; within both limits |
| Cubism with vintage recoloring through the native launcher | Passed |
| Per-user MSI installation to a dedicated test directory | Passed; Windows Installer exit code 0 |
| Installed native application startup and conversion | Passed |
| Desktop and Start menu shortcuts | Created successfully |
| Uninstall and shortcut removal | Passed; Windows Installer exit code 0; test launcher and shortcuts removed |

The earlier feature checks covered line and shape limits, overlap-depth ordering, keyboard layers, preview cancellation, style preprocessing, ZIP contents, retained image selection, and explicit regeneration. This release additionally exercised the packaging and drag-and-drop paths listed above.

## Limits of verification

Native drag-and-drop was exercised through the same Swing file-list transfer interface, not an automated physical mouse gesture from Explorer. Installer tests used the release workstation. A clean Windows virtual machine, additional display scaling settings, organization-managed Windows policies, and a future version upgrade remain useful pre-publication checks. The installer is unsigned.

The public application sources, documentation, example, and application JAR are checked for known private identifiers and local account paths before bundling. Unmodified upstream dependency licenses retain their own copyright holders and notices.


