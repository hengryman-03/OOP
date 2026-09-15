'use client';

import { useState } from 'react';
import { api } from '@/lib/study-buddy-api';
import {
  Account,
  Course,
  StudentProfile,
  StudyMode,
  StudyArrangement,
  StudyGoal,
  modes,
  arrangements,
  goals,
  label,
} from '@/lib/study-buddy-types';
import { AvailabilityEditor, Field, useAction } from '@/components/ui';

export function ProfileEditor({
  profile,
  account,
  courses,
  refresh,
}: {
  profile: StudentProfile | null;
  account: Account;
  courses: Course[];
  refresh: () => void;
}) {
  const [form, setForm] = useState<StudentProfile>(
    profile ?? {
      id: account.id,
      name: account.name,
      school: '',
      programme: '',
      yearOfStudy: 1,
      contactNumber: '',
      currentCourses: ['IS442'],
      preference: {
        courseCode: 'IS442',
        preferredMode: 'EITHER',
        preferredArrangement: 'EITHER',
        studyGoal: 'CONCEPT_REVIEW',
        preferredGroupSize: 2,
        availability: [
          { dayOfWeek: 'WEDNESDAY', startTime: '19:00', endTime: '21:00' },
        ],
      },
    },
  );
  const action = useAction(refresh);
  const patch = (value: Partial<StudentProfile>) =>
    setForm((f) => ({ ...f, ...value }));
  const preference = (value: Partial<StudentProfile['preference']>) =>
    patch({ preference: { ...form.preference, ...value } });
  return (
    <form
      className="panel form-panel"
      onSubmit={(e) => {
        e.preventDefault();
        void action.run(
          () => api('/students', 'POST', form),
          'Your profile and study preferences are saved.',
        );
      }}
    >
      <div className="section-heading">
        <div>
          <h2>
            {profile ? 'Your study profile' : 'Set up your study profile'}
          </h2>
          <p>A little about you helps us find the right people.</p>
        </div>
        <span className="eyebrow">01 / About you</span>
      </div>
      {action.feedback}
      <fieldset disabled={action.busy} className="plain-fieldset">
        <div className="form-grid">
          <Field label="Full name">
            <input
              required
              maxLength={100}
              value={form.name}
              onChange={(e) => patch({ name: e.target.value })}
            />
          </Field>
          <Field label="School">
            <input
              required
              maxLength={100}
              value={form.school}
              onChange={(e) => patch({ school: e.target.value })}
            />
          </Field>
          <Field label="Programme">
            <input
              required
              maxLength={120}
              value={form.programme}
              onChange={(e) => patch({ programme: e.target.value })}
            />
          </Field>
          <Field label="Year of study">
            <input
              type="number"
              required
              min={1}
              max={8}
              value={form.yearOfStudy}
              onChange={(e) => patch({ yearOfStudy: Number(e.target.value) })}
            />
          </Field>
          <Field
            label="Contact number"
            hint="Only you and your accepted buddies can see this."
          >
            <input
              type="tel"
              required
              minLength={7}
              maxLength={20}
              value={form.contactNumber ?? ''}
              onChange={(e) => patch({ contactNumber: e.target.value })}
            />
          </Field>
        </div>
        <fieldset>
          <legend>Courses currently taken</legend>
          <div className="course-checks">
            {courses.map((c) => (
              <label
                key={c.code}
                className={
                  form.currentCourses.includes(c.code) ? 'checked' : ''
                }
              >
                <input
                  type="checkbox"
                  checked={form.currentCourses.includes(c.code)}
                  onChange={(e) =>
                    patch({
                      currentCourses: e.target.checked
                        ? [...form.currentCourses, c.code]
                        : form.currentCourses.filter((v) => v !== c.code),
                    })
                  }
                />
                {c.code}
                <small>{c.title}</small>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="section-heading divider">
          <div>
            <h2>How you like to study</h2>
            <p>Update these preferences whenever your schedule changes.</p>
          </div>
          <span className="eyebrow">02 / Preferences</span>
        </div>
        <div className="form-grid">
          <Field label="Course to find a buddy for">
            <select
              required
              value={form.preference.courseCode}
              onChange={(e) => preference({ courseCode: e.target.value })}
            >
              {courses.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code} · {c.title}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Meeting mode">
            <select
              value={form.preference.preferredMode}
              onChange={(e) =>
                preference({ preferredMode: e.target.value as StudyMode })
              }
            >
              {modes.map((v) => (
                <option key={v} value={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Study arrangement">
            <select
              value={form.preference.preferredArrangement}
              onChange={(e) =>
                preference({
                  preferredArrangement: e.target.value as StudyArrangement,
                  ...(e.target.value === 'ONE_TO_ONE'
                    ? { preferredGroupSize: 2 }
                    : {}),
                })
              }
            >
              {arrangements.map((v) => (
                <option key={v} value={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Study goal">
            <select
              value={form.preference.studyGoal}
              onChange={(e) =>
                preference({ studyGoal: e.target.value as StudyGoal })
              }
            >
              {goals.map((v) => (
                <option key={v} value={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Preferred group size (including you)">
            <input
              required
              type="number"
              min={2}
              max={
                form.preference.preferredArrangement === 'ONE_TO_ONE' ? 2 : 20
              }
              value={form.preference.preferredGroupSize}
              onChange={(e) =>
                preference({ preferredGroupSize: Number(e.target.value) })
              }
            />
          </Field>
        </div>
        <AvailabilityEditor
          value={form.preference.availability}
          onChange={(availability) => preference({ availability })}
        />
        <div className="form-footer">
          <p>
            Your contact details stay private until a buddy request is accepted.
          </p>
          <button className="button" type="submit">
            {action.busy ? 'Saving…' : 'Save profile'}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
