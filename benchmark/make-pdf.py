"""Builds a tagged PDF from a Markdown report with headless Chrome: python3 benchmark/make-pdf.py docs/benchmark.md

Real headings, table header cells, a PDF outline and the document's language, so a screen reader can move
through it the same way as the Markdown.
"""
import pathlib
import re
import subprocess
import sys
import tempfile

import markdown

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
@page { size: A4; margin: 18mm 17mm 20mm;
  @bottom-center { content: "Page " counter(page) " of " counter(pages); font: 9pt "Helvetica Neue", Arial, sans-serif; color: #444; } }
html { font: 11pt/1.55 "Helvetica Neue", Arial, sans-serif; color: #111; background: #fff; }
body { margin: 0; }
h1 { font-size: 23pt; line-height: 1.2; margin: 0 0 6pt; }
h2 { font-size: 16pt; margin: 22pt 0 6pt; padding-top: 8pt; border-top: 1.5pt solid #111; break-after: avoid; }
h3 { font-size: 12.5pt; margin: 16pt 0 4pt; break-after: avoid; }
p, ul, ol { margin: 0 0 8pt; }
li { margin: 0 0 3pt; }
strong { font-weight: 700; }
a { color: #0b4f9c; }
code { font: 9.5pt Menlo, monospace; background: #f1f1f1; padding: 0 2pt; border-radius: 2pt; }
pre { background: #f1f1f1; padding: 8pt; border-radius: 3pt; overflow-wrap: anywhere; white-space: pre-wrap; }
pre code { padding: 0; background: none; }
table { border-collapse: collapse; width: 100%; margin: 6pt 0 12pt; font-size: 10pt; }
thead { display: table-header-group; }
tr { break-inside: avoid; }
th, td { border: 0.75pt solid #8a8a8a; padding: 4pt 6pt; text-align: left; vertical-align: top; }
th { background: #ececec; font-weight: 700; }
blockquote { margin: 0 0 8pt; padding: 6pt 10pt; border-left: 3pt solid #111; background: #f6f6f6; }
"""


def build(source: pathlib.Path) -> pathlib.Path:
    text = source.read_text(encoding="utf-8")
    title = re.search(r"^# (.+)$", text, re.M).group(1)
    body = markdown.markdown(text, extensions=["tables", "toc", "sane_lists", "fenced_code"])
    # Column headers say so, for screen readers moving through a table cell by cell.
    body = re.sub(r"<th(?=[ >])", '<th scope="col"', body)
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>{title}</title>
<meta name="author" content="Sweepie Softie"><style>{CSS}</style></head>
<body>{body}</body></html>"""
    pdf = source.with_suffix(".pdf")
    with tempfile.TemporaryDirectory() as tmp:
        html = pathlib.Path(tmp, source.stem + ".html")
        html.write_text(page, encoding="utf-8")
        subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer", "--generate-pdf-document-outline",
                        f"--print-to-pdf={pdf}", html.as_uri()], check=True, capture_output=True)
    return pdf


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        print(build(pathlib.Path(arg)))
