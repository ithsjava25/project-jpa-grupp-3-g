package org.example.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "restaurant_tables")

public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "table_id")
    private RestaurantTable restaurantTable;

   @OneToMany(mappedBy = "table", cascade = CascadeType.PERSIST)
   private List<Booking> bookings = new ArrayList<>();


    @Column(name="table_number", nullable = false, unique = true)
    private int tableNumber;

    @Column(name="capacity", nullable = false)
    private int capacity;

    public RestaurantTable getTable() {
        return restaurantTable;
    }

    public void setTable(RestaurantTable restaurantTable) {
        this.restaurantTable = restaurantTable;
    }

    public RestaurantTable(int capacity, int tableNumber){
        this.capacity = capacity;
        this.tableNumber = tableNumber;
    }

    public RestaurantTable() {}

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

}
