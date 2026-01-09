package org.example;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.persistence.*;
import org.example.entity.*;
import org.example.entity.Table;
import org.example.service.BookingService;
import org.hibernate.jpa.HibernatePersistenceConfiguration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) {

        List<Class<?>> entities = getEntities("org.example.entity");

        final PersistenceConfiguration cfg = new HibernatePersistenceConfiguration("emf")
            .jdbcUrl("jdbc:mysql://localhost:3306/restaurant_booking")
            .jdbcUsername("root")
            .jdbcPassword("root123")
            .property("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider")
            .property("hibernate.hikari.maximumPoolSize", "10")
            .property("hibernate.hikari.minimumIdle", "5")
            .property("hibernate.hikari.idleTimeout", "300000")
            .property("hibernate.hikari.connectionTimeout", "20000")
            .property("hibernate.hbm2ddl.auto", "update")
            .property("hibernate.show_sql", "true")
            .property("hibernate.format_sql", "true")
            .property("hibernate.highlight_sql", "true")
            .managedClasses(entities);

        try (EntityManagerFactory emf = cfg.createEntityManagerFactory()) {

            // Skapa initial data om den inte finns
            createInitialData(emf);

            // Starta huvudmeny
            BookingService bookingService = new BookingService(emf);
            mainMenu(bookingService, emf);

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
            System.out.println("✅ Initial data created!");
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

    private static List<Class<?>> getEntities(String pkg) {
        try (ScanResult scanResult = new ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages(pkg)
            .scan()) {
            return scanResult.getClassesWithAnnotation(Entity.class).loadClasses();
        }
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
                case "view all bookings", "rb", "3" -> viewAllBookings(bookingService);
                case "delete booking", "db", "4" -> deleteBookingMenu(bookingService);
                case "view tables", "5" -> viewTables(bookingService);
                case "view guests", "6" -> viewGuests(bookingService);
                case "exit", "7" -> {
                    System.out.println("👋 Goodbye!");
                    running = false;
                }
                default -> System.out.println("❌ Invalid option!");
            }
        }
    }

    private static void createBookingMenu(BookingService bookingService) {
        System.out.println("\n═══ CREATE NEW BOOKING ═══");

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

        // Datum
        String dateStr = IO.readln("\nEnter date (YYYY-MM-DD): ");
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);

        // Antal gäster
        int partySize = Integer.parseInt(IO.readln("\nEnter party size: "));

        // Visa tillgängliga gäster
        List<Guest> guests = bookingService.getAllGuests();
        System.out.println("\n👥 Available Guests:");
        guests.forEach(g -> System.out.println("  " + g.getId() + ". " + g.getName() + " (" + g.getContact() + ")"));

        List<Long> guestIds = new ArrayList<>();
        String addMore = "y";
        while (addMore.equalsIgnoreCase("y")) {
            Long guestId = Long.parseLong(IO.readln("\nEnter Guest ID: "));
            guestIds.add(guestId);
            addMore = IO.readln("Add another guest? (y/n): ");
        }

        try {
            bookingService.createBooking(tableId, timeSlotId, date, partySize, guestIds);
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private static void updateBookingMenu(BookingService bookingService) {
        System.out.println("\n═══ UPDATE BOOKING ═══");

        viewAllBookings(bookingService);

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
                System.out.println("❌ Error: " + e.getMessage());
            }
        } else {
            System.out.println("❌ Invalid status!");
        }
    }

    private static void viewAllBookings(BookingService bookingService) {
        System.out.println("\n═══ ALL BOOKINGS ═══");
        List<Booking> bookings = bookingService.getAllBookings();

        if (bookings.isEmpty()) {
            System.out.println("No bookings found.");
        } else {
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
    }

    private static void deleteBookingMenu(BookingService bookingService) {
        System.out.println("\n═══ DELETE BOOKING ═══");

        viewAllBookings(bookingService);

        Long bookingId = Long.parseLong(IO.readln("\nEnter Booking ID to delete: "));
        String confirm = IO.readln("Are you sure? (y/n): ");

        if (confirm.equalsIgnoreCase("y")) {
            try {
                bookingService.deleteBooking(bookingId);
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        } else {
            System.out.println("❌ Deletion cancelled.");
        }
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
