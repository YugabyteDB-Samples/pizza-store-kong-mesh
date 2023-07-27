CREATE TYPE order_status AS ENUM(
    'Ordered',
    'Baking',
    'Delivering',
    'YummyInMyTummy'
);

CREATE TYPE store_location AS ENUM(
    'NewYork',
    'Berlin',
    'Sydney'
);

CREATE CAST (varchar AS order_status) WITH INOUT AS IMPLICIT;

CREATE CAST (varchar AS store_location) WITH INOUT AS IMPLICIT;

CREATE TABLE pizza_order(
    id int PRIMARY KEY,
    status order_status NOT NULL,
    location store_location NOT NULL,
    order_time timestamp NOT NULL DEFAULT now()
);

