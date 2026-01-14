package org.example;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.example.service.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.example.service.BookingStatus.CANCELLED;
import static org.junit.Assert.assertTrue;

@Testcontainers
class AppTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.5.0")
        .withDatabaseName("restaurant_booking")
        .withUsername("root")
        .withPassword("root123");

    private static EntityManagerFactory emf;

    @BeforeAll
    static void wireDbProperties() {

        mysql.start();

        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", mysql.getJdbcUrl());
        properties.put("jakarta.persistence.jdbc.user", mysql.getUsername());
        properties.put("jakarta.persistence.jdbc.password", mysql.getPassword());
        properties.put("hibernate.hbm2ddl.auto", "create-drop");

        emf = Persistence.createEntityManagerFactory("restaurantPU", properties);
    }

    @BeforeEach
    void deleteDataFromDatabase() {

        emf.runInTransaction(em -> {
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE Guests").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE Tables").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE Bookings").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE booking_guests").executeUpdate();
            em.createNativeQuery("TRUNCATE TABLE TimeSlot").executeUpdate();
            em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        });
    }

    @Test
    @Order(0)
    void testConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(
            mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        assertThat(conn.isValid(1)).isTrue();
        assertThat(conn).isNotNull();
    }

    @Test
    @Order(1)
    public void containerRunning() {
        assertTrue(mysql.isRunning());
    }

    @Test
    @Order(2)
    void createNewGuests() {

        //Arrange
        Guest guest1 = new Guest("Johan", "bord för 4", "0756489992");
        Guest guest2 = new Guest("Anna", "allergisk mot nötter", "hej@gmail.com");

        Booking myBooking = new Booking();

        //Act
        myBooking.addGuest(guest1);
        myBooking.addGuest(guest2);

        //Assert
        assertThat(myBooking.getGuests())
            .as("Guests should correctly add new guests.")
            .hasSize(2)
            .containsExactlyInAnyOrder(guest1, guest2);
    }

    @Test
    @Order(3)
    void should_show_allGuests_fromDatabase() {

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        Guest guest1 = new Guest("Johan", "bord för 4", "0756489992");
        Guest guest2 = new Guest("Anna", "allergisk mot nötter", "hej@gmail.com");

        em.persist(guest1);
        em.persist(guest2);
        em.getTransaction().commit();
        em.close();

        BookingService myBookingService = new BookingService(emf);

        List<Guest> guests = myBookingService.getAllGuests();

        assertThat(guests).isNotEmpty();
        assertThat(guests).hasSize(2);
    }

    @Test
    @Order(4)
    void should_show_allTables_fromDatabase() {

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        Table table1 = new Table(4, "1");
        Table table2 = new Table(6, "2");

        em.persist(table1);
        em.persist(table2);
        em.getTransaction().commit();
        em.close();

        BookingService myBookingService = new BookingService(emf);

        List<Table> tables = myBookingService.getAllTables();

        assertThat(tables).isNotEmpty();
        assertThat(tables).hasSize(2);
    }

    @Test
    @Order(5)
    void should_show_allHours_fromDatabase() {

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        TimeSlot time1 = new TimeSlot("14:00", "16:00");
        TimeSlot time2 = new TimeSlot("15:00", "17:00");

        em.persist(time1);
        em.persist(time2);
        em.getTransaction().commit();
        em.close();

        BookingService myBookingService = new BookingService(emf);

        List<TimeSlot> times = myBookingService.getAllTimeSlots();

        assertThat(times).isNotEmpty();
        assertThat(times).hasSize(2);

    }

    @Test
    @Order(6)
    @DisplayName("Should create and save a booking in the database when all data is valid")
    void createBooking_thenCanSeeItInDataBase() {

        BookingService myBooking = new BookingService(emf);

        Long tableId = emf.callInTransaction(em -> {
            Table table = new Table(4, "1");
            em.persist(table);
            return table.getId();
        });

        Long timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });

        var guestId = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");

        int partySize = 4;

        LocalDate date = LocalDate.now().plusDays(1);

        myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guestId));

        var allBookings = myBooking.getAllBookings();

        assertThat(allBookings)
            .hasSize(1)
            .first()
            .satisfies(b -> {
                assertThat(b.getParty()).isEqualTo(partySize);
                assertThat(b.getDate()).isEqualTo(date);
                assertThat(b.getTable().getId()).isEqualTo(tableId);
                assertThat(b.getTimeSlot().getId()).isEqualTo(timeSlotId);
                assertThat(b.getGuests()).hasSize(1);
            });
    }

    @Test
    @Order(7)
    @DisplayName("Should throw illegalArgumentException when a company of 10 tries to book a table with capacity 2")
    void should_throwIllegalArgumentException_whenBookingASmallerTable() {

        BookingService myBooking = new BookingService(emf);
        var tableId = emf.callInTransaction(em -> {
            Table table = new Table(2, "1");
            em.persist(table);
            return table.getId();
        });
        var timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });
        var guestId = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");
        int partySize = 10;
        LocalDate date = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> {
            myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guestId));
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Party size (" + partySize + ") exceeds table capacity");

        var allBookings = myBooking.getAllBookings();
        assertThat(allBookings).isEmpty();

    }

    @Test
    @Order(8)
    @DisplayName("Should avoid that two guests book the same table at the same time")
    void should_throwIllegalArgumentException_whenTwoGuests_BookSameTable_AtTheSameTime() {

        BookingService myBooking = new BookingService(emf);
        var tableId = emf.callInTransaction(em -> {
            Table table = new Table(2, "1");
            em.persist(table);
            return table.getId();
        });
        var timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });
        var guestA = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");
        var guestB = myBooking.createGuest("Johan", "VIP", "0727617345");
        int partySize = 2;
        LocalDate date = LocalDate.now().plusDays(1);

        myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guestA));

        assertThatThrownBy(() -> {
            myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guestB));
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already booked");

        var allBookings = myBooking.getAllBookings();
        assertThat(allBookings).hasSize(1);
    }

    @Test
    @Order(9)
    @DisplayName("Should throw illegalArgumentException when a guest tries to book a table for a date before of today's date")
    void should_throwIllegalArgumentException_whenBookingATable_aDateBeforeToday(){

        BookingService myBooking = new BookingService(emf);
        var tableId = emf.callInTransaction(em -> {
            Table table = new Table(2, "1");
            em.persist(table);
            return table.getId();
        });
        var timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });
        var guest = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");
        int partySize = 2;
        LocalDate date = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> {
            myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guest));
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot book a date in the past");

        assertThat(myBooking.getAllBookings()).isEmpty();
    }


    @Test
    @Order(10)
    @DisplayName("Should throw illegalArgumentException when a guest tries to book a table more than three months in advance")
    void should_throwIllegalArgumentException_whenBookingATable_moreThanThreeMonthsInAdvance(){

        BookingService myBooking = new BookingService(emf);
        var tableId = emf.callInTransaction(em -> {
            Table table = new Table(2, "1");
            em.persist(table);
            return table.getId();
        });
        var timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });
        var guest = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");
        int partySize = 2;
        LocalDate date = LocalDate.now().plusMonths(3).plusDays(1);

        assertThatThrownBy(() -> {
            myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guest));
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot book more than 3 months in advance");

        assertThat(myBooking.getAllBookings()).isEmpty();

    }

    @Test
    @Order(11)
    void updateBookingStatus_thenCanSeeItInDataBase() {

        BookingService myBooking = new BookingService(emf);
        var tableId = emf.callInTransaction(em -> {
            Table table = new Table(2, "1");
            em.persist(table);
            return table.getId();
        });
        var timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });
        var guestA = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");
        int partySize = 2;
        LocalDate date = LocalDate.now().plusDays(1);
        BookingStatus newStatus = CANCELLED;

        myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guestA));
        var bookingId = myBooking.getAllBookings().get(0).getId();
        myBooking.updateBookingStatus(bookingId, newStatus);
        var bookingEnBaseDeDatos = myBooking.getAllBookings().get(0);

        assertThat(bookingEnBaseDeDatos.getStatus())
            .isEqualTo(CANCELLED);
    }

    @Test
    @Order(12)
    void deleteBooking(){

        BookingService myBooking = new BookingService(emf);
        var tableId = emf.callInTransaction(em -> {
            Table table = new Table(2, "1");
            em.persist(table);
            return table.getId();
        });
        var timeSlotId = emf.callInTransaction(em -> {
            TimeSlot time = new TimeSlot("16:00", "18:00");
            em.persist(time);
            return time.getId();
        });
        var guestA = myBooking.createGuest("Gabriela", "Allergi mot nötter", "0727658449");
        int partySize = 2;
        LocalDate date = LocalDate.now().plusDays(1);

        myBooking.createBooking(tableId, timeSlotId, date, partySize, List.of(guestA));
        var bookingId = myBooking.getAllBookings().get(0).getId();
        myBooking.deleteBooking(bookingId);

        assertThat(myBooking.getAllBookings()).isEmpty();
    }

    @Test
    @Order(13)
    void should_show_allBookings_fromDataBase(){

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        Table table = new Table(4, "1");
        TimeSlot time = new TimeSlot("18:00", "20:00");
        Guest guest = new Guest("Gabriela", "VIP", "0820573338");

        em.persist(table);
        em.persist(time);
        em.persist(guest);

        Booking booking1 = new Booking(LocalDate.now().plusDays(1), time ,2, table, List.of(guest));
        Booking booking2 = new Booking(LocalDate.now().plusDays(2), time,4, table, List.of(guest));
        Booking booking3 = new Booking(LocalDate.now().plusDays(3), time ,6, table, List.of(guest));

        em.persist(booking1);
        em.persist(booking2);
        em.persist(booking3);
        em.getTransaction().commit();
        em.close();

        BookingService myBooking = new BookingService(emf);

        assertThat(myBooking.getAllBookings()).hasSize(3);
    }
}


