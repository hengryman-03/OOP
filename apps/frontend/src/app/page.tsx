'use client';

import { useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/study-buddy-api';
import {
  Account,
  AccountUsage,
  BuddyRequest,
  Course,
  MatchingConfig,
  MembershipRequest,
  StudentProfile,
  StudyGroup,
} from '@/lib/study-buddy-types';
import { Avatar, Badge, Empty, Field } from '@/components/ui';
import { ProfileEditor } from '@/features/profile/profile-editor';
import { MatchExplorer } from '@/features/matching/match-explorer';
import { ConnectionsPanel } from '@/features/connections/connections-panel';
import { GroupsPanel } from '@/features/groups/groups-panel';
import { AdminPanel } from '@/features/admin/admin-panel';

type Tab =
  | 'Find buddies'
  | 'Connections'
  | 'Study groups'
  | 'My profile'
  | 'Administration';
const symbols: Record<Tab, string> = {
  'Find buddies': '⌕',
  Connections: '↔',
  'Study groups': '◉',
  'My profile': '▤',
  Administration: '⚙',
};

export default function Home() {
  const [account, setAccount] = useState<Account | null>(null),
    [choices, setChoices] = useState<Account[]>([]),
    [ready, setReady] = useState(false),
    [switching, setSwitching] = useState(false),
    [error, setError] = useState(''),
    [selection, setSelection] = useState('S001');
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api<Account[]>('/session/accounts'),
      api<Account>('/session').catch((e) => {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }),
    ])
      .then(([options, current]) => {
        if (!cancelled) {
          setChoices(options);
          setAccount(current);
        }
      })
      .catch((e) => {
        if (!cancelled) setError(e.message);
      })
      .finally(() => {
        if (!cancelled) setReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  async function switchAccount(id: string) {
    setSwitching(true);
    setAccount(null);
    setError('');
    try {
      const current = await api<Account>('/session', 'POST', { accountId: id });
      setAccount(current);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not select account.');
    } finally {
      setSwitching(false);
    }
  }
  async function refreshChoices() {
    try {
      setChoices(await api<Account[]>('/session/accounts'));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not refresh accounts.');
    }
  }
  if (!ready || switching)
    return (
      <main className="welcome">
        <div className="brand">
          <span className="brand-mark">sb.</span> studybuddy
        </div>
        <p role="status">
          {switching
            ? 'Switching your study space…'
            : 'Opening your study space…'}
        </p>
      </main>
    );
  if (!account)
    return (
      <main className="welcome">
        <div className="welcome-panel">
          <div className="brand">
            <span className="brand-mark">sb.</span> studybuddy
          </div>
          <span className="eyebrow">A little company. A lot of progress.</span>
          <h1>
            Your next study session
            <br />
            starts with a connection.
          </h1>
          <p>
            Find compatible study buddies, share your goals, and make room for
            learning together.
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void switchAccount(selection);
            }}
          >
            <Field label="Choose a demo account">
              <select
                value={selection}
                onChange={(e) => setSelection(e.target.value)}
              >
                {choices.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} · {a.id}
                    {a.role === 'SYSTEM_ADMINISTRATOR' ? ' · Admin' : ''}
                  </option>
                ))}
              </select>
            </Field>
            <button className="button wide" disabled={!choices.length}>
              Enter study space →
            </button>
          </form>
          <p className="demo-note">
            <strong>Coursework demo</strong> · This account switcher does not
            use passwords. Seeded profiles are fictional.
          </p>
          {error && (
            <div role="alert" className="notice error">
              {error}
              <button
                className="button ghost"
                onClick={() => window.location.reload()}
              >
                Retry connection
              </button>
            </div>
          )}
        </div>
      </main>
    );
  return (
    <Workspace
      key={account.id}
      account={account}
      choices={choices}
      switchAccount={switchAccount}
      refreshChoices={refreshChoices}
      logout={async () => {
        try {
          await api('/session', 'DELETE');
          setAccount(null);
        } catch (e) {
          setError(e instanceof Error ? e.message : 'Could not sign out.');
        }
      }}
      globalError={error}
    />
  );
}

interface WorkspaceData {
  students: StudentProfile[];
  courses: Course[];
  groups: StudyGroup[];
  requests: BuddyRequest[];
  memberships: MembershipRequest[];
  profile: StudentProfile | null;
  accounts: AccountUsage[];
  config: MatchingConfig | null;
}
/** Owns navigation and data loading; each domain screen owns only its form/workflow state. */
function Workspace({
  account,
  choices,
  switchAccount,
  refreshChoices,
  logout,
  globalError,
}: {
  account: Account;
  choices: Account[];
  switchAccount: (id: string) => Promise<void>;
  refreshChoices: () => Promise<void>;
  logout: () => Promise<void>;
  globalError: string;
}) {
  const admin = account.role === 'SYSTEM_ADMINISTRATOR';
  const [tab, setTab] = useState<Tab>(
      admin ? 'Administration' : 'Find buddies',
    ),
    [data, setData] = useState<WorkspaceData | null>(null),
    [revision, setRevision] = useState(0),
    [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api<StudentProfile[]>('/students'),
      api<Course[]>('/students/courses'),
      api<StudyGroup[]>('/groups'),
      api<MembershipRequest[]>('/groups/join-requests'),
      admin
        ? Promise.resolve([])
        : api<BuddyRequest[]>(`/connections/students/${account.id}`),
      admin
        ? Promise.resolve(null)
        : api<StudentProfile>(`/students/${account.id}`).catch((e) => {
            if (e instanceof ApiError && e.status === 404) return null;
            throw e;
          }),
      admin ? api<AccountUsage[]>('/admin/accounts') : Promise.resolve([]),
      admin
        ? api<MatchingConfig>('/admin/matching-config')
        : Promise.resolve(null),
    ])
      .then(
        ([
          students,
          courses,
          groups,
          memberships,
          requests,
          profile,
          accounts,
          config,
        ]) => {
          if (!cancelled) {
            setData({
              students,
              courses,
              groups,
              memberships,
              requests,
              profile,
              accounts,
              config,
            });
            setError('');
          }
        },
      )
      .catch((e) => {
        if (!cancelled) {
          setError(e.message);
          setData(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [account.id, admin, revision]);
  const refresh = () => {
    setRevision((v) => v + 1);
    void refreshChoices();
  };
  const tabs: Tab[] = admin
    ? ['Administration', 'Study groups']
    : ['Find buddies', 'Connections', 'Study groups', 'My profile'];
  const pending =
    data?.requests.filter(
      (r) => r.receiverId === account.id && r.status === 'PENDING',
    ).length ?? 0;
  const displayName = data?.profile?.name ?? account.name;
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a href="/" className="brand">
          <span className="brand-mark">sb.</span> studybuddy
        </a>
        <div className="sidebar-caption">YOUR STUDY SPACE</div>
        <nav aria-label="Main navigation">
          {tabs.map((t) => (
            <button
              key={t}
              className={tab === t ? 'selected' : ''}
              aria-current={tab === t ? 'page' : undefined}
              onClick={() => setTab(t)}
            >
              <span aria-hidden="true">{symbols[t]}</span>
              {t}
              {t === 'Connections' && pending > 0 && <b>{pending}</b>}
            </button>
          ))}
        </nav>
        <div className="sidebar-note">
          <span aria-hidden="true">✦</span>
          <h3>
            Small steps.
            <br />
            Shared progress.
          </h3>
          <p>The right company makes learning a little easier.</p>
        </div>
        <div className="sidebar-bottom">
          <Badge muted>Coursework demo</Badge>
          <p>All times in Singapore time</p>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Your workspace <span>/</span> <strong>{tab}</strong>
          </div>
          <div className="account-control">
            <Avatar name={displayName} />
            <Field label="Demo account">
              <select
                aria-label="Switch demo account"
                value={account.id}
                onChange={(e) => void switchAccount(e.target.value)}
              >
                {choices.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} · {a.id}
                    {a.role === 'SYSTEM_ADMINISTRATOR' ? ' · Admin' : ''}
                  </option>
                ))}
              </select>
            </Field>
            <button className="button ghost" onClick={() => void logout()}>
              Exit
            </button>
          </div>
        </header>
        <main className="workspace-main">
          <div className="page-heading">
            <div>
              <span className="eyebrow">
                {admin ? 'System administrator' : 'Your learning community'}
              </span>
              <h1>
                {admin
                  ? 'Keep the community connected.'
                  : `Hello, ${displayName.split(' ')[0]}.`}
              </h1>
              <p>
                {admin
                  ? 'Manage accounts and shape better study recommendations.'
                  : 'Good things happen when you learn together.'}
              </p>
            </div>
            <div className="community-count">
              <strong>{data?.students.length ?? '—'}</strong>
              <span>students · {data?.courses.length ?? '—'} courses</span>
            </div>
          </div>
          {(error || globalError) && (
            <div className="notice error" role="alert">
              {error || globalError}
              <button className="button ghost" onClick={refresh}>
                Retry
              </button>
            </div>
          )}
          {!data ? (
            <div className="panel loading" role="status">
              {error
                ? 'Data could not be loaded. Retry or select an active account.'
                : 'Loading your workspace…'}
            </div>
          ) : (
            <>
              {!admin && !data.profile && tab !== 'My profile' ? (
                <Empty title="Let’s set up your profile first">
                  <button
                    className="button"
                    onClick={() => setTab('My profile')}
                  >
                    Complete my profile
                  </button>
                </Empty>
              ) : (
                <>
                  {tab === 'Find buddies' && data.profile && (
                    <MatchExplorer
                      profile={data.profile}
                      courses={data.courses}
                      requests={data.requests}
                      refresh={refresh}
                    />
                  )}
                  {tab === 'Connections' && (
                    <ConnectionsPanel
                      id={account.id}
                      students={data.students}
                      requests={data.requests}
                      refresh={refresh}
                    />
                  )}
                  {tab === 'Study groups' && (
                    <GroupsPanel
                      account={account}
                      courses={data.courses}
                      students={data.students}
                      groups={data.groups}
                      requests={data.memberships}
                      refresh={refresh}
                    />
                  )}
                  {tab === 'My profile' && (
                    <ProfileEditor
                      profile={data.profile}
                      account={account}
                      courses={data.courses}
                      refresh={refresh}
                    />
                  )}
                  {tab === 'Administration' && data.config && (
                    <AdminPanel
                      account={account}
                      accounts={data.accounts}
                      config={data.config}
                      refresh={refresh}
                    />
                  )}
                </>
              )}
            </>
          )}
          <footer className="workspace-footer">
            Study Buddy Matcher <span>Built for shared progress.</span>
            <button className="button ghost" onClick={refresh}>
              Refresh data
            </button>
          </footer>
        </main>
      </div>
    </div>
  );
}
