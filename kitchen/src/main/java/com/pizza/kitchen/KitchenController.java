package com.pizza.kitchen;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kitchen")
public class KitchenController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/order")
    public ResponseEntity<Object> addNewOrder(
            @RequestParam("id") int id,
            @RequestParam("location") String location) {

        long startTime = System.currentTimeMillis();

        Timestamp orderTime = Timestamp.from(Instant.now());

        jdbcTemplate.update("INSERT INTO pizza_order values(?, 'Ordered', ?, ?)",
                (prepStmt) -> {
                    prepStmt.setObject(1, id);
                    prepStmt.setString(2, location);
                    prepStmt.setTimestamp(3, Timestamp.from(Instant.now()));
                });

        long execTime = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(generateResponse(execTime, new Order(id, "Ordered", location, orderTime)));
    }

    @PutMapping("/order")
    public ResponseEntity<Object> updateOrder(
            @RequestParam("id") int id,
            @RequestParam("status") String status,
            @RequestParam(name = "location", required = false) String location) {

        long startTime = System.currentTimeMillis();

        int updatedRows = 0;

        if (Objects.nonNull(location))
            updatedRows = jdbcTemplate.update(
                    "UPDATE pizza_order SET status = ? WHERE id = ? and location = ?::store_location",
                    (prepStmt) -> {
                        prepStmt.setString(1, status);
                        prepStmt.setObject(2, id);
                        prepStmt.setObject(3, location);
                    });
        else
            updatedRows = jdbcTemplate.update("UPDATE pizza_order SET status = ? WHERE id = ?",
                    (prepStmt) -> {
                        prepStmt.setString(1, status);
                        prepStmt.setObject(2, id);
                    });

        long execTime = System.currentTimeMillis() - startTime;

        return updatedRows == 0
                ? new ResponseEntity<>(generateResponse(execTime, "Order is not found"), HttpStatus.NOT_FOUND)
                : ResponseEntity.ok(generateResponse(execTime, new OrderStatus(id, status)));
    }

    @GetMapping("/order")
    public ResponseEntity<Object> getOrder(
            @RequestParam("id") int id,
            @RequestParam(name = "location", required = false) String location) {

        long startTime = System.currentTimeMillis();

        List<Order> orders;

        if (Objects.nonNull(location))
            orders = jdbcTemplate.query(
                    "SELECT * FROM pizza_order WHERE id = ? and location = ?::store_location",
                    (prepStmt) -> {
                        prepStmt.setInt(1, id);
                        prepStmt.setString(2, location);
                    },
                    new OrderRowMapper());
        else
            orders = jdbcTemplate.query(
                    "SELECT * FROM pizza_order WHERE id = ?",
                    (prepStmt) -> {
                        prepStmt.setInt(1, id);
                    },
                    new OrderRowMapper());

        long execTime = System.currentTimeMillis() - startTime;

        return orders.size() == 0
                ? new ResponseEntity<>(generateResponse(execTime, "Order is not found"), HttpStatus.NOT_FOUND)
                : ResponseEntity.ok(generateResponse(execTime, orders.get(0)));
    }

    @DeleteMapping("/orders")
    public ResponseEntity<Object> deleteOrders() {
        long startTime = System.currentTimeMillis();

        jdbcTemplate.execute("TRUNCATE pizza_order");

        long execTime = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(generateResponse(execTime, "Orders have been deleted"));
    }

    private static Map<String, Object> generateResponse(long execTime, Object response) {
        Map<String, Object> result = new HashMap<>();

        result.put("result: ", response);
        result.put("db latency", String.format("%.3f", (float) execTime / 1000) + "s");

        return result;
    }
}
