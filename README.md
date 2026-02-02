
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

### Options

* `-i, --input`: Path to the target application (JAR/WAR/Folder).
* `-o, --output`: Directory to save the analysis report.
* `-l, --libs`: (Optional) Path to dependency libraries (for better precision).
* `-p, --packages`: (Optional) Restrict analysis to specific package prefixes (e.g., `com.apache`).
* `-f, --format`: Output format (`json`, `xml`, `html`). Default is `html`.