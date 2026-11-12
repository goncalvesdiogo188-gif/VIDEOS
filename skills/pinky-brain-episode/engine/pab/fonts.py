"""Register the bundled fonts with fontconfig before cairo renders any text.

Must be imported before the first cairo text call.
"""
import os
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_DIR = os.path.join(ROOT, "assets", "fonts")

TITLE = "Bangers"        # titles / comic lettering (OFL)
SUBS = "Sniglet"         # subtitles, rounded & very readable (OFL)
SANS = "DejaVu Sans"     # small props text (system fallback)


def register():
    if os.environ.get("PAB_FONTS_OK"):
        return
    conf_dir = tempfile.mkdtemp(prefix="pab_fc_")
    conf = os.path.join(conf_dir, "fonts.conf")
    with open(conf, "w") as f:
        f.write(f"""<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <dir>{FONT_DIR}</dir>
  <cachedir>{conf_dir}/cache</cachedir>
</fontconfig>
""")
    os.environ["FONTCONFIG_FILE"] = conf
    os.environ["PAB_FONTS_OK"] = "1"


register()
