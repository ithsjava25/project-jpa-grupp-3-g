package org.example;

import jakarta.persistence.*;
import org.example.config.HibernateConfig;
import org.example.service.*;
import org.example.service.Table;
import org.example.service.BookingService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class App {
    static void main() {

        EntityManagerFactory emf = HibernateConfig.getEntityManagerFactory(null);

        try {
            createInitialData(emf);
            BookingService bookingService = new BookingService(emf);
            mainMenu(bookingService, emf);
        } finally {
            if (emf != null && emf.isOpen()) {
                emf.close();
            }
        }
    }

    private static void createInitialData(EntityManagerFactory emf) {
        // Kolla om data redan finns
        Long count = emf.callInTransaction(em ->
            em.createQuery("SELECT COUNT(t) FROM Table t", Long.class).getSingleResult()
        );

        if (count == 0) {
            hours(emf);
            createTable(emf);
            createGuest(emf);
            System.out.println("Initial data created!");
        }
    }

    private static void createGuest(EntityManagerFactory emf) {
        emf.runInTransaction(em -> {
            em.persist(new Guest("Gabriela", "Bord för fyra", "072762668"));
            em.persist(new Guest("Samuel", "Bord för 3", "072778882"));
            em.persist(new Guest("Anna", "VIP", "0701234567"));
            em.persist(new Guest("Erik", "Allergisk mot nötter", "0709876543"));
        });
    }

    private static void createTable(EntityManagerFactory emf) {
        emf.runInTransaction(em -> {
            for (int i = 1; i <= 5; i++) {
                Table table = new Table();
                table.setTableNumber(String.valueOf(i));
                table.setCapacity(i == 4 ? 6 : (i % 2 == 0 ? 2 : 4));
                em.persist(table);
            }
        });
    }

    private static void hours(EntityManagerFactory emf) {
        emf.runInTransaction(em -> {
            String[] times = {"16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00"};
            for (int i = 0; i < times.length; i++) {
                String start = times[i];
                String[] parts = start.split(":");
                int hour = Integer.parseInt(parts[0]) + 2;
                String end = hour + ":" + parts[1];
                em.persist(new TimeSlot(start, end));
            }
        });
    }

    public static void mainMenu(BookingService bookingService, EntityManagerFactory emf) {
        boolean running = true;
        while (running) {
            String menu = """

                ╔════════════════════════════════════╗
                ║     RESTAURANT BOOKING SYSTEM      ║
                ╠════════════════════════════════════╣
                ║ 1. CREATE BOOKING                  ║
                ║ 2. UPDATE BOOKING                  ║
                ║ 3. VIEW ALL BOOKINGS               ║
                ║ 4. DELETE BOOKING                  ║
                ║ 5. VIEW TABLES                     ║
                ║ 6. VIEW GUESTS                     ║
                ║ 7. EXIT                            ║
                ╚════════════════════════════════════╝
                """;

            String select = IO.readln(menu + "\nSelect option: ").toLowerCase();

            switch (select) {
                case "create booking", "cb", "1" -> createBookingMenu(bookingService);
                case "update booking", "ub", "2" -> updateBookingMenu(bookingService);
                case "view all bookings", "rb", "3" -> showBookings(bookingService);
                case "delete booking", "db", "4" -> deleteBookingMenu(bookingService);
                case "view tables", "5" -> viewTables(bookingService);
                case "view guests", "6" -> viewGuests(bookingService);
                case "exit", "7" -> {
                    System.out.println("Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid option!");
            }
        }
    }

    private static void createBookingMenu(BookingService bookingService) {
        System.out.println("\n═══ CREATE NEW BOOKING ═══");

        try {
            // Visa tillgängliga bord
            List<Table> tables = bookingService.getAllTables();
            System.out.println("\n📋 Available Tables:");
            tables.forEach(t -> System.out.println("  " + t.getId() + ". Table " + t.getTableNumber() + " (Capacity: " + t.getCapacity() + ")"));

            Long tableId = Long.parseLong(IO.readln("\nEnter Table ID: "));

            // Visa tillgängliga tider
            List<TimeSlot> timeSlots = bookingService.getAllTimeSlots();
            System.out.println("\n⏰ Available Time Slots:");
            timeSlots.forEach(ts -> System.out.println("  " + ts.getId() + ". " + ts.getStartTime() + " - " + ts.getFinishTime()));

            Long timeSlotId = Long.parseLong(IO.readln("\nEnter TimeSlot ID: "));

            // Datum med validering
            LocalDate date = null;
            while (date == null) {
                String dateStr = IO.readln("\nEnter date (YYYY-MM-DD): ");
                try {
                    date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);

                    // Validera att datumet är korrekt
                    LocalDate today = LocalDate.now();
                    // hur många månader fram man kan boka
                    LocalDate maxDate = today.plusMonths(3);

                    if (date.isBefore(today)) {
                        System.out.println("Date cannot be in the past! Please enter a future date.");
                        date = null;
                    } else if (date.isAfter(maxDate)) {
                        System.out.println("Date cannot be more than 3 months in the future! (Max: " + maxDate + ")");
                        date = null;
                    }
                } catch (Exception e) {
                    System.out.println("Invalid date format! Please use YYYY-MM-DD");
                }
            }

            // Antal gäster
            int partySize = Integer.parseInt(IO.readln("\nEnter party size: "));

            // Lägg till gäster
            List<Long> guestIds = new ArrayList<>();
            String addMore = "y";

            while (addMore.equalsIgnoreCase("y")) {
                System.out.println("\n👥 ADD GUEST:");
                System.out.println("1. Select existing guest");
                System.out.println("2. Create new guest");

                String guestChoice = IO.readln("Choose option (1 or 2): ").trim();

                if (guestChoice.equals("1")) {
                    // Välj befintlig gäst
                    List<Guest> guests = bookingService.getAllGuests();
                    System.out.println("\n📋 Available Guests:");
                    guests.forEach(g -> System.out.println("  " + g.getId() + ". " + g.getName() + " (" + g.getContact() + ")"));

                    Long guestId = Long.parseLong(IO.readln("\nEnter Guest ID: "));
                    guestIds.add(guestId);

                } else if (guestChoice.equals("2")) {
                    // Skapa ny gäst
                    System.out.println("\n═══ CREATE NEW GUEST ═══");
                    String name = IO.readln("Enter guest name: ");
                    String contact = IO.readln("Enter contact (phone/email): ");
                    String note = IO.readln("Enter note (allergies, preferences, etc.): ");

                    try {
                        Long newGuestId = bookingService.createGuest(name, note, contact);
                        guestIds.add(newGuestId);
                        System.out.println("Guest created successfully!");
                    } catch (Exception e) {
                        System.out.println("Error creating guest: " + e.getMessage());
                    }
                } else {
                    System.out.println("Invalid option! Please enter 1 or 2.");
                    continue;
                }

                addMore = IO.readln("\nAdd another guest? (y/n): ").trim();
            }

            // Skapa bokning med validering
            try {
                bookingService.createBooking(tableId, timeSlotId, date, partySize, guestIds);
            } catch (IllegalArgumentException e) {
                System.out.println("Booking failed: " + e.getMessage());
            }

        } catch (NumberFormatException e) {
            System.out.println("Invalid input! Please enter valid numbers.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void updateBookingMenu(BookingService bookingService) {
        System.out.println("\n═══ UPDATE BOOKING ═══");

        List<Booking> bookings = bookingService.getAllBookings();

        if (bookings.isEmpty()) {
            System.out.println("No bookings found to update.");
            return;
        }

        // visar alla bokningar
        showBookings(bookings);

        try {
            Long bookingId = Long.parseLong(IO.readln("\nEnter Booking ID to update: "));

            String statusMenu = """

            Select new status:
            1. PENDING
            2. CONFIRMED
            3. CANCELLED
            4. COMPLETED
            5. NO_SHOW
            """;

            String choice = IO.readln(statusMenu + "\nEnter choice: ");

            BookingStatus newStatus = switch (choice) {
                case "1" -> BookingStatus.PENDING;
                case "2" -> BookingStatus.CONFIRMED;
                case "3" -> BookingStatus.CANCELLED;
                case "4" -> BookingStatus.COMPLETED;
                case "5" -> BookingStatus.NO_SHOW;
                default -> null;
            };

            if (newStatus != null) {
                try {
                    bookingService.updateBookingStatus(bookingId, newStatus);
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            } else {
                System.out.println("Invalid status!");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format!");
        }
    }

    private static void deleteBookingMenu(BookingService bookingService) {
        System.out.println("\n═══ DELETE BOOKING ═══");

        List<Booking> bookings = bookingService.getAllBookings();

        if (bookings.isEmpty()) {
            System.out.println("No bookings found to delete.");
            return;
        }
        //Visar alla bokningar - metod längre ner
        showBookings(bookings);

        try {
            Long bookingId = Long.parseLong(IO.readln("\nEnter Booking ID to delete: "));
            String confirm = IO.readln("Are you sure? (y/n): ");

            if (confirm.equalsIgnoreCase("y")) {
                bookingService.deleteBooking(bookingId);
            } else {
                System.out.println("Deletion cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID format!");
        }
    }

    private static void showBookings(BookingService bookingService) {
        System.out.println("\n═══ ALL BOOKINGS ═══");

        List<Booking> bookings = bookingService.getAllBookings();

        if (bookings.isEmpty()) {
            System.out.println("📭 No bookings found.");
            return;
        }

        showBookings(bookings);
    }

    private static void showBookings(List<Booking> bookings) {
        bookings.forEach(b -> {
            System.out.println("\n📅 Booking ID: " + b.getId());
            System.out.println("   Date: " + b.getDate());
            System.out.println("   Time: " + b.getTimeSlot().getStartTime() + " - " + b.getTimeSlot().getFinishTime());
            System.out.println("   Table: " + b.getTable().getTableNumber());
            System.out.println("   Party Size: " + b.getParty());
            System.out.println("   Status: " + b.getStatus());
            System.out.println("   Guests: " + b.getGuests().stream().map(Guest::getName).toList());
        });
    }

    private static void viewTables(BookingService bookingService) {
        System.out.println("\n═══ ALL TABLES ═══");
        bookingService.getAllTables().forEach(t ->
            System.out.println("Table " + t.getTableNumber() + " - Capacity: " + t.getCapacity())
        );
    }

    private static void viewGuests(BookingService bookingService) {
        System.out.println("\n═══ ALL GUESTS ═══");
        bookingService.getAllGuests().forEach(g ->
            System.out.println(g.getName() + " - " + g.getContact() + " (" + g.getNote() + ")")
        );
    }
}
