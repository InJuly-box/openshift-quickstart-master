# Workshop: Knative Eventing on OpenShift with Quarkus 

## 1. Prerequisites

### 1.1. JDK11+

```shell
$ java --version
java 16.0.2 2021-07-20
Java(TM) SE Runtime Environment (build 16.0.2+7-67)
Java HotSpot(TM) 64-Bit Server VM (build 16.0.2+7-67, mixed mode, sharing)
```

### 1.2. Maven 3.5+

```shell
$ mvn -version
Apache Maven 3.6.3 (cecedd343002696d0abb50b32b541b8a6ba2883f)
Maven home: /Users/pminkows/apache-maven-3.6.3
Java version: 16.0.2, vendor: Oracle Corporation, runtime: /Library/Java/JavaVirtualMachines/jdk-16.0.2.jdk/Contents/Home
Default locale: en_PL, platform encoding: UTF-8
```

### 1.3. CLI `oc` client 4.0+

```shell
$ oc version
Client Version: 4.6.12
```

### 1.4. CLI `odo` client 2.0+

Tool for deploying app directly from the current version of the code. If you are familiar with other tools then `odo` you may use it instead.
```shell
$ odo version  
odo v2.2.1 (17a078b67)
```
### 1.5. CLI `kn`

```shell
$ kn version
Version:      v0.20.0
Build Date:   2021-01-14 17:12:42
Git Revision: 8ca5db3a
Supported APIs:
* Serving
  - serving.knative.dev/v1 (knative-serving v0.20.0)
* Eventing
  - sources.knative.dev/v1alpha2 (knative-eventing v0.20.0)
  - eventing.knative.dev/v1beta1 (knative-eventing v0.20.0)
```

### 1.6. IDE for Java Development
The presenter will use IntelliJ IDEA.

### 1.7. Git client

```shell
git --version
git version 2.24.3 (Apple Git-128)
```

## 2. Before start

Clone the following repository from GitHub:
```shell
git clone https://github.com/piomin/sample-quarkus-serverless-kafka.git
```
Go to the `serverless` directory. There three applications there: `order-saga`, `payment-saga`, and `shipment-saga`:
```shell
cd serverless
```

## 3. Kafka support

First, go to the `order-saga` directory. \ 
Add the following dependency into Maven `pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
</dependency>
```
Then, open the `pl.redhat.samples.serverless.order.service.OrderPublisher` class. Add the following implementation for sending messages to the Kafka topic:
```java
@ApplicationScoped
public class OrderPublisher {

    private final Random random = new Random();

    @Outgoing("order-events")
    public Multi<Order> publishOrder() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .map(tick -> {
                    int r = random.nextInt(1000);
                    return new Order(r, r%10+1, r%10+1, 5, 100, "NEW");
                });
    }
}
```
Go the `application.properties` file, and the address of your Kafka cluster:
```properties
kafka.bootstrap.servers = <your-kafka-cluster>
```
Then add the SASL credentials:
```properties
mp.messaging.connector.smallrye-kafka.security.protocol = SASL_SSL
mp.messaging.connector.smallrye-kafka.sasl.mechanism = PLAIN
mp.messaging.connector.smallrye-kafka.sasl.jaas.config = org.apache.kafka.common.security.plain.PlainLoginModule required username="<your-kafka-username>" password="<your-kafka-password>";
```
Add the mapping for Kafka topic name:
```properties
mp.messaging.outgoing.order-events.connector = smallrye-kafka
mp.messaging.outgoing.order-events.topic = <your-topic-name>
mp.messaging.outgoing.order-events.value.serializer = io.quarkus.kafka.client.serialization.ObjectMapperSerializer
```
Run the `order-saga` locally using the following Maven command:
```shell
mvn quarkus:dev
```

Go to the `payment-saga` directory. Add the same dependency `quarkus-smallrye-reactive-messaging-kafka` to the Maven `pom.xml`.
Create `OrderDeserializer` class in the `pl.redhat.samples.serverless.payment.domain.deserialize` package:
```java
public class OrderDeserializer extends ObjectMapperDeserializer<Order> {

    public OrderDeserializer() {
        super(Order.class);
    }

}
```
Go to the `pl.redhat.samples.serverless.payment.service.OderConsumer` and add the following implementation:
```java
@ApplicationScoped
public class OrderConsumer {

    @Inject
    Logger log;

    @Incoming("order-events")
    public void consumeOrder(Order order) {
        log.infof("Received: %s", order);
    }
}
```
Add the same address of the Kafka broker and credentials as before. Then add mapping for the Kafka topic:
```properties
mp.messaging.incoming.order-events.connector = smallrye-kafka
mp.messaging.incoming.order-events.topic = <your-topic-name>
mp.messaging.incoming.order-events.value.deserializer = pl.redhat.samples.serverless.payment.domain.deserialize.OrderDeserializer
```
Run the `payment-saga` application using the `mvn quarkus:dev` command.

## 4. Quarkus Functions and HTTP

Add the following dependency into Maven `pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-funqy-http</artifactId>
</dependency>
```
Go to the `pl.redhat.samples.serverless.payment.function.OrderReserveFunction`, and add the implementation:
```java
public class OrderReserveFunction {

    @Inject
    Logger log;

    @Funq
    public void reserve(Order order) {
        log.infof("Received order: %s", order);
    }
}    
```
Send the HTTP request to the Funqy endpoint:
```shell
curl http://localhost:8080/reserve -d "{\"customerId\":1,\"productId\":1,\"productCount\":3,\"amount\":1000}" -H "application/json"
```

## 5. Deploy Applications on OpenShift Serverless (Knative)

These actions need to be performed for our both applications. \
Add the following dependency into Maven `pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-openshift</artifactId>
</dependency>
```
Add the following properties into the `application.properties` file:
```properties
quarkus.kubernetes.deploy = true
quarkus.kubernetes.deployment-target = knative
quarkus.container-image.group = <your-openshift-namespace>
quarkus.container-image.registry = image-registry.openshift-image-registry.svc:5000
```
Before running the build command ensure you have deleted the class `OrderConsumer`. \
Just run the following Maven command to build and deploy the applications:
```shell
mvn clean package
```
After build is finished go to the `target/kubernetes/` directory. Open the file `openshift.yml` and `knative.yml`.

With Knative CLI display a list of services:
```shell
kn services list
```
Then, go to OpenShift Console. Expand the `Serverless` tab. Choose `Serving`. Click `order-saga' -> `Edit Service`. \
In the YAML manifest configure minimal number of running instances to disable `scale-to-zero`:
```yaml
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/minScale: '1'
```
Then run the following command to display a list of revisions:
```shell
kn revision list
```
List the Knative `Routes`:
```shell
kn route list
```
Display the details of the `order-saga` route:
```shell
kn route describe order-saga
```
Do the same thing using OpenShift Console.

## 6. Configure Knative Eventing

Create a secret on OpenShift for the Kafka SASL authentication:
```shell
oc create secret generic kafka-sasl-auth --from-literal=username=<your-username> --from-literal=password=<your-password>
```

In the `k8s` catalog define the file `kafka-source.yml` with the following content:
```yaml
apiVersion: sources.knative.dev/v1beta1
kind: KafkaSource
metadata:
  name: kafka-source-orders-payment
spec:
  consumerGroup: payment
  bootstrapServers:
    - my-cluster-kafka-bootstrap.kafka:9092
  topics:
    - <your-topic-name>
  sink:
    ref:
      apiVersion: serving.knative.dev/v1
      kind: Service
      name: payment-saga
    uri: /reserve
  net:
    sasl:
      enable: true
      user:
        secretKeyRef:
          name: kafka-sasl-auth
          key: username
      password:
        secretKeyRef:
          name: kafka-sasl-auth
          key: password
```
Alternatively, you can switch to the `Developer` perspective on OpenShift. Then choose `+Add` -> `Event Source` -> `Kafka Source`. \
Click button `Create Event Source`. Fill the required fields. Click `Create`.

Also add the file `kafka-binding.yaml` with the following content:
```yaml
apiVersion: bindings.knative.dev/v1beta1
kind: KafkaBinding
metadata:
  name: kafka-binding-payment-saga
spec:
  subject:
    apiVersion: serving.knative.dev/v1
    kind: Service
    name: payment-saga
  bootstrapServers:
    - my-cluster-kafka-bootstrap.kafka:9092
```
Change the value of the Kafka cluster address property in the `application.properties` file:
```properties
kafka.bootstrap.servers = ${KAFKA_BOOTSTRAP_SERVERS}
```
Configure HTTP `access.log` for incoming HTTP requests:
```properties
quarkus.http.access-log.enabled = true
quarkus.http.access-log.pattern = long
```
Redeploy the application on OpenShift Serverless. Observe the logs generated by the `payment-service`. Download and store the logs file. We will use it later.

Display a list of Knative `Sources`:
```shell
kn source list
```

## 7. Emitting Events 

Finish the implementation of the SAGA pattern in the `OrderReserveFunction` class. You should use `Emitter` to implement imperative way of sending events:
```java
public class OrderReserveFunction {

    @Inject
    Logger log;

    @Inject
    AccountRepository repository;
    @Inject
    @Channel("reserve-events")
    Emitter<Order> orderEmitter;

    @Funq
    public void reserve(Order order) {
        log.infof("Received order: %s", order);
        doReserve(order);
    }

    private void doReserve(Order order) {
        Account account = repository.findById(order.getCustomerId());
        log.infof("Account: %s", account);
        if (order.getStatus().equals("NEW")) {
            account.setReservedAmount(account.getReservedAmount() + order.getAmount());
            account.setCurrentAmount(account.getCurrentAmount() - order.getAmount());
            order.setStatus("IN_PROGRESS");
            log.infof("Order reserved: %s", order);
            orderEmitter.send(order);
        } else if (order.getStatus().equals("CONFIRMED")) {
            account.setReservedAmount(account.getReservedAmount() - order.getAmount());
        }
        repository.persist(account);
    }

}
```
Configure outgoing channel in the `application.properties`:
```properties
mp.messaging.outgoing.reserve-events.connector = smallrye-kafka
mp.messaging.outgoing.reserve-events.topic = <your-topic-name>
mp.messaging.outgoing.reserve-events.value.serializer = io.quarkus.kafka.client.serialization.ObjectMapperSerializer
```
If needed, create a topic on Kafka using AMQ Streams operator using OpenShift Console or the following YAML manifest e.g. `kafka-topic.yaml`:
```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: <your-topic-name>
  labels:
    strimzi.io/cluster: my-cluster
  namespace: kafka
spec:
  partitions: 10
  replicas: 3
```
Then apply the changes:
```shell
oc apply -f kafka-topic.yaml

```
Switch to the `shipment-saga` module. Once again add the following dependency into Maven `pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-funqy-http</artifactId>
</dependency>
```
Open the file `pl.redhat.samples.serverless.shipment.function.OrderReserveFunction`. \
Provide the similar implementation of the SAGA pattern for `Product` entity. \
Then configure outgoing channel for `Emitter`. \
Add new `KafkaSource` and `KafkaBinding` objects dedicated for the `shipment-saga`.

Display a list of Knative `Sources`:
```shell
kn source list
```
Display a list of running pods:
```shell
oc get pod --field-selector=status.phase=Running
```

## 8. Create Broker and Triggers

Create the broker using Knative CLI:
```shell
kn broker create <your-username>-broker
```
Display a list of brokers:
```shell
kn broker list
```
Go to the OpenShift Console and display a list of brokers in your namespace by accessing `Serverless` -> `Eventing`. Then choose the `Brokers` tab. \
Remove previously created `KafkaSources`. Create a new `KafkaSource` for all your topics and your broker as the `Sink`:
```yaml
apiVersion: sources.knative.dev/v1beta1
kind: KafkaSource
metadata:
  name: kafka-source-to-broker
spec:
  consumerGroup: payment
  bootstrapServers:
    - my-cluster-kafka-bootstrap.kafka:9092
  topics:
    - <your-topic-name-1>
    - <your-topic-name-2>
  sink:
    ref:
      apiVersion: eventing.knative.dev/v1
      kind: Broker
      name: pminkows-broker
  net:
    sasl:
      enable: true
      user:
        secretKeyRef:
          name: kafka-sasl-auth
          key: username
      password:
        secretKeyRef:
          name: kafka-sasl-auth
          key: password
```
Open the previously saved file with your applications log. Verify a structure of the example `CloudEvent` request. \
Then create triggers. First, we will create for `payment-saga`:
```yaml
apiVersion: eventing.knative.dev/v1
kind: Trigger
metadata:
  name: payment-saga-trigger
spec:
  broker: <your-broker-name>
  filter:
    attributes:
      type: dev.knative.kafka.event
      source: /apis/v1/namespaces/<your-namespace>/kafkasources/<your-kafka-source-name>#order-events
  subscriber:
    ref:
      apiVersion: serving.knative.dev/v1
      kind: Service
      name: payment-saga
    uri: /reserve
```
Do the same thing for `shipment-saga`. \
After that create a trigger for `order-saga`. It forward messages from the `reserve-orders` binding:
```yaml
apiVersion: eventing.knative.dev/v1
kind: Trigger
metadata:
  name: order-saga-trigger
spec:
  broker: <your-broker-name>
  filter:
    attributes:
      type: dev.knative.kafka.event
      source: /apis/v1/namespaces/<your-namespace>/kafkasources/<your-kafka-source-name>#reserve-events
  subscriber:
    ref:
      apiVersion: serving.knative.dev/v1
      kind: Service
      name: order-saga
    uri: /confirm
```

Define a new method for sending orders:
```java
@Outgoing("order-events")
public Multi<Message<Order>> publishOrder() {
    return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
            .map(tick -> {
                long r = random.nextInt(10000);
                return Message.of(new Order(r, r%10+1, r%10+1, 5, 100, "NEW"))
                        .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                                .withHeaders(new RecordHeaders().add("X-Routing-Name", "reserve".getBytes()))
                                .build());
            });
}
```
Redeploy the `order-saga` application. Verify logs generated by the `payment-saga` pod. Find the `Ce-*` header for the header set by the `order-saga`. \
Then edit `payment-saga-trigger` and `shipment-saga-trigger`, and replace `type` and `source` attributes with the custom attribute based on the header. 
