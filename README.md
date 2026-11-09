# Registro de Equipos Mineros

Aplicación fullstack para registrar equipos mineros (camiones, excavadoras, perforadoras), controlar su horómetro y avisar cuando requieren mantenimiento.

> 🚧 En construcción.

## Stack

| Parte | Tecnología | Hosting |
|---|---|---|
| Frontend | Vue 3 + Vite | Vercel |
| Backend | Java 21 + Spring Boot 4 (API REST) | Render (Docker) |
| Base de datos | MySQL 8 + migraciones Flyway | Aiven |

## Estructura

```
├── backend/            # API REST Spring Boot
│   └── src/main/resources/db/migration/   # Migraciones Flyway
├── frontend/           # SPA Vue
├── docker-compose.yml  # MySQL local
└── .env.example        # Variables necesarias para el despliegue
```

## Levantar en local

Requisitos: Java 21+, Node 18+, Docker.

```bash
# 1. Base de datos
docker compose up -d mysql

# 2. Backend (aplica migraciones al arrancar) → http://localhost:8080/health
cd backend && ./mvnw spring-boot:run

# 3. Frontend → http://localhost:5173
cd frontend && cp .env.example .env && npm install && npm run dev
```

## Despliegue

_Pendiente: `./deploy.sh`._

## Uso de IA

Se usó **Claude Code** (Anthropic) como asistente para la estructura del proyecto, el código y la documentación. Todo el código fue revisado y entendido por el autor.
