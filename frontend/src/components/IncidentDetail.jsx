import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import Badge from './Badge'
import Markdown from './Markdown'
import ResolveForm from './ResolveForm'

const ALERT_STATUS_STYLES = {
  OPEN: 'bg-amber-100 text-amber-800',
  INVESTIGATING: 'bg-blue-100 text-blue-800',
  RESOLVED: 'bg-green-100 text-green-800',
}

const ANALYSIS_STATUS_STYLES = {
  PENDING: 'bg-slate-100 text-slate-600',
  RUNNING: 'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  DEGRADED: 'bg-amber-100 text-amber-800',
  FAILED: 'bg-red-100 text-red-800',
}

const NOTIFICATION_STATUS_STYLES = {
  PENDING: 'bg-slate-100 text-slate-600',
  SENT: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
}

const IN_FLIGHT_ANALYSIS_STATUSES = ['PENDING', 'RUNNING']

export default function IncidentDetail({ incidentId, onBack }) {
  const [incident, setIncident] = useState(null)
  const [alerts, setAlerts] = useState([])
  const [analyses, setAnalyses] = useState([])
  const [notifications, setNotifications] = useState([])
  const [analyzing, setAnalyzing] = useState(false)
  const [showResolveForm, setShowResolveForm] = useState(false)
  const [error, setError] = useState(null)
  const analysisPollRef = useRef(null)
  const notificationPollRef = useRef(null)

  function loadAll() {
    api.get(`/incidents/${incidentId}`).then(setIncident).catch((e) => setError(e.message))
    api.get(`/incidents/${incidentId}/alerts`).then(setAlerts)
    api.get(`/incidents/${incidentId}/analysis`).then(setAnalyses)
    api.get(`/incidents/${incidentId}/notifications`).then(setNotifications)
  }

  useEffect(() => {
    loadAll()
    return () => {
      clearInterval(analysisPollRef.current)
      clearInterval(notificationPollRef.current)
    }
  }, [incidentId])

  const latestAnalysis = analyses[0]

  useEffect(() => {
    clearInterval(analysisPollRef.current)
    if (latestAnalysis && IN_FLIGHT_ANALYSIS_STATUSES.includes(latestAnalysis.status)) {
      analysisPollRef.current = setInterval(() => {
        api.get(`/incidents/${incidentId}/analysis`).then(setAnalyses)
      }, 2000)
    }
    return () => clearInterval(analysisPollRef.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [latestAnalysis?.status])

  const hasPendingNotification = notifications.some((n) => n.status === 'PENDING')

  useEffect(() => {
    clearInterval(notificationPollRef.current)
    if (hasPendingNotification) {
      notificationPollRef.current = setInterval(() => {
        api.get(`/incidents/${incidentId}/notifications`).then(setNotifications)
      }, 2000)
    }
    return () => clearInterval(notificationPollRef.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasPendingNotification])

  async function handleAnalyze() {
    setAnalyzing(true)
    setError(null)
    try {
      await api.post(`/incidents/${incidentId}/analyze`)
      api.get(`/incidents/${incidentId}/analysis`).then(setAnalyses)
    } catch (err) {
      setError(err.message)
    } finally {
      setAnalyzing(false)
    }
  }

  function handleResolved() {
    setShowResolveForm(false)
    loadAll()
  }

  if (!incident) {
    return (
      <div>
        <button onClick={onBack} className="mb-4 text-sm text-slate-500 hover:text-slate-800">
          ← Back
        </button>
        {error ? <p className="text-sm text-red-600">{error}</p> : <p className="text-sm text-slate-400">Loading…</p>}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <button onClick={onBack} className="text-sm text-slate-500 hover:text-slate-800">
          ← Back
        </button>
        <div className="flex items-center gap-3">
          <Badge text={incident.status} className={ALERT_STATUS_STYLES[incident.status]} />
          {incident.status !== 'RESOLVED' && !showResolveForm && (
            <button
              onClick={() => setShowResolveForm(true)}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
            >
              Resolve & write postmortem
            </button>
          )}
        </div>
      </div>

      {showResolveForm && (
        <ResolveForm incidentId={incidentId} onResolved={handleResolved} onCancel={() => setShowResolveForm(false)} />
      )}

      {incident.status === 'RESOLVED' && incident.rootCauseSummary && (
        <section className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Postmortem</h2>
          <Markdown text={incident.rootCauseSummary} />
        </section>
      )}

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-900">Analysis</h2>
          <button
            onClick={handleAnalyze}
            disabled={analyzing || (latestAnalysis && IN_FLIGHT_ANALYSIS_STATUSES.includes(latestAnalysis.status))}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700 disabled:opacity-50"
          >
            {analyzing ? 'Submitting…' : 'Analyze'}
          </button>
        </div>

        {!latestAnalysis && <p className="text-sm text-slate-400">No analysis requested yet.</p>}

        {latestAnalysis && (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Badge text={latestAnalysis.status} className={ANALYSIS_STATUS_STYLES[latestAnalysis.status]} />
              {latestAnalysis.status === 'DEGRADED' && (
                <span className="text-xs text-amber-700">AI unavailable — raw alert fallback</span>
              )}
              {latestAnalysis.status === 'COMPLETED' && (
                <span className="text-xs text-green-700">AI-generated</span>
              )}
            </div>
            {latestAnalysis.resultSummary && <Markdown text={latestAnalysis.resultSummary} />}
            {(latestAnalysis.status === 'COMPLETED' || latestAnalysis.status === 'DEGRADED') && (
              <p className="text-xs text-slate-400">
                Retrieved context:{' '}
                {latestAnalysis.retrievedContext || 'no matching historical postmortem found'}
              </p>
            )}
          </div>
        )}

        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-900">Correlated alerts ({alerts.length})</h2>
        <ul className="space-y-2 text-sm">
          {alerts.map((alert) => (
            <li key={alert.id} className="rounded-md border border-slate-100 px-3 py-2">
              <span className="font-medium text-slate-800">[{alert.severity}]</span>{' '}
              <span className="text-slate-500">{alert.sourceSystem}:</span> {alert.title}
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-3 text-sm font-semibold text-slate-900">Notification delivery</h2>
        {notifications.length === 0 && <p className="text-sm text-slate-400">No notifications yet.</p>}
        <ul className="space-y-2 text-sm">
          {notifications.map((n) => (
            <li key={n.id} className="flex items-center justify-between rounded-md border border-slate-100 px-3 py-2">
              <span className="text-slate-600">
                {n.channel} · attempt {n.attempts}
              </span>
              <Badge text={n.status} className={NOTIFICATION_STATUS_STYLES[n.status]} />
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
