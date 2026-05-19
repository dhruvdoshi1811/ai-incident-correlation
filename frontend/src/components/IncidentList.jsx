import { useEffect, useState } from 'react'
import { api } from '../api'
import Badge from './Badge'

const STATUS_STYLES = {
  OPEN: 'bg-amber-100 text-amber-800',
  INVESTIGATING: 'bg-blue-100 text-blue-800',
  RESOLVED: 'bg-green-100 text-green-800',
}

export default function IncidentList({ user, onOpenIncident }) {
  const [incidents, setIncidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [postmortemTitle, setPostmortemTitle] = useState('')
  const [postmortemContent, setPostmortemContent] = useState('')
  const [postmortemStatus, setPostmortemStatus] = useState(null)

  const [scenarios, setScenarios] = useState([])
  const [scenarioId, setScenarioId] = useState('')
  const [stormCount, setStormCount] = useState(10)
  const [stormResult, setStormResult] = useState(null)
  const [stormRunning, setStormRunning] = useState(false)

  function loadIncidents() {
    setLoading(true)
    api
      .get('/incidents')
      .then(setIncidents)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(loadIncidents, [])

  useEffect(() => {
    if (user.role !== 'ADMIN') return
    api.get('/demo/scenarios').then((list) => {
      setScenarios(list)
      if (list.length > 0) setScenarioId(list[0].id)
    })
  }, [user.role])

  async function handleSeedPostmortem(e) {
    e.preventDefault()
    setPostmortemStatus(null)
    try {
      await api.post('/postmortems', { title: postmortemTitle, content: postmortemContent })
      setPostmortemStatus('Postmortem saved.')
      setPostmortemTitle('')
      setPostmortemContent('')
    } catch (err) {
      setPostmortemStatus(err.message)
    }
  }

  async function handleTriggerStorm() {
    setStormRunning(true)
    setStormResult(null)
    try {
      const result = await api.post(`/demo/simulate-alert-storm?scenario=${scenarioId}&count=${stormCount}`)
      setStormResult(result)
      loadIncidents()
    } catch (err) {
      setStormResult({ error: err.message })
    } finally {
      setStormRunning(false)
    }
  }

  return (
    <div className="space-y-8">
      {user.role === 'ADMIN' && (
        <div className="grid gap-6 md:grid-cols-2">
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Seed postmortem</h2>
            <form onSubmit={handleSeedPostmortem} className="space-y-3">
              <input
                placeholder="Title"
                required
                value={postmortemTitle}
                onChange={(e) => setPostmortemTitle(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
              />
              <textarea
                placeholder="Content"
                required
                rows={3}
                value={postmortemContent}
                onChange={(e) => setPostmortemContent(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
              />
              <button
                type="submit"
                className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700"
              >
                Save
              </button>
              {postmortemStatus && <p className="text-sm text-slate-500">{postmortemStatus}</p>}
            </form>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="mb-3 text-sm font-semibold text-slate-900">Simulate an incident</h2>
            <p className="mb-3 text-sm text-slate-500">
              Pick a scenario to fire a realistic mix of alerts and demonstrate correlation collapsing them into one incident.
            </p>
            <div className="space-y-3">
              <select
                value={scenarioId}
                onChange={(e) => setScenarioId(e.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              >
                {scenarios.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.displayName}
                  </option>
                ))}
              </select>
              {scenarioId && (
                <p className="text-xs text-slate-400">
                  {scenarios.find((s) => s.id === scenarioId)?.description}
                </p>
              )}
              <div className="flex items-center gap-3">
                <input
                  type="number"
                  min={2}
                  max={50}
                  value={stormCount}
                  onChange={(e) => setStormCount(Number(e.target.value))}
                  className="w-20 rounded-md border border-slate-300 px-3 py-2 text-sm"
                />
                <button
                  onClick={handleTriggerStorm}
                  disabled={stormRunning || !scenarioId}
                  className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700 disabled:opacity-50"
                >
                  {stormRunning ? 'Running…' : 'Trigger scenario'}
                </button>
              </div>
            </div>
            {stormResult && (
              <p className="mt-3 text-sm text-slate-600">
                {stormResult.error
                  ? stormResult.error
                  : `${stormResult.alertsFired} alerts fired → ${stormResult.resultingIncidentIds.length} incident(s) (${stormResult.allCorrelated ? 'all correlated' : 'not fully correlated'})`}
              </p>
            )}
          </div>
        </div>
      )}

      <div className="rounded-lg border border-slate-200 bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h2 className="text-sm font-semibold text-slate-900">Incidents</h2>
          <button onClick={loadIncidents} className="text-sm text-slate-500 hover:text-slate-800">
            Refresh
          </button>
        </div>

        {loading && <p className="px-5 py-4 text-sm text-slate-400">Loading…</p>}
        {error && <p className="px-5 py-4 text-sm text-red-600">{error}</p>}
        {!loading && incidents.length === 0 && (
          <p className="px-5 py-4 text-sm text-slate-400">No incidents yet.</p>
        )}

        {!loading && incidents.length > 0 && (
          <table className="w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr>
                <th className="px-5 py-2 font-medium">Status</th>
                <th className="px-5 py-2 font-medium">Correlated alerts</th>
                <th className="px-5 py-2 font-medium">Created</th>
                <th className="px-5 py-2 font-medium">Resolved</th>
              </tr>
            </thead>
            <tbody>
              {incidents.map((incident) => (
                <tr
                  key={incident.id}
                  onClick={() => onOpenIncident(incident.id)}
                  className="cursor-pointer border-t border-slate-100 hover:bg-slate-50"
                >
                  <td className="px-5 py-3">
                    <Badge text={incident.status} className={STATUS_STYLES[incident.status] || 'bg-slate-100 text-slate-700'} />
                  </td>
                  <td className="px-5 py-3">{incident.correlatedAlertCount}</td>
                  <td className="px-5 py-3 text-slate-500">{new Date(incident.createdAt).toLocaleString()}</td>
                  <td className="px-5 py-3 text-slate-500">
                    {incident.resolvedAt ? new Date(incident.resolvedAt).toLocaleString() : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
