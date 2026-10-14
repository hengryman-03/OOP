package studybuddy.backend.connection.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.connection.model.*;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.service.StudentService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectionService {
    private final StudyRepository repository;
    private final StudentService students;

    public ConnectionService(StudyRepository repository, StudentService students) {
        this.repository = repository;
        this.students = students;
    }

    @Transactional
    public BuddyRequest sendRequest(BuddyRequest request, Actor actor) {
        repository.lock();
        actor.requireStudent();
        students.required(actor.id());
        students.requireActiveStudent(request.getReceiverId());
        students.required(request.getReceiverId());
        if (actor.id().equals(request.getReceiverId()))
            throw new IllegalArgumentException("You cannot send a request to yourself.");
        if (listForStudent(actor.id()).stream()
                .anyMatch(
                        r ->
                                (r.getSenderId().equals(request.getReceiverId())
                                                || r.getReceiverId()
                                                        .equals(request.getReceiverId()))
                                        && (r.getStatus() == RequestStatus.PENDING
                                                || r.getStatus() == RequestStatus.ACCEPTED)))
            throw DomainException.conflict(
                    "A pending request or active connection already exists between these"
                        + " students.");
        request.setSenderId(actor.id());
        request.setId(UUID.randomUUID().toString());
        request.setStatus(RequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        return repository.save(request.getId(), request);
    }

    @Transactional
    public BuddyRequest updateStatus(String id, RequestStatus status, Actor actor) {
        repository.lock();
        actor.requireStudent();
        BuddyRequest request =
                repository
                        .find(BuddyRequest.class, id)
                        .orElseThrow(() -> DomainException.missing("Buddy request not found."));
        boolean participant =
                actor.id().equals(request.getSenderId())
                        || actor.id().equals(request.getReceiverId());
        if (!participant) throw DomainException.forbidden("This is not your buddy request.");
        if (status == RequestStatus.ACCEPTED || status == RequestStatus.DECLINED) {
            if (!actor.id().equals(request.getReceiverId()))
                throw DomainException.forbidden(
                        "Only the recipient can accept or decline a request.");
            if (request.getStatus() != RequestStatus.PENDING)
                throw DomainException.conflict("This request has already been decided.");
            if (status == RequestStatus.ACCEPTED)
                students.requireActiveStudent(request.getSenderId());
        } else if (status == RequestStatus.ENDED) {
            if (request.getStatus() != RequestStatus.ACCEPTED)
                throw DomainException.conflict("Only an active connection can be ended.");
        } else throw new IllegalArgumentException("Choose ACCEPTED, DECLINED, or ENDED.");
        request.setStatus(status);
        return repository.save(id, request);
    }

    public List<BuddyRequest> listForStudent(String id) {
        return repository.all(BuddyRequest.class).stream()
                .filter(r -> id.equals(r.getSenderId()) || id.equals(r.getReceiverId()))
                .sorted(java.util.Comparator.comparing(BuddyRequest::getCreatedAt).reversed())
                .toList();
    }

    public List<BuddyRequest> activeConnections(String id) {
        return listForStudent(id).stream()
                .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                .toList();
    }
}
