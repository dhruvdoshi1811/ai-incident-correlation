import { useState } from 'react'
import { api } from '../api'

const CATEGORIES = ['INFRASTRUCTURE', 'CONFIGURATION', 'CODE_BUG', 'THIRD_PARTY', 'HUMAN_ERROR', 'UNKNOWN']
const IMPACTS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export default function ResolveForm({ incidentId, onResolved, onCancel }) {
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState(CATEGORIES[0])
  const [impact, setImpact] = useState(IMPACTS[1])
  const [whatHappened, setWhatHappened] = useState('')
  const [rootCauseDetail, setRootCauseDetail] = useState('')
  const [resolutionSteps, setResolutionSteps] = useState('')
  const [preventionActions, setPreventionActions] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await api.post(`/incidents/${incidentId}/resolve`, {
        title,
        category,
        impact,
        whatHappened,
        rootCauseDetail,
        resolutionSteps,
        preventionActions,
      })
      onResolved()
    } catch (err) {
      setError(err.message)
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5">
      <h3 className="text-sm font-semibold text-slate-900">Resolve &amp; write postmortem</h3>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Title</label>
        <input
          required
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>

      <div>
        <span className="mb-1 block text-sm font-medium text-slate-700">Root cause category</span>
        <div className="flex flex-wrap gap-3 text-sm">
          {CATEGORIES.map((c) => (
            <label key={c} className="flex items-center gap-1.5">
              <input type="radio" name="category" checked={category === c} onChange={() => setCategory(c)} />
              {c.replace('_', ' ')}
            </label>
          ))}
        </div>
      </div>

      <div>
        <span className="mb-1 block text-sm font-medium text-slate-700">Impact</span>
        <div className="flex flex-wrap gap-3 text-sm">
          {IMPACTS.map((i) => (
            <label key={i} className="flex items-center gap-1.5">
              <input type="radio" name="impact" checked={impact === i} onChange={() => setImpact(i)} />
              {i}
            </label>
          ))}
        </div>
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">What happened</label>
        <textarea
          required
          rows={2}
          value={whatHappened}
          onChange={(e) => setWhatHappened(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Root cause detail</label>
        <textarea
          required
          rows={2}
          value={rootCauseDetail}
          onChange={(e) => setRootCauseDetail(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Resolution steps taken</label>
        <textarea
          required
          rows={2}
          value={resolutionSteps}
          onChange={(e) => setResolutionSteps(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Prevention / follow-up actions</label>
        <textarea
          required
          rows={2}
          value={preventionActions}
          onChange={(e) => setPreventionActions(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {submitting ? 'Saving…' : 'Resolve & save postmortem'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
