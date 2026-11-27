# order-service

The store's order service: product catalog, discount coupons, and order creation, lookup and cancellation.

## Stack

- Java 21
- Spring Boot 3.5 (Web, Data JPA, Validation)
- In-memory H2 (data is recreated on every start)
- Maven

## Prerequisites

- **JDK 21 or newer.** Check with `java -version`.
- **Internet access on the first build**, to download the dependencies from Maven Central. The first build
  takes a few minutes; later ones are fast.
- **Port 8080 free** (or pick another one, see below).

You do not need Maven installed: the project ships with the Maven Wrapper (`./mvnw`), which downloads the right
Maven version on first use. If you already have Maven 3.9+, plain `mvn` works too.

## How to run

```bash
git clone https://github.com/renanbona/order-service-challenge.git
cd order-service-challenge

./mvnw test               # runs the test suite
./mvnw spring-boot:run    # starts the application (Ctrl+C to stop)
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

If port 8080 is already in use, start the application on another port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

The API runs at `http://localhost:8080`. The H2 console is at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:orders`, user `sa`, no password).
There is a web panel at `http://localhost:8080/` to explore the API from the browser. It is served by the
application itself, so there is nothing else to install or start. If you started the application on another
port, the panel, the API and the H2 console are all on that port instead.

## Seed data

| id | SKU    | Product             | Price    | Stock   |
|----|--------|---------------------|----------|---------|
| 1  | NB-001 | Notebook Pro 14     | 4500.00  | 10      |
| 2  | MS-002 | Wireless Mouse      | 150.00   | 50      |
| 3  | KB-003 | Mechanical Keyboard | 350.00   | 2       |

| Coupon     | Discount | Expires    |
|------------|----------|------------|
| WELCOME10  | 10% (whole purchase)            | 2099-12-31 |
| LASTDAY15  | 15% (whole purchase)            | today      |
| EXPIRED20  | 20% (whole purchase)            | yesterday  |
| MOUSE10    | 10% on Wireless Mouse (id 2)    | 2099-12-31 |
| NOTEBOOK5OLD | 5% on Notebook Pro 14 (id 1) | yesterday  |

## Endpoints

| Method | Path                             | Description                       |
|--------|----------------------------------|-----------------------------------|
| GET    | `/products`                      | Lists products                    |
| GET    | `/products/{id}`                 | Gets a product                    |
| PUT    | `/products/{id}/price`           | Updates price `{"price": 99.90}`  |
| POST   | `/orders`                        | Creates an order                  |
| GET    | `/orders/{id}`                   | Gets an order                     |
| POST   | `/orders/{id}/cancel`            | Cancels an order                  |
| GET    | `/customers/{customerId}/orders` | A customer's orders               |
| GET    | `/coupons`                       | Lists coupons                     |
| POST   | `/coupons`                       | Creates a coupon (see below)      |

Example:

```bash
curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId": 1, "items": [{"productId": 2, "quantity": 2}], "couponCode": "WELCOME10"}'
```

## Business rules

1. An order is only created if there is stock for all items. Stock is decremented when the order is created.
2. The quantity of each item must be at least 1.
3. Each item's price is the product's price **at the time of purchase**. Later price changes do not affect orders already placed.
4. Coupons give a percentage discount and are valid **up to and including the expiry date**. A coupon either applies to the **whole purchase** (the discount is a percentage of the subtotal) or to a **single product** (the discount is a percentage of that product's lines only). A product coupon is rejected if the order does not contain that product.

Creating a coupon with `POST /coupons`:

```bash
curl -s -X POST localhost:8080/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code": "KB30", "percentOff": 30, "expiresAt": "2099-12-31", "productId": 3}'
```

Omit `productId` (or send `null`) for a whole-purchase coupon. Codes are stored in upper case. Creating a coupon fails with 422 if the code already exists or the expiry date is not in the future, and with 404 if the product does not exist.
5. Cancelling an order returns the items' quantities to stock.
6. A cancelled order cannot be cancelled again.

## Tasks

The tasks for this session are in [`docs/TASKS.md`](docs/TASKS.md).
