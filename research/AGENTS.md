This directory holds a growing knowledge base for the research topic described below.
Use OpenAlex (help.openalex.org) for research. For each query, store the query and the response as json document in the `queries` directory. For each paper retrieved, store the metadata as a json document in the `papers` directory. If full text is available (typically pdf), store it in a separate directory under the `papers` directory. Run a pdf to markdown (or text) converter to access th PDF content.

OpenAlex API Key: stored in `research/openalex_api_key.txt` (gitignored).

# OpenAlex helper script

Use `./openalex.py` to run queries and store results; it handles all storage locations automatically:

- Search and store query, response, and paper metadata (optionally download + convert full text):
  ```
  ./openalex.py search "query string" --filter "publication_year:2015-2026,open_access.is_oa:true" --sort cited_by_count:desc --max 25 --pdf
  ```
- Fetch a single work by OpenAlex ID (`W...`) or DOI:
  ```
  ./openalex.py fetch W123456789 --pdf
  ./openalex.py fetch 10.1234/some.doi --pdf
  ```

Storage layout (managed by the script, do not write these files manually):
- `queries/<timestamp>-<slug>.json` — the query parameters, request URL, and full API response
- `papers/Wxxxxxxxxx.json` — trimmed metadata per paper
- `papers/Wxxxxxxxxx/paper.pdf` — full text PDF (downloaded with `--pdf`, from `content.openalex.org`)
- `papers/Wxxxxxxxxx/paper.md` — markdown conversion of the PDF via markitdown (installed in `.venv`)

Notes:
- Use filters like `has_content.pdf:true` to limit searches to papers with downloadable PDFs.
- Read `papers/*/paper.md` for paper content; the `abstract` field in the metadata holds the plain-text abstract.
- Paper JSONs include `references` and `cited_by` (list of citing works); refresh via `./openalex.py fetch <Wid>`.
- The default search sort is `relevance_score:desc` (required when using `search`; sorting by cited_by_count bypasses relevance and returns junk).

# Research workflow

- On search, do NOT download PDFs (`--pdf`). Store metadata only, review the
  abstracts for relevance, then download full text selectively for the core
  papers with `./openalex.py fetch <Wid> --pdf`.
- Remove stored papers that turn out to be irrelevant on review.
- For each paper where full text is available (`papers/Wxxxxxxxxx/paper.md`),
  create a `papers/Wxxxxxxxxx/contributions.md` file describing the
  contributions of the paper to the research topic: what the paper teaches
  that is relevant to building the distributed file system described below,
  and how it relates to the topic's characteristics (multi-master,
  conflict resolution, eventual consistency, fault tolerance, scalability).
  Update it if the paper's relevance to the topic changes.
- Maintain `OVERVIEW.md` with the current state of the research, including a
  short overview of the findings. Do NOT list all papers in OVERVIEW.md;
  instead, the papers table at the end of OVERVIEW.md is auto-generated from
  the `papers/` directory by `./generate_overview.py` — run that script after
  adding/removing papers, and only edit the manual sections above the
  `AUTO-GENERATED` marker.

# Research Topic
How can a distributed file system be built?

The system should support multiple nodes. Each node can go offline and come back online without affecting the overall system. The nodes can operate without network connectivity for some time and synchronize changes once they reconnect.

The system should have the following characteristics:
- Multi Master: Nodes can act as independent masters, allowing updates to be made on any node without requiring a central authority.
- Conflict Resolution: The system should have a mechanism to detect and resolve conflicts that arise when multiple nodes update the same file concurrently. Graceful handling of directory rename and move conflicts is a primary concern. File content conflicts are NOT merged automatically at the content level: conflicting file contents should be preserved (e.g., keep both versions) rather than merged.
- Eventual Consistency: The system should ensure that all nodes eventually converge to the same state, even if they have been operating independently for some time.
- Fault Tolerance: The system should be resilient to node failures, ensuring that data is not lost and the system continues to operate smoothly.
- Scalability: The system should support a relatively small number of nodes (up to 10). Scalability to a large number of nodes is not a primary concern.

The goal is to find the most relevant papers and research that address the design and implementation of distributed file systems with the characteristics outlined above. The focus should be on concrete descriptions of how to build such a system.