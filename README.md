# E-commerce · Microservicios con Spring Cloud

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker%20Compose-15%20containers-blue)

> **Sexto proyecto de la serie de arquitecturas.** Reimplementa el mismo dominio
> como microservicios **reales e independientes**, orquestados con el stack
> Spring Cloud Netflix (Eureka + Config Server + Gateway), **Kafka** como event
> bus y una **PostgreSQL por servicio**. La saga que en el proyecto 18 era una
> transacción local aquí es **distribuida, con consistencia eventual y outbox
> pattern**. La coreografía (choreography) hace que cada servicio reaccione a
> eventos sin un orquestador central.

---

## 🎯 Los tres casos de uso

| Caso | Endpoint (vía Gateway) | Descripción |
|------|------------------------|-------------|
| **CASO 1** | `POST /api/catalog/products` | Alta de producto (catálogo) |
| **CASO 2** | `POST /api/orders` | Inicia la **saga distribuida** (202 Accepted) |
| **CASO 3** | `GET /api/customers/{id}/orders` | Historial paginado de pedidos |

---

## 🏗️ Arquitectura

```
                              ┌───────────────────┐
        Cliente  ───────────► │  api-gateway 8080 │ ──┐  (descubre servicios
                              └───────────────────┘   │   por nombre en Eureka)
                                                       │
     ┌──────────────────┐    ┌──────────────────┐     │
     │ config-server    │    │ discovery-server │◄────┘  lb://order-service ...
     │ 8888 (config)    │    │ 8761 (Eureka)    │
     └──────────────────┘    └──────────────────┘

  ┌───────────────┐  ┌──────────────┐  ┌───────────────┐  ┌────────────────┐
  │ identity 8081 │  │ catalog 8082 │  │ order 8083    │  │ payment 8084   │
  │  DB 5451      │  │  DB 5452     │  │  DB 5453      │  │  DB 5454       │
  └───────────────┘  └──────────────┘  └───────────────┘  └────────────────┘
  ┌────────────────┐  ┌───────────────────┐
  │ inventory 8085 │  │ notification 8086 │            ┌──────────────────────┐
  │  DB 5455       │  │  DB 5456          │            │  Kafka (KRaft)        │
  └────────────────┘  └───────────────────┘            │  9092 int / 9095 ext  │
                                                        │  kafka-ui :8090       │
   REST síncrono (Feign + LoadBalancer):                └──────────────────────┘
     order ──► identity   (¿existe el cliente?)
     order ──► catalog    (productos + precios, batch)
```

Cada microservicio es un **proceso Java independiente**, con su **propia base de
datos** (aislamiento físico, sin foreign keys entre BDs) y su propio ciclo de
vida. No comparten memoria ni transacciones: **toda** la comunicación es por red
(REST síncrono o eventos Kafka).

---

## 🔄 Flujo de la saga distribuida (choreography)

```
Cliente → Gateway → order-service   POST /api/orders
   ├── REST → identity-service  (valida cliente)
   ├── REST → catalog-service   (obtiene productos + precios, snapshot)
   ├── guarda Order(PENDING) + OrderPlacedEvent en outbox  (misma TX)
   └── 202 Accepted

[outbox publisher] ─► Kafka order-events (OrderPlaced)
        │
        ├─► inventory-service:  reserva stock  ─► inventory-events (StockReserved)
        │        │
        │        └─► payment-service:  cobra (failure-rate 15%)
        │                 ├─ éxito ─► payment-events (PaymentSucceeded)
        │                 └─ fallo ─► payment-events (PaymentFailed)
        │
        └─► payment-service:  registra cobro pendiente (proyección local)

Kafka payment-events (PaymentSucceeded) ─► order-service ─► Order = PAID
        └─► order-events (OrderConfirmed) ─► notification-service (aviso OK)

COMPENSACIÓN (si el pago falla):
Kafka payment-events (PaymentFailed) ─► order-service ─► Order = CANCELLED
        └─► order-events (OrderCancelled)
                 ├─► inventory-service:  libera stock reservado (RELEASED)
                 └─► notification-service: aviso de cancelación
```

Consistencia **eventual**: la saga completa tarda ~1-3 s en converger.

---

## 🧩 Los 9 procesos + dependencias

### Infraestructura Spring Cloud
| Servicio | Puerto | Rol |
|----------|--------|-----|
| **config-server** | 8888 | Configuración centralizada (perfil `native`, lee `config-repo`) |
| **discovery-server** | 8761 | Registro de servicios (Netflix Eureka) |
| **api-gateway** | 8080 | Puerta de entrada única, enruta por nombre lógico (`lb://`) |

### Servicios de negocio
| Servicio | Puerto | DB | Consume | Publica |
|----------|--------|----|---------|---------|
| **identity-service** | 8081 | 5451 | — | — |
| **catalog-service** | 8082 | 5452 | — | — |
| **order-service** | 8083 | 5453 | inventory-events, payment-events | order-events |
| **payment-service** | 8084 | 5454 | order-events, inventory-events | payment-events |
| **inventory-service** | 8085 | 5455 | order-events | inventory-events |
| **notification-service** | 8086 | 5456 | order-events | — |

### Dependencias externas
| Componente | Puertos |
|------------|---------|
| **Kafka** (KRaft) | 9092 interno · **9095** externo |
| **kafka-ui** | 8090 |
| **PostgreSQL** (×6) | 5451 – 5456 |

**Puertos externos ocupados:** `8080-8086, 8090, 8761, 8888, 9095, 5451-5456`.

---

## 🚀 Cómo ejecutar

> Requisitos: **JDK 25**, **Docker** + **Docker Compose v2**.

```bash
# 1. Compilar TODOS los JARs (shared + 3 infra + 6 servicios = 10 módulos)
./mvnw clean package -DskipTests

# 2. Levantar el stack (build de 9 imágenes + 15 contenedores, ~2 min)
docker compose up -d --build

# 3. Esperar el arranque y verificar
sleep 90
docker compose ps          # todos Up / (healthy)

# 4. Ver los servicios registrados en Eureka
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps \
  | python3 -m json.tool
open http://localhost:8761  # consola de Eureka
```

### Ejecutar los 3 casos vía Gateway

```bash
# CASO 1 — crear producto
PROD=$(curl -s -X POST http://localhost:8080/api/catalog/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"MS-001","name":"Laptop","description":"desc","price":1299.99,"stock":10}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# CASO 2 — crear pedido (inicia la saga; responde 202 Accepted)
ORDER=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "x-correlation-id: test-$(date +%s)" \
  -d "{\"customerId\":2,\"lines\":[{\"productId\":$PROD,\"quantity\":2}]}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['orderId'])")

sleep 3   # la saga es asíncrona (consistencia eventual)

# CASO 3 — historial del cliente (status debe ser PAID)
curl -s "http://localhost:8080/api/customers/2/orders" | python3 -m json.tool
```

### Inspeccionar el sistema

```bash
# Eventos en Kafka
open http://localhost:8090

# Estado del pedido en su BD aislada
docker exec ecommerce-ms-pg-order \
  psql -U order_user -d order_db -c "SELECT id, status, total_amount FROM orders;"

# Reservas de stock (compensación: RELEASED)
docker exec ecommerce-ms-pg-inventory \
  psql -U inventory_user -d inventory_db -c "SELECT order_id, status FROM stock_reservations;"

# Pagos (failure-rate 15% ⇒ algunos FAILED)
docker exec ecommerce-ms-pg-payment \
  psql -U payment_user -d payment_db -c "SELECT order_id, status FROM payments;"
```

### Parar y limpiar

```bash
docker compose down -v     # elimina también los volúmenes de datos
```

---

## 🧠 Decisiones de diseño

- **Comunicación mixta.** REST síncrono (Feign + Spring Cloud LoadBalancer sobre
  Eureka) para *queries* (`order → identity`, `order → catalog`); Kafka
  asíncrono para eventos de dominio.
- **Database per service.** Seis PostgreSQL independientes, sin FKs entre ellas.
  Duplicación controlada por snapshots: `order` guarda `customer_email`;
  `inventory` guarda `product_id`+`quantity` de cada reserva.
- **Outbox pattern.** `order`, `inventory` y `payment` guardan el evento en la
  tabla `outbox_events` **en la misma transacción** que el cambio de negocio; un
  `@Scheduled` (cada 500 ms) lo publica en Kafka y marca `published_at`. Garantiza
  atomicidad "commit en BD + publicación del evento" sin two-phase commit.
- **Payload String (JSON crudo) en Kafka.** Los eventos viajan como JSON `String`;
  la (de)serialización a POJO ocurre en cada handler con **Jackson 3**. Desacopla
  versiones entre servicios y evita acoplar el deserializador de Kafka al tipo.
- **Correlation-id end-to-end.** Un interceptor servlet extrae/genera
  `x-correlation-id`, lo mete en el MDC de Logback y lo propaga en las llamadas
  Feign (RequestInterceptor) y en las cabeceras de Kafka.
- **Simulación de pago.** `payment-service` falla el cobro con probabilidad
  `app.payments.failure-rate` (0.15), lo que ejercita la **compensación** de la
  saga de forma realista.

### Compromisos (documentados para el alcance académico)

- **Stock canónico en catalog, reservas en inventory.** Para no multiplicar
  servicios, el stock "real" vive en `catalog-service` e `inventory-service`
  solo gestiona **reservas** (virtuales). La reserva siempre tiene éxito; el
  fallo de la saga se demuestra en el pago.
- **StockReservedEvent sin importe.** Como el evento de reserva no transporta
  importe ni `customerId`, `payment-service` mantiene una **proyección local**
  alimentada por `OrderPlacedEvent` para conocer el importe al cobrar.
- **Choreography sin coordinador.** No hay orquestador central; cada servicio
  reacciona a eventos. El proyecto 20 reimplementa la misma saga con
  **orchestration**.

---

## 📊 Monolito (14-18) vs microservicios (19)

| Aspecto | 14-18 (monolito) | **19 (microservicios)** |
|---------|------------------|-------------------------|
| Procesos | 1 | **9** |
| Bases de datos | 1 (a lo sumo 6 schemas) | **6** (una por servicio) |
| Transacciones | ACID local | **Consistencia eventual** |
| Comunicación | Llamadas a método | **REST + Kafka** |
| Escalado | Vertical | **Horizontal por servicio** |
| Failure isolation | No | **Sí** |
| Complejidad operacional | Baja | **Alta** |

---

## 🔭 Limitaciones y siguientes proyectos

- Saga por **choreography** (sin coordinador) → el **20** añade **orchestration**.
- Sin trazabilidad visual del flujo distribuido → el **21** añade **Jaeger,
  Prometheus y Grafana** (observabilidad).

---

## 📚 Portfolio de la serie

| # | Proyecto | Descripción |
|---|----------|-------------|
| 01 | [java-oop-fundamentals](https://github.com/crishof/java-oop-fundamentals) | POO, generics, records, sealed classes, patrones |
| 02 | [java-collections-streams](https://github.com/crishof/java-collections-streams) | Collections Framework y Streams API |
| 03 | [spring-core-ioc](https://github.com/crishof/spring-core-ioc) | Spring Core, IoC, DI y AOP |
| 04 | [spring-rest-api](https://github.com/crishof/spring-rest-api) | REST API con Spring MVC y OpenAPI |
| 05 | [spring-jpa-hibernate](https://github.com/crishof/spring-jpa-hibernate) | JPA, Hibernate, relaciones y caché |
| 06 | [spring-data-jpa](https://github.com/crishof/spring-data-jpa) | Spring Data JPA, paginación y specs |
| 07 | [spring-security-jwt](https://github.com/crishof/spring-security-jwt) | Spring Security, JWT y OAuth2 |
| 08 | [spring-testing](https://github.com/crishof/spring-testing) | Testing profesional con JUnit y Testcontainers |
| 09 | [spring-async](https://github.com/crishof/spring-async) | @Async, CompletableFuture y Scheduling |
| 10 | [spring-rabbitmq](https://github.com/crishof/spring-rabbitmq) | Mensajería con RabbitMQ y AMQP |
| 11 | [spring-kafka](https://github.com/crishof/spring-kafka) | Event streaming con Apache Kafka |
| 12 | [spring-docker](https://github.com/crishof/spring-docker) | Containerización con Docker y Compose |
| 13 | [spring-cicd](https://github.com/crishof/spring-cicd) | CI/CD con GitHub Actions |
| 14 | [ecommerce-layered-architecture](https://github.com/crishof/ecommerce-layered-architecture) | Monolito en capas (N-Tier) — BASELINE |
| 15 | [ecommerce-modular-monolith](https://github.com/crishof/ecommerce-modular-monolith) | Monolito modular por dominios |
| 16 | [ecommerce-hexagonal](https://github.com/crishof/ecommerce-hexagonal) | Arquitectura hexagonal (Ports & Adapters) |
| 17 | [ecommerce-clean-architecture](https://github.com/crishof/ecommerce-clean-architecture) | Clean Architecture (Uncle Bob) |
| 18 | [ecommerce-cqrs-event-sourcing](https://github.com/crishof/ecommerce-cqrs-event-sourcing) | CQRS y Event Sourcing |
| 19 | [ecommerce-microservices](https://github.com/crishof/ecommerce-microservices) ← *este proyecto* | **Microservicios con Spring Cloud** |
| 20 | [ecommerce-saga-pattern](https://github.com/crishof/ecommerce-saga-pattern) | Saga Pattern y transacciones distribuidas |
| 21 | [ecommerce-observability](https://github.com/crishof/ecommerce-observability) | Observabilidad con Prometheus y Grafana |

## Estado del proyecto
- ✅ Build: 11 módulos Maven BUILD SUCCESS, 13 tests pasando
- ✅ 15 containers healthy con `docker compose up -d` (config-server 8888, Eureka 8761, gateway 8080, 6 servicios 8081-8086, kafka 9095, kafka-ui 8090, 6 PostgreSQL 5451-5456)
- ✅ Eureka registra los 7 servicios; verificado end-to-end vía gateway con los 3 casos de uso
- ✅ Saga distribuida por choreography verificada: 15 pedidos con failure-rate=0.15 → 12 PAID / 3 CANCELLED con compensación consistente cross-service (payment FAILED×3, inventory RELEASED×3, notification ORDER_CANCELLED×3)
- ✅ Topics Kafka verificados: `order-events` (32), `inventory-events` (16), `payment-events` (16)
- ✅ Outbox pattern en order/inventory/payment: atomicidad tx-DB + publicación Kafka
- ✅ Correlation-id propagado end-to-end vía MDC + Kafka headers
- ⚠️ Choreography sin orquestador: debugging requiere `docker logs` de varios servicios. Resuelto en el proyecto 20 (orchestration) y el 21 (observability)
- 🎯 **Diferencial**: el dominio pasa de 1 proceso (18) a 9 procesos independientes con BDs aisladas. Comunicación exclusivamente por red (REST + Kafka). Consistencia eventual

---
_Autor: **Cristian Hoffmann** — Proyecto académico Java 25 / Spring Boot 4.1.0._
