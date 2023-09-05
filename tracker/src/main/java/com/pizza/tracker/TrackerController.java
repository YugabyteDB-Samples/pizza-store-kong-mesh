package com.pizza.tracker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/tracker")
public class TrackerController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/order")
    public ResponseEntity<Object> getOrderStatus(@RequestParam("id") int id,
            @RequestParam(name = "location", required = false) String location) {
        List<Order> orders;

        long startTime = System.currentTimeMillis();

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

    @GetMapping("/orders")
    public ResponseEntity<Object> getAllOrdersStatus(
            @RequestParam(name = "location", required = false) String location) {

        long startTime = System.currentTimeMillis();

        List<Order> orders;

        if (Objects.nonNull(location))
            orders = jdbcTemplate.query(
                    "SELECT * FROM pizza_order WHERE location = ?::store_location",
                    (prepStmt) -> {
                        prepStmt.setString(1, location);
                    },
                    new OrderRowMapper());
        else
            orders = jdbcTemplate.query(
                    "SELECT * FROM pizza_order",
                    new OrderRowMapper());

        long execTime = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(generateResponse(execTime, orders));
    }

    private static Map<String, Object> generateResponse(long execTime, Object response) {
        Map<String, Object> result = new HashMap<>();

        result.put("result: ", response);
        result.put("db latency", String.format("%.3f", (float) execTime / 1000) + "s");

        return result;
    }
}
