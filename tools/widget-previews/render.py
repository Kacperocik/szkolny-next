"""Generate static launcher previews with Pillow, independently of Android layouts."""

import argparse
import os
from functools import lru_cache
from io import BytesIO
from pathlib import Path
from zipfile import BadZipFile, ZipFile

try:
    from PIL import Image, ImageDraw, ImageFont, features
except ImportError:
    raise SystemExit(
        "Pillow is required. Install tools/widget-previews/requirements.txt "
        "with python -m pip install -r."
    )

ROOT = Path(__file__).resolve().parents[2]
S = 3
ICON_VERSION = "5.8.55.0-kotlin"
ICON_FONT_NAME = "community_material_font_v5_8_55.ttf"
FONT_PAIRS = (
    ("Roboto-Regular.ttf", "Roboto-Bold.ttf"),
    ("arial.ttf", "arialbd.ttf"),
    ("Arial.ttf", "Arial Bold.ttf"),
    ("DejaVuSans.ttf", "DejaVuSans-Bold.ttf"),
    ("LiberationSans-Regular.ttf", "LiberationSans-Bold.ttf"),
)
PRIMARY, SECONDARY, TEXT, VARIANT = "#10619F", "#5A5F66", "#1E2022", "#43474D"


@lru_cache(maxsize=64)
def load_font(source, size):
    return ImageFont.truetype(
        BytesIO(source) if isinstance(source, bytes) else str(source),
        size,
    )


def find_text_fonts(font_dir=None):
    """Use a complete regular/bold pair, never an implicit bitmap fallback."""
    if font_dir is not None:
        directories = [font_dir.expanduser()]
    else:
        windows = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
        directories = [
            Path.home() / ".local/share/fonts",
            Path.home() / ".fonts",
            Path.home() / "Library/Fonts",
            Path("/Library/Fonts"),
            Path("/System/Library/Fonts/Supplemental"),
            Path("/usr/share/fonts/truetype/dejavu"),
            Path("/usr/share/fonts/truetype/liberation2"),
            Path("/usr/share/fonts/truetype/liberation"),
            windows,
        ]
    for directory in directories:
        for regular_name, bold_name in FONT_PAIRS:
            regular, bold = directory / regular_name, directory / bold_name
            if regular.is_file() and bold.is_file():
                load_font(regular, 16)
                load_font(bold, 16)
                return regular, bold
    if font_dir is None:
        # Pillow also searches platform font directories for font filenames.
        for regular, bold in FONT_PAIRS:
            try:
                load_font(regular, 16)
                load_font(bold, 16)
                return regular, bold
            except OSError:
                continue
    raise ValueError(
        "No supported regular/bold font pair found. Use --font-dir with "
        "Roboto, Arial, DejaVu Sans or Liberation Sans TTF files."
    )


def find_icon_font(explicit_path=None):
    if explicit_path is not None:
        path = explicit_path.expanduser()
        if not path.is_file():
            raise ValueError(f"Icon font does not exist: {path}")
        load_font(path, 20)
        return path

    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))).expanduser()
    caches = gradle_home / "caches"
    artifacts = (
        caches / "modules-2/files-2.1/com.mikepenz"
        / "community-material-typeface" / ICON_VERSION
    )
    # Read the original dependency directly; this also works before Gradle
    # creates transformed resources and across Gradle cache layout changes.
    for archive in sorted(artifacts.glob("*/*.aar")):
        try:
            with ZipFile(archive) as dependency:
                data = dependency.read("res/font/" + ICON_FONT_NAME)
            load_font(data, 20)
            return data
        except (BadZipFile, KeyError, OSError):
            continue
    for pattern in (
        "*/transforms/*/transformed/**/" + ICON_FONT_NAME,
        "transforms-*/*/transformed/**/" + ICON_FONT_NAME,
    ):
        for path in sorted(caches.glob(pattern)):
            if path.is_file():
                load_font(path, 20)
                return path
    raise ValueError(
        f"Community Material {ICON_VERSION} font was not found in {gradle_home}. "
        "Build the app to populate the Gradle cache, set GRADLE_USER_HOME, "
        "or pass --icon-font with " + ICON_FONT_NAME + "."
    )


def render_previews(output_dir, regular, bold, iconfont):
    output_dir.mkdir(parents=True, exist_ok=True)
    im, d = None, None

    def rect(box): return tuple(round(v*S) for v in box)
    def text(x, y, value, size, color=TEXT, heavy=False, anchor='lt'):
        d.text((x*S, y*S), value, font=load_font(bold if heavy else regular, round(size*S)), fill=color, anchor=anchor)
    def line(points, color, width=2):
        d.line([(x*S,y*S) for x,y in points], fill=color, width=round(width*S), joint='curve')
    def icon(cx, cy, kind, color):
        glyph = {'refresh':'\uecc9', 'download':'\uf436', 'happy':'\uf4a1'}[kind]
        font = load_font(iconfont, (36 if kind == 'happy' else 20)*S)
        d.text((cx*S,cy*S), glyph, font=font, fill=color, anchor='mm')

    def card(w,h,header=False):
        nonlocal im,d
        im=Image.new('RGBA',(w*S,h*S))
        d=ImageDraw.Draw(im)
        d.rounded_rectangle(rect((0,0,w-1/S,h-1/S)),radius=20*S,fill='#F2F4F7')
        if header:
            mask=Image.new('L',im.size); md=ImageDraw.Draw(mask)
            md.rounded_rectangle(rect((0,0,w-1/S,h-1/S)),radius=20*S,fill=255)
            md.rectangle(rect((0,52,w,h)),fill=0)
            im.paste(PRIMARY,(0,0,*im.size),mask); d=ImageDraw.Draw(im)

    def save(name):
        # Static lossless WebP with alpha is supported by the project's minSdk 21.
        path=output_dir / f'widget_{name}_launcher_preview.webp'
        im.save(path,format='WEBP',lossless=True,method=6,exact=True)
        with Image.open(path) as check:
            assert check.format == 'WEBP' and check.getpixel((0,0))[3] == 0
            check.load()
        print(f'{path}: {im.width}x{im.height}, {path.stat().st_size} bytes')

    card(320,220,True)
    text(16,11,'Anna Nowak',16,'white',True)
    text(16,32,'Poniedziałek, 26 października',11,'#D0E4FF')
    icon(286,26,'refresh','white')
    for i,(time,subject,room) in enumerate([
        ('8:00 - 8:45','Matematyka','sala 12'),
        ('8:55 - 9:40','Język polski','sala 24'),
        ('9:50 - 10:35','Historia','sala 8')]):
        y=62+i*50
        text(12,y+10,time,12,SECONDARY,True)
        text(109,y,subject,14,TEXT,True)
        text(109,y+20,room,12,VARIANT)
    save('timetable')

    card(320,240,True)
    text(16,18,'Powiadomienia',16,'white',True)
    icon(286,26,'download','white')
    for i,(title,body,body2,time) in enumerate([
        ('Anna Nowak - Nowa ocena','Nowa ocena (5) z przedmiotu','język polski','10:15'),
        ('Anna Nowak - Sprawdzian','Matematyka: równania','liniowe','9:30'),
        ('Anna Nowak - Wiadomość','Informacja o wycieczce','','8:10')]):
        y=62+i*59
        text(12,y,title,12,SECONDARY,True)
        text(12,y+17,body,14,TEXT,True)
        text(12,y+34,body2,14,TEXT,True)
        text(308,y+20,time,11,VARIANT,anchor='rt')
    save('notifications')

    card(180,180)
    icon(90,30,'happy',TEXT)
    text(90,53,'7',36,TEXT,True,anchor='mt')
    text(90,100,'7 to dzisiejszy',16,TEXT,True,anchor='mt')
    text(90,121,'szczęśliwy numerek.',16,TEXT,True,anchor='mt')
    text(90,151,'Anna Nowak',12,SECONDARY,anchor='mt')
    save('lucky_number')

    card(270,204)
    text(12,22,'Anna Nowak',12,SECONDARY)
    icon(240,28,'refresh',TEXT)
    text(12,51,'Matematyka',20,TEXT,True)
    text(12,80,'sala 12',14,VARIANT)
    text(258,80,'8:00 - 8:45',14,VARIANT,anchor='rt')
    d.rounded_rectangle(rect((12,105,258,109)),radius=2*S,fill='#D0D9E5')
    d.rounded_rectangle(rect((12,105,98,109)),radius=2*S,fill=PRIMARY)
    text(258,119,'Zostało 29:15',14,TEXT,True,anchor='rt')
    text(12,148,'Wydarzenia na lekcji:',12,SECONDARY)
    text(12,169,'Kartkówka z ułamków',13)
    save('current_lesson')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font-dir", type=Path, help="Directory with a supported regular/bold TTF pair.")
    parser.add_argument("--icon-font", type=Path, help="Community Material 5.8.55 TTF; overrides Gradle discovery.")
    parser.add_argument(
        "--output-dir", type=Path,
        default=ROOT / "app/src/main/res/drawable-nodpi",
        help="Output directory; defaults to the project's Android drawable resources.",
    )
    args = parser.parse_args()
    try:
        if not features.check("webp"):
            raise ValueError("This Pillow installation does not support WebP.")
        regular, bold = find_text_fonts(args.font_dir)
        iconfont = find_icon_font(args.icon_font)
        render_previews(args.output_dir.expanduser(), regular, bold, iconfont)
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
