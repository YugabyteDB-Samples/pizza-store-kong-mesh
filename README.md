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

## Deploying Kong Mesh

Start a Kong Mesh instance in the [standalone deployment](https://docs.konghq.com/mesh/2.4.x/production/deployment/stand-alone/) mode:

1. Download and install the [kumactl](https://docs.konghq.com/mesh/2.4.x/production/install-kumactl/) tool:
    ```shell
    curl -L https://docs.konghq.com/mesh/installer.sh | VERSION=2.4.0 sh -

    cd kong-mesh-2.4.0/bin
    PATH=$(pwd):$PATH
    ```
2. Start a Kuma Control Plane (CP) in the standalone mode:
    ```shell
    kuma-cp run
    ```
3. Extract the admin credentials:
    ```shell
    export TOKEN=$(curl http://localhost:5681/global-secrets/admin-user-token | jq -r .data | base64 -d)
    ```
4. Register the Control Plane with the Kong Mesh:
    ```shell
    kumactl config control-planes add \
        --name pizza-store-control-plane \
        --address http://localhost:5681 \
        --auth-type=tokens \
        --auth-conf token=$TOKEN \
        --skip-verify
    ```
5. Open the Kong Mesh GUI to make sure CP and Mesh components are running:
    http://localhost:5681/gui/


## Starting Microservices and Dataplanes

First, create a folder for the dataplane tokens:
```shell
mkdir $HOME/kong-mesh
```

Next, start a kitchen service and its dataplane:

1. Navigate to the root directory of the kitchen microservice:
    ```shell
    cd kitchen

    mvn spring-boot:run
    ```

2. Generate a toke for the service:
    ```shell
    kumactl generate dataplane-token --tag kuma.io/service=kitchen-service --valid-for=720h > $HOME/kong-mesh/kuma-token-kitchen-service
    ```

3. Start a Dataplane (DP) for the service:
    ```shell
    kuma-dp run \
        --cp-address=https://localhost:5678 \
        --dataplane-file=standalone/kitchen-dp-config.yaml \
        --dataplane-token-file=$HOME/kong-mesh/kuma-token-kitchen-service
    ```

Finally, repeat the steps to start a tracker microservice with its dataplane:
1. Navigate to the root directory of the kitchen microservice:
    ```shell
    cd tracker

    mvn spring-boot:run
    ```

2. Generate a toke for the service:
    ```shell
    kumactl generate dataplane-token --tag kuma.io/service=tracker-service --valid-for=720h > $HOME/kong-mesh/kuma-token-tracker-service
    ```

3. Start a Dataplane (DP) for the service:
    ```shell
    kuma-dp run \
        --cp-address=https://localhost:5678 \
        --dataplane-file=standalone/tracker-dp-config.yaml \
        --dataplane-token-file=$HOME/kong-mesh/kuma-token-tracker-service
    ```


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
