package org.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="bookings")

public class Booking {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="booking_Time", nullable = false)
    private LocalDateTime time;

    @Column(name="party_size", nullable = false)
    private int party;

    @ManyToOne
    @JoinColumn(name="table_id", nullable = false)
    private RestaurantTable table;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "booking_guests",
        joinColumns = @JoinColumn(name = "booking_id"),
        inverseJoinColumns = @JoinColumn (name = "guest_id"))
    private List<Guest> guests = new ArrayList<>();

    public Booking(RestaurantTable table, LocalDateTime time, int party){
        this.table = table;
        this.time = time;
        this.party = party;
    }

    public void addGuest(Guest guest){
        guests.add(guest);
        guest.getBookings().add(this);
    }

    public void removeGuest(Guest guest){
        guests.remove(guest);
        guest.getBookings().remove(this);
    }

    public Booking() {}

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public int getParty() {
        return party;
    }

    public void setParty(int party) {
        this.party = party;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public void setTable(RestaurantTable table) {
        this.table = table;
    }

    public List<Guest> getGuests() {
        return guests;
    }

    public void setGuests(List<Guest> guests) {
        this.guests = guests;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString(){
        return "Booking id = " + id + ", time = " + time + ", party = " + party + ", table = " + (table != null ? table.getTableNumber() : "N/A") + ".";
    }

}
