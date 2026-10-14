'use client';

import { useEffect, useRef, useState } from 'react';
import { api, query } from '@/lib/study-buddy-api';
import {
  BuddyRequest,
  Course,
  MatchResult,
  StudentProfile,
  arrangements,
  days,
  goals,
  label,
  modes,
} from '@/lib/study-buddy-types';
import { Avatar, Badge, Empty, Field, Times, useAction } from '@/components/ui';

export function MatchExplorer({
  profile,
  courses,
  requests,
  refresh,
}: {
  profile: StudentProfile;
  courses: Course[];
  requests: BuddyRequest[];
  refresh: () => void;
}) {
  const [filters, setFilters] = useState({
    courseCode: profile.preference.courseCode,
    studyGoal: '',
    studyMode: '',
    arrangement: '',
    day: '',
    startTime: '',
    endTime: '',
    overlapOnly: false,
  });
  const [matches, setMatches] = useState<MatchResult[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState('');
  const [selected, setSelected] = useState<StudentProfile | null>(null),
    [message, setMessage] = useState('');
  const action = useAction(refresh);
  const profileSection = useRef<HTMLElement>(null);
  useEffect(() => {
    if (selected)
      profileSection.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      });
  }, [selected]);
  const filterQuery = query(filters);
  useEffect(() => {
    let cancelled = false;
    api<MatchResult[]>(`/students/${profile.id}/matches${filterQuery}`)
      .then((data) => {
        if (!cancelled) {
          setMatches(data);
          setError('');
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e.message);
          setMatches([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [profile.id, filterQuery]);
  const change = (patch: Partial<typeof filters>) => {
    setLoading(true);
    setSelected(null);
    setFilters((f) => ({ ...f, ...patch }));
  };
  const relationship = (id: string) =>
    requests.find(
      (r) =>
        (r.senderId === id || r.receiverId === id) &&
        ['PENDING', 'ACCEPTED'].includes(r.status),
    );
  async function view(id: string) {
    await action.run(async () => {
      const student = await api<StudentProfile>(`/students/${id}`);
      setSelected(student);
      setMessage('');
    }, 'Profile opened.');
  }
  return (
    <div className="stack">
      {action.feedback}
      <section className="hero">
        <div>
          <span className="eyebrow">Better together</span>
          <h2>Find your study rhythm.</h2>
          <p>Meet peers who share your courses, goals, and free time.</p>
        </div>
        <div className="hero-art" aria-hidden="true">
          <span>YOU</span>
          <i>+</i>
          <span>
            YOUR
            <br />
            PEOPLE
          </span>
        </div>
      </section>
      <section className="panel">
        <div className="section-heading">
          <div>
            <h2>Find your people</h2>
            <p>Fine-tune your search. Every score has an explanation.</p>
          </div>
          <Badge>{matches.length} suggestions</Badge>
        </div>
        <div className="filter-grid">
          <Field label="Course">
            <select
              value={filters.courseCode}
              onChange={(e) => change({ courseCode: e.target.value })}
            >
              <option value="">All courses</option>
              {courses.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code} · {c.title}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Study goal">
            <select
              value={filters.studyGoal}
              onChange={(e) => change({ studyGoal: e.target.value })}
            >
              <option value="">Any goal</option>
              {goals.map((v) => (
                <option key={v} value={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Meeting mode">
            <select
              value={filters.studyMode}
              onChange={(e) => change({ studyMode: e.target.value })}
            >
              <option value="">Any mode</option>
              {modes.map((v) => (
                <option key={v} value={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <details className="advanced">
          <summary>Availability & more filters</summary>
          <div className="filter-grid">
            <Field label="Arrangement">
              <select
                value={filters.arrangement}
                onChange={(e) => change({ arrangement: e.target.value })}
              >
                <option value="">Any arrangement</option>
                {arrangements.map((v) => (
                  <option key={v} value={v}>
                    {label(v)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Available day">
              <select
                value={filters.day}
                onChange={(e) =>
                  change({ day: e.target.value, startTime: '', endTime: '' })
                }
              >
                <option value="">Any day</option>
                {days.map((v) => (
                  <option key={v} value={v}>
                    {label(v)}
                  </option>
                ))}
              </select>
            </Field>
            <label className="check-inline">
              <input
                type="checkbox"
                checked={filters.overlapOnly}
                onChange={(e) => change({ overlapOnly: e.target.checked })}
              />{' '}
              Overlaps my schedule
            </label>
          </div>
          {filters.day && (
            <form
              className="time-filter"
              onSubmit={(e) => {
                e.preventDefault();
                const form = new FormData(e.currentTarget);
                change({
                  startTime: String(form.get('start')),
                  endTime: String(form.get('end')),
                });
              }}
            >
              <Field label="Available from">
                <input name="start" type="time" required defaultValue="18:00" />
              </Field>
              <Field label="Available until">
                <input name="end" type="time" required defaultValue="21:00" />
              </Field>
              <button className="button secondary" type="submit">
                Apply time window
              </button>
              <button
                className="button ghost"
                type="button"
                onClick={() => change({ startTime: '', endTime: '' })}
              >
                Clear time
              </button>
            </form>
          )}
        </details>
      </section>
      {error && (
        <div role="alert" className="notice error">
          {error}
        </div>
      )}
      {loading ? (
        <div className="panel loading" role="status">
          Finding compatible study buddies…
        </div>
      ) : matches.length === 0 ? (
        <Empty title="No matches for these filters">
          Try another course or broaden your availability.
        </Empty>
      ) : (
        <div className="match-grid">
          {matches.map((match, index) => (
            <article className="panel match-card" key={match.student.id}>
              <div className="match-top">
                <Avatar name={match.student.name} large />
                <div className="score">
                  <strong>
                    {match.score}
                    <small>%</small>
                  </strong>
                  <span>compatibility</span>
                </div>
              </div>
              <h3>{match.student.name}</h3>
              <p className="muted-text">
                {match.student.programme} · Year {match.student.yearOfStudy}
              </p>
              <div className="tags">
                <Badge>{match.student.preference.courseCode}</Badge>
                <Badge muted>
                  {label(match.student.preference.preferredMode)}
                </Badge>
                {index === 0 && <Badge>Top match</Badge>}
              </div>
              <p className="goal-text">
                {label(match.student.preference.studyGoal)}
              </p>
              <Times slots={match.student.preference.availability} />
              <details className="score-details">
                <summary>Why this match?</summary>
                {Object.entries(match.breakdown).map(([key, value]) => (
                  <div className="breakdown-row" key={key}>
                    <span>
                      {
                        (
                          {
                            courseScore: 'Course',
                            availabilityScore: 'Availability',
                            studyModeScore: 'Meeting mode',
                            studyGoalScore: 'Study goal',
                            groupSizeScore: 'Group size & arrangement',
                          } as Record<string, string>
                        )[key]
                      }
                    </span>
                    <strong>{value} pts</strong>
                  </div>
                ))}
                <p>
                  Points reflect the administrator’s current strategy and
                  weights.
                </p>
              </details>
              <button
                className="button secondary wide"
                disabled={action.busy}
                onClick={() => void view(match.student.id)}
              >
                View study profile <span aria-hidden="true">↗</span>
              </button>
            </article>
          ))}
        </div>
      )}
      {selected && (
        <section
          ref={profileSection}
          className="panel public-profile"
          aria-label="Selected study profile"
        >
          <div className="section-heading">
            <div className="person-heading">
              <Avatar name={selected.name} large />
              <div>
                <h2>{selected.name}</h2>
                <p>
                  {selected.school} · {selected.programme} · Year{' '}
                  {selected.yearOfStudy}
                </p>
              </div>
            </div>
            <button className="button ghost" onClick={() => setSelected(null)}>
              Close profile
            </button>
          </div>
          <div className="tags">
            {selected.currentCourses.map((c) => (
              <Badge key={c}>{c}</Badge>
            ))}
          </div>
          <p>
            {label(selected.preference.studyGoal)} ·{' '}
            {label(selected.preference.preferredMode)} ·{' '}
            {label(selected.preference.preferredArrangement)} · Preferred size{' '}
            {selected.preference.preferredGroupSize}
          </p>
          <Times slots={selected.preference.availability} />
          <div className="privacy-note">
            {selected.contactNumber
              ? `Buddy contact: ${selected.contactNumber}`
              : 'Contact number is hidden until your buddy request is accepted.'}
          </div>
          {relationship(selected.id) ? (
            <Badge>
              {relationship(selected.id)?.status === 'ACCEPTED'
                ? 'Already connected'
                : 'Buddy request pending'}
            </Badge>
          ) : (
            <form
              className="request-form"
              onSubmit={(e) => {
                e.preventDefault();
                void action.run(
                  () =>
                    api('/connections/requests', 'POST', {
                      receiverId: selected.id,
                      message,
                    }),
                  'Buddy request sent. You can follow it in Connections.',
                );
              }}
            >
              <Field label="Say hello (optional)">
                <textarea
                  maxLength={500}
                  rows={3}
                  placeholder="Hi! Would you like to review this week’s concepts together?"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                />
              </Field>
              <button className="button" disabled={action.busy}>
                Send buddy request
              </button>
            </form>
          )}
        </section>
      )}
    </div>
  );
}
