#!/usr/bin/env python3
"""
Generate a printable HTML catalog of all 317 courses WITH their generated
codes (AICIT-C001..C317), duration and inferred category — matching exactly
what the V12 migration imports. Output: scripts/AICIT-Course-Catalog.html
(convert to PDF with: cupsfilter AICIT-Course-Catalog.html > AICIT-Course-Catalog.pdf)
"""
import re, pathlib, datetime

ROOT = pathlib.Path(__file__).resolve().parent.parent
TSV  = ROOT / "scripts" / "course_list.tsv"
OUT  = ROOT / "scripts" / "AICIT-Course-Catalog.html"

# Reuse the SAME category inference as the migration generator so the PDF
# matches the database exactly.
import importlib.util
spec = importlib.util.spec_from_file_location("gcm", ROOT / "scripts" / "gen_course_migration.py")
gcm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gcm)

def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))

rows = []
for line in TSV.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    sr, name, dur = [x.strip() for x in line.split("\t")]
    sr = int(sr)
    code = f"AICIT-C{sr:03d}"
    cat = gcm.infer_category(name)
    rows.append((sr, code, name, dur, cat))

assert 300 <= len(rows) <= 317, f"unexpected row count: {len(rows)}"

tr = "\n".join(
    f"<tr><td class='sr'>{sr}</td><td class='code'>{code}</td>"
    f"<td class='name'>{esc(name)}</td><td class='dur'>{esc(dur)}</td>"
    f"<td class='cat'>{esc(cat)}</td></tr>"
    for sr, code, name, dur, cat in rows
)

today = datetime.date.today().strftime("%d %B %Y")

html = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>AICIT Course Catalog</title>
<style>
  @page {{ size: A4 portrait; margin: 14mm 12mm; }}
  * {{ box-sizing: border-box; }}
  body {{ font-family: 'Helvetica Neue', Arial, sans-serif; color:#0b1b40; margin:0; }}
  .head {{ text-align:center; border-bottom:3px solid #c9a24a; padding-bottom:10px; margin-bottom:14px; }}
  .head h1 {{ font-size:20px; margin:0 0 2px; letter-spacing:.5px; }}
  .head .sub {{ font-size:11px; color:#555; }}
  .head .meta {{ font-size:10px; color:#777; margin-top:4px; }}
  table {{ width:100%; border-collapse:collapse; font-size:9.5px; }}
  thead th {{ background:#0b1b40; color:#fff; text-align:left; padding:5px 6px; font-size:9px;
             text-transform:uppercase; letter-spacing:.4px; }}
  tbody td {{ padding:3.5px 6px; border-bottom:1px solid #e7e7e7; vertical-align:top; }}
  tbody tr:nth-child(even) {{ background:#f7f9fc; }}
  .sr   {{ width:34px; color:#999; text-align:right; }}
  .code {{ width:92px; font-family:'Courier New',monospace; font-weight:bold; color:#8a6d1f; white-space:nowrap; }}
  .name {{ font-weight:600; }}
  .dur  {{ width:78px; white-space:nowrap; color:#1e3a8a; font-weight:600; }}
  .cat  {{ width:120px; color:#555; }}
  thead {{ display:table-header-group; }}
  tr {{ page-break-inside:avoid; }}
</style></head>
<body>
  <div class="head">
    <h1>AICIT — Official Course Catalog</h1>
    <div class="sub">All India Council for Information Technology</div>
    <div class="meta">{len(rows)} courses · Generated {today} · Codes AICIT-C001–AICIT-C{len(rows):03d}</div>
  </div>
  <table>
    <thead><tr><th class="sr">#</th><th>Course Code</th><th>Course Name</th><th>Duration</th><th>Category</th></tr></thead>
    <tbody>
{tr}
    </tbody>
  </table>
</body></html>"""

OUT.write_text(html, encoding="utf-8")
print(f"Wrote {OUT} ({len(rows)} courses)")
