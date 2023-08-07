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

CREATE TABLESPACE usa_ts WITH (
    replica_placement = '{"num_replicas": 1, "placement_blocks":
  [{"cloud":"gcp","region":"us-east4","zone":"us-east4-a","min_num_replicas":1}]}'
);

CREATE TABLESPACE europe_ts WITH (
    replica_placement = '{"num_replicas": 1, "placement_blocks":
  [{"cloud":"gcp","region":"europe-west3","zone":"europe-west3-a","min_num_replicas":1}]}'
);

CREATE TABLESPACE australia_ts WITH (
    replica_placement = '{"num_replicas": 1, "placement_blocks":
  [{"cloud":"gcp","region":"australia-southeast1","zone":"australia-southeast1-a","min_num_replicas":1}]}'
);

CREATE TABLE pizza_order(
    id int,
    status order_status NOT NULL,
    location store_location NOT NULL,
    order_time timestamp NOT NULL DEFAULT now()
)
PARTITION BY LIST (location);

CREATE TABLE pizza_order_usa PARTITION OF pizza_order(id, status, location, order_time, PRIMARY KEY (id, location))
FOR VALUES IN ('NewYork') TABLESPACE usa_ts;

CREATE TABLE pizza_order_europe PARTITION OF pizza_order(id, status, location, order_time, PRIMARY KEY (id, location))
FOR VALUES IN ('Berlin') TABLESPACE europe_ts;

CREATE TABLE pizza_order_australia PARTITION OF pizza_order(id, status, location, order_time, PRIMARY KEY (id, location))
FOR VALUES IN ('Sydney') TABLESPACE australia_ts;

