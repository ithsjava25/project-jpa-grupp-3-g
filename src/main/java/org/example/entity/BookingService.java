package org.example.service;

import jakarta.persistence.EntityManagerFactory;
import org.example.entity.*;

import java.time.LocalDate;
import java.util.List;

public class BookingService {

    private final EntityManagerFactory emf;

    public BookingService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    // Visa alla tillgängliga bord
    public List<Table> getAllTables() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT t FROM Table t", Table.class).getResultList()
        );
    }

    // Visa alla tillgängliga tider
    public List<TimeSlot> getAllTimeSlots() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT ts FROM TimeSlot ts", TimeSlot.class).getResultList()
        );
    }

    // Visa alla gäster
    public List<Guest> getAllGuests() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT g FROM Guest g", Guest.class).getResultList()
        );
    }

    // Skapa ny bokning
    public void createBooking(Long tableId, Long timeSlotId, LocalDate date, int partySize, List<Long> guestIds) {
        emf.runInTransaction(em -> {
            // Hämta bord
            Table table = em.find(Table.class, tableId);
            if (table == null) {
                throw new IllegalArgumentException("Table not found!");
            }

            // Hämta tidslucka
            TimeSlot timeSlot = em.find(TimeSlot.class, timeSlotId);
            if (timeSlot == null) {
                throw new IllegalArgumentException("TimeSlot not found!");
            }

            // Skapa bokning
            Booking booking = new Booking();
            booking.setTime(date);
            booking.setTimeSlot(timeSlot);
            booking.setParty(partySize);
            booking.setTable(table);

            // Lägg till gäster
            for (Long guestId : guestIds) {
                Guest guest = em.find(Guest.class, guestId);
                if (guest != null) {
                    booking.addGuest(guest);
                }
            }

            em.persist(booking);
            System.out.println("Booking created successfully!");
        });
    }

    // Visa alla bokningar
    public List<Booking> getAllBookings() {
        return emf.callInTransaction(em ->
            em.createQuery("SELECT b FROM Booking b", Booking.class).getResultList()
        );
    }

    // Visa en specifik bokning
    public Booking getBooking(Long id) {
        return emf.callInTransaction(em -> em.find(Booking.class, id));
    }

    // Uppdatera bokningsstatus
    public void updateBookingStatus(Long bookingId, BookingStatus newStatus) {
        emf.runInTransaction(em -> {
            Booking booking = em.find(Booking.class, bookingId);
            if (booking == null) {
                throw new IllegalArgumentException("Booking not found!");
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

    // Ta bort bokning
    public void deleteBooking(Long bookingId) {
        emf.runInTransaction(em -> {
            Booking booking = em.find(Booking.class, bookingId);
            if (booking == null) {
                throw new IllegalArgumentException("Booking not found!");
            }
            em.remove(booking);
            System.out.println("Booking deleted successfully!");
        });
    }
}
