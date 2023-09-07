# Deploying Kong Mesh and YugabyteDB Across Multiple Regions

This guide walks you through the process for running the application, Kong Mesh and YugabyteDB across several regions.

TBD - architecture diagram

Note, the guide is created for the Google Cloud Platform (GCP) and YugabyteDB Managed. If you prefer to run the app in a different cloud environment, 
then make sure to adjust the region/zone names in the sections below and in the `schema\pizza_store_geo_distributed.sql` file.

## Prerequisite

* [Google Cloud](http://console.cloud.google.com/) account.
* [YugabyteDB Managed](http://cloud.yugabyte.com) account.

## Start Geo-Partitioned YugabyteDB Cluster

Start a [geo-partitioned YugabyteDB Managed cluster](https://docs.yugabyte.com/preview/yugabyte-cloud/cloud-basics/create-clusters/create-clusters-geopartition/) in GCP in the following regions:
* `us-east4`
* `europe-west3`
* `australia-southeast1`

TBD - screenshot

Once the cluster is started check the zone names assigned by YugabyteDB Managed to the regions. If necessary, update the zone names for the `usa_ts`, `europe_ts`, and `australia_ts` tablespaces from the `schema\pizza_store_geo_distributed.sql` file. The region and zone names must be identical.

## Create Database Schema

Connect to your YugabyteDB Managed instance using [Cloud Shell](https://docs.yugabyte.com/preview/yugabyte-cloud/cloud-connect/connect-cloud-shell/) and load the schema from the `schema\pizza_store_geo_distributed.sql` file.

## Starting VMs

Start three virtual machines in GCP in the regions similar to the ones used by the YugabyteDB cluster:
    * `us-east4`
    * `europe-west3`
    * `australia-southeast1`

Do the following on each VM:
    1. Install JDK 17+ with Maven.
    2. Download and install the [kumactl](https://docs.konghq.com/mesh/2.4.x/production/install-kumactl/) tool:
        ```shell
        curl -L https://docs.konghq.com/mesh/installer.sh | VERSION=2.4.0 sh -

        cd kong-mesh-2.4.0/bin
        PATH=$(pwd):$PATH
        ```
    3. Add the `kong-mesh-2.4.0/bin` directory to the `.bashrc` or `.zshrc` file.

## Deploying Kong Global Control Plane

Next, deploy a [global control plane (CP)](https://docs.konghq.com/mesh/2.4.x/production/cp-deployment/multi-zone/):

1. Connect to the VM from `us-east4` and deploy the global CP:
    ```shell
    KUMA_MODE=global kuma-cp run
    ```

    