import { useEffect, useState } from 'react'
import { api, getToken, setToken } from './api'
import Login from './components/Login'
import IncidentList from './components/IncidentList'
import IncidentDetail from './components/IncidentDetail'
import AdminPanel from './components/AdminPanel'

export default function App() {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [view, setView] = useState('incidents')
  const [selectedIncidentId, setSelectedIncidentId] = useState(null)

  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    api
      .get('/auth/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  function handleAuthenticated() {
    setLoading(true)
    api
      .get('/auth/me')
      .then(setUser)
      .finally(() => setLoading(false))
  }

  function handleLogout() {
    setToken(null)
    setUser(null)
    setView('incidents')
  }

  function openIncident(id) {
    setSelectedIncidentId(id)
    setView('detail')
  }

  if (loading) {
    return <div className="flex min-h-screen items-center justify-center text-slate-400">Loading…</div>
  }

  if (!user) {
    return <Login onAuthenticated={handleAuthenticated} />
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-6">
            <h1 className="text-lg font-semibold text-slate-900">Incident Copilot</h1>
            <nav className="flex gap-4 text-sm">
              <button
                onClick={() => setView('incidents')}
                className={view === 'incidents' || view === 'detail' ? 'font-medium text-slate-900' : 'text-slate-500 hover:text-slate-800'}
              >
                Incidents
              </button>
              {user.role === 'ADMIN' && (
                <button
                  onClick={() => setView('admin')}
                  className={view === 'admin' ? 'font-medium text-slate-900' : 'text-slate-500 hover:text-slate-800'}
                >
                  Ops Console
                </button>
              )}
            </nav>
          </div>
          <div className="flex items-center gap-4 text-sm text-slate-500">
            <span>
              {user.email} <span className="text-slate-400">({user.role})</span>
            </span>
            <button onClick={handleLogout} className="text-slate-500 hover:text-slate-800">
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-6 py-8">
        {(view === 'incidents' || view === 'detail') && (
          <p className="mb-6 text-sm text-slate-500">Investigate, resolve, and document incidents.</p>
        )}
        {view === 'incidents' && <IncidentList user={user} onOpenIncident={openIncident} />}
        {view === 'detail' && (
          <IncidentDetail incidentId={selectedIncidentId} onBack={() => setView('incidents')} />
        )}
        {view === 'admin' && user.role === 'ADMIN' && <AdminPanel />}
      </main>
    </div>
  )
}
