# Digital Banking System - Microservices

A Digital Banking System built using Java, Spring Boot, and Microservices Architecture. The application provides banking functionalities such as account management, transactions, payments, fraud detection, and notifications through independently deployable services.

## Architecture

The system consists of the following microservices:

- **API Gateway** - Central entry point for client requests and service routing.
- **Account Service** - Handles customer account management and account-related operations.
- **Transaction Service** - Handles transactions and fund transfers.
- **Payment Service** - Manages payment processing.
- **Fraud Detection Service** - Performs fraud detection and validation.
- **Notification Service** - Handles transaction and payment notifications.

### Supporting Infrastructure

- **MySQL** - Relational database
- **Redis** - Caching and fraud detection support
- **Apache Kafka** - Asynchronous event-driven communication
- **Zookeeper** - Kafka coordination
- **Docker** - Containerization
- **Docker Compose** - Multi-container orchestration
- **Amazon ECR** - Docker image registry
- **AWS EC2** - Application deployment

## Technologies Used

| Category | Technologies |
|---|---|
| Language | Java |
| Framework | Spring Boot |
| Architecture | Microservices |
| API | REST APIs |
| Service Communication | OpenFeign |
| Messaging | Apache Kafka |
| Caching | Redis |
| Database | MySQL |
| Testing | JUnit 5, Postman |
| Containerization | Docker, Docker Compose |
| Cloud | AWS EC2, Amazon ECR |
| Build Tool | Maven |

## Key Features

- Customer account management
- Fund transfers and transaction processing
- Payment processing
- Fraud detection
- Transaction notifications
- RESTful APIs
- Inter-service communication
- Event-driven communication using Apache Kafka
- Distributed transaction management using the Saga Pattern
- Redis-based caching
- Dockerized microservices
- AWS deployment using Amazon ECR and EC2

## Microservice Communication

The application uses both synchronous and asynchronous communication between services.

### Synchronous Communication

REST APIs and OpenFeign are used for synchronous service-to-service communication.

```text
Client
  |
  v
API Gateway
  |
  +----> Account Service
  |
  +----> Transaction Service
  |
  +----> Payment Service
  |
  +----> Fraud Detection Service
  |
  +----> Notification Service
```

### Asynchronous Communication

Apache Kafka is used for asynchronous, event-driven communication.

```text
Transaction Service
        |
        | Kafka Event
        v
   Kafka Broker
        |
        +----> Fraud Detection Service
        |
        +----> Notification Service
```

## Distributed Transactions

The Saga Pattern is used to manage distributed transaction workflows across multiple microservices.

```text
Transaction
     |
     v
Payment
     |
     v
Fraud Detection
     |
     v
Notification
```

This approach allows the application to coordinate operations across independent services.

## Caching

Redis is integrated to reduce repetitive database access and improve application performance.

Redis is used for:

- Transaction-related caching
- Fraud detection checks
- Reducing unnecessary database queries

## Project Structure

```text
banking-system/
│
├── account-service/
├── transaction-service/
├── payment-service/
├── fraud-detection-service/
├── notification-service/
├── api-gateway/
│
├── docker-compose.yml
└── README.md
```

Each microservice is maintained as an independent Spring Boot application.

## Running the Application Locally

### Prerequisites

Install the following:

- Java
- Maven
- Docker
- Docker Compose
- Git

### Clone the Repository

```bash
git clone git@github.com:yuvanesh-m/Digital-Banking-System-Microservices.git
cd Digital-Banking-System-Microservices
```

### Build the Application

Build the Spring Boot services using Maven:

```bash
./mvnw clean package -DskipTests
```

### Start the Application

Start all services using Docker Compose:

```bash
docker compose up -d
```

Check the running containers:

```bash
docker compose ps
```

View all service logs:

```bash
docker compose logs -f
```

View logs for a specific service:

```bash
docker compose logs -f transaction-service
```

## AWS Deployment

The application was containerized using Docker and deployed on an AWS EC2 instance.

The Docker images were stored in Amazon Elastic Container Registry (ECR).

### Deployment Flow

```text
Spring Boot Microservices
          |
          v
     Docker Images
          |
          v
      Amazon ECR
          |
          v
       AWS EC2
          |
          v
    Docker Compose
          |
          v
   Running Containers
```

### Deployment Steps

1. Dockerized each Spring Boot microservice.
2. Built Docker images for the required deployment architecture.
3. Created repositories in Amazon ECR.
4. Tagged the Docker images with the ECR repository URI.
5. Pushed the images to Amazon ECR.
6. Configured the AWS EC2 instance with permission to access ECR.
7. Logged the EC2 instance into Amazon ECR.
8. Used Docker Compose to pull and start the microservices.

Pull the latest images:

```bash
docker compose pull
```

Start the application:

```bash
docker compose up -d
```

Check running containers:

```bash
docker ps
```

## Testing

The application was tested using:

- **JUnit 5** for unit testing
- **Postman** for API testing

Testing covered:

- Account operations
- Transaction processing
- Payment workflows
- Fraud detection
- Notification flows
- REST API functionality

## Future Enhancements

- Centralized configuration
- Distributed tracing
- Application monitoring and observability
- CI/CD pipeline
- Additional automated integration testing
- Swagger/OpenAPI documentation

## Author

**Yuvanesh M**

Java Backend Developer
- LinkedIn: [https://linkedin.com/in/yuvanesh-mp/](https://www.linkedin.com/in/yuvanesh-m-ab178a212/)
 
