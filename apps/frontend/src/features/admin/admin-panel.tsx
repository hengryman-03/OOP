'use client';

import { useState } from 'react';
import { sendPasswordResetEmail } from 'firebase/auth';
import { api } from '@/lib/study-buddy-api';
import { firebaseConfigured, getFirebaseAuth } from '@/lib/firebase';
import {
  Account,
  AccountDetail,
  AccountUsage,
  MatchingConfig,
  label,
} from '@/lib/study-buddy-types';
import { Badge, Field, Modal, useAction } from '@/components/ui';

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
  const [inviteName, setInviteName] = useState(''),
    [inviteEmail, setInviteEmail] = useState(''),
    [inviteBusy, setInviteBusy] = useState(false),
    [inviteFeedback, setInviteFeedback] = useState<{
      text: string;
      error: boolean;
    } | null>(null);
  const action = useAction(refresh);
  const [detailFor, setDetailFor] = useState<Account | null>(null),
    [detail, setDetail] = useState<AccountDetail | null>(null),
    [detailLoading, setDetailLoading] = useState(false),
    [detailError, setDetailError] = useState('');
  async function openDetail(a: Account) {
    setDetailFor(a);
    setDetail(null);
    setDetailError('');
    setDetailLoading(true);
    try {
      setDetail(await api<AccountDetail>(`/admin/accounts/${a.id}/detail`));
    } catch (e) {
      setDetailError(
        e instanceof Error ? e.message : 'Could not load usage details.',
      );
    } finally {
      setDetailLoading(false);
    }
  }
  function closeDetail() {
    setDetailFor(null);
    setDetail(null);
    setDetailError('');
  }
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
  async function inviteStudent() {
    setInviteBusy(true);
    setInviteFeedback(null);
    let created: Account;
    try {
      created = await api<Account>('/admin/accounts/invitations', 'POST', {
        name: inviteName.trim(),
        email: inviteEmail.trim(),
      });
    } catch (error) {
      setInviteFeedback({
        text:
          error instanceof Error ? error.message : 'Could not create account.',
        error: true,
      });
      setInviteBusy(false);
      return;
    }
    refresh();
    setInviteName('');
    setInviteEmail('');
    const address = created.email ?? inviteEmail.trim();
    try {
      await sendPasswordResetEmail(getFirebaseAuth(), address);
      setInviteFeedback({
        text: `Account created for ${address}. Firebase accepted the invitation email request; ask the student to check their inbox and then log in.`,
        error: false,
      });
    } catch (error) {
      setInviteFeedback({
        text: `Account created for ${address}, but the invitation email request failed. Use Resend invite below. ${error instanceof Error ? error.message : ''}`,
        error: true,
      });
    } finally {
      setInviteBusy(false);
    }
  }
  function resendInvite(email: string) {
    void action.run(
      () => sendPasswordResetEmail(getFirebaseAuth(), email),
      'Firebase accepted the invitation email request.',
    );
  }
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
                {[
                  'BALANCED',
                  'AVAILABILITY_FIRST',
                  'COURSE_FIRST',
                  'PARETO',
                ].map((s) => (
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
            Balanced ranks by weighted score. Availability-first ranks by shared
            minutes, then score. Course-first ranks preferred-course matches
            before enrolled peers, then score. Pareto ranks non-dominated
            trade-offs first, then score. Weights determine the 100-point score
            and which criteria Pareto considers. A zero weight also disables
            that criterion as a strict priority.
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
            <p>
              Invite students by email, or manage existing account names and
              statuses. Students can still self-register.
            </p>
          </div>
        </div>
        <form
          className="account-form"
          onSubmit={(event) => {
            event.preventDefault();
            void inviteStudent();
          }}
        >
          <h3>Invite a student</h3>
          <p className="help">
            Creates a Firebase login and requests an email link for the student
            to set their password. Use an inbox the student can access.
          </p>
          <div className="form-grid">
            <Field label="Student name">
              <input
                required
                maxLength={100}
                value={inviteName}
                onChange={(event) => setInviteName(event.target.value)}
              />
            </Field>
            <Field label="Student email">
              <input
                required
                type="email"
                maxLength={254}
                value={inviteEmail}
                onChange={(event) => setInviteEmail(event.target.value)}
              />
            </Field>
          </div>
          <button
            className="button"
            disabled={inviteBusy || !firebaseConfigured}
          >
            {inviteBusy ? 'Creating account…' : 'Create account and invite'}
          </button>
          {!firebaseConfigured && (
            <p className="help">
              Firebase web configuration is required to send invitations.
            </p>
          )}
          {inviteFeedback && (
            <div
              className={`notice ${inviteFeedback.error ? 'error' : 'success'}`}
              role={inviteFeedback.error ? 'alert' : 'status'}
            >
              {inviteFeedback.text}
            </div>
          )}
        </form>
        {draft && (
          <form
            className="account-form"
            onSubmit={(e) => {
              e.preventDefault();
              void action.run(async () => {
                await api('/admin/accounts', 'POST', draft);
                setDraft(null);
              }, 'Account updated.');
            }}
          >
            <h3>Edit account</h3>
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
                <input readOnly value={label(draft.role ?? '')} />
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
                <th>Last sign-in</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {accounts
                .filter((a) =>
                  `${a.account.name} ${a.account.id} ${a.account.email ?? ''}`
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
                        {a.email && <small>{a.email}</small>}
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
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => openDetail(a)}
                        >
                          {activeConnections} buddies
                        </button>
                        {' · '}
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => openDetail(a)}
                        >
                          {groupsJoined} groups
                        </button>
                        <small>{pendingRequests} pending buddy requests</small>
                      </td>
                      <td>
                        {a.lastActiveAt
                          ? new Date(a.lastActiveAt).toLocaleString('en-SG')
                          : 'Not yet'}
                      </td>
                      <td>
                        <div className="actions">
                          {a.email &&
                            !profileComplete &&
                            a.status === 'ACTIVE' && (
                              <button
                                className="button ghost"
                                disabled={action.busy || !firebaseConfigured}
                                onClick={() => a.email && resendInvite(a.email)}
                              >
                                Resend invite
                              </button>
                            )}
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
                                  `Delete ${a.name}'s account, related records, and Firebase identity if present? This cannot be undone.`,
                                )
                              )
                                void action.run(
                                  () =>
                                    api(`/admin/accounts/${a.id}`, 'DELETE'),
                                  'Account, Firebase identity (if present), and related records deleted.',
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
      {detailFor && (
        <Modal
          title={`${detailFor.name} · Usage details`}
          onClose={closeDetail}
        >
          {detailLoading && <p className="help">Loading…</p>}
          {detailError && (
            <div className="notice error" role="alert">
              {detailError}
            </div>
          )}
          {detail && (
            <>
              <div className="modal-section">
                <h4>Buddies ({detail.buddies.length})</h4>
                {detail.buddies.length === 0 ? (
                  <p className="help">No active buddy connections.</p>
                ) : (
                  <div className="modal-list">
                    {detail.buddies.map((b) => (
                      <div className="modal-list-item" key={b.id}>
                        <strong>{b.name}</strong>
                        <small>{b.id}</small>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div className="modal-section">
                <h4>Groups ({detail.groups.length})</h4>
                {detail.groups.length === 0 ? (
                  <p className="help">Not a member of any active group.</p>
                ) : (
                  <div className="modal-list">
                    {detail.groups.map((g) => (
                      <div className="modal-list-item" key={g.id}>
                        <strong>{g.name}</strong>
                        <small>
                          {g.courseCode} · {g.memberIds.length}/
                          {g.maximumGroupSize} members
                          {g.leaderId === detailFor.id ? ' · Leader' : ''}
                        </small>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </Modal>
      )}
    </div>
  );
}
