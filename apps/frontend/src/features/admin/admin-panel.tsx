'use client';

import { useState } from 'react';
import { api } from '@/lib/study-buddy-api';
import {
  Account,
  AccountUsage,
  MatchingConfig,
  label,
} from '@/lib/study-buddy-types';
import { Badge, Field, useAction } from '@/components/ui';

export function AdminPanel({
  account,
  accounts,
  config,
  refresh,
}: {
  account: Account;
  accounts: AccountUsage[];
  config: MatchingConfig;
  refresh: () => void;
}) {
  const [weights, setWeights] = useState(config),
    [draft, setDraft] = useState<Partial<Account> | null>(null),
    [search, setSearch] = useState('');
  const action = useAction(refresh);
  const weightLabels = {
    courseWeight: 'Course alignment',
    availabilityWeight: 'Availability overlap',
    studyModeWeight: 'Meeting mode',
    studyGoalWeight: 'Study goal',
    groupSizeWeight: 'Group size & arrangement',
  };
  const total = Object.keys(weightLabels).reduce(
    (sum, key) => sum + Number(weights[key as keyof typeof weightLabels]),
    0,
  );
  return (
    <div className="stack">
      <div className="stats-grid">
        <div className="stat">
          <span>User accounts</span>
          <strong>{accounts.length}</strong>
        </div>
        <div className="stat">
          <span>Active students</span>
          <strong>
            {
              accounts.filter(
                (a) =>
                  a.account.role === 'STUDENT' && a.account.status === 'ACTIVE',
              ).length
            }
          </strong>
        </div>
        <div className="stat">
          <span>Completed profiles</span>
          <strong>{accounts.filter((a) => a.profileComplete).length}</strong>
        </div>
      </div>
      {action.feedback}
      <form
        className="panel"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(
            () => api('/admin/matching-config', 'PUT', weights),
            'Matching configuration saved. New searches use these settings.',
          );
        }}
      >
        <div className="section-heading">
          <div>
            <h2>Matching configuration</h2>
            <p>Choose what matters most when bringing students together.</p>
          </div>
          <Badge>Live settings</Badge>
        </div>
        <fieldset disabled={action.busy} className="plain-fieldset">
          <div className="form-grid">
            <Field label="Matching strategy">
              <select
                value={weights.strategy}
                onChange={(e) =>
                  setWeights((w) => ({
                    ...w,
                    strategy: e.target.value as MatchingConfig['strategy'],
                  }))
                }
              >
                {['BALANCED', 'AVAILABILITY_FIRST', 'COURSE_FIRST'].map((s) => (
                  <option key={s} value={s}>
                    {label(s)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Maximum results">
              <input
                required
                type="number"
                min={1}
                max={50}
                value={weights.maxResults}
                onChange={(e) =>
                  setWeights((w) => ({
                    ...w,
                    maxResults: Number(e.target.value),
                  }))
                }
              />
            </Field>
            {Object.entries(weightLabels).map(([key, title]) => (
              <Field
                key={key}
                label={title}
                hint="Set to 0 to disable this criterion."
              >
                <input
                  required
                  type="number"
                  min={0}
                  max={1000}
                  value={weights[key as keyof typeof weightLabels]}
                  onChange={(e) =>
                    setWeights((w) => ({ ...w, [key]: Number(e.target.value) }))
                  }
                />
              </Field>
            ))}
          </div>
          <p className="help">
            Balanced uses your weights directly. Availability-first triples the
            availability weight; course-first triples the course weight.
            Effective weights are normalized to 100 points. At least one
            criterion must be enabled.
          </p>
          <div className="form-footer">
            <span>
              Configured weight total: <strong>{total}</strong>
            </span>
            <button className="button" disabled={total <= 0}>
              {action.busy ? 'Saving…' : 'Save matching settings'}
            </button>
          </div>
        </fieldset>
      </form>
      <section className="panel">
        <div className="section-heading">
          <div>
            <h2>User accounts</h2>
            <p>Create accounts, maintain status, and review basic activity.</p>
          </div>
          <button
            className="button"
            onClick={() =>
              setDraft({ name: '', role: 'STUDENT', status: 'ACTIVE' })
            }
          >
            + Create account
          </button>
        </div>
        {draft && (
          <form
            className="account-form"
            onSubmit={(e) => {
              e.preventDefault();
              void action.run(
                async () => {
                  await api('/admin/accounts', 'POST', draft);
                  setDraft(null);
                },
                draft.id
                  ? 'Account updated.'
                  : 'Account created. Select it in demo mode to complete its profile.',
              );
            }}
          >
            <h3>{draft.id ? 'Edit account' : 'New account'}</h3>
            <div className="form-grid">
              <Field label="Account name">
                <input
                  required
                  maxLength={100}
                  value={draft.name}
                  onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                />
              </Field>
              <Field label="Role">
                <select
                  disabled={!!draft.id}
                  value={draft.role}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      role: e.target.value as Account['role'],
                    })
                  }
                >
                  <option value="STUDENT">Student</option>
                  <option value="SYSTEM_ADMINISTRATOR">
                    System administrator
                  </option>
                </select>
              </Field>
              <Field label="Status">
                <select
                  value={draft.status}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      status: e.target.value as Account['status'],
                    })
                  }
                >
                  <option>ACTIVE</option>
                  <option>SUSPENDED</option>
                </select>
              </Field>
            </div>
            <div className="actions">
              <button className="button" disabled={action.busy}>
                Save account
              </button>
              <button
                type="button"
                className="button ghost"
                onClick={() => setDraft(null)}
              >
                Cancel
              </button>
            </div>
          </form>
        )}
        <Field label="Search accounts">
          <input
            type="search"
            placeholder="Search by name or ID"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </Field>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Account</th>
                <th>Status</th>
                <th>Usage</th>
                <th>Last active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {accounts
                .filter((a) =>
                  `${a.account.name} ${a.account.id}`
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map(
                  ({
                    account: a,
                    profileComplete,
                    activeConnections,
                    groupsJoined,
                    pendingRequests,
                  }) => (
                    <tr key={a.id}>
                      <td>
                        <strong>{a.name}</strong>
                        <small>{a.id}</small>
                        <small>
                          {label(a.role)}
                          {a.role === 'STUDENT' && !profileComplete
                            ? ' · Profile needed'
                            : ''}
                        </small>
                      </td>
                      <td>
                        <Badge muted={a.status !== 'ACTIVE'}>
                          {label(a.status ?? '')}
                        </Badge>
                      </td>
                      <td>
                        {activeConnections} buddies · {groupsJoined} groups
                        <small>{pendingRequests} pending buddy requests</small>
                      </td>
                      <td>
                        {a.lastActiveAt
                          ? new Date(a.lastActiveAt).toLocaleString('en-SG')
                          : 'Not yet'}
                      </td>
                      <td>
                        <div className="actions">
                          <button
                            className="button ghost"
                            onClick={() => setDraft(a)}
                          >
                            Edit
                          </button>
                          <button
                            className="button danger"
                            disabled={action.busy || a.id === account.id}
                            onClick={() => {
                              if (
                                window.confirm(
                                  `Delete ${a.name}'s account, profile, and requests? This cannot be undone.`,
                                )
                              )
                                void action.run(
                                  () =>
                                    api(`/admin/accounts/${a.id}`, 'DELETE'),
                                  'Account and related personal records deleted.',
                                );
                            }}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ),
                )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
