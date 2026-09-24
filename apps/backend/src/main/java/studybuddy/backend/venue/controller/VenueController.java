package studybuddy.backend.venue.controller;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.auth.SessionService;
import studybuddy.backend.session.model.StudySession;
import studybuddy.backend.venue.model.Room;
import studybuddy.backend.venue.model.RoomBooking;
import studybuddy.backend.venue.service.VenueService;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
public class VenueController {
    private final VenueService venue;
    private final SessionService auth;

    public VenueController(VenueService venue, SessionService auth) {
        this.venue = venue;
        this.auth = auth;
    }

    @GetMapping("/rooms")
    public List<Room> rooms() {
        return venue.listRooms();
    }

    @GetMapping("/recommendations")
    public List<Room> recommendations(
            @RequestParam String sessionId,
            @RequestParam(required = false) String building,
            @RequestParam(defaultValue = "false") boolean whiteboard,
            @RequestParam(defaultValue = "false") boolean projector,
            @RequestParam(defaultValue = "false") boolean power,
            HttpSession session) {
        return venue.recommend(sessionId, building, whiteboard, projector, power, auth.actor(session));
    }

    @PostMapping("/bookings")
    public RoomBooking book(
            @RequestParam String sessionId, @RequestParam String roomId, HttpSession session) {
        return venue.book(sessionId, roomId, auth.actor(session));
    }

    @PostMapping("/bookings/release")
    public StudySession release(@RequestParam String sessionId, HttpSession session) {
        return venue.release(sessionId, auth.actor(session));
    }
}
