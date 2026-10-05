# Release Notes

## Version 0.2.1

Fixed a render-validation failure caused by tiny differences in rotation arithmetic between the internal preview and exported Java. Both now construct rotations using the same AffineTransform factory. Exact pixel comparison remains enabled. Also corrected dark-mode error-dialog text contrast.

The failure was reproduced with Balanced quality and unlimited budgets on both a detailed synthetic image and a locally supplied image. Both conversions now pass compilation, execution, and exact pixel comparison. The supplied image and its derived geometry are excluded from the public package.

## Version 0.2.0

This release adds File Explorer drag-and-drop loading, a Windows MSI installer with bundled runtimes, an offline user guide, and public release documentation. The application uses neutral titles and default filenames, with optional author attribution left empty.

File drops use the same preview workflow as the file chooser. The application accepts one supported image, retains settings, rejects new drops during processing, and preserves the current session if destination selection is canceled. The Java source pane retains its normal selection and copy behavior.

The automatic workflow retains source and image previews, manual regeneration, session image persistence, line and shape limits, art styles, recoloring, cumulative layers, and ZIP packaging for supporting files. The compiler is given an isolated temporary classpath during export validation.

## Known limitations

- The installer is unsigned; no publisher certificate is included.
- Conversion produces an approximation. Ten visible technique families and close likeness are not guaranteed for every image or budget.
- Image retention lasts for the current session; the source file must remain accessible.
- Style targets can contain more detail than the selected Java budget permits.
- The optional legacy editor has a separate workflow and exports loose supporting files.
- Installation and runtime checks were performed on the release workstation, not a clean Windows virtual machine. Cross-version upgrade behavior requires a later release to test.

See `VERIFICATION.md` for the checks performed on this release.

