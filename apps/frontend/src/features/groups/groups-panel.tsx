'use client';

import { useEffect, useRef, useState } from 'react';
import { api, query } from '@/lib/study-buddy-api';
import {
  Account,
  Course,
  MembershipRequest,
  StudentProfile,
  StudyGroup,
  label,
} from '@/lib/study-buddy-types';
import { Avatar, Badge, Empty, Field, Times, useAction } from '@/components/ui';
import { GroupEditor } from './group-editor';

export function GroupsPanel({
  account,
  courses,
  students,
  groups,
  requests,
  refresh,
}: {
  account: Account;
  courses: Course[];
  students: StudentProfile[];
  groups: StudyGroup[];
  requests: MembershipRequest[];
  refresh: () => void;
}) {
  const [editor, setEditor] = useState<StudyGroup | 'new' | null>(null),
    [scope, setScope] = useState('Discover'),
    [course, setCourse] = useState('');
  const [selectedId, setSelectedId] = useState(''),
    [replacement, setReplacement] = useState('');
  const action = useAction(refresh),
    admin = account.role === 'SYSTEM_ADMINISTRATOR';
  const selected = groups.find((g) => g.id === selectedId);
  const detailsSection = useRef<HTMLElement>(null);
  useEffect(() => {
    if (selectedId)
      detailsSection.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      });
  }, [selectedId]);
  const name = (id: string) =>
    students.find((s) => s.id === id)?.name ?? `${id} (unavailable)`;
  const shown = groups.filter(
    (g) =>
      (!course || g.courseCode === course) &&
      (scope === 'Closed'
        ? g.status === 'CLOSED'
        : g.status === 'ACTIVE' &&
          (scope === 'My groups'
            ? g.memberIds.includes(account.id)
            : scope === 'I lead'
              ? g.leaderId === account.id
              : true)),
  );
  const manage = (g: StudyGroup) => admin || g.leaderId === account.id;
  if (editor)
    return (
      <GroupEditor
        group={editor === 'new' ? undefined : editor}
        courses={courses}
        cancel={() => setEditor(null)}
        done={() => {
          setEditor(null);
          refresh();
        }}
      />
    );
  return (
    <div className="stack">
      <div className="section-heading">
        <div>
          <h2>A place to learn together</h2>
          <p>Join a study circle or bring one of your own to life.</p>
        </div>
        {!admin && (
          <button className="button" onClick={() => setEditor('new')}>
            + Create group
          </button>
        )}
      </div>
      {action.feedback}
      <div className="group-toolbar">
        <div className="tabs" role="tablist" aria-label="Group categories">
          {['Discover', 'My groups', 'I lead', 'Closed'].map((v) => (
            <button
              role="tab"
              aria-selected={scope === v}
              className={scope === v ? 'active' : ''}
              key={v}
              onClick={() => {
                setScope(v);
                setSelectedId('');
              }}
            >
              {v}
            </button>
          ))}
        </div>
        <Field label="Filter by course">
          <select value={course} onChange={(e) => setCourse(e.target.value)}>
            <option value="">All courses</option>
            {courses.map((c) => (
              <option key={c.code}>{c.code}</option>
            ))}
          </select>
        </Field>
      </div>
      {shown.length === 0 ? (
        <Empty title="No groups here yet">
          Try a different course or create a new study group.
        </Empty>
      ) : (
        <div className="group-grid">
          {shown.map((g) => {
            const pending = requests.some(
                (r) =>
                  r.groupId === g.id &&
                  r.studentId === account.id &&
                  r.status === 'PENDING',
              ),
              member = g.memberIds.includes(account.id),
              full = g.memberIds.length >= g.maximumGroupSize;
            return (
              <article className="panel group-card" key={g.id}>
                <div className="section-heading">
                  <Badge>{g.courseCode}</Badge>
                  <span className="capacity">
                    {g.memberIds.length}/{g.maximumGroupSize} members
                  </span>
                </div>
                <h3>{g.name}</h3>
                <p>{g.description}</p>
                <div className="tags">
                  <Badge muted>{label(g.preferredMode)}</Badge>
                  <Badge muted>{label(g.studyGoal)}</Badge>
                  {g.status === 'CLOSED' && <Badge muted>Closed</Badge>}
                </div>
                <Times slots={g.availability} />
                <div className="leader-line">
                  <Avatar name={name(g.leaderId)} />
                  <span>
                    Led by {name(g.leaderId)}
                    {g.leaderId === account.id ? ' (you)' : ''}
                  </span>
                </div>
                <div className="actions">
                  <button
                    className="button secondary"
                    onClick={() => {
                      setSelectedId(g.id ?? '');
                      setReplacement('');
                    }}
                  >
                    {manage(g) ? 'Manage group' : 'View members'}
                  </button>
                  {!admin && !member && g.status === 'ACTIVE' && (
                    <button
                      className="button"
                      disabled={action.busy || pending || full}
                      onClick={() =>
                        void action.run(
                          () => api(`/groups/${g.id}/join-requests`, 'POST'),
                          'Join request sent to the group leader.',
                        )
                      }
                    >
                      {pending
                        ? 'Request pending'
                        : full
                          ? 'Group full'
                          : 'Request to join'}
                    </button>
                  )}
                  {member && (
                    <Badge>
                      {g.leaderId === account.id ? 'You lead' : 'Member'}
                    </Badge>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
      {selected && (
        <section ref={detailsSection} className="panel">
          <div className="section-heading">
            <div>
              <span className="eyebrow">Group details</span>
              <h2>{selected.name}</h2>
              <p>
                {selected.status === 'CLOSED'
                  ? 'This group is closed. New requests and membership changes are unavailable.'
                  : 'Manage your members and keep the group moving.'}
              </p>
            </div>
            <button className="button ghost" onClick={() => setSelectedId('')}>
              Close details
            </button>
          </div>
          <h3>Members</h3>
          <div className="member-list">
            {selected.memberIds.map((id) => (
              <div key={id}>
                <div className="person-heading">
                  <Avatar name={name(id)} />
                  <span>{name(id)}</span>
                  {id === selected.leaderId && <Badge>Leader</Badge>}
                </div>
                {selected.status === 'ACTIVE' &&
                  id !== selected.leaderId &&
                  (manage(selected) || id === account.id) && (
                    <button
                      className="button ghost"
                      disabled={action.busy}
                      onClick={() => {
                        if (
                          window.confirm(
                            id === account.id
                              ? 'Leave this group?'
                              : `Remove ${name(id)} from the group?`,
                          )
                        )
                          void action.run(
                            () =>
                              api(
                                `/groups/${selected.id}/remove-member${query({ studentId: id })}`,
                                'POST',
                              ),
                            'Membership updated.',
                          );
                      }}
                    >
                      {id === account.id ? 'Leave group' : 'Remove'}
                    </button>
                  )}
              </div>
            ))}
          </div>
          {manage(selected) && (
            <>
              <h3 className="divider">Membership requests</h3>
              {requests.filter(
                (r) => r.groupId === selected.id && r.status === 'PENDING',
              ).length === 0 ? (
                <p className="muted-text">No pending requests.</p>
              ) : (
                requests
                  .filter(
                    (r) => r.groupId === selected.id && r.status === 'PENDING',
                  )
                  .map((r) => (
                    <div className="request-row" key={r.id}>
                      <span>{name(r.studentId)}</span>
                      <div className="actions">
                        <button
                          className="button"
                          disabled={action.busy}
                          onClick={() =>
                            void action.run(
                              () =>
                                api(
                                  `/groups/join-requests/${r.id}/decision?decision=ACCEPTED`,
                                  'POST',
                                ),
                              'Member accepted.',
                            )
                          }
                        >
                          Accept member
                        </button>
                        <button
                          className="button ghost"
                          disabled={action.busy}
                          onClick={() =>
                            void action.run(
                              () =>
                                api(
                                  `/groups/join-requests/${r.id}/decision?decision=DECLINED`,
                                  'POST',
                                ),
                              'Membership request declined.',
                            )
                          }
                        >
                          Decline member
                        </button>
                      </div>
                    </div>
                  ))
              )}
            </>
          )}
          {manage(selected) && selected.status === 'ACTIVE' && (
            <>
              <div className="actions divider">
                <button
                  className="button secondary"
                  onClick={() => setEditor(selected)}
                >
                  Edit group
                </button>
                <button
                  className="button danger"
                  disabled={action.busy}
                  onClick={() => {
                    if (
                      window.confirm(
                        'Close this group? Pending join requests will be declined and the group cannot be reopened.',
                      )
                    )
                      void action.run(
                        () => api(`/groups/${selected.id}/close`, 'POST'),
                        'Group closed.',
                      );
                  }}
                >
                  Close group
                </button>
              </div>
              <div className="handover">
                <h3>Leadership handover</h3>
                <p>
                  Pass the group to an existing member. To leave, transfer
                  leadership or close the group. A leader with no other members
                  closes the group when leaving.
                </p>
                {selected.memberIds.length > 1 && (
                  <>
                    <Field label="New group leader">
                      <select
                        value={replacement}
                        onChange={(e) => setReplacement(e.target.value)}
                      >
                        <option value="">Choose an existing member</option>
                        {selected.memberIds
                          .filter((id) => id !== selected.leaderId)
                          .map((id) => (
                            <option key={id} value={id}>
                              {name(id)}
                            </option>
                          ))}
                      </select>
                    </Field>
                    <div className="actions">
                      <button
                        className="button secondary"
                        disabled={action.busy || !replacement}
                        onClick={() =>
                          void action.run(
                            () =>
                              api(
                                `/groups/${selected.id}/transfer-leadership${query({ newLeaderId: replacement })}`,
                                'POST',
                              ),
                            'Leadership transferred. The previous leader remains a member.',
                          )
                        }
                      >
                        Transfer & stay
                      </button>
                      {selected.leaderId === account.id && (
                        <button
                          className="button secondary"
                          disabled={action.busy || !replacement}
                          onClick={() => {
                            if (
                              window.confirm(
                                'Transfer leadership and leave this group?',
                              )
                            )
                              void action.run(
                                () =>
                                  api(
                                    `/groups/${selected.id}/leader-quits${query({ replacementLeaderId: replacement })}`,
                                    'POST',
                                  ),
                                'Leadership transferred. You have left the group.',
                              );
                          }}
                        >
                          Transfer & leave
                        </button>
                      )}
                    </div>
                  </>
                )}
                {selected.leaderId === account.id && (
                  <button
                    className="button danger"
                    disabled={action.busy}
                    onClick={() => {
                      if (
                        window.confirm(
                          'Close this group and end your leadership?',
                        )
                      )
                        void action.run(
                          () =>
                            api(
                              `/groups/${selected.id}/leader-quits?closeGroup=true`,
                              'POST',
                            ),
                          'Group closed.',
                        );
                    }}
                  >
                    Close group & leave
                  </button>
                )}
              </div>
            </>
          )}
        </section>
      )}
      {!admin && (
        <section className="panel">
          <h2>Your group requests</h2>
          {requests.filter((r) => r.studentId === account.id).length === 0 ? (
            <p className="muted-text">
              Your join requests and their decisions will appear here.
            </p>
          ) : (
            requests
              .filter((r) => r.studentId === account.id)
              .map((r) => (
                <div className="request-row" key={r.id}>
                  <span>
                    {groups.find((g) => g.id === r.groupId)?.name ??
                      'Unavailable group'}
                  </span>
                  <Badge muted={r.status !== 'ACCEPTED'}>
                    {label(r.status)}
                  </Badge>
                </div>
              ))
          )}
        </section>
      )}
    </div>
  );
}
