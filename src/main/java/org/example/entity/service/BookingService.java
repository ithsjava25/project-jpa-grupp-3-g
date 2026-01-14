package org.example.entity.service;

import jakarta.persistence.EntityManagerFactory;
import org.example.entity.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.example.entity.BookingStatus.*;

public class BookingService {

    private final EntityManagerFactory emf;

    public BookingService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // Skapar en ny gäst med validering och skydd mot dubletter
    public Long createGuest(String name, String note, String contact) {
        name = (name == null) ? null : name.trim();
        note = (note == null) ? null : note.trim();
        contact = (contact == null) ? null : contact.trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }

        if (name.length() > 50) {
            throw new IllegalArgumentException("Name is too long (max 50 characters)");
        }

        if (contact == null || contact.isBlank()) {
            throw new IllegalArgumentException("Contact cannot be empty");
        }
        contact = contact.trim().toLowerCase();

        // Mail och mobilnummer validering
        boolean isEmail = contact.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        boolean isPhone = contact.matches("^\\d{10}$");

        if (!isEmail && !isPhone) {
            throw new IllegalArgumentException("Contact must be a valid email or phone number");
        }

        String finalContact = contact;
        String finalName = name;
        String finalNote = note;
        String finalName1 = name;
        String finalNote1 = note;
        String finalContact1 = contact;
        return emf.callInTransaction(em -> {

            // Befintlig guest validering
            List<Guest> existing = em.createQuery(
                    "SELECT g FROM Guest g WHERE g.contact = :contact",
                    Guest.class
                ).setParameter("contact", finalContact)
                .getResultList();

            if (!existing.isEmpty()) return existing.get(0).getId();
            try {
                Guest guest = new Guest(finalName1, finalNote1, finalContact1);
                em.persist(guest);
                em.flush();
                return guest.getId();
                } catch (jakarta.persistence.PersistenceException ex) {
                // Likely unique constraint race: re-query and return existing if present.
                    List<Guest> after = em.createQuery(
                     "SELECT g FROM Guest g WHERE g.contact = :contact",
                     Guest.class
                        ).setParameter("contact", finalContact1)
                    .getResultList();
                if (!after.isEmpty()) return after.get(0).getId();
                throw ex;
                }
        });
    }

    // Tar bort en gäst endast om den inte har bokningar

    public void deleteGuest(Long guestId) {

        emf.runInTransaction(em -> {

            Guest guest = em.find(Guest.class, guestId);
            if (guest == null) {
                throw new IllegalArgumentException("Guest not found");
            }

            Long count = em.createQuery(
                    "SELECT COUNT(b) FROM Booking b JOIN b.guests g WHERE g.id = :gid",
                    Long.class
                ).setParameter("gid", guestId)
                .getSingleResult();

            if (count > 0) {
                throw new IllegalStateException("Cannot delete guest with existing bookings");
            }

            em.remove(guest);
        });
    }



    // Skapa bokning MED validering
    public void createBooking(Long tableId, Long timeSlotId, LocalDate date, int partySize, List<Long> guestIds) {
        emf.runInTransaction(em -> {
            // 1. Hämta bord
            Table table = em.find(Table.class, tableId);
            if (table == null) {
                throw new IllegalArgumentException("Table not found!");
            }

            // 2. Hämta tidslucka
            TimeSlot timeSlot = em.find(TimeSlot.class, timeSlotId);
            if (timeSlot == null) {
                throw new IllegalArgumentException("TimeSlot not found!");
            }

            // 3. VALIDERA KAPACITET
            if (partySize > table.getCapacity()) {
                throw new IllegalArgumentException(
                    "Party size (" + partySize + ") exceeds table capacity (" + table.getCapacity() + ")!"
                );
            }

            if (partySize < 1) {
                throw new IllegalArgumentException("Party size must be at least 1!");
            }

            // 4. VALIDERA DATUM
            LocalDate today = LocalDate.now();
            LocalDate maxDate = today.plusMonths(3);

            if (date.isBefore(today)) {
                throw new IllegalArgumentException("Cannot book a date in the past!");
            }

            if (date.isAfter(maxDate)) {
                throw new IllegalArgumentException("Cannot book more than 3 months in advance!");
            }

            // 5. VALIDERA ATT BORDET INTE ÄR BOKAT FÖR SAMMA TID/DATUM
            Long existingBookings = em.createQuery(
                    "SELECT COUNT(b) FROM Booking b " +
                        "WHERE b.table.id = :tableId " +
                        "AND b.date = :date " +
                        "AND b.timeSlot.id = :timeSlotId " +
                        "AND b.status != :cancelledStatus",
                    Long.class
                )
                .setParameter("tableId", tableId)
                .setParameter("date", date)
                .setParameter("timeSlotId", timeSlotId)
                .setParameter("cancelledStatus", BookingStatus.CANCELLED)
                .getSingleResult();

            if (existingBookings > 0) {
                throw new IllegalArgumentException(
                    "Table " + table.getTableNumber() +
                        " is already booked for " + date +
                        " at " + timeSlot.getStartTime() + "!"
                );
            }

            // 6. Validera att minst en gäst finns
            if (guestIds == null || guestIds.isEmpty()) {
                throw new IllegalArgumentException("Booking must have at least one guest!");
            }
            // Validering antalet guest får inte överstiga party size
            if (guestIds.size() > partySize) {
                throw new IllegalArgumentException(
                    "Number of registered guests cannot exceed party size."
                );
            }

            // 7. Skapa bokning
            Booking booking = new Booking();
            booking.setDate(date);
            booking.setTimeSlot(timeSlot);
            booking.setParty(partySize);
            booking.setTable(table);

            // 8. Lägg till gäster
            for (Long guestId : guestIds) {
                Guest guest = em.find(Guest.class, guestId);
                if (guest != null) {
                    booking.addGuest(guest);
                } else {
                    throw new IllegalArgumentException("Guest with ID " + guestId + " not found!");
                }
            }

            em.persist(booking);
            System.out.println("Booking created successfully!");
        });
    }

    public List<Table> getAllTables() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT t FROM Table t", Table.class).getResultList()
        );
    }

    public List<TimeSlot> getAllTimeSlots() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT ts FROM TimeSlot ts", TimeSlot.class).getResultList()
        );
    }

    public List<Guest> getAllGuests() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT g FROM Guest g", Guest.class).getResultList()
        );
    }

    public List<Booking> getAllBookings() {
        return emf.callInTransaction(em ->
            em.createQuery(
                "SELECT DISTINCT b FROM Booking b " +
                    "LEFT JOIN FETCH b.guests " +
                    "LEFT JOIN FETCH b.table " +
                    "LEFT JOIN FETCH b.timeSlot",
                Booking.class
            ).getResultList()
        );
    }

    public Optional<Booking> getBooking(Long id) {
        return emf.callInTransaction(em ->
            em.createQuery(
                    "SELECT b FROM Booking b " +
                        "LEFT JOIN FETCH b.guests " +
                        "LEFT JOIN FETCH b.table " +
                        "LEFT JOIN FETCH b.timeSlot " +
                        "WHERE b.id = :id",
                    Booking.class
                )
                .setParameter("id", id)
                .getResultStream()
                .findFirst()
        );
    }

    public void updateBookingStatus(Long bookingId, BookingStatus newStatus) {
        emf.runInTransaction(em -> {
            Booking booking = em.find(Booking.class, bookingId);
            if (booking == null) {
                System.out.println("Booking with ID " + bookingId + " not found!");
                return;
            }

            switch (newStatus) {
                case CONFIRMED -> booking.confirmBooking();
                case CANCELLED -> booking.cancelBooking();
                case COMPLETED -> booking.completeBooking();
                case NO_SHOW -> booking.noShowBooking();
            }

            System.out.println("Booking status updated to: " + newStatus);
        });
    }

    public void deleteBooking(Long bookingId) {
        emf.runInTransaction(em -> {
            try {
                Booking booking = em.createQuery(
                        "SELECT b FROM Booking b " +
                            "LEFT JOIN FETCH b.guests " +
                            "WHERE b.id = :id",
                        Booking.class
                    )
                    .setParameter("id", bookingId)
                    .getSingleResult();
                for (Guest g : booking.getGuests()) {
                    g.getBookings().remove(booking);
                }
                booking.getGuests().clear();
                em.remove(booking);
                System.out.println("Booking deleted successfully!");

            } catch (jakarta.persistence.NoResultException e) {
                System.out.println("Booking with ID " + bookingId + " not found!");
            }
        });
    }

    // Hitta lediga bord för ett specifikt datum/tid
    public List<Table> getAvailableTables(LocalDate date, Long timeSlotId) {
        return emf.callInTransaction(em ->
            em.createQuery(
                    "SELECT t FROM Table t WHERE t.id NOT IN " +
                        "(SELECT b.table.id FROM Booking b " +
                        "WHERE b.date = :date " +
                        "AND b.timeSlot.id = :timeSlotId " +
                        "AND b.status != :cancelledStatus)",
                    Table.class
                )
                .setParameter("date", date)
                .setParameter("timeSlotId", timeSlotId)
                .setParameter("cancelledStatus", BookingStatus.CANCELLED)
                .getResultList()
        );
    }
    // Hitta guest utan booking
    public List<Guest> getGuestsWithoutBookings() {
        return emf.callInTransaction(em ->
            em.createQuery(
                "SELECT g FROM Guest g WHERE g.bookings IS EMPTY",
                Guest.class
            ).getResultList()
        );
    }


}
