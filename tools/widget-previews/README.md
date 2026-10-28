# Launcher preview artwork

`render.py` generates four independent, static lossless WebP resources in
`app/src/main/res/drawable-nodpi/widget_*_launcher_preview.webp`.
It does not modify Android layouts or create a demo profile.

Use Python 3.9 or newer with Pillow and WebP support. From the project root:

```sh
python -m pip install -r tools/widget-previews/requirements.txt
python tools/widget-previews/render.py
```

The script works independently of the current working directory. Its default
output directory is resolved relative to the script's project checkout.

Text font discovery supports Windows, macOS and Linux. It looks for a complete
regular/bold pair of Roboto, Arial, DejaVu Sans or Liberation Sans in system and
user font directories, then tries Pillow's platform font lookup. Use `--font-dir`
to select a directory containing one of those TTF pairs explicitly. Use the same
font files on each machine when identical typography is required.

For icons, the script reads the application's Community Material 5.8.55 font from
its cached AAR dependency or extracted Gradle resources. It respects
`GRADLE_USER_HOME`, falling back to `~/.gradle`. Run a Gradle build first if the
dependency has not been downloaded, or pass `--icon-font` with
`community_material_font_v5_8_55.ttf`. It never downloads dependencies itself.
These are the same refresh, download-outline and emoticon-happy-outline glyphs
used by the providers.

To render into a separate directory without overwriting Android resources:

```sh
python tools/widget-previews/render.py --output-dir app/build/widget-previews
```

`--help` lists all options. Missing fonts, missing icon dependencies or unavailable
WebP support produce an error before any previews are written. Fonts are loaded
from the local machine; no fonts are bundled with this tool.

Artwork follows the normal-size runtime layouts: 52dp list headers, 12dp row
padding, 20dp runtime corner radii, and runtime text sizes. Colors are the light
M3 blue palette from values/colors_m3.xml with the default #F2F4F7 widget body.
The samples deliberately use full opacity for launcher readability. Anna Nowak
and all sample school data are fictional. Images are rendered at 3 pixels per dp.
This is deterministic artwork, not an Android screenshot: font metrics and exact
line placement can differ from a device. No runtime widget behavior is affected.

All four provider XMLs use previewImage. The current-lesson previewLayout was
removed so Android 12+ launchers also use the populated image instead of an empty
runtime layout. Older launchers use the same image. The project's minSdk is 21,
which supports lossless WebP with alpha. Launcher scaling varies by device.

Only the WebPs and provider references affect the app. This directory is an
optional offline source for regenerating the artwork.
