# Pizza Store With Kong Mesh and YugabyteDB

This project provides a functional skeleton for a pizza store implemented using Kong Mesh and YugabyteDB.

The project comes with two microservices that support various REST API endpoints for client requests:
1. The Kitchen service (see the `kitchen` directory) - allows customers to order pizza.
2. The Tracker service (see the `tracker` directory) - lets customers check their order status.

![pizza_store_spring_cloud_v2](https://github.com/YugabyteDB-Samples/pizza-store-spring-cloud/assets/1537233/c725688d-6b58-49ca-861d-8048cdbdd0b2)

Both microservices register with the [Spring Discovery Service](https://spring.io/projects/spring-cloud-netflix)(aka. Spring Cloud Netflix). This allows all the registered services to connect and communicate with each other directly using only their names.

The client interacts with the microservices via [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway). Following the provided routes configuration, the gateway resolves user requests and forwards them to respective services. The gateway also registers with the Discovery Service to benefit from the automatic discovery of the registered Kitchen and Tracker services.

YugabyteDB is a database that can scale horizontally, withstand various outages, and pin pizza orders to required locations. The application supports stretched and geo-partitioned YugabyteDB clusters.

## Starting YugabyteDB

You can use a YugabyteDB deployment option that works best for you. 

Configure the following environment variables that are used in the `docker-compose.yaml` during the start of microservice instances:
* `DB_URL` - the database connection URL in the `jdbc:postgresql://{HOSTNAME}:5433/yugabyte` format.
* `DB_USER` - a user name to connect with.
* `DB_PASSWORD` - the password.

If you run a YugabyteDB instance on a local machine and the instance is accessible via `localhost`, then you don't need to configure the settings above.

### Creating Standard Schema

Use contents of the `schema/pizza_store.sql` script to create tables and other database objects used by the application.

### Creating Geo-Partitioned Schema

If you'd like to use a geo-partitioned YugabyteDB cluster, then the pizza store can pin orders to locations across the United States, Europe, and Australia. Presently, the app supports the following locations - `NewYork`, `Berlin` and `Sydney`.

You can start a [geo-partitioned cluster using YugabyteDB Managed](https://docs.yugabyte.com/preview/yugabyte-cloud/cloud-basics/create-clusters/create-clusters-geopartition/). The geo-partitioned schema (see `schema/pizza_store_geo_distributed.sql`) is pre-configured for Google Cloud Platform (`gcp`) and the following regions - `us-east4`, `europe-west3` and `australia-southeast1`. You either need to start a YugabyteDB Managed instance with the same configuration or adjust the application schema file with your cloud provider and regions.

Alternatively, you can start the cluster locally using the [yugabyted](https://docs.yugabyte.com/preview/reference/configuration/yugabyted/) tool:
```shell
mkdir $HOME/yugabyte

# macOS only (add IPs to the loopback) ----
sudo ifconfig lo0 alias 127.0.0.2
sudo ifconfig lo0 alias 127.0.0.3
# macOS only ----

./yugabyted start --advertise_address=127.0.0.1 --base_dir=$HOME/yugabyte/node1 \
    --cloud_location=gcp.us-east4.us-east4-a \
    --fault_tolerance=region

./yugabyted start --advertise_address=127.0.0.2 --join=127.0.0.1 --base_dir=$HOME/yugabyte/node2 \
    --cloud_location=gcp.europe-west3.europe-west3-a \
    --fault_tolerance=region
    
./yugabyted start --advertise_address=127.0.0.3 --join=127.0.0.1 --base_dir=$HOME/yugabyte/node3 \
    --cloud_location=gcp.australia-southeast1.australia-southeast1-a \
    --fault_tolerance=region

./yugabyted configure data_placement --fault_tolerance=region --base_dir=$HOME/yugabyte/node1
```

Once the cluster is ready, use the contents of the `schema/pizza_store_geo_distributed.sql` script to create tables and other database objects the application uses.

## Starting Application

The application conveniently starts in containers using Docker Compose.

1. Navigate to the root directory of the project and start the app:
    ```shell
    docker compose up --build
    ```
    Note, the `--build` command is needed only during the first start or whenever you update the application source code.

2. Confirm there are no errors in the logs and open the Discovery Service dashboard (`localhost:8761`) to make sure all the services have been registered:
    ![spring_discovery_service](https://github.com/YugabyteDB-Samples/pizza-store-spring-cloud/assets/1537233/7d3bd049-07bb-496c-bc2a-82ac3cca71e3)

## Sending Requests Via Cloud Gateway

Now you can use the [HTTPie tool](https://httpie.io) to send REST requests via the running Spring Cloud Gateway Instance. The gateway routes are configured in the `api-gateway/.../ApiGatewayApplication.java` file. 

Requests to the Kitchen microservice:
* Put new pizza orders in:
    ```shell
    http POST localhost:8080/kitchen/order id=={ID} location=={LOCATION}
    ```
    where:
    * `ID` - an order integer id.
    * `LOCATION` - one of the following - `NewYork`, `Berlin` and `Sydney`

* Update order status:
    ```shell
    http PUT localhost:8080/kitchen/order id=={ID} status=={STATUS} [location=={LOCATION}]
    ```
    where:
    * `ID` - an order id.
    * `STATUS` - one of the following - `Ordered`, `Baking`, `Delivering` and `YummyInMyTummy`.
    * `LOCATION`(optional) - used for geo-partitioned deployments to avoid global transactions. Accepts one of the following - `NewYork`, `Berlin`, and `Sydney`.
    
* Delete all orders:
    ```shell
    http DELETE localhost:8080/kitchen/orders
    ```

Requests to the Tracker microservice:
* Get an order status:
    ```shell
    http GET localhost:8080/tracker/order id=={ID} [location=={LOCATION}]
    ```
    * `ID` - an order id.
    * `LOCATION`(optional) - used for geo-partitioned deployments to avoid global transactions. Accepts one of the following - `NewYork`, `Berlin`, and `Sydney`.
* Get all orders status:
    ```shell
    http GET localhost:8080/tracker/orders [location=={LOCATION}]
    ```
    * `LOCATION`(optional) - used for geo-partitioned deployments to avoid global transactions. Accepts one of the following - `NewYork`, `Berlin`, and `Sydney`.
