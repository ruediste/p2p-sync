import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { coreGraphData, fullGraphData, contributions, type GraphNode } from './data'
import './App.css'

const PANEL_KEY = 'panelWidth'

const loadPanelWidth = () => {
  const v = Number(localStorage.getItem(PANEL_KEY))
  return Number.isFinite(v) && v >= 200 && v <= 800 ? v : 360
}

function Graph() {
  const fgRef = useRef<any>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const draggingRef = useRef(false)
  const [selected, setSelected] = useState<GraphNode | null>(null)
  const [showCiting, setShowCiting] = useState(false)
  const [panelWidth, setPanelWidth] = useState(loadPanelWidth)
  const [graphWidth, setGraphWidth] = useState(900)
  const [graphHeight, setGraphHeight] = useState(window.innerHeight)
  const panelWidthRef = useRef(panelWidth)
  panelWidthRef.current = panelWidth

  const updateSizes = useCallback(() => {
    const total = containerRef.current?.clientWidth ?? 1200
    setGraphWidth(Math.max(300, total))
    setGraphHeight(window.innerHeight)
  }, [])

  useEffect(() => {
    updateSizes()
    const ro = new ResizeObserver(updateSizes)
    if (containerRef.current) ro.observe(containerRef.current)
    window.addEventListener('resize', updateSizes)
    return () => {
      ro.disconnect()
      window.removeEventListener('resize', updateSizes)
    }
  }, [updateSizes])

  const startDrag = (e: React.PointerEvent) => {
    e.preventDefault()
    draggingRef.current = true
    const move = (ev: PointerEvent) => {
      if (!draggingRef.current || !containerRef.current) return
      const rect = containerRef.current.getBoundingClientRect()
      const w = Math.min(Math.max(rect.right - ev.clientX, 200), rect.width - 300)
      setPanelWidth(w)
    }
    const up = () => {
      draggingRef.current = false
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
      document.body.classList.remove('dragging')
      localStorage.setItem(PANEL_KEY, String(panelWidthRef.current))
    }
    document.body.classList.add('dragging')
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
  }

  const detail = selected

  const paintLabel = (node: any, ctx: CanvasRenderingContext2D, r: number) => {
    ctx.font = '5px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const label =
      node.title.length > 48 ? node.title.slice(0, 47) + '…' : node.title
    ctx.fillStyle = '#333'
    ctx.fillText(label, node.x, node.y - r - 6)
  }

  const paintNode = (node: any, ctx: CanvasRenderingContext2D) => {
    const r = node.isCiting ? 3.5 : 5
    // draw the default node circle, then the title on top
    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, 2 * Math.PI, false)
    ctx.fillStyle = node.isCiting ? '#9aa3b2' : '#646cff'
    ctx.fill()
    paintLabel(node, ctx, r)
  }

  const graphData = useMemo(() => (showCiting ? fullGraphData : coreGraphData), [
    showCiting,
  ])

  return (
    <div
      className="main"
      ref={containerRef}
      style={{ '--panel-width': `${panelWidth}px` } as React.CSSProperties}
    >
      <div className="graph-wrap">
        <label className="citing-toggle">
          <input
            type="checkbox"
            checked={showCiting}
            onChange={(e) => setShowCiting(e.target.checked)}
          />
          Show citing papers
        </label>
        <ForceGraph2D
          ref={fgRef}
          graphData={graphData}
          width={graphWidth}
          height={graphHeight}
        nodeRelSize={5}
        nodeLabel="title"
        nodeCanvasObject={paintNode}
        linkColor={() => '#000000'}
        linkDirectionalArrowLength={4}
        linkDirectionalArrowRelPos={1}
        onNodeClick={(n: GraphNode) =>
          setSelected((s) => (s?.id === n.id ? null : n))
        }
        cooldownTicks={200}
      />
      </div>
      <div className="divider" onPointerDown={startDrag} />
      <div className="detail" style={{ width: panelWidth }}>
        {detail ? (
          <>
            <h2>{detail.title}</h2>
            <p className="meta">
              {detail.authors.join(', ')}
              {detail.year ? ` (${detail.year})` : ''}
            </p>
            {contributions[detail.id] && (
              <div className="contributions">
                <h3>Contributions</h3>
                <div className="markdown">
                  <Markdown remarkPlugins={[remarkGfm]}>
                    {contributions[detail.id]}
                  </Markdown>
                </div>
              </div>
            )}
            <p className="abstract">
              {detail.abstract ?? 'No abstract available.'}
            </p>
          </>
        ) : (
          <p className="meta">
            Click a node to see its abstract here.
          </p>
        )}
      </div>
    </div>
  )
}

export default function App() {
  return (
    <div className="app">
      <h1>Paper Citation Graph</h1>
      <Graph />
      <p className="hint">
        Hover a node to see its title and connections. Click for details.
      </p>
    </div>
  )
}