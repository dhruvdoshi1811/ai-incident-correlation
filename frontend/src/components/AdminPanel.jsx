import { useEffect, useState } from 'react'
import { api } from '../api'
import Badge from './Badge'

const CIRCUIT_STATE_STYLES = {
  CLOSED: 'bg-green-100 text-green-800',
  OPEN: 'bg-red-100 text-red-800',
  HALF_OPEN: 'bg-amber-100 text-amber-800',
}

const OUTCOME_STYLES = {
  SUCCESS: 'bg-green-100 text-green-800',
  CIRCUIT_OPEN: 'bg-red-100 text-red-800',
  RATE_LIMITED: 'bg-amber-100 text-amber-800',
  TIMEOUT: 'bg-amber-100 text-amber-800',
  ERROR: 'bg-red-100 text-red-800',
}

export default function AdminPanel() {
  const [circuitStatus, setCircuitStatus] = useState(null)
  const [usageLog, setUsageLog] = useState([])
  const [failedNotifications, setFailedNotifications] = useState([])
  const [retrying, setRetrying] = useState(null)
  const [resetting, setResetting] = useState(false)

  function loadAll() {
    api.get('/admin/circuit-breaker-status').then(setCircuitStatus)
    api.get('/admin/llm-usage').then(setUsageLog)
    api.get('/admin/notifications?status=FAILED').then(setFailedNotifications)
  }

  useEffect(loadAll, [])

  const outcomeCounts = usageLog.reduce((acc, entry) => {
    acc[entry.outcome] = (acc[entry.outcome] || 0) + 1
    return acc
  }, {})

  async function handleRetry(id) {
    setRetrying(id)
    try {
      await api.post(`/admin/notifications/${id}/retry`)
      loadAll()
    } finally {
      setRetrying(null)
    }
  }

  async function handleResetDemoData() {
    if (!window.confirm('Reset demo data? This clears all alerts, incidents, analyses, and notifications.')) {
      return
    }
    setResetting(true)
    try {
      await api.post('/admin/reset-demo-data')
      loadAll()
    } finally {
      setResetting(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Ops Console</h2>
          <p className="text-sm text-slate-500">Trigger simulated incidents and monitor system health.</p>
        </div>
        <div className="flex items-center gap-4">
          <button
            onClick={handleResetDemoData}
            disabled={resetting}
            className="text-sm text-red-600 hover:text-red-800 disabled:opacity-50"
          >
            {resetting ? 'Resetting…' : 'Reset demo data'}
          </button>
          <button onClick={loadAll} className="text-sm text-slate-500 hover:text-slate-800">
            Refresh
          </button>
        </div>
      </div>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h3 className="mb-3 text-sm font-semibold text-slate-900">LLM circuit breaker (llmCall)</h3>
        {!circuitStatus && <p className="text-sm text-slate-400">Loading…</p>}
        {circuitStatus && (
          <div className="flex flex-wrap items-center gap-6 text-sm">
            <Badge text={circuitStatus.state} className={CIRCUIT_STATE_STYLES[circuitStatus.state] || 'bg-slate-100 text-slate-700'} />
            <span className="text-slate-500">
              Failure rate: {circuitStatus.failureRate < 0 ? 'not enough calls yet' : `${circuitStatus.failureRate}%`}
            </span>
            <span className="text-slate-500">Buffered: {circuitStatus.bufferedCalls}</span>
            <span className="text-slate-500">Failed: {circuitStatus.failedCalls}</span>
            <span className="text-slate-500">Successful: {circuitStatus.successfulCalls}</span>
            <span className="text-slate-500">Rejected (not permitted): {circuitStatus.notPermittedCalls}</span>
          </div>
        )}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h3 className="mb-3 text-sm font-semibold text-slate-900">LLM usage log ({usageLog.length})</h3>
        <div className="mb-3 flex flex-wrap gap-2">
          {Object.entries(outcomeCounts).map(([outcome, count]) => (
            <Badge key={outcome} text={`${outcome}: ${count}`} className={OUTCOME_STYLES[outcome] || 'bg-slate-100 text-slate-700'} />
          ))}
        </div>
        <div className="max-h-64 overflow-y-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr>
                <th className="py-1 pr-4 font-medium">Timestamp</th>
                <th className="py-1 pr-4 font-medium">Outcome</th>
                <th className="py-1 font-medium">Latency (ms)</th>
              </tr>
            </thead>
            <tbody>
              {usageLog.slice(0, 30).map((entry) => (
                <tr key={entry.id} className="border-t border-slate-100">
                  <td className="py-1.5 pr-4 text-slate-500">{new Date(entry.timestamp).toLocaleString()}</td>
                  <td className="py-1.5 pr-4">
                    <Badge text={entry.outcome} className={OUTCOME_STYLES[entry.outcome] || 'bg-slate-100 text-slate-700'} />
                  </td>
                  <td className="py-1.5">{entry.latencyMs}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {usageLog.length === 0 && <p className="py-2 text-sm text-slate-400">No LLM calls logged yet.</p>}
        </div>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h3 className="mb-3 text-sm font-semibold text-slate-900">Failed notifications ({failedNotifications.length})</h3>
        {failedNotifications.length === 0 && <p className="text-sm text-slate-400">Nothing stuck right now.</p>}
        <ul className="space-y-2 text-sm">
          {failedNotifications.map((n) => (
            <li key={n.id} className="flex items-center justify-between rounded-md border border-slate-100 px-3 py-2">
              <span className="text-slate-600">
                {n.channel} · {n.attempts} attempts · incident {n.incidentId.slice(0, 8)}…
              </span>
              <button
                onClick={() => handleRetry(n.id)}
                disabled={retrying === n.id}
                className="rounded-md border border-slate-300 px-3 py-1 text-xs hover:bg-slate-100 disabled:opacity-50"
              >
                {retrying === n.id ? 'Retrying…' : 'Retry'}
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
