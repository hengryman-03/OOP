'use client';

import { useEffect, useState } from 'react';
import { api, query } from '@/lib/study-buddy-api';
import {
  Room,
  RoomBooking,
  StudyGroup,
  StudySession,
} from '@/lib/study-buddy-types';
import { Badge, Empty, Field, Modal, Times, useAction } from '@/components/ui';

export function VenuesPanel({
  groups,
  sessions,
  rooms,
  refresh,
}: {
  groups: StudyGroup[];
  sessions: StudySession[];
  rooms: Room[];
  refresh: () => void;
}) {
  const [sessionId, setSessionId] = useState(''),
    [building, setBuilding] = useState(''),
    [whiteboard, setWhiteboard] = useState(false),
    [projector, setProjector] = useState(false),
    [power, setPower] = useState(false);
  const [recommendations, setRecommendations] = useState<Room[]>([]),
    [loading, setLoading] = useState(false),
    [error, setError] = useState('');
  const [confirming, setConfirming] = useState<Room | null>(null);
  const action = useAction(refresh);

  const session = sessions.find((s) => s.id === sessionId);
  const groupName = (groupId: string) =>
    groups.find((g) => g.id === groupId)?.name ?? 'Unavailable group';
  const buildings = Array.from(new Set(rooms.map((r) => r.building))).sort();
  const bookedRoom = session
    ? rooms.find((r) => r.id === session.bookedRoomId)
    : undefined;

  const filterQuery = query({ sessionId, building, whiteboard, projector, power });
  useEffect(() => {
    if (!sessionId) {
      setRecommendations([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    api<Room[]>(`/venues/recommendations${filterQuery}`)
      .then((data) => {
        if (!cancelled) {
          setRecommendations(data);
          setError('');
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e.message);
          setRecommendations([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [filterQuery, sessionId]);

  return (
    <div className="stack">
      <section className="hero">
        <div>
          <span className="eyebrow">Room recommendations</span>
          <h2>Find a room for your next session.</h2>
          <p>Pick a planned session, then book a space that fits.</p>
        </div>
        <Badge muted>Demo venue data</Badge>
      </section>
      <section className="panel">
        {action.feedback}
        <div className="filter-grid">
          <Field label="Session">
            <select
              value={sessionId}
              onChange={(e) => setSessionId(e.target.value)}
            >
              <option value="">Choose a session</option>
              {sessions.map((s) => (
                <option key={s.id} value={s.id}>
                  {groupName(s.groupId)} · {s.sessionDate}{' '}
                  {s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)}
                </option>
              ))}
            </select>
          </Field>
        </div>
        {!session ? (
          <Empty title="Plan a session first">
            Go to Study groups to plan a session, then come back here to book
            a room.
          </Empty>
        ) : (
          <>
            {bookedRoom && (
              <div className="booking-summary">
                <strong>Current booking</strong>
                <span>
                  {bookedRoom.building} · {bookedRoom.roomNumber} · fits up to{' '}
                  {bookedRoom.capacity}
                </span>
                <button
                  className="button ghost"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(
                      () =>
                        api(
                          `/venues/bookings/release${query({ sessionId })}`,
                          'POST',
                        ),
                      'Booking released.',
                    )
                  }
                >
                  Un-book
                </button>
              </div>
            )}
            <div className="filter-grid">
              <Field label="Preferred building">
                <select
                  value={building}
                  onChange={(e) => setBuilding(e.target.value)}
                >
                  <option value="">Any building</option>
                  {buildings.map((b) => (
                    <option key={b}>{b}</option>
                  ))}
                </select>
              </Field>
              <label className="check-inline">
                <input
                  type="checkbox"
                  checked={whiteboard}
                  onChange={(e) => setWhiteboard(e.target.checked)}
                />{' '}
                Whiteboard
              </label>
              <label className="check-inline">
                <input
                  type="checkbox"
                  checked={projector}
                  onChange={(e) => setProjector(e.target.checked)}
                />{' '}
                Display or projector
              </label>
              <label className="check-inline">
                <input
                  type="checkbox"
                  checked={power}
                  onChange={(e) => setPower(e.target.checked)}
                />{' '}
                Power sockets
              </label>
            </div>
            {error && (
              <div role="alert" className="notice error">
                {error}
              </div>
            )}
            {loading ? (
              <div className="panel loading" role="status">
                Finding available rooms…
              </div>
            ) : recommendations.length === 0 ? (
              <Empty title="No rooms match these filters">
                Try a different building or fewer facility filters.
              </Empty>
            ) : (
              <div className="room-grid">
                {recommendations.map((room) => (
                  <article className="panel room-card" key={room.id}>
                    <div className="section-heading">
                      <Badge>{room.building}</Badge>
                      <span className="capacity">Fits {room.capacity}</span>
                    </div>
                    <h3>{room.roomNumber}</h3>
                    <div className="tags">
                      {room.hasWhiteboard && <Badge muted>Whiteboard</Badge>}
                      {room.hasProjector && (
                        <Badge muted>Display / projector</Badge>
                      )}
                      {room.hasPowerSockets && (
                        <Badge muted>Power sockets</Badge>
                      )}
                    </div>
                    <Times slots={room.weeklyAvailability} />
                    <button
                      className="button secondary wide"
                      disabled={action.busy}
                      onClick={() => setConfirming(room)}
                    >
                      Book this room
                    </button>
                  </article>
                ))}
              </div>
            )}
          </>
        )}
      </section>
      {confirming && session && (
        <Modal title="Confirm booking" onClose={() => setConfirming(null)}>
          <p>
            Book <strong>{confirming.building} · {confirming.roomNumber}</strong>{' '}
            for {groupName(session.groupId)} on {session.sessionDate},{' '}
            {session.startTime.slice(0, 5)}–{session.endTime.slice(0, 5)}?
          </p>
          <div className="actions">
            <button
              className="button"
              disabled={action.busy}
              onClick={() =>
                void action
                  .run(
                    () =>
                      api<RoomBooking>(
                        `/venues/bookings${query({ sessionId, roomId: confirming.id })}`,
                        'POST',
                      ),
                    'Room booked.',
                  )
                  .then((ok) => {
                    if (ok) setConfirming(null);
                  })
              }
            >
              Confirm booking
            </button>
            <button
              type="button"
              className="button ghost"
              onClick={() => setConfirming(null)}
            >
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
