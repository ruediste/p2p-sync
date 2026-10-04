#!/usr/bin/env python3
"""Helper for querying OpenAlex and storing queries/papers locally.

Usage:
  ./openalex.py search "distributed file system conflict resolution" \
      [--filter "publication_year:2015-2026,open_access.is_oa:true"] \
      [--max 25] [--pdf]

  ./openalex.py fetch W123456789 [--pdf]
  ./openalex.py fetch 10.1234/some.doi [--pdf]

Storage layout (see AGENT.md):
  queries/YYYYMMDDHHMMSS-slug.json   -> {"query": {...}, "url": ..., "response": ...}
  papers/Wxxxxxxxxx.json             -> trimmed work metadata
  papers/Wxxxxxxxxx/paper.pdf        -> full text PDF (if available, --pdf)
  papers/Wxxxxxxxxx/paper.md         -> markdown conversion of the PDF
"""

import argparse
import datetime
import json
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

BASE = Path(__file__).resolve().parent
QUERIES_DIR = BASE / "queries"
PAPERS_DIR = BASE / "papers"
API = "https://api.openalex.org"
CONTENT = "https://content.openalex.org"


def api_key():
    key_file = BASE / "openalex_api_key.txt"
    return key_file.read_text().strip()


def get(url, params=None):
    if params:
        params = {k: v for k, v in params.items() if v is not None}
        params["api_key"] = api_key()
        url = url + "?" + urllib.parse.urlencode(params, doseq=True)
    req = urllib.request.Request(url, headers={"User-Agent": "p2p-sync research helper"})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode())


def slugify(text, max_len=40):
    s = re.sub(r"[^a-zA-Z0-9]+", "-", text.lower()).strip("-")
    return s[:max_len].rstrip("-")


def save_paper(work, update=False):
    wid = work["id"].rsplit("/", 1)[-1]
    path = PAPERS_DIR / f"{wid}.json"
    if path.exists() and not update:
        return wid, path
    meta = {
        "id": wid,
        "title": work.get("title") or work.get("display_name"),
        "doi": work.get("doi"),
        "publication_date": work.get("publication_date"),
        "publication_year": work.get("publication_year"),
        "type": work.get("type"),
        "cited_by_count": work.get("cited_by_count"),
        "authors": [
            a["author"]["display_name"] for a in work.get("authorships", [])
        ],
        "venue": (work.get("primary_location") or {}).get("source", {})
        and (work.get("primary_location")["source"] or {}).get("display_name"),
        "open_access": work.get("open_access"),
        "has_content": work.get("has_content"),
        "content_urls": work.get("content_urls"),
        "abstract": abstract_to_text(work),
        "topics": [t.get("display_name") for t in work.get("topics", [])],
        "references": work.get("referenced_works"),
        "cited_by": citing_works(wid),
    }
    path.write_text(json.dumps(meta, indent=2))
    return wid, path


def citing_works(wid):
    """Return [{id, title}] of works citing this work (via the cites filter)."""
    try:
        data = get(f"{API}/works", {
            "filter": f"cites:{wid}",
            "select": "id,display_name,abstract_inverted_index",
            "per-page": 200,
        })
    except Exception as e:
        print(f"{wid}: citing works fetch failed: {e}")
        return None
    return [
        {"id": w["id"].rsplit("/", 1)[-1], "title": w.get("display_name"),
         "abstract": abstract_to_text(w)}
        for w in data.get("results", [])
    ]


def abstract_to_text(work):
    inv = work.get("abstract_inverted_index")
    if not inv:
        return None
    words = sorted((p, w) for w, ps in inv.items() for p in ps)
    return " ".join(w for _, w in words)


SKIP_TAGS = {"script", "style", "head", "svg", "math", "noscript", "form"}


class HTMLToMarkdown(HTMLParser):
    """Minimal HTML -> text/markdown converter (stdlib only)."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []
        self.skip_depth = 0
        self.in_pre = False

    def handle_starttag(self, tag, attrs):
        if tag in SKIP_TAGS:
            self.skip_depth += 1
            return
        if self.skip_depth:
            return
        if tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            self.out.append("\n\n" + "#" * int(tag[1]) + " ")
        elif tag == "p" or tag in ("div", "section"):
            self.out.append("\n\n")
        elif tag == "li":
            self.out.append("\n- ")
        elif tag == "br":
            self.out.append("\n")
        elif tag == "blockquote":
            self.out.append("\n\n> ")
        elif tag == "pre":
            self.out.append("\n\n```\n")
            self.in_pre = True
        elif tag in ("strong", "b"):
            self.out.append("**")
        elif tag in ("em", "i"):
            self.out.append("*")
        elif tag == "a":
            for k, v in attrs:
                if k == "href" and v:
                    self.out.append("[")
                    break

    def handle_endtag(self, tag):
        if tag in SKIP_TAGS:
            self.skip_depth = max(0, self.skip_depth - 1)
            return
        if self.skip_depth:
            return
        if tag == "pre":
            self.out.append("\n```\n\n")
            self.in_pre = False
        elif tag in ("strong", "b"):
            self.out.append("**")
        elif tag in ("em", "i"):
            self.out.append("*")
        elif tag == "a":
            self.out.append("]")
        elif tag in ("h1", "h2", "h3", "h4", "h5", "h6"):
            self.out.append("\n\n")
        elif tag == "p":
            self.out.append("\n\n")

    def handle_data(self, data):
        if self.skip_depth:
            return
        text = data if self.in_pre else re.sub(r"\s+", " ", data)
        self.out.append(text)

    def text(self):
        raw = "".join(self.out)
        raw = re.sub(r"[ \t]+", " ", raw)
        raw = re.sub(r"\n{3,}", "\n\n", raw)
        return raw.strip()


def fetch_arxiv_html(work):
    """Try arxiv.org HTML rendering; return markdown text or None."""
    wid = work["id"].rsplit("/", 1)[-1]
    arxiv_id = None
    doi = work.get("doi") or ""
    m = re.search(r"10\.48550/arxiv\.(\S+)", doi, re.I)
    if m:
        arxiv_id = m.group(1)
    else:
        for loc in work.get("locations") or []:
            url = (loc.get("landing_page_url") or "")
            m = re.search(r"arxiv\.org/(?:abs|html)/([a-zA-Z0-9./-]+?)(?:v\d+)?$", url)
            if m:
                arxiv_id = m.group(1)
                break
    if not arxiv_id:
        return None
    for version in ("", "v1"):
        url = f"https://arxiv.org/html/{arxiv_id}{version}"
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": "p2p-sync research helper"})
            with urllib.request.urlopen(req, timeout=60) as r:
                if r.status != 200:
                    continue
                html = r.read().decode("utf-8", "replace")
            parser = HTMLToMarkdown()
            parser.feed(html)
            text = parser.text()
            if len(text) > 3000:
                print(f"{wid}: used arxiv HTML {url}")
                return text
        except Exception as e:
            print(f"{wid}: arxiv HTML {url} failed: {e}")
    return None


def download_pdf(work):
    wid = work["id"].rsplit("/", 1)[-1]
    has = work.get("has_content") or {}
    if not has.get("pdf"):
        print(f"{wid}: no PDF available")
        return
    d = PAPERS_DIR / wid
    pdf = d / "paper.pdf"
    md = d / "paper.md"
    if md.exists():
        print(f"{wid}: already converted ({md})")
        return
    d.mkdir(parents=True, exist_ok=True)
    html = fetch_arxiv_html(work)
    if html:
        md.write_text(html)
        print(f"{wid}: wrote {md} (arxiv HTML)")
        return
    if not pdf.exists():
        d.mkdir(parents=True, exist_ok=True)
        url = f"{CONTENT}/works/{wid}.pdf?api_key={api_key()}"
        print(f"{wid}: downloading PDF ...")
        req = urllib.request.Request(url, headers={"User-Agent": "p2p-sync research helper"})
        with urllib.request.urlopen(req) as r, open(pdf, "wb") as f:
            f.write(r.read())
    print(f"{wid}: converting PDF to markdown ...")
    md_bin = BASE / ".venv" / "bin" / "python"
    r = subprocess.run(
        [str(md_bin), "-c",
         "import sys; import pymupdf4llm;"
         "print(pymupdf4llm.to_markdown(sys.argv[1], use_ocr=False))",
         str(pdf)],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(f"{wid}: pymupdf4llm failed: {r.stderr.strip()[:500]}")
        return
    md.write_text(r.stdout)
    print(f"{wid}: wrote {md}")


def cmd_search(args):
    params = {
        "search": args.query,
        "filter": args.filter,
        "per-page": min(args.max, 200),
        "sort": args.sort,
    }
    data = get(f"{API}/works", params)
    results = data.get("results", [])
    print(f"{data.get('meta', {}).get('count', '?')} matches, fetched {len(results)}")

    url = f"{API}/works?" + urllib.parse.urlencode(
        {k: v for k, v in params.items() if v is not None})
    QUERIES_DIR.mkdir(exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    qpath = QUERIES_DIR / f"{stamp}-{slugify(args.query)}.json"
    qpath.write_text(json.dumps({"query": params, "url": url, "response": data},
                                indent=2))
    print(f"query stored: {qpath}")

    PAPERS_DIR.mkdir(exist_ok=True)
    for work in results[: args.max]:
        wid, ppath = save_paper(work)
        print(f"paper stored: {ppath}")
        if args.pdf:
            download_pdf(work)


def resolve_work_id(ref):
    ref = ref.strip()
    if re.fullmatch(r"W\d+", ref):
        wid = ref
    elif ref.lower().startswith("10.") or "doi.org/" in ref:
        doi = ref if ref.lower().startswith("10.") else \
            "https://doi.org/" + ref.split("doi.org/")[-1]
        wid = None
        data = get(f"{API}/works/doi:{urllib.parse.quote(doi, safe='/:')}")
        wid = data["id"].rsplit("/", 1)[-1]
        return data, wid
    else:
        sys.exit(f"cannot interpret work reference: {ref}")
    return get(f"{API}/works/{wid}"), wid


def cmd_fetch(args):
    work, wid = resolve_work_id(args.work)
    PAPERS_DIR.mkdir(exist_ok=True)
    _, ppath = save_paper(work, update=True)
    print(f"paper stored: {ppath}")
    if args.pdf:
        download_pdf(work)


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("search", help="search works and store query + papers")
    s.add_argument("query", help="search string")
    s.add_argument("--filter", default=None, help="OpenAlex filter, e.g. publication_year:2020-2026")
    s.add_argument("--sort", default="relevance_score:desc",
                   help="sort; default relevance_score (use 'none' for relevance)")
    s.add_argument("--max", type=int, default=25, help="papers to store")
    s.add_argument("--pdf", action="store_true", help="download + convert full text")
    s.set_defaults(func=cmd_search)

    f = sub.add_parser("fetch", help="fetch a single work by OpenAlex ID or DOI")
    f.add_argument("work")
    f.add_argument("--pdf", action="store_true")
    f.set_defaults(func=cmd_fetch)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()