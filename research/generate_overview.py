#!/usr/bin/env python3
"""Regenerate the auto-generated sections of OVERVIEW.md from the papers/ directory.

Manual content above the marker is preserved; everything after the marker is
regenerated from papers/*.json.
"""

import datetime
import json
from pathlib import Path

BASE = Path(__file__).resolve().parent
OVERVIEW = BASE / "OVERVIEW.md"
PAPERS = BASE / "papers"

MARKER = "<!-- AUTO-GENERATED: papers table (regenerate with ./generate_overview.py) -->"


def fmt_authors(authors, n=3):
    if not authors:
        return ""
    s = ", ".join(authors[:n])
    return s + " et al." if len(authors) > n else s


def main():
    rows = []
    sections = []
    for f in sorted(PAPERS.glob("W*.json")):
        d = json.loads(f.read_text())
        wid = d["id"]
        has_fulltext = "yes" if (PAPERS / wid / "paper.md").exists() else ""
        title = (d.get("title") or "").replace("|", "/")
        authors = fmt_authors(d.get("authors") or [])
        year = d.get("publication_year") or ""
        cites = d.get("cited_by_count") or ""
        venue = (d.get("venue") or "").replace("|", "/")
        rows.append(
            f"| [{title}](papers/{wid}.json) | {authors} | {year} | {venue} | "
            f"{cites} | {has_fulltext} |"
        )
        abstract = (d.get("abstract") or "").strip()
        abstract = " ".join(abstract.split())
        if abstract:
            sections.append((title, wid, abstract))

    table = [
        MARKER,
        "",
        f"_Auto-generated from papers/ on {datetime.date.today().isoformat()}. "
        f"{len(rows)} papers stored._",
        "",
        "| Paper | Authors | Year | Venue | Cited by | Fulltext |",
        "|---|---|---|---|---|---|",
        *rows,
    ]

    if sections:
        table += ["", "## Abstracts", ""]
        for title, wid, abstract in sections:
            table += [f"### [{title}](papers/{wid}.json)", "", abstract, ""]

    table.append("")

    if OVERVIEW.exists():
        existing = OVERVIEW.read_text()
        manual = existing.split(MARKER)[0]
    else:
        manual = ""
    OVERVIEW.write_text(manual.rstrip() + "\n\n" + "\n".join(table))


if __name__ == "__main__":
    main()
