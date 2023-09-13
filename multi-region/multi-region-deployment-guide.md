# Deploying Kong Mesh and YugabyteDB Across Multiple Regions

This guide walks you through the process for running the application, Kong Mesh and YugabyteDB across several regions.

![multi_region_mesh_arch](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/435a6ae7-82da-4727-9782-a8108ce16b57)

Note, the guide is created for the Google Cloud Platform (GCP) and YugabyteDB Managed. If you prefer to run the app in a different cloud environment, 
then adjust the region/zone names in the sections below and in the `schema\pizza_store_geo_distributed.sql` file.

## Prerequisite

* [Google Cloud](http://console.cloud.google.com/) account.
* [YugabyteDB Managed](http://cloud.yugabyte.com) account.

## Start Geo-Partitioned YugabyteDB Cluster

Start a [geo-partitioned YugabyteDB Managed cluster](https://docs.yugabyte.com/preview/yugabyte-cloud/cloud-basics/create-clusters/create-clusters-geopartition/) in GCP in the following regions:

* `us-east4`
* `europe-west3`
* `australia-southeast1`


![cluster_create](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/23318ae0-36c2-408e-96bc-05320d98cd85)

Once the cluster is started, check the zone names assigned by YugabyteDB Managed to the regions. If necessary, update the zone names for the `usa_ts`, `europe_ts`, and `australia_ts` tablespaces from the `schema\pizza_store_geo_distributed.sql` file. The region and zone names must be identical.

## Create Database Schema

Connect to your YugabyteDB Managed instance using [Cloud Shell](https://docs.yugabyte.com/preview/yugabyte-cloud/cloud-connect/connect-cloud-shell/) and load the schema from the `schema\pizza_store_geo_distributed.sql` file.

## Starting VMs

Start three virtual machines in GCP in the regions similar to the ones used by the YugabyteDB cluster:
* `us-east4`
* `europe-west3`
* `australia-southeast1`

Update the network firewall settings byt allowing access to the following ports from any machine (`0.0.0.0`) for the `HTTP` protocol:
* `8080,5681,5685,6681`

Then repeat the following for every VM:

1. Install JDK 17+ with Maven.
2. Download and install the [kumactl](https://docs.konghq.com/mesh/2.4.x/production/install-kumactl/) tool:
    ```shell
    curl -L https://docs.konghq.com/mesh/installer.sh | VERSION=2.4.0 sh -

    cd kong-mesh-2.4.0/bin
    PATH=$(pwd):$PATH
    ```
3. Add the `kong-mesh-2.4.0/bin` directory to the `.bashrc` or `.zshrc` file.

## Init Application

Prepare application and Kong Mesh on each VM for the deployment.

Repeat the following for each VM:

1. Connect to the VM
2. Clone the project:
    ```shell
    git clone https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh.git
    ```
3. Go to the `multi-region` directory of the project:
    ```shell
    cd pizza-store-kong-mesh/multi-region
    ```
4. Run the `init.sh` script:
    ```shell
    ./init.sh
    ```
5. Export the following environment variables:
    ```shell
    export GLOBAL_CP_IP_ADDRESS={global_cp}
    export DB_URL="{db_url}"
    export DB_USER={db_user}
    export DB_PASSWORD={db_password}
    ```
    where
    * `GLOBAL_CP_IP_ADDRESS` - public or private IP address of the VM running the global CP instance.
    * `DB_URL` - the database connection URL in the `jdbc:postgresql://{HOSTNAME}:5433/yugabyte` format. The `HOSTNAME` needs to refer to a YugabyteDB Managed endpoint that is closest to the VM. For instance, the Kong Mesh and app of the `us-east4` VM have to connect to the database node from the `us-east4` region.
    * `DB_USER` - a user name to connect with.
    * `DB_PASSWORD` - the password.

6. Remain in the `multi-region` directory.

## Deploying Kong Global Control Plane

Next, deploy a [global control plane (CP)](https://docs.konghq.com/mesh/2.4.x/production/cp-deployment/multi-zone/):

1. Make sure you're in the `pizza-store-kong-mesh/multi-region` of the `us-east4` VM.
    
2. Provide a path to a Kong Mesh license. If you skip this step, then start control and data planes for two regions out of three (i.e. `us-east4` and `europe-west3`):
    ```shell
    export KMESH_LICENSE_PATH={path_to_the_license_file}
    ```
3. Deploy the global CP:
    ```shell
    ./start-global-cp.sh 
    ```
4. Use the logs to check that the CP is running:
    ```shell
    tail -f logs/global-cp.log
    ```
5. Open the Kong Mesh GUI to confirm the status of the global CP:
    http://{VM_PUBLIC_IP_ADDRESS}:6681/gui
    
    ![global-cp](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/604cb292-701d-409e-a3cc-56ed0a9ec9eb)

Configure required resources needed for mesh gateways via the global CP:

1. Register global CP to `kumactl`:
    ```shell
    kumactl config control-planes add --address http://localhost:6681 --name global-cp
    ```
2. Register gateway routes:
    ```shell
    kumactl apply -f ../standalone/mesh-gateway-config.yaml
    kumactl apply -f ../standalone/mesh-gateway-route-config.yaml
    ```
3. Configure mesh timeouts:
    ```shell
    kumactl apply -f ../standalone/mesh-timeout-config.yaml
    ```
4. Switch `kumactl` back to the local control plane:
    ```shell
    kumactl config control-planes switch --name local
    ```

## Deploying Zone Control Plane

Now deploy a dedicated control plane in each region. Make sure you're executing the commands below from the `pizza-store-kong-mesh/multi-region` directory.

1. Start a CP on the `us-east4` VM:
    ```shell
    ./start-zone-cp.sh \
        -z us-east4
    ```

2. Start a CP on the `europe-west3` VM:
    ```shell
    ./start-zone-cp.sh \
        -z europe-west3
    ```

3. Start a CP on the `australia-southeast1` VM:
    ```shell
    ./start-zone-cp.sh \
        -z australia-southeast1
    ```
4. Make sure the zone CPs are started and registered with the global CP:
    http://{GLOBAL_CP_PUBLIC_IP_ADDRESS}:6681/gui/zones/zone-cps
    
    ![zone-cps](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/7af47c90-5e50-4cde-afd7-893cc391d35d)

    
If the zones fail to start or register, check the logs in the `pizza-store-kong-mesh/multi-region/logs` directory.

Note, presently, the application doesn't have a capability requiring cross-zone communication between data planes. You need to configure [zone ingress and egress](https://docs.konghq.com/mesh/2.4.x/production/cp-deployment/multi-zone/#set-up-the-zone-control-planes) if this is necessary for your application.

## Starting Apps and Data Planes

Now, let's start microservice instances and data planes in each region. 

Repeat this on every VM:

1. Make sure you're in the the `pizza-store-kong-mesh/multi-region` directory.

2. Start the apps and data planes:
    ```shell
    ./start-apps-and-dps.sh
    ```
3. Use global CP GUI to confirm that DPs and apps have been started successfully:
    http://{GLOBAL_CP_PUBLIC_IP_ADDRESS}:6681/gui/mesh/default/data-planes
    http://{GLOBAL_CP_PUBLIC_IP_ADDRESS}:6681/gui/mesh/default/services
    
    ![zone-data-planes](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/556914a0-d54a-4291-8e5a-89db473367ae)


Check the logs in the `pizza-store-kong-mesh/multi-region/logs` directory if services or DPs fail to start or register.

## Starting Gateways

Next, configure a gateway in each region.

Repeat below on every VM:

1. Make sure you're in the the `pizza-store-kong-mesh/multi-region` directory.

2. Start the gateway:
    ```shell
    ./start-gateway.sh
    ```
3. Use global CP GUI to confirm that the gateways have been started:
    http://{GLOBAL_CP_PUBLIC_IP_ADDRESS}:6681/gui/mesh/default/gateways
    
    ![gateways](https://github.com/YugabyteDB-Samples/pizza-store-kong-mesh/assets/1537233/64ac2a84-3e06-4a90-b73b-5c1767f9e582)


Check the `pizza-store-kong-mesh/multi-region/logs/gateway.log` log if there is an issue starting the gateway.

## Sending Requests Via Gateway

Now you can use the [HTTPie tool](https://httpie.io) to send REST requests via the gateway DP of the Kong Mesh.

Requests to the Kitchen microservice:

* Put new pizza orders in:
    ```shell
    http POST :8080/kitchen/order id=={ID} location=={LOCATION}
    ```
    where:
    * `ID` - an order integer id.
    * `LOCATION` - one of the following - `NewYork`, `Berlin` and `Sydney`

* Update order status:
    ```shell
    http PUT :8080/kitchen/order id=={ID} status=={STATUS} [location=={LOCATION}]
    ```
    where:
    * `ID` - an order id.
    * `STATUS` - one of the following - `Ordered`, `Baking`, `Delivering` and `YummyInMyTummy`.
    * `LOCATION`(optional) - used for geo-partitioned deployments to avoid global transactions. Accepts one of the following - `NewYork`, `Berlin`, and `Sydney`.
    
* Delete all orders:
    ```shell
    http DELETE :8080/kitchen/orders
    ```

Requests to the Tracker microservice via the tracker DP listening on port `5082`:

* Get an order status:
    ```shell
    http GET :8080/tracker/order id=={ID} [location=={LOCATION}]
    ```
    * `ID` - an order id.
    * `LOCATION`(optional) - used for geo-partitioned deployments to avoid global transactions. Accepts one of the following - `NewYork`, `Berlin`, and `Sydney`.
* Get all orders status:
    ```shell
    http GET localhost:8080/tracker/orders [location=={LOCATION}]
    ```
    * `LOCATION`(optional) - used for geo-partitioned deployments to avoid global transactions. Accepts one of the following - `NewYork`, `Berlin`, and `Sydney`.

## Termination

To stop the Kong Mesh with all the application processes, do the following on every VM:
```shell
pkill -9 -f kuma
pkill -9 -f java
```
