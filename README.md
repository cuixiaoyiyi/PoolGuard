
# PoolGuard 🛡️

**A Static Analysis Tool for Detecting Management Misuses in Java Thread Pools.**

## 📖 Introduction

**PoolGuard** is a specialized static analysis tool designed to detect **Management Misuses** in Java Thread Pools (`java.util.concurrent.ExecutorService`).

Unlike general-purpose linters (e.g., SpotBugs, PMD) or basic concurrency checkers (e.g., RacerD) that focus on data races or simple thread creation, **PoolGuard** specifically models the **configuration** and **lifecycle** of thread pools. It employs inter-procedural typestate analysis to detect deep-seated issues such as resource exhaustion, silent data loss, and classloader leaks caused by improper thread pool management.

## ✨ Key Features

* **Configuration Reconstruction**: Automatically identifies thread pool creation points (including custom wrappers and factory methods) to analyze parameters like queue capacity and rejection policies.
* **Lifecycle Verification**: Tracks the state of executors to ensure proper termination, preventing thread leaks and "zombie" tasks.
* **Safety Policy Analysis**: Detects risky rejection policies (e.g., `DiscardPolicy`) that lead to silent failures.
* **High Precision**: Validated on real-world large-scale projects with a low false-positive rate.

## 🐛 Detected Patterns

PoolGuard currently supports detection of the following misuse patterns:

| ID | Pattern Name | Acronym | Description | Impact |
| --- | --- | --- | --- | --- |
| 1 | **Unbounded Size of Cache/Queue** | `UBSCQ` | Using unbounded queues (e.g., `LinkedBlockingQueue` without capacity) or cached pools. | **OOM Error** |
| 2 | **Not Terminated** | `NT` | Thread pools are created but never shut down properly. | **Thread Leak** |
| 3 | **Discard Policy Swallowing Error** | `DPSE` | Using `DiscardPolicy` without logging or handling triggers. | **Silent Data Loss** |
| 4 | **Exception Not Caught** | `ENC` | Uncaught exceptions in worker threads terminate the thread silently. | **Task Failure** |
| 5 | **Unrefactored ThreadLocal** | `UTL` | Using `ThreadLocal` in static thread pools without cleanup. | **ClassLoader Leak** |
| 6 | **Repeated Creation of ThreadPool** | `RCTP` | Creating pools inside frequent loops or API calls. | **Resource Exhaustion** |

## 🚀 Real-World Impact

PoolGuard has been evaluated on **49** popular open-source projects (the E-Real dataset). It successfully detected **56 previously unknown issues**, of which **24** have been fixed or confirmed by developers.

### Prerequisites

* JDK 1.8 or higher
* Maven 3.6+

## 💻 Usage

PoolGuard analyzes compiled Java bytecode (`.jar`, or directory of `.class` files).

### Basic Command

```bash
java -jar poolguard.jar -i <path-to-target> -o <output-dir> [options]

```
### Reported Issues

|NO.|Project|Fork|Star|#Misuse (*Fixed)|Confirmed Issue Id|
|:---|:---|:---|:---|:---|:---|
|1|[Apache Dubbo](https://github.com/apache/dubbo)|26.6k|41.7k|2 (1)|15969, 15886|
|2|[Apache Pulsar](https://github.com/apache/pulsar)|3.7k|15.1k|2 (1)|25153, 25135|
|3|[Apache RocketMQ](https://github.com/apache/rocketmq)|12.2k|20.8k|2 (1)|9983, 9985|
|4|[Apache Curator](https://github.com/apache/curator)|1.8k|3.5k|2 (0)|1282, 1283|
|5|[Apollo Kotlin](https://github.com/apollographql/apollo-kotlin)|689|3.9k|2 (0)|6821, 6822|
|6|[Apache Iceberg](https://github.com/apache/iceberg)|3.0k|8.4k|1 (1)|15031|
|7|[Google Guava](https://github.com/google/guava)|11.1k|51.4k|1 (0)|8152|
|8|[Apache Shenyu](https://github.com/apache/shenyu)|3.0k|8.7k|1 (0)|6262|
|9|[Apache HugeGraph](https://github.com/apache/incubator-hugegraph)|576|2.9k|1 (0)|2939|
|10|[Apache IoTDB](https://github.com/apache/iotdb)|1.1k|6.3k|1 (0)|17016|
|11|[Redis Lettuce](https://github.com/redis/lettuce)|1.1k|5.7k|1 (0)|3604|
|12|[AWS Amplify Android](https://github.com/aws-amplify/aws-sdk-android)|549|1.1k|1 (0)|3685|
|13|[ShedLock](https://github.com/lukas-krecan/ShedLock)|564|4.1k|1 (0)|3145|
|14|[Jenkins Office365](https://github.com/jenkinsci/office-365-connector-plugin)|86|96|1 (0)|422|
|15|[React Native WebRTC](https://github.com/react-native-webrtc/react-native-webrtc)|1.3k|4.9k|1 (0)|1783|
|16|[HttpToolkit Android](https://github.com/httptoolkit/httptoolkit-android)|93|587|1 (0)|38|
|17|[Apache ShardingSphere](https://github.com/apache/shardingsphere)|7.2k|19.5k|1 (0)|37714|
|18|[ElasticJob](https://github.com/apache/shardingsphere-elasticjob)|3.3k|8.2k|1 (0)|2493|
|19|[Apache InLong](https://github.com/apache/inlong)|800|2.1k|1 (0)|12064|
|20|[Alibaba Canal](https://github.com/alibaba/canal)|7.3k|27.5k|1 (0)|5563|
|21|[KIE Drools](https://github.com/apache/incubator-kie-drools)|2.5k|5.1k|1 (0)|6554|
|22|[SSH on Web](https://github.com/javaterminal/ssh-on-web)|32|140|1 (0)|3|
|23|[Web3j](https://github.com/web3j/web3j)|1.7k|5.1k|1 (0)|2244|
|24|[OpenFeign](https://github.com/OpenFeign/feign)|3.6k|8.5k|1 (0)|3178|
|25|[Spring Security](https://github.com/spring-projects/spring-security)|6.3k|9.2k|1 (0)|18389|
|26|[Spring Cloud OpenFeign](https://github.com/spring-cloud/spring-cloud-openfeign)|1.5k|3.1k|1 (0)|1308|
|27|[AWS SDK Java](https://github.com/aws/aws-sdk-java)|3.6k|5.8k|1 (0)|3196|
|28|[Netflix Hystrix](https://github.com/Netflix/Hystrix)|4.7k|24.1k|1 (0)|2116|
|29|[Reactor Core](https://github.com/reactor/reactor-core)|1.5k|5.1k|1 (0)|4176|
|30|[HikariCP](https://github.com/brettwooldridge/HikariCP)|3.1k|18.5k|1 (0)|2378|
|31|[JetCache](https://github.com/alibaba/jetcache)|1.2k|5.3k|1 (0)|1000|
|32|[AWS Glue Client](https://github.com/awslabs/aws-glue-data-catalog-client-for-apache-hive-metastore)|150|180|1 (0)|86|
|33|[Olap4j](https://github.com/olap4j/olap4j)|80|220|1 (0)|73|
|34|[AndroidPerfMon](https://github.com/markzhai/AndroidPerformanceMonitor)|1.1k|6.2k|1 (0)|154|
|35|[Semantic Metrics](https://github.com/spotify/semantic-metrics)|60|150|1 (0)|144|
|36|[TLS Channel](https://github.com/marianobarrios/tls-channel)|60|240|1 (0)|329|
|37|[JBoss Threads](https://github.com/jboss/jboss-threads)|150|200|1 (0)|284|
|38|[Google Truth](https://github.com/google/truth)|400|2.8k|1 (0)|1624|
|39|[PP4J](https://github.com/ViktorC/PP4J)|10|40|1 (0)|16|
|40|[Concurrency Limits](https://github.com/Netflix/concurrency-limits)|250|1.5k|1 (0)|231|
|41|[Spring Statemachine](https://github.com/spring-projects/spring-statemachine)|1.2k|1.8k|1 (0)|1208|
|42|[Spring Integration](https://github.com/spring-projects/spring-integration)|2.5k|2.5k|1 (0)|10696|
|43|[Spring Integration Ext](https://github.com/spring-projects/spring-integration-extensions)|300|200|1 (0)|264|
|44|[NativeStackBlur](https://github.com/Commit451/NativeStackBlur)|100|500|1 (0)|11|
|45|[gRPC Java](https://github.com/grpc/grpc-java)|10.5k|40k|1 (0)|12601|
|**Total**||||**50 (5)**||

### Options

* `-i, --input`: Path to the target application (JAR/WAR/Folder).
* `-o, --output`: Directory to save the analysis report.
* `-l, --libs`: (Optional) Path to dependency libraries (for better precision).
* `-p, --packages`: (Optional) Restrict analysis to specific package prefixes (e.g., `com.apache`).
* `-f, --format`: Output format (`json`, `xml`, `html`). Default is `html`.
