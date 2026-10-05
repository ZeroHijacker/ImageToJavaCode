# Portrait Studio User Guide

## Purpose and objectives

This guide explains how to transform an image into a Java 2D drawing, review the result, and export a reproducible program. After completing the workflow, you should be able to control drawing complexity, interpret technique counts, and navigate cumulative layers.

## 1. Create your first portrait

1. Drop one image from File Explorer onto a portrait preview, or choose **Choose image and preview**. Select a future Java destination, such as `Portrait.java`. This chooses the name; it does not save an export yet.
2. Adjust quality, source-line and shape limits, art style, recoloring, and optional layer controls.
3. Review the actual Java rendering, style target, source code, and technique report.
4. Change settings and click **Regenerate** to reuse the selected image. No conversion happens merely by changing a setting.
5. Save the reviewed result, or choose **Discard image** to clear the selection and preview.

Dark mode is enabled initially. Author attribution is optional and empty by default. The selected image and destination stay in memory for this app session; the source file must remain accessible. Saving retains the selection. Later regenerations use an unused numbered filename instead of overwriting existing exports. Closing the app ends the session.

## 2. Understand the output files

Saving writes two files: `Portrait.java` and `Portrait_assets.zip`. The ZIP contains the Java-rendered PNG, normalized original reference PNG, styled target PNG, technique report, and image-analysis JSON. The ZIP is not required to run the Java program.

Images are processed locally. Temporary Java/class files are created to verify compilation and rendering, then cleaned up. Discard clears the in-memory preview and does not delete previously saved exports. A forced process termination can interrupt cleanup.

The original input file is not modified. Reference images in the ZIP are normalized pixel images rather than copies of the original file's EXIF metadata. Reports retain any author text explicitly entered by the user. Do not share generated archives unless you intend to share their reference images and attribution. Java source itself reproduces the image using drawing geometry, so it can also reveal the subject.

This release contains no personal course files, historical screenshots, user photographs, stored user settings, or previous project archives. The included example is a synthetic geometric test image.

## 3. Adjust the drawing

- **Maximum Java lines:** 300-100000, or No limit; counts comments and blank lines too.
- **Maximum drawing shapes:** 1-100000, or No limit. Both ceilings apply together.
- **Show count:** total shapes, visible technique families, or both.
- **Art style:** Original, Realism, Mosaic, Abstract, Cubism, Minimalist, Pixelated.
- **Recolor:** None, Monochrome, Vintage, Sepia, Cool, Warm, Posterized.
- **Add layer comments:** labels each overlap depth in source.
- **Enable keyboard layer viewer:** includes cumulative layer navigation in the exported Java program.

Realism enhances existing detail; it cannot recover unknown detail. Geometric styles are approximations. Small budgets can substantially reduce likeness or visible technique coverage. Review the actual Java preview, not only the style target. RMSE is measured against the styled target and is not a measure of aesthetic quality.

Each shape is one drawing mark. Non-overlapping shapes can share an overlap-depth layer. Layer N includes all layers from 1 through N. In the exported window: H/F1 toggles the hidden legend, arrows navigate, Home shows the base, A/End shows all layers, and a number followed by Enter jumps to a layer. Escape hides the legend.

## 4. Display the exported portrait

On another computer, install a full JDK 17 or newer to run Java source directly. The installer includes a private runtime for Portrait Studio; it does not add Java to the system PATH. Advanced users can invoke the installed `runtime/bin/java.exe` by its full path.

```text
java Portrait.java
```

Or compile with `javac Portrait.java`, then run `java Portrait`. After compilation, `java Portrait --png image.png` writes the full render without a window.

## 5. Use the command line

```text
java -jar PortraitStudio.jar --generate input.png Portrait.java 1 5000 250 true true cubism vintage
```

Arguments after the destination are optional: quality (0/1/2), maximum lines (default 5000), maximum shapes (default unlimited), layer comments, layer viewer, art style, recolor. Use 0 for an unlimited ceiling. Boolean options default false, style defaults original, and recolor defaults none. The CLI saves directly after verification; use the desktop UI for review before saving. `PORTRAIT_PYTHON` can specify a Python executable.

## 6. Explore the source and optional editor

`Build.bat` rebuilds the JAR from the included Java sources. The automatic interface is `AutomaticStudio.java`, processing/export is `AutomaticEngine.java`, image methods are `AutoAnalysis.py`, and theme colors/icons are `StudioTheme.java`.

An optional legacy manual editor is available with `java -jar PortraitStudio.jar --editor`. It uses a separate workflow and can save `.portrait` projects containing reference imagery. Its export format uses numeric shape records and loose image/report files. The main automatic workflow uses explicit drawing statements and the companion ZIP.


## 7. Resolve common problems

| Situation | Action |
| --- | --- |
| The image does not load | Use one readable PNG, JPEG, WEBP, BMP, or TIFF file. URLs, folders, and multiple-file drops are not accepted. A matching extension alone does not make a damaged file readable. |
| A new setting does not change the picture | Select **Regenerate**. Conversion is deliberately deferred until you request it. |
| Save is disabled | Wait for conversion, or regenerate after changing a drawing setting. |
| Fewer than nine techniques are visible | Increase both budgets and regenerate. Inspect the report; the application does not guarantee ten useful techniques for every image. |
| The portrait lacks detail | Increase the source and shape limits. Compare the Java result with the original and style target. Very small limits necessarily simplify the image. |
| A drop is ignored during conversion | Wait for completion or select Cancel before loading another image. |
| The retained image can no longer be read | Restore the source file or select it again. Persistence lasts for the current session and retains the file path, not a permanent imported library. |
| The legend does not respond | Enable the layer viewer before regenerating, run the exported Java program, and click its window before pressing H. |
| Python cannot start in the installed application | Clear an obsolete PORTRAIT_PYTHON override and repair or reinstall the application. Do not run the developer setup script for an installer installation. |
| Conversion is slow | Start with Quick quality and a modest shape budget. Cancel remains available during processing. |

The automatic portrait is a geometric approximation. Inspect facial proportions, colors, hair, and distinctive details before submitting or sharing it. Technique coverage is a diagnostic measure, not a guarantee of an assessment score.
