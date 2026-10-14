'use client';

import { useState } from 'react';
import { api } from '@/lib/study-buddy-api';
import { BuddyRequest, StudentProfile, label } from '@/lib/study-buddy-types';
import { Avatar, Badge, Empty, Times, useAction } from '@/components/ui';

export function ConnectionsPanel({
  id,
  students,
  requests,
  refresh,
}: {
  id: string;
  students: StudentProfile[];
  requests: BuddyRequest[];
  refresh: () => void;
}) {
  const [section, setSection] = useState('Inbox'),
    [profile, setProfile] = useState<StudentProfile | null>(null);
  const action = useAction(refresh);
  const inbox = requests.filter(
    (r) => r.receiverId === id && r.status === 'PENDING',
  ).length;
  const shown = requests.filter((r) =>
    section === 'Inbox'
      ? r.receiverId === id && r.status === 'PENDING'
      : section === 'Sent'
        ? r.senderId === id && r.status === 'PENDING'
        : section === 'Active buddies'
          ? r.status === 'ACCEPTED'
          : ['DECLINED', 'ENDED'].includes(r.status),
  );
  const name = (studentId: string) =>
    students.find((s) => s.id === studentId)?.name ?? studentId;
  return (
    <div className="stack">
      <div className="section-heading">
        <div>
          <h2>Your study connections</h2>
          <p>Start a conversation, make a plan, and learn together.</p>
        </div>
      </div>
      {action.feedback}
      <div className="tabs" role="tablist" aria-label="Connection categories">
        {['Inbox', 'Sent', 'Active buddies', 'History'].map((v) => (
          <button
            key={v}
            role="tab"
            aria-selected={section === v}
            className={section === v ? 'active' : ''}
            onClick={() => {
              setSection(v);
              setProfile(null);
            }}
          >
            {v}
            {v === 'Inbox' && inbox > 0 ? ` (${inbox})` : ''}
          </button>
        ))}
      </div>
      {shown.length === 0 ? (
        <Empty
          title={
            section === 'Inbox'
              ? 'You’re all caught up'
              : `No ${section.toLowerCase()} yet`
          }
        >
          Find a study buddy to get the conversation started.
        </Empty>
      ) : (
        shown.map((r) => {
          const other = r.senderId === id ? r.receiverId : r.senderId;
          return (
            <article className="panel connection-card" key={r.id}>
              <div className="section-heading">
                <div className="person-heading">
                  <Avatar name={name(other)} />
                  <div>
                    <h3>{name(other)}</h3>
                    <p>
                      {r.senderId === id ? 'Sent' : 'Received'}{' '}
                      {new Date(r.createdAt).toLocaleDateString('en-SG')}
                    </p>
                  </div>
                </div>
                <Badge muted={r.status !== 'ACCEPTED'}>{label(r.status)}</Badge>
              </div>
              {r.message && <blockquote>{r.message}</blockquote>}
              <div className="actions">
                <button
                  className="button secondary"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(
                      async () =>
                        setProfile(
                          await api<StudentProfile>(`/students/${other}`),
                        ),
                      'Profile opened.',
                    )
                  }
                >
                  View profile{r.status === 'ACCEPTED' ? ' & contact' : ''}
                </button>
                {r.receiverId === id && r.status === 'PENDING' && (
                  <>
                    <button
                      className="button"
                      disabled={action.busy}
                      onClick={() =>
                        void action.run(
                          () =>
                            api(
                              `/connections/requests/${r.id}/status?status=ACCEPTED`,
                              'POST',
                            ),
                          'Request accepted. You can now view each other’s contact number.',
                        )
                      }
                    >
                      Accept request
                    </button>
                    <button
                      className="button ghost"
                      disabled={action.busy}
                      onClick={() =>
                        void action.run(
                          () =>
                            api(
                              `/connections/requests/${r.id}/status?status=DECLINED`,
                              'POST',
                            ),
                          'Request declined.',
                        )
                      }
                    >
                      Decline
                    </button>
                  </>
                )}
                {r.status === 'ACCEPTED' && (
                  <button
                    className="button danger"
                    disabled={action.busy}
                    onClick={() => {
                      if (
                        window.confirm(
                          `End your study connection with ${name(other)}? Contact access will be removed.`,
                        )
                      ) {
                        setProfile(null);
                        void action.run(
                          () =>
                            api(
                              `/connections/requests/${r.id}/status?status=ENDED`,
                              'POST',
                            ),
                          'Connection ended. Contact details are private again.',
                        );
                      }
                    }}
                  >
                    End connection
                  </button>
                )}
              </div>
            </article>
          );
        })
      )}
      {profile && (
        <section className="panel">
          <div className="section-heading">
            <div>
              <h2>{profile.name}</h2>
              <p>
                {profile.school} · {profile.programme} · Year{' '}
                {profile.yearOfStudy}
              </p>
            </div>
            <button className="button ghost" onClick={() => setProfile(null)}>
              Close profile
            </button>
          </div>
          <p>{profile.currentCourses.join(' · ')}</p>
          <p>
            {label(profile.preference.studyGoal)} ·{' '}
            {label(profile.preference.preferredMode)}
          </p>
          <Times slots={profile.preference.availability} />
          <div className="privacy-note">
            {profile.contactNumber
              ? `Contact number: ${profile.contactNumber}`
              : 'Contact number is hidden until a buddy request is accepted.'}
          </div>
        </section>
      )}
    </div>
  );
}
