package studybuddy.backend.venue.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.group.model.GroupStatus;
import studybuddy.backend.group.model.MemberJoiningEvent;
import studybuddy.backend.group.model.MemberLeftEvent;
import studybuddy.backend.group.model.StudyGroup;
import studybuddy.backend.group.service.StudyGroupService;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.session.model.StudySession;
import studybuddy.backend.session.service.StudySessionService;
import studybuddy.backend.venue.model.Room;
import studybuddy.backend.venue.model.RoomBooking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class VenueService {
    private final StudyRepository repository;
    private final StudySessionService sessionsSvc;
    private final StudyGroupService groups;

    public VenueService(
            StudyRepository repository, StudySessionService sessionsSvc, StudyGroupService groups) {
        this.repository = repository;
        this.sessionsSvc = sessionsSvc;
        this.groups = groups;
    }

    private void requireParticipant(StudyGroup group, Actor actor) {
        if (!actor.isAdmin()
                && !group.getLeaderId().equals(actor.id())
                && !group.getMemberIds().contains(actor.id()))
            throw DomainException.forbidden("You must be a member of this group.");
    }

    private void requireOpen(StudyGroup group) {
        if (group.getStatus() == GroupStatus.CLOSED)
            throw DomainException.conflict("This group is closed.");
    }

    /** Members may join after a session is planned, so the room must also fit today's group. */
    private int requiredSeats(StudySession session, StudyGroup group) {
        return Math.max(session.getMaxGroupSize(), group.getMemberIds().size());
    }

    private boolean withinOpeningHours(Room room, DayOfWeek day, LocalTime start, LocalTime end) {
        return room.getWeeklyAvailability().stream()
                .anyMatch(
                        slot ->
                                slot.getDayOfWeek() == day
                                        && !start.isBefore(slot.getStartTime())
                                        && !end.isAfter(slot.getEndTime()));
    }

    private boolean conflictsWithExistingBooking(
            Room room, LocalDate date, LocalTime start, LocalTime end) {
        return repository.all(RoomBooking.class).stream()
                .filter(b -> b.getRoomId().equals(room.getId()) && b.getDate().equals(date))
                .anyMatch(b -> b.getStartTime().isBefore(end) && start.isBefore(b.getEndTime()));
    }

    /**
     * A member joining must still fit every room the group has booked for today or later. The
     * exception reaches the publisher, so the join is rejected and its transaction rolls back.
     */
    @EventListener
    public void onMemberJoining(MemberJoiningEvent event) {
        repository.all(RoomBooking.class).stream()
                .filter(b -> b.getGroupId().equals(event.groupId()))
                .filter(b -> !b.getDate().isBefore(LocalDate.now()))
                .map(b -> repository.find(Room.class, b.getRoomId()))
                .flatMap(java.util.Optional::stream)
                .filter(room -> room.getCapacity() < event.newMemberCount())
                .mapToInt(Room::getCapacity)
                .min()
                .ifPresent(
                        seats -> {
                            throw DomainException.conflict(
                                    "A booked room for this group only seats "
                                            + seats
                                            + ". Release or rebook it before adding another member.");
                        });
    }

    /** Bookings made by a departing member pass to the group's current leader. */
    @EventListener
    public void onMemberLeft(MemberLeftEvent event) {
        repository.all(RoomBooking.class).stream()
                .filter(b -> b.getGroupId().equals(event.groupId()))
                .filter(b -> event.studentId().equals(b.getBookedBy()))
                .forEach(
                        b -> {
                            b.setBookedBy(event.leaderId());
                            repository.save(b.getId(), b);
                        });
    }

    public List<Room> listRooms() {
        return repository.all(Room.class);
    }

    public List<Room> recommend(
            String sessionId,
            String building,
            boolean whiteboard,
            boolean projector,
            boolean power,
            Actor actor) {
        StudySession session = sessionsSvc.required(sessionId);
        StudyGroup group = groups.required(session.getGroupId());
        requireParticipant(group, actor);
        requireOpen(group);
        int seats = requiredSeats(session, group);
        DayOfWeek day = session.getSessionDate().getDayOfWeek();
        return repository.all(Room.class).stream()
                .filter(r -> building == null || building.isBlank() || r.getBuilding().equalsIgnoreCase(building))
                .filter(r -> !whiteboard || r.isHasWhiteboard())
                .filter(r -> !projector || r.isHasProjector())
                .filter(r -> !power || r.isHasPowerSockets())
                .filter(r -> r.getCapacity() >= seats)
                .filter(r -> withinOpeningHours(r, day, session.getStartTime(), session.getEndTime()))
                .filter(
                        r ->
                                !conflictsWithExistingBooking(
                                        r, session.getSessionDate(), session.getStartTime(), session.getEndTime()))
                .sorted(Comparator.comparingInt(Room::getCapacity))
                .toList();
    }

    private void releaseAnyBookingFor(StudySession session) {
        repository.all(RoomBooking.class).stream()
                .filter(b -> b.getSessionId().equals(session.getId()))
                .forEach(b -> repository.delete(RoomBooking.class, b.getId()));
    }

    @Transactional
    public RoomBooking book(String sessionId, String roomId, Actor actor) {
        repository.lock();
        StudySession session = sessionsSvc.required(sessionId);
        StudyGroup group = groups.required(session.getGroupId());
        requireParticipant(group, actor);
        requireOpen(group);
        Room room =
                repository
                        .find(Room.class, roomId)
                        .orElseThrow(() -> DomainException.missing("Room not found."));
        if (room.getCapacity() < requiredSeats(session, group))
            throw DomainException.conflict("This room has fewer seats than the group needs.");
        DayOfWeek day = session.getSessionDate().getDayOfWeek();
        if (!withinOpeningHours(room, day, session.getStartTime(), session.getEndTime()))
            throw DomainException.conflict("This room is not open at the session's date and time.");
        if (conflictsWithExistingBooking(
                room, session.getSessionDate(), session.getStartTime(), session.getEndTime()))
            throw DomainException.conflict("This room is already booked for that date and time.");
        releaseAnyBookingFor(session);
        RoomBooking booking =
                new RoomBooking(
                        UUID.randomUUID().toString(),
                        roomId,
                        sessionId,
                        session.getGroupId(),
                        session.getSessionDate(),
                        session.getStartTime(),
                        session.getEndTime(),
                        actor.id());
        repository.save(booking.getId(), booking);
        session.setBookedRoomId(roomId);
        repository.save(session.getId(), session);
        return booking;
    }

    @Transactional
    public StudySession release(String sessionId, Actor actor) {
        repository.lock();
        StudySession session = sessionsSvc.required(sessionId);
        StudyGroup group = groups.required(session.getGroupId());
        requireParticipant(group, actor);
        releaseAnyBookingFor(session);
        session.setBookedRoomId(null);
        return repository.save(session.getId(), session);
    }
}
