# Pizza Store With Kong Mesh and YugabyteDB

This project provides a functional skeleton for a pizza store implemented using Kong Mesh and YugabyteDB.

The project comes with two microservices that support various REST API endpoints for client requests:
1. The Kitchen service (see the `kitchen` directory) - allows customers to order pizza.
2. The Tracker service (see the `tracker` directory) - lets customers check their order status.

![standalone_mesh_arch](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/14e35556-095e-4c9d-8156-84c46087eea8)


There is a Kuma data plane (KUMA-DP) running alongside the kitchen and tracker microservices. The DPs are used for the service-to-service communication. 

The Kuma control plane (KUMA-CP) is used to configure and manage the mesh components including the DPs of the kitchen and tracker services.

The users interact with the microservices via the mesh gateway instance that is deployed as a standalone DP of the Kong Mesh. Following the provided routes configuration, the gateway resolves user requests and forwards them to respective services.

YugabyteDB is a database that can scale horizontally, withstand various outages, and pin pizza orders to required locations. The application supports stretched and geo-partitioned YugabyteDB clusters.

## Starting YugabyteDB

You can use a YugabyteDB deployment option that works best for you. 

Configure the following environment variables that are checked by the kitchen and tracker during the start of microservice instances (see the `application.properties` files for details):
* `DB_URL` - the database connection URL in the `jdbc:postgresql://{HOSTNAME}:5433/yugabyte` format.
* `DB_USER` - a user name to connect with.
* `DB_PASSWORD` - the password.

Note, if you run a YugabyteDB instance on a local machine and the instance is accessible via `localhost`, then you don't need to configure the settings above.

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

## Deploying Kong Mesh Control Plane

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
4. Register the Control Plane with the mesh:
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
   
![control-plane](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/919ce28f-20ab-44a6-b721-810792c5d1b8)


## Starting Microservices and Data Planes

First, create a folder for the data plane tokens:
```shell
mkdir $HOME/kong-mesh
```

Also, unless you run a YugabyteDB instance and Kong Mesh on the same machine, you need to define the following environment variables that are used by the microservices:
* `DB_URL` - the database connection URL in the `jdbc:postgresql://{HOSTNAME}:5433/yugabyte` format.
* `DB_USER` - a user name to connect with.
* `DB_PASSWORD` - the password.

Next, start a kitchen service and its data plane (DP):

1. Navigate to the root directory of the kitchen microservice:
    ```shell
    cd kitchen

    mvn spring-boot:run
    ```

2. Generate a token for the service:
    ```shell
    kumactl generate dataplane-token --tag kuma.io/service=kitchen-service --valid-for=720h > $HOME/kong-mesh/kuma-token-kitchen-service
    ```

3. Start a DP instance for the service:
    ```shell
    kuma-dp run \
        --cp-address=https://localhost:5678 \
        --dataplane-file=standalone/kitchen-dp-config.yaml \
        --dataplane-token-file=$HOME/kong-mesh/kuma-token-kitchen-service
    ```

Finally, repeat the steps to start a tracker microservice with its data plane:
1. Navigate to the root directory of the kitchen microservice:
    ```shell
    cd tracker

    mvn spring-boot:run
    ```

2. Generate a token for the service:
    ```shell
    kumactl generate dataplane-token --tag kuma.io/service=tracker-service --valid-for=720h > $HOME/kong-mesh/kuma-token-tracker-service
    ```

3. Start a DP instance for the service:
    ```shell
    kuma-dp run \
        --cp-address=https://localhost:5678 \
        --dataplane-file=standalone/tracker-dp-config.yaml \
        --dataplane-token-file=$HOME/kong-mesh/kuma-token-tracker-service
    ```

Go to the Kong Mesh GUI to confirm the data planes and respective services are running normally:
http://localhost:5681/gui/mesh/default/data-planes

![data-planes](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/90a8cbd1-7574-4253-9732-01566c2b6a17)

## Configuring Mesh Gateway

The data planes that are deployed alongsied the kitchen and tracker microservices should be use for the service-to-service communication 
within the mesh network. The external user traffic should be forward via a mesh gateway instance that can function as a standalone data plane in the mesh.

1. Start a built-in gateway instance:
    ```shell
    kumactl generate dataplane-token --tag kuma.io/service=mesh-gateway --valid-for=720h > $HOME/kong-mesh/kuma-token-mesh-gateway

    kuma-dp run \
        --cp-address=https://localhost:5678/ \
        --dns-enabled=false \
        --dataplane-token-file=$HOME/kong-mesh/kuma-token-mesh-gateway \
        --dataplane-file=standalone/mesh-gateway-dp-config.yaml
    ```
2. Configure the gateway and its routes:
    ```shell
    kumactl apply -f standalone/mesh-gateway-config.yaml
    kumactl apply -f standalone/mesh-gateway-route-config.yaml
    ```
3. Confirm the gateway is configured properly via the Kong Mesh GUI:
    http://localhost:5681/gui/mesh/default/gateways

TBD picture

## Sending Requests Via Gateway

Now you can use the [HTTPie tool](https://httpie.io) to send REST requests via the gateway DP of the Kong Mesh.

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

Requests to the Tracker microservice via the tracker DP listening on port `5082`:

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
