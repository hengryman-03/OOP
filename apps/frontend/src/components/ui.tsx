'use client';

import { ReactNode, useState } from 'react';
import { AvailabilitySlot, days, label } from '@/lib/study-buddy-types';

export function Field({
  label: title,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  return (
    <label className="field">
      <span>{title}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-symbol" aria-hidden="true">
        ✦
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export function Badge({
  children,
  muted = false,
}: {
  children: ReactNode;
  muted?: boolean;
}) {
  return <span className={`badge${muted ? ' muted' : ''}`}>{children}</span>;
}
export function Avatar({
  name,
  large = false,
}: {
  name: string;
  large?: boolean;
}) {
  return (
    <span className={`avatar${large ? ' large' : ''}`} aria-hidden="true">
      {name
        .split(' ')
        .slice(0, 2)
        .map((n) => n[0])
        .join('')}
    </span>
  );
}
export function Times({ slots }: { slots: AvailabilitySlot[] }) {
  return (
    <div className="time-list">
      {slots.map((s, i) => (
        <span key={i}>
          {label(s.dayOfWeek).slice(0, 3)} · {s.startTime.slice(0, 5)}–
          {s.endTime.slice(0, 5)}
        </span>
      ))}
    </div>
  );
}
export function AvailabilityEditor({
  value,
  onChange,
}: {
  value: AvailabilitySlot[];
  onChange: (v: AvailabilitySlot[]) => void;
}) {
  const update = (index: number, patch: Partial<AvailabilitySlot>) =>
    onChange(value.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  return (
    <fieldset>
      <legend>Weekly availability</legend>
      <p className="help">
        All times are in Singapore time. Add as many study windows as you need.
      </p>
      {value.map((slot, i) => (
        <div className="slot-row" key={i}>
          <Field label={`Day ${i + 1}`}>
            <select
              value={slot.dayOfWeek}
              onChange={(e) => update(i, { dayOfWeek: e.target.value })}
            >
              {days.map((d) => (
                <option key={d}>{d}</option>
              ))}
            </select>
          </Field>
          <Field label="From">
            <input
              type="time"
              required
              value={slot.startTime.slice(0, 5)}
              onChange={(e) => update(i, { startTime: e.target.value })}
            />
          </Field>
          <Field label="Until">
            <input
              type="time"
              required
              value={slot.endTime.slice(0, 5)}
              onChange={(e) => update(i, { endTime: e.target.value })}
            />
          </Field>
          <button
            type="button"
            className="button ghost"
            disabled={value.length === 1}
            aria-label={`Remove availability slot ${i + 1}`}
            onClick={() => onChange(value.filter((_, n) => n !== i))}
          >
            Remove
          </button>
        </div>
      ))}
      <button
        className="button secondary"
        type="button"
        disabled={value.length >= 21}
        onClick={() =>
          onChange([
            ...value,
            { dayOfWeek: 'MONDAY', startTime: '18:00', endTime: '20:00' },
          ])
        }
      >
        + Add time slot
      </button>
    </fieldset>
  );
}
/** Every mutation uses the same pending/error feedback and only refreshes after success. */
export function useAction(onSuccess?: () => void) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [success, setSuccess] = useState('');
  async function run(
    operation: () => Promise<unknown>,
    message = 'Saved successfully.',
  ) {
    if (busy) return false;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await operation();
      setSuccess(message);
      onSuccess?.();
      return true;
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : 'Something went wrong. Please try again.',
      );
      return false;
    } finally {
      setBusy(false);
    }
  }
  const feedback = (
    <>
      {error && (
        <div className="notice error" role="alert">
          {error}
        </div>
      )}
      {success && (
        <div className="notice success" role="status">
          {success}
        </div>
      )}
    </>
  );
  return { busy, run, feedback };
}
