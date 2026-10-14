'use client';

import { useState } from 'react';
import { api } from '@/lib/study-buddy-api';
import {
  Course,
  StudyGroup,
  StudyGoal,
  StudyMode,
  goals,
  modes,
  label,
} from '@/lib/study-buddy-types';
import { AvailabilityEditor, Field, useAction } from '@/components/ui';

export function GroupEditor({
  group,
  courses,
  done,
  cancel,
}: {
  group?: StudyGroup;
  courses: Course[];
  done: () => void;
  cancel: () => void;
}) {
  const [form, setForm] = useState<StudyGroup>(
    group ?? {
      name: '',
      description: '',
      courseCode: 'IS442',
      studyGoal: 'CONCEPT_REVIEW',
      preferredMode: 'EITHER',
      maximumGroupSize: 5,
      leaderId: '',
      memberIds: [],
      availability: [
        { dayOfWeek: 'WEDNESDAY', startTime: '19:00', endTime: '21:00' },
      ],
    },
  );
  const action = useAction(done);
  const patch = (value: Partial<StudyGroup>) =>
    setForm((f) => ({ ...f, ...value }));
  return (
    <form
      className="panel"
      onSubmit={(e) => {
        e.preventDefault();
        void action.run(() =>
          api(
            group ? `/groups/${group.id}` : '/groups',
            group ? 'PUT' : 'POST',
            form,
          ),
        );
      }}
    >
      <div className="section-heading">
        <div>
          <h2>{group ? 'Edit study group' : 'Start a study group'}</h2>
          <p>Make a little space for collective progress.</p>
        </div>
        <button type="button" className="button ghost" onClick={cancel}>
          Cancel
        </button>
      </div>
      {action.feedback}
      <fieldset disabled={action.busy} className="plain-fieldset">
        <div className="form-grid">
          <Field label="Group name">
            <input
              required
              maxLength={100}
              value={form.name}
              onChange={(e) => patch({ name: e.target.value })}
            />
          </Field>
          <Field label="Course">
            <select
              value={form.courseCode}
              onChange={(e) => patch({ courseCode: e.target.value })}
            >
              {courses.map((c) => (
                <option value={c.code} key={c.code}>
                  {c.code} · {c.title}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Study goal">
            <select
              value={form.studyGoal}
              onChange={(e) =>
                patch({ studyGoal: e.target.value as StudyGoal })
              }
            >
              {goals.map((v) => (
                <option value={v} key={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Meeting mode">
            <select
              value={form.preferredMode}
              onChange={(e) =>
                patch({ preferredMode: e.target.value as StudyMode })
              }
            >
              {modes.map((v) => (
                <option value={v} key={v}>
                  {label(v)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Maximum group size" hint="Includes the group leader.">
            <input
              type="number"
              min={Math.max(2, group?.memberIds.length ?? 2)}
              max={20}
              required
              value={form.maximumGroupSize}
              onChange={(e) =>
                patch({ maximumGroupSize: Number(e.target.value) })
              }
            />
          </Field>
        </div>
        <Field label="Description">
          <textarea
            required
            maxLength={1000}
            rows={3}
            value={form.description}
            onChange={(e) => patch({ description: e.target.value })}
          />
        </Field>
        <AvailabilityEditor
          value={form.availability}
          onChange={(availability) => patch({ availability })}
        />
        <div className="form-footer">
          <p>
            As leader, you can review requests, manage members, and hand over
            leadership.
          </p>
          <button className="button">
            {action.busy ? 'Saving…' : group ? 'Save group' : 'Create group'}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
