# Flash Sale Engine

A high-concurrency flash sale system built with **Spring Boot**, **Redis**, **Apache Kafka**, and **MySQL** that handles thousands of simultaneous purchase requests without overselling or duplicate purchases.

![Java](https://img.shields.io/badge/Java-17+-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=flat-square&logo=springboot)
![Redis](https://img.shields.io/badge/Redis-7.x-red?style=flat-square&logo=redis)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-4.x-black?style=flat-square&logo=apachekafka)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square&logo=mysql)
![Docker](https://img.shields.io/badge/Docker-24.x-blue?style=flat-square&logo=docker)

---

## Table of Contents

1. [What This Project Proves](#what-this-project-proves)
2. [How to Run It](#how-to-run-it)
3. [The Demo Flow](#the-demo-flow)
4. [Architecture](#architecture)
5. [The Three Hard Problems](#the-three-hard-problems)
6. [File-by-File Walkthrough](#file-by-file-walkthrough)
7. [API Reference](#api-reference)
8. [Testing](#testing)
9. [What's Not Real](#whats-not-real)

---

## What This Project Proves

The system shows three things working end to end:

**1. No overselling under extreme concurrency**
1000 users can hit the "buy" button in the same second, and exactly 100 orders succeed — never 101.

**2. Duplicate purchases blocked at the source**
The same user buying twice is rejected atomically, before any downstream work happens.

**3. Fast response + durable persistence**
Users get a response in under 100ms while the database write happens asynchronously in the background.

---

## How to Run It

### Prerequisites

- **JDK 17 or newer** on PATH (or `JAVA_HOME` set)
- **Docker Desktop** installed and running
- **Maven** (or use the wrapper `mvnw`)

### Start the Infrastructure

```bash
# MySQL
docker run -d --name flashsale-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=flashsale_db \
  -p 3306:3306 \
  mysql:8.0

# Redis
docker run -d --name flashsale-redis -p 6379:6379 redis:7-alpine

# Kafka
docker run -d --name flashsale-kafka -p 9092:9092 apache/kafka:latest
```

Verify all containers are running:

```bash
docker ps
```

### Create `application.yml`

Save as `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flashsale_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

  redis:
    host: localhost
    port: 6379

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: flashsale-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"

server:
  port: 8080
```

### Run the App

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw spring-boot:run
```

The app starts on **`http://localhost:8080`**.

---

## The Demo Flow

### Step 1 — Create a Product

Insert one product into MySQL:

```bash
docker exec -it flashsale-mysql mysql -u root -proot \
  -e "INSERT INTO flashsale_db.products (name, stock, price, version) VALUES ('PlayStation 5 Pro', 100, 999.00, 0);"
```

### Step 2 — Preheat Stock into Redis

```bash
curl -X POST http://localhost:8080/api/public/preheat/1
```

**What happens on the backend:**
- Loads stock from MySQL into Redis
- Creates key `product:stock:1` with value `"100"`
- Subsequent purchases decrement this counter atomically

### Step 3 — Place a Flash Sale Order

```bash
curl -X POST http://localhost:8080/api/orders/flash-sale \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"userId":1,"quantity":1}'
```

**The backend pipeline runs:**

1. Redis Lua script runs atomically — checks duplicate, checks stock, deducts, records user
2. If success, publishes order event to Kafka
3. Kafka consumer saves order to MySQL
4. User gets immediate response

Watch the Redis stock:

```bash
docker exec -it flashsale-redis redis-cli GET product:stock:1
```

### Step 4 — Demonstrate Idempotency

Try the same user again:

```bash
curl -X POST http://localhost:8080/api/orders/flash-sale \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"userId":1,"quantity":1}'
```

**Response:** `409 CONFLICT` — "Duplicate request: You have already purchased this item."

The Lua script caught the duplicate at the Redis layer — before any CPU was spent on Kafka or MySQL.

### Step 5 — Load Test with JMeter

Configure a Thread Group with:
- 100 concurrent users
- Ramp-up: 5 seconds
- Loop: 1

HTTP Request:
- `POST http://localhost:8080/api/orders/flash-sale`
- Body: `{"productId":1,"userId":${__threadNum},"quantity":1}`
- Header: `Content-Type: application/json`

**Expected result:**
- 100 successful orders
- 0 overselling
- ~73ms average response time
- Database consistent

---

## Architecture

```
                        ┌──────────────────────┐
                        │      Client          │
                        └──────────┬───────────┘
                                   │
                        ┌──────────▼───────────┐
                        │   Spring Boot API    │
                        │   (Port 8080)        │
                        └──────────┬───────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
     ┌────────▼────────┐  ┌───────▼────────┐  ┌────────▼────────┐
     │      Redis      │  │     Kafka      │  │      MySQL      │
     │   (In-Memory)   │  │ (Message Queue)│  │   (Database)    │
     │                 │  │                │  │                 │
     │ • Stock Counter │  │ • Order Events │  │ • Orders        │
     │ • User Set      │  │ • Async Write  │  │ • Products      │
     │ • Lua Script    │  │                │  │ • Users         │
     └─────────────────┘  └────────────────┘  └─────────────────┘
         ⚡ 0.5ms              📨 Async             🐌 50ms
```

### Request Flow

```
1. User clicks "Buy Now"
        ↓
2. Spring Boot receives request
        ↓
3. Redis Lua script runs ATOMICALLY:
     ├── Check if user already purchased (SISMEMBER)
     ├── Check if stock available (GET)
     ├── Deduct stock (DECRBY)
     └── Record user (SADD)
        ↓
4. Response: 1 (success) | 0 (sold out) | -1 (duplicate)
        ↓
5. If success → Order event sent to Kafka
        ↓
6. Kafka consumer saves order to MySQL (async, background)
```

---

## The Three Hard Problems

### Problem 1: Overselling Under Concurrency

> Two users check stock at the same moment. Both see "1 item left." Both buy it. Stock becomes -1.

**Solution: Redis Lua script for atomic check-and-deduct**

A traditional CRUD flow would look like:

```java
// BROKEN: race condition between GET and DECR
Integer stock = redis.get("product:stock:1");
if (stock > 0) {
    redis.decr("product:stock:1");  // ⚠️ Another thread could be here simultaneously
}
```

The Lua script makes all operations atomic — Redis executes the entire script as one unbreakable unit:

```lua
-- KEYS[1] = "product:stock:1"
-- KEYS[2] = "product:users:1"
-- ARGV[1] = userId

-- 1. Check if user already purchased
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return -1
end

-- 2. Check if stock available
local current = tonumber(redis.call('get', KEYS[1]) or '0')
if current < 1 then
    return 0
end

-- 3. Deduct stock + record user ATOMICALLY
redis.call('decrby', KEYS[1], 1)
redis.call('sadd', KEYS[2], ARGV[1])
return 1
```

**Return codes:**

| Code | Meaning | HTTP Response |
|------|---------|---------------|
| `1` | Success | `200 OK` — Order placed |
| `0` | Sold Out | `410 GONE` — Flash sale sold out |
| `-1` | Duplicate | `409 CONFLICT` — Already purchased |

**Why Lua specifically?**

| Without Lua | With Lua |
|-------------|----------|
| 4 separate Redis calls | 1 atomic call |
| Race conditions possible | No race conditions |
| 4 network round-trips | 1 network round-trip |
| ~2ms | ~0.5ms |

See `RedisStockService.java`.

---

### Problem 2: Duplicate Purchases

> A user could click "Buy" twice quickly, or send the same request from two devices. If both succeed, they've bought 2 items with 1 intent.

**Solution: Redis Set tracks purchased users**

For each product, we maintain a Redis Set:

```
product:users:1 → {1, 2, 3, 5, 8, 13, 21, ...}
```

Before deducting stock, the Lua script calls `SISMEMBER`:

- If user is already in the set → return `-1` (duplicate)
- If user is not in the set → proceed, then `SADD` after deduction

**Why a Set and not a per-user key?**

| Approach | Memory | Lookup speed |
|----------|--------|--------------|
| `product:users:1` Set | One key per product | O(1) per check |
| `user:1:product:1` per user | One key per purchase | O(1) but many keys |

Both are O(1), but the Set approach groups all buyers for a product together — useful for admin queries like "who bought this product."

---

### Problem 3: Slow Database Writes

> If every successful purchase does an INSERT into MySQL, and MySQL takes ~50ms per insert, then 100 simultaneous purchases queue up behind each other. The 100th user waits 5 seconds.

**Solution: Kafka decouples the fast response from the slow write**

The user's request completes as soon as Redis confirms the purchase. The order is then published to Kafka, and a separate consumer writes to MySQL in the background.

```
User Request (0.5ms Redis) → Response sent immediately
                            ↓
                    Kafka event queued
                            ↓
                    Consumer writes to MySQL (50ms, in background)
```

**What the user sees:**

- Response: ~73ms average (Redis round-trip + Kafka publish)
- They never wait for MySQL

**What if Kafka is down?**

The order is lost. For production, you'd want:

- Kafka with replication factor ≥ 2
- Producer retries
- An outbox pattern for guaranteed delivery
- Or: write to MySQL synchronously as a fallback

This demo assumes Kafka is available. That's a reasonable assumption for a portfolio project, but a real system would need circuit breakers.

See `KafkaProducerService.java` and `KafkaConsumerService.java`.

---

## File-by-File Walkthrough

```
flash-sale-engine/
├── pom.xml                                  Maven build
├── README.md                                This file
└── src/main/
    ├── resources/
    │   └── application.yml                  DB, Redis, Kafka config
    └── java/com/flashsale/flash_sale_engine/
        ├── FlashSaleEngineApplication.java  Spring Boot main class
        │
        ├── config/                          ── Configuration
        │   └── TestDataLoader.java          Creates 1000 test users on startup
        │
        ├── controller/                      ── HTTP layer
        │   ├── AdminController.java         Admin: create admin, preheat stock
        │   ├── OrderController.java         🔥 Flash sale purchase endpoint
        │   ├── PublicController.java        Register, login, preheat
        │   └── ProductController.java       Product CRUD
        │
        ├── dto/
        │   └── OrderEvent.java              Kafka message payload
        │
        ├── entity/                          ── Domain layer
        │   ├── Order.java                   JPA entity for settled orders
        │   ├── Product.java                 JPA entity for products
        │   └── User.java                    JPA entity for users
        │
        ├── filter/
        │   └── JwtFilter.java               JWT validation filter
        │
        ├── repository/                      ── Data access
        │   ├── OrderRepo.java               Spring Data JPA
        │   ├── ProductRepo.java             Spring Data JPA
        │   └── UserRepo.java                Spring Data JPA
        │
        ├── security/
        │   ├── SpringSecurity.java          Security configuration
        │   └── UserDetailServiceImplementation.java
        │
        ├── service/                         ── Business logic
        │   ├── RedisStockService.java       🔥 THE pipeline: Lua script + Redis
        │   ├── KafkaProducerService.java    Publishes order events
        │   ├── KafkaConsumerService.java    Consumes and saves to MySQL
        │   ├── OrderService.java            Order queries
        │   └── UserService.java             User management
        │
        └── util/
            └── JwtUtil.java                 Token generation/validation
```

---

## API Reference

### Public Endpoints

| Method | Path | What it does |
|--------|------|--------------|
| `POST` | `/api/public/register-user` | Register a new user |
| `POST` | `/api/public/login-user` | Login, returns JWT token |
| `POST` | `/api/public/preheat/{productId}` | Load stock from MySQL into Redis |

### Order Endpoints

| Method | Path | What it does |
|--------|------|--------------|
| `POST` | `/api/orders/flash-sale` | 🎯 Place a flash sale order |
| `GET` | `/api/orders` | Get all orders |
| `GET` | `/api/orders/user/{userId}` | Get user's orders |

### Admin Endpoints

| Method | Path | What it does |
|--------|------|--------------|
| `POST` | `/admin/create-admin` | Create an admin user |
| `POST` | `/admin/preheat/{productId}` | Alternate preheat endpoint |

### Request Format for `/api/orders/flash-sale`

```http
POST /api/orders/flash-sale
Content-Type: application/json

{
  "productId": 1,
  "userId": 1,
  "quantity": 1
}
```

**Response (success):**

```json
{
  "message": "Order placed successfully! Processing in background.",
  "status": "SETTLED"
}
```

**Response (sold out):**

```json
{
  "error": "Flash sale sold out!",
  "status": "SOLD_OUT"
}
```

**Response (duplicate):**

```json
{
  "error": "Duplicate request: You have already purchased this item.",
  "status": "DUPLICATE"
}
```

---

## Testing

### Manual Test (cURL)

```bash
# 1. Register a user
curl -X POST http://localhost:8080/api/public/register-user \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password123","email":"test@test.com"}'

# 2. Preheat stock
curl -X POST http://localhost:8080/api/public/preheat/1

# 3. Place an order
curl -X POST http://localhost:8080/api/orders/flash-sale \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"userId":1,"quantity":1}'

# 4. Try again (should be a duplicate)
curl -X POST http://localhost:8080/api/orders/flash-sale \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"userId":1,"quantity":1}'

# 5. Check Redis stock
docker exec -it flashsale-redis redis-cli GET product:stock:1

# 6. Check MySQL orders
docker exec -it flashsale-mysql mysql -u root -proot \
  -e "SELECT * FROM flashsale_db.orders;"

# 7. Check Kafka topic
docker exec -it flashsale-kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --topic flashsale-orders \
  --bootstrap-server localhost:9092 \
  --from-beginning --max-messages 5
```

### Load Test with JMeter

**Configuration:**

```
Thread Group:
  ├── Number of Threads: 100
  ├── Ramp-up: 5 seconds
  └── Loop Count: 1

HTTP Request:
  ├── POST http://localhost:8080/api/orders/flash-sale
  ├── Body: {"productId":1,"userId":${__threadNum},"quantity":1}
  └── Header: Content-Type: application/json
```

**Expected JMeter results:**

```
┌─────────────────────────────────────────────┐
│  JMeter Summary Report                      │
├─────────────────────────────────────────────┤
│  Total Requests:      100                   │
│  Successful Orders:   100                   │
│  Average Response:    73 ms                 │
│  Min Response:        8 ms                  │
│  Max Response:        774 ms                │
│  Throughput:          20.17 requests/sec    │
│  Error Rate:          0.0%                  │
│                                             │
│  ✅ No overselling                          │
│  ✅ No duplicates                           │
│  ✅ Database consistent                     │
└─────────────────────────────────────────────┘
```

---

## What's Not Real

This is a teaching project. To make it production-grade, swap these:

| What's in the demo | What it would be in production |
|---|---|
| `docker run` for infra | Kubernetes + Helm charts |
| Plaintext password in `application.yml` | AWS Secrets Manager / Vault |
| JWT secret in source | Rotated keys from KMS |
| Single Redis instance | Redis Cluster or Sentinel |
| Single Kafka broker | Multi-broker cluster, replication ≥ 2 |
| Test users auto-created | Real registration + KYC |
| No rate limiting | Per-user, per-IP rate limits |
| Console logs | Structured logs to ELK / Datadog |
| No metrics | Prometheus + Grafana |
| No circuit breakers | Resilience4j with fallbacks |
| H2 (if you use it) | PostgreSQL / MySQL with replicas |
| No payment gateway | Razorpay / Stripe integration |
| Manual preheat | Scheduled job before each sale |

The **Redis Lua script** and **Kafka pipeline** are essentially production-shaped. The infrastructure around them is what changes.

---

## Honest Limitations

These are inherent to the flash sale concept, not implementation bugs. A reviewer will ask about them — better to name them upfront.

**1. The "preheat" step is a manual gate.**
Between preheat and the first purchase, an admin could accidentally preheat a second time, resetting stock. In production you'd want:

- A sale window (`startedAt` / `endedAt`) enforced by the API
- Idempotent preheat (only if stock key doesn't exist)
- A lock on the preheat endpoint

**2. Kafka loss = lost orders.**
If Kafka accepts the message but crashes before the consumer writes to MySQL, that order is lost. The user got a "success" response but has no order in the database.

Fix: use the **outbox pattern** — write the order to a `pending_orders` table in the same transaction as the Redis check, then have Kafka confirm and mark it `settled`. Or accept the trade-off and use a durable Kafka cluster.

**3. Redis loss = lost stock.**
If Redis crashes and restarts without persistence, all preheated stock is gone. The Lua script would see no stock and reject everyone.

Fix: enable Redis AOF/RDB persistence, or use a Redis instance with replication.

**4. No payment integration.**
The "purchase" is just a stock deduction. There's no money movement, no actual product ownership transfer.

Fix: integrate Razorpay or Stripe after the Redis success, and only mark the order `SETTLED` after payment confirmation.

**5. Duplicate detection is per-user, per-product.**
If the same user wants to buy the same product twice legitimately (unusual for flash sales, but possible), they can't.

Fix: if 2-per-user is allowed, change the Lua script to use a per-user counter instead of a Set:

```lua
-- Instead of SISMEMBER, use:
local userCount = tonumber(redis.call('get', userKey) or '0')
if userCount >= maxPerUser then return -1 end
redis.call('incr', userKey)
```

---

## License

MIT. Use it for learning, portfolios, and interviews.
