# ⛏️ Registro de Equipos Mineros

[![CI](https://github.com/josheme15/mining-equipment-registry/actions/workflows/ci.yml/badge.svg)](https://github.com/josheme15/mining-equipment-registry/actions/workflows/ci.yml)

Aplicación fullstack para registrar equipos mineros (camiones, excavadoras y perforadoras), controlar su horómetro y avisar cuándo **requieren mantenimiento**. Se despliega completa con **un solo comando**: `./deploy.sh`.

| | URL |
|---|---|
| 🖥️ Frontend | https://mining-equipment-registry.vercel.app |
| ⚙️ Backend (API) | https://mining-equipment-registry.onrender.com |
| ❤️ Health check | https://mining-equipment-registry.onrender.com/health |

> ℹ️ El backend está en el plan gratuito de Render: si no recibe tráfico por ~15 min se suspende y **la primera petición puede tardar ~1 minuto** en responder.

---

## Contenido

- [Qué hace](#qué-hace)
- [Arquitectura](#arquitectura)
- [Modelo de datos y reglas](#modelo-de-datos-y-reglas)
- [API REST](#api-rest)
- [Levantar en local](#levantar-en-local)
- [Despliegue](#despliegue)
- [Pruebas y CI](#pruebas-y-ci)
- [Decisiones y limitaciones](#decisiones-y-limitaciones)
- [Uso de IA](#uso-de-ia)

---

## Qué hace

- **Crear, listar, editar y eliminar equipos** con código, tipo y horómetro (horas de uso).
- Cada tipo tiene un **intervalo de mantenimiento**. Al alcanzarlo, el equipo se muestra como **"Requiere mantenimiento"**.
- **Registrar un mantenimiento** guarda el historial y **reinicia el ciclo** del equipo.
- Resumen con totales, filtro de equipos pendientes, búsqueda por código y barra de progreso del ciclo.

## Arquitectura

```
┌──────────────────┐  HTTPS/JSON   ┌──────────────────────┐  JDBC + SSL  ┌─────────────────┐
│  Frontend        │ ────────────► │  Backend             │ ───────────► │  MySQL 8        │
│  Vue 3 + Vite    │   API REST    │  Spring Boot 4       │              │  (Aiven)        │
│  Vercel          │ ◄──────────── │  Java 21 · Render    │ ◄─────────── │  Flyway         │
└──────────────────┘               └──────────────────────┘              └─────────────────┘
```

| Capa | Tecnología | Hosting |
|---|---|---|
| Frontend | Vue 3 + Vite (SPA) | Vercel |
| Backend | Java 21, Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) | Render (Docker) |
| Base de datos | MySQL 8.4, migraciones con Flyway | Aiven |
| CI | GitHub Actions | GitHub |

**Monorepo**: frontend y backend viven en el mismo repositorio, pero se publican como servicios independientes, cada uno con su propia URL.

**Backend en capas, organizado por funcionalidad**:

```
backend/src/main/java/com/reto/mining/
├── equipment/        Controller → Service → Repository + entidad Equipment (con la regla de negocio)
├── equipmenttype/    Tipos de equipo e intervalos
├── maintenance/      Registro e historial de mantenimientos
├── health/           /version (commit en ejecución, lo usa deploy.sh)
└── shared/           Manejo global de errores y CORS
```

La regla de mantenimiento vive en la entidad `Equipment`, así que se prueba sin Spring ni base de datos. Los controladores exponen DTOs, nunca entidades.

**Estructura del repositorio**:

```
├── backend/                 API REST Spring Boot (+ Dockerfile)
│   └── src/main/resources/db/migration/   Migraciones Flyway (V1, V2, …)
├── frontend/                SPA Vue
├── .github/workflows/ci.yml Pruebas en cada push
├── deploy.sh                Despliegue completo con un comando
├── docker-compose.yml       MySQL (y backend) para desarrollo local
└── .env.example             Variables que necesita deploy.sh
```

## Modelo de datos y reglas

```
equipment_types                 equipments                              maintenances
───────────────────             ──────────────────────────────          ─────────────────────
id                  PK          id                          PK          id             PK
code        UNIQUE              code                 UNIQUE             equipment_id   FK → equipments
name                            equipment_type_id    FK ──────►         performed_at      (ON DELETE CASCADE)
maintenance_interval_hours      hour_meter                              hour_meter_at
                                last_maintenance_hour_meter             notes
                                created_at / updated_at
```

Intervalos iniciales (migración `V2`): **Camión 250 h · Excavadora 500 h · Perforadora 400 h**. Se guardan en la base de datos y no en el código, así que se pueden ajustar con una nueva migración.

**Reglas de negocio**

| Regla | Dónde se aplica |
|---|---|
| `horas desde el último mantenimiento = hour_meter − last_maintenance_hour_meter` | `Equipment` |
| **Requiere mantenimiento** si esas horas son **≥ intervalo del tipo** | `Equipment.requiresMaintenance()` |
| Registrar mantenimiento: guarda el historial con el horómetro actual y hace `last_maintenance_hour_meter = hour_meter`. **Todo en una transacción.** | `MaintenanceService` |
| El horómetro **no puede disminuir** ni ser negativo | Entidad + validación + `CHECK` en BD |
| El código es **único**, se normaliza (`cam-001` → `CAM-001`) | Servicio + `UNIQUE` en BD |
| Un equipo nuevo empieza su ciclo en **0 h** | `Equipment` (ver [decisiones](#decisiones-y-limitaciones)) |
| Eliminar un equipo borra su historial | `ON DELETE CASCADE` |

## API REST

Base: `https://mining-equipment-registry.onrender.com`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/health` | Estado del servicio (incluye conexión a BD) | 200 `{"status":"UP"}` / 503 |
| GET | `/version` | Commit en ejecución | 200 |
| GET | `/api/equipment-types` | Tipos e intervalos | 200 |
| GET | `/api/equipments` | Lista de equipos con estado calculado | 200 |
| GET | `/api/equipments/{id}` | Detalle | 200 / 404 |
| POST | `/api/equipments` | Crear | 201 / 400 / 409 / 422 |
| PUT | `/api/equipments/{id}` | Editar | 200 / 400 / 404 / 409 / 422 |
| DELETE | `/api/equipments/{id}` | Eliminar (con su historial) | 204 / 404 |
| POST | `/api/equipments/{id}/maintenances` | Registrar mantenimiento | 201 / 404 |
| GET | `/api/equipments/{id}/maintenances` | Historial (más reciente primero) | 200 / 404 |

<details>
<summary>Ejemplos</summary>

```bash
# Crear
curl -X POST $API/api/equipments -H "Content-Type: application/json" \
  -d '{"code":"CAM-001","typeId":1,"hourMeter":260}'
```
```json
{
  "id": 1, "code": "CAM-001",
  "type": {"id": 1, "code": "TRUCK", "name": "Camión", "maintenanceIntervalHours": 250},
  "hourMeter": 260, "lastMaintenanceHourMeter": 0,
  "hoursSinceMaintenance": 260, "hoursUntilMaintenance": 0,
  "requiresMaintenance": true, "createdAt": "...", "updatedAt": "..."
}
```
```bash
# Registrar mantenimiento (notes es opcional)
curl -X POST $API/api/equipments/1/maintenances -H "Content-Type: application/json" \
  -d '{"notes":"Cambio de aceite"}'
```

Errores, siempre con el mismo formato:
```json
{ "status": 422, "error": "Unprocessable Content",
  "message": "El horómetro no puede disminuir (actual: 260 h)", "fieldErrors": {} }
```
</details>

## Levantar en local

**Requisitos:** Java 21+, Node 20+, Docker.

```bash
# 1. Base de datos MySQL
docker compose up -d mysql

# 2. Backend → http://localhost:8080/health
#    (aplica las migraciones al arrancar; por defecto se conecta al MySQL de docker-compose)
cd backend
./mvnw spring-boot:run

# 3. Frontend → http://localhost:5173  (en otra terminal)
cd frontend
cp .env.example .env        # VITE_API_URL=http://localhost:8080
npm install
npm run dev
```

> Alternativa: `docker compose up -d --build` levanta MySQL **y** el backend en contenedores.

## Despliegue

### Requisitos (una sola vez)

1. **Herramientas**: `git`, `java` 21+, `node` 20+, `npm`, `curl` y la CLI de Vercel (`npm i -g vercel`). En Windows, ejecutar desde **Git Bash**.
2. **Servicios** (todos con plan gratuito):
   - **Aiven**: servicio MySQL.
   - **Render**: Web Service con *Root Directory* `backend`, runtime **Docker**, *Health Check Path* `/health` y **Auto-Deploy desactivado** (el despliegue lo dispara `deploy.sh`). Variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`.
   - **Vercel**: un token en *Account Settings → Tokens*. El proyecto se crea solo en el primer despliegue.
3. **Variables**: `cp .env.example .env` y completar los valores. `.env` está en `.gitignore` y **nunca se sube**.

| Variable | Descripción |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión JDBC a MySQL de producción (para las migraciones) |
| `RENDER_DEPLOY_HOOK_URL` | Deploy Hook del servicio en Render (*Settings → Deploy Hook*) |
| `BACKEND_URL` | URL pública del backend |
| `VERCEL_TOKEN` | Token de Vercel |
| `FRONTEND_URL` | URL pública del frontend |

### Desplegar

```bash
git push            # Render despliega lo que está en GitHub
./deploy.sh
```

| Paso | Qué hace | Si falla |
|---|---|---|
| 1 | Verifica herramientas, variables y que `main` esté sin cambios pendientes y sincronizada con GitHub | Aborta antes de tocar nada |
| 2 | Ejecuta las pruebas del backend y compila el frontend | Aborta antes de tocar nada |
| 3 | Registra la versión publicada actualmente (commit del backend e ID del despliegue de Vercel) | Avisa que no habrá rollback |
| 4 | Aplica las migraciones Flyway en la base de producción | Aborta sin publicar |
| 5 | Dispara el Deploy Hook de Render y espera (vía `/version`) a que corra **el commit nuevo** | **Rollback** |
| 6 | Despliega el frontend en Vercel (`vercel deploy --prod`) con `VITE_API_URL` | **Rollback** |
| 7 | Verifica `/health` = UP, que el frontend responde y que el CORS acepta al frontend | **Rollback** |

- Cualquier error termina con un **mensaje claro** (qué paso falló y qué hacer) y **código de salida 1**.
- **Idempotente**: se puede ejecutar varias veces. Flyway solo aplica migraciones nuevas y volver a desplegar el mismo commit no cambia nada.

### Rollback automático

Si el despliegue falla **después** de empezar a publicar (pasos 5 a 7), el script vuelve a la versión anterior:

- **Backend**: redespliega en Render el commit anterior (`Deploy Hook` + `&ref=<sha>`) y espera a que `/version` lo confirme. Si el build nuevo falló, Render nunca dejó de servir la versión anterior.
- **Frontend**: `vercel promote <despliegue anterior>`. Se usa `promote` y no `vercel rollback` porque este último desactiva la asignación automática del dominio en los despliegues siguientes.
- **Base de datos**: las migraciones **no se revierten**. Por eso deben ser compatibles con la versión anterior del código (patrón *expand/contract*: agregar columnas o tablas primero y eliminarlas en un despliegue posterior).

Para probarlo con un fallo simulado al final del despliegue:

```bash
SIMULATE_FAILURE=1 ./deploy.sh     # despliega, falla a propósito y restaura la versión anterior (exit 1)
```

## Pruebas y CI

```bash
cd backend && ./mvnw test
```

19 pruebas automatizadas:
- `EquipmentTest`: la regla de mantenimiento (límite exacto del intervalo, reinicio del ciclo, intervalo por tipo, el horómetro no baja).
- `EquipmentServiceTest` (Mockito): código duplicado, tipo inexistente, normalización.
- `EquipmentControllerTest` (MockMvc): validaciones y códigos HTTP (400, 404, 409, 422).

**GitHub Actions** (`.github/workflows/ci.yml`) corre en cada push y pull request: pruebas del backend, build del frontend y build de la imagen Docker.

## Decisiones y limitaciones

- **Un equipo nuevo empieza su ciclo en 0 h.** Si se registra con un horómetro que ya supera el intervalo, aparece como "Requiere mantenimiento". Sin historial no se puede asumir que esté al día, así que se elige el criterio conservador. El ciclo se reinicia al registrar el primer mantenimiento.
- **El horómetro solo aumenta**, igual que un horómetro físico. Corregir un valor mal ingresado hacia abajo requeriría una funcionalidad aparte con auditoría.
- **El mantenimiento usa el horómetro actual del equipo**: primero se actualiza el horómetro y luego se registra el mantenimiento.
- **Monorepo**: un solo `git push` y un solo `deploy.sh` controlan ambos servicios.
- **Sin autenticación**: está fuera del alcance del reto.
- **Plan gratuito**: el backend se suspende tras ~15 minutos sin uso; por eso el script espera con reintentos.

## Uso de IA

Se usó **Claude Code** (Anthropic) como asistente durante el desarrollo: propuesta de arquitectura, generación de código (backend, frontend, `deploy.sh`, CI), pruebas, depuración del despliegue y redacción de este README. Revisé, probé y entiendo todo el código entregado, y las decisiones de diseño están explicadas arriba.
