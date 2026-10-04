export interface PaperMeta {
  id: string
  title: string
  doi: string | null
  publication_year: number | null
  cited_by_count: number | null
  authors: string[]
  abstract: string | null
  references: string[] | null
  cited_by: { id: string; title: string; abstract?: string | null }[] | null
}

export interface GraphNode {
  id: string
  title: string
  year: number | null
  authors: string[]
  abstract: string | null
  degree: number
  isCiting?: boolean
}

export interface GraphEdge {
  source: string
  target: string
}

const modules = import.meta.glob<PaperMeta>('../../papers/*.json', {
  eager: true,
})

export const papers: PaperMeta[] = Object.values(modules).sort((a, b) =>
  (a.title ?? '').localeCompare(b.title ?? ''),
)

const byId = new Map(papers.map((p) => [p.id, p]))

export const openalexId = (id: string) => id.replace(/^.*\//, '')

export const nodes: GraphNode[] = papers.map((p) => ({
  id: p.id,
  title: p.title ?? p.id,
  year: p.publication_year,
  authors: p.authors ?? [],
  abstract: p.abstract,
  degree: 0,
}))

export const edges: GraphEdge[] = []
const seen = new Set<string>()
for (const p of papers) {
  for (const ref of p.references ?? []) {
    const rid = openalexId(ref)
    if (byId.has(rid)) {
      const key = `${rid}->${p.id}`
      if (!seen.has(key)) {
        seen.add(key)
        edges.push({ source: rid, target: p.id })
      }
    }
  }
}
const nodeById = new Map(nodes.map((n) => [n.id, n]))

// Add citing papers (from each paper's cited_by array) as nodes too,
// so they show up in the graph along with their abstract.
for (const p of papers) {
  for (const c of p.cited_by ?? []) {
    const cid = openalexId(c.id)
    if (byId.has(cid)) continue
    let node = nodeById.get(cid)
    if (!node) {
      node = {
        id: cid,
        title: c.title ?? cid,
        year: null,
        authors: [],
        abstract: c.abstract ?? null,
        degree: 0,
        isCiting: true,
      }
      nodes.push(node)
      nodeById.set(cid, node)
    }
    const key = `${cid}->${p.id}`
    if (!seen.has(key)) {
      seen.add(key)
      edges.push({ source: cid, target: p.id })
    }
  }
}

for (const e of edges) {
  nodeById.get(e.source)!.degree++
  nodeById.get(e.target)!.degree++
}

export interface GraphData {
  nodes: GraphNode[]
  links: { source: string; target: string }[]
}

export const fullGraphData: GraphData = {
  nodes: nodes.map((n) => ({ ...n })),
  links: edges.map((e) => ({ source: e.source, target: e.target })),
}

export const coreGraphData: GraphData = {
  nodes: fullGraphData.nodes.filter((n) => !n.isCiting),
  links: fullGraphData.links.filter(
    (l) => !nodeById.get(l.source)?.isCiting,
  ),
}

const contribModules = import.meta.glob('../../papers/*/contributions.md', {
  eager: true,
  query: '?raw',
  import: 'default',
}) as Record<string, string>

export const contributions: Record<string, string> = Object.fromEntries(
  Object.entries(contribModules).map(([path, text]) => {
    const wid = path.match(/W\d+/)![0]
    return [wid, text]
  }),
)