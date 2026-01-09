package org.example;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.example.entity.*;

import java.time.LocalDateTime;

public class App {
    public static void main(String[] args) {
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("restaurantPU");
             EntityManager em = emf.createEntityManager()) {

            try {
                em.getTransaction().begin();

                RestaurantTable table2 = new RestaurantTable("T2", 5);
                em.persist(table2);

                Guest guest3 = new Guest("Martin Martinsson", "martin@hotmail.se", "0707939910", "Gillar äpplen");
                Booking booking = new Booking(
                    table2,
                    LocalDateTime.now().plusDays(1).withHour(18).withMinute(0),
                    4
                );
                booking.addGuest(guest3);

                booking.confirm();
                em.persist(booking);

                em.getTransaction().commit();

                System.out.println("Data created successfully!");
                System.out.println(table2);
                System.out.println(booking);
                System.out.println("Guests in booking: " + booking.getGuests().size());

            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw e;
            }

        } catch (Exception e) {
            System.err.println("Error:");
            e.printStackTrace();
        }
    }
}
