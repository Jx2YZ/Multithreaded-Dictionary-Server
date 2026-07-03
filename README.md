# Multi-threaded Dictionary Server

A client-server application that implements a multi-threaded dictionary server allowing concurrent clients to perform dictionary operations.

## Features

### Basic Features

- Query word meanings
- Add new words with meanings
- Remove existing words
- Add additional meanings to existing words
- Update existing meanings
- GUI-based client interface
- Multi-threaded server implementation
- Persistent dictionary storage

### Additional Features

- Thread pool management
- Server management GUI with manual start/stop controls and graceful shutdown
- Thread-safe concurrency using `ReadWriteLock` (multiple concurrent readers, single writer)
- Configurable per-request delay simulation to demonstrate concurrent request handling

## Requirements

- Java 11 or higher
- Maven

## Building the Project

```bash
# Clone and enter the repository
git clone https://github.com/<your-username>/multithreaded-dictionary-server.git
cd multithreaded-dictionary-server

# Verify prerequisites (Java 11+, Maven 3.6+)
java -version
mvn -version

# Build both JARs (compiles, packages, skips nothing)
mvn clean package
```

Build output lands in `target/`:

```bash
ls target/*.jar
# target/DictionaryServer.jar
# target/DictionaryClient.jar
```

To rebuild from scratch after changes:

```bash
mvn clean package   # full clean rebuild
mvn package         # incremental (faster)
```

## Running the Application

### 1. Start the server

```bash
java -jar target/DictionaryServer.jar <port> <dictionary-file>
```

| Argument | Description | Example |
|---|---|---|
| `<port>` | TCP port to listen on (1024–65535) | `1234` |
| `<dictionary-file>` | Path to the persistence file; created automatically if it doesn't exist | `dictionary.txt` |

Concrete example:

```bash
java -jar target/DictionaryServer.jar 1234 dictionary.txt
```

The server GUI opens — click **Start Server** to begin accepting connections. **Shutdown Server** performs a graceful three-step termination (stop accepting, close listener, force-close active client sockets).

### 2. Start the client

```bash
java -jar target/DictionaryClient.jar <server-address> <server-port> <sleep-duration>
```

| Argument | Description | Example |
|---|---|---|
| `<server-address>` | Hostname or IP of the server | `localhost` |
| `<server-port>` | Port the server is listening on | `1234` |
| `<sleep-duration>` | Per-request delay in ms, attached to every request to simulate server-side work | `2000` |

Concrete example:

```bash
java -jar target/DictionaryClient.jar localhost 1234 0
```

### 3. Demonstrate concurrency (optional)

Launch multiple clients with a 3-second delay and issue simultaneous operations:

```bash
# Terminal 1 — server
java -jar target/DictionaryServer.jar 1234 dictionary.txt

# Terminals 2 & 3 — two clients with 3000 ms simulated work
java -jar target/DictionaryClient.jar localhost 1234 3000
java -jar target/DictionaryClient.jar localhost 1234 3000
```

Expected timings, which empirically verify the `ReadWriteLock` semantics:

- Two concurrent **queries** → both return in ~3 s (parallel reads)
- Two concurrent **adds** → return in ~3 s and ~6 s (serialised writes)
- **Query during a write** → ~6 s (reader blocks until the writer releases the lock)

## Using the Client

1. **Query a word**:
   - Enter the word in the "Word" field
   - Click "Query"

2. **Add a new word**:
   - Enter the word in the "Word" field
   - Enter one or more meanings in the "Meaning" field (one per line)
   - Click "Add Word"

3. **Remove a word**:
   - Enter the word in the "Word" field
   - Click "Remove Word"

4. **Add meaning to existing word**:
   - Enter the word in the "Word" field
   - Enter the new meaning in the "Meaning" field
   - Click "Add Meaning"

5. **Update existing meaning**:
   - Enter the word in the "Word" field
   - Enter the old meaning on the first line of the "Meaning" field
   - Enter the new meaning on the second line
   - Click "Update Meaning"

## Concurrency Model

The server uses a fixed thread pool, with each connected client handled on a dedicated thread. Access to the shared dictionary is controlled with a `ReentrantReadWriteLock`:

- **Read operations** (`query`) acquire the read lock. Multiple clients can read concurrently.
- **Write operations** (`addWord`, `removeWord`, `addMeaning`, `updateMeaning`) acquire the write lock. Writes are mutually exclusive with each other and with reads, ensuring atomicity and preventing read/write conflicts.
- File persistence (`saveDictionary`) is always called while holding the write lock, so on-disk state remains consistent.

The configurable `<sleep-duration>` is applied inside each dictionary method *after* the read or write lock has been acquired. Sleeping under the lock extends the critical section, which is what makes concurrent behaviour (parallel reads, serialised writes) observable with multiple clients.

## Error Handling

The application handles various error scenarios:

- Invalid command-line arguments (incorrect port number, missing dictionary file, non-numeric sleep duration)
- Network communication errors (server/address not reachable, connection failures, automatic reconnect with retry)
- Dictionary operations errors (word not found, duplicate words)
- File I/O errors (cannot find dictionary file, error reading/writing to file)
- Input validation errors (invalid input from console or GUI)

All errors are properly reported to the user through the GUI with clear error messages.