package studybuddy.backend.venue.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
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
        DayOfWeek day = session.getSessionDate().getDayOfWeek();
        return repository.all(Room.class).stream()
                .filter(r -> building == null || building.isBlank() || r.getBuilding().equalsIgnoreCase(building))
                .filter(r -> !whiteboard || r.isHasWhiteboard())
                .filter(r -> !projector || r.isHasProjector())
                .filter(r -> !power || r.isHasPowerSockets())
                .filter(r -> r.getCapacity() >= session.getMaxGroupSize())
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
        Room room =
                repository
                        .find(Room.class, roomId)
                        .orElseThrow(() -> DomainException.missing("Room not found."));
        if (room.getCapacity() < session.getMaxGroupSize())
            throw DomainException.conflict("This room's capacity is below the session's group size.");
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
