# Privacy and Local Data

Portrait Studio processes images on the computer running it. The application contains no telemetry, account sign-in, remote image service, or automatic update check. Developer dependency setup requires internet access; ordinary use of the installed application does not.

The current image path, settings, preview, and optional author text are held for the active session. Discard clears the selected image and preview. Closing the application ends the session. The original file is not modified, and previously saved exports are not deleted by Discard.

Preview generation creates temporary image-analysis files, Java source, compiled classes, and a rendered PNG. Normal completion or cancellation cleans up the temporary directory. A crash or forced shutdown may leave temporary files in the operating system's temporary folder.

Saving creates a Java file and an assets ZIP. The ZIP includes a normalized reference image, style target, rendered portrait, analysis data, and technique report. Normalization does not copy the original image's EXIF metadata. Optional author text is retained in the output. The Java geometry itself reproduces the image and may reveal the subject.

Share exports only when you intend to share their image content. Error dialogs and diagnostic logs can contain local paths; remove identifying details before reporting a problem. The public application source and example do not include private course material, personal photographs, or the developer's local account paths.
