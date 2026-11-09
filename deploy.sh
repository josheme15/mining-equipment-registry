#!/usr/bin/env bash
# =============================================================================
#  deploy.sh — Despliega backend (Render) + frontend (Vercel) con un solo comando
#
#  Uso:   ./deploy.sh
#         SIMULATE_FAILURE=1 ./deploy.sh   # fuerza un fallo al final para probar el rollback
#
#  Pasos:
#    1. Verificar herramientas, variables de entorno y estado de git
#    2. Probar y compilar (backend + frontend)
#    3. Guardar la versión actualmente publicada (para poder volver a ella)
#    4. Aplicar migraciones de base de datos (Flyway → Aiven MySQL)
#    5. Desplegar backend en Render y esperar a que corra el commit nuevo
#    6. Desplegar frontend en Vercel
#    7. Verificar que ambos servicios responden (/health en el backend)
#
#  Si un paso falla: mensaje claro y código de salida distinto de 0.
#  Si falla después de empezar a publicar: vuelve automáticamente a la versión anterior.
#  Es idempotente: ejecutarlo varias veces no rompe nada.
# =============================================================================
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
BRANCH="main"

# Tiempos máximos de espera (segundos)
BACKEND_DEPLOY_TIMEOUT="${BACKEND_DEPLOY_TIMEOUT:-900}"   # Render compila Java: puede tardar
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"                    # el plan free "despierta" lento

# Estado para el rollback
PREV_BACKEND_SHA=""
PREV_FRONTEND_ID=""
BACKEND_TOUCHED=false
FRONTEND_TOUCHED=false
ROLLING_BACK=false

# -----------------------------------------------------------------------------
# Utilidades de salida
# -----------------------------------------------------------------------------
if [ -t 1 ]; then
  RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; BLUE=$'\e[34m'; BOLD=$'\e[1m'; RESET=$'\e[0m'
else
  RED=""; GREEN=""; YELLOW=""; BLUE=""; BOLD=""; RESET=""
fi

CURRENT_STEP="inicio"
START_TIME=$(date +%s)

step() { CURRENT_STEP="$1"; echo; echo "${BLUE}${BOLD}==> $1${RESET}"; }
info() { echo "    $1"; }
ok()   { echo "    ${GREEN}✔ $1${RESET}"; }
warn() { echo "    ${YELLOW}⚠ $1${RESET}"; }

fail() {
  trap - ERR
  echo >&2
  echo "${RED}${BOLD}✖ ERROR en el paso «${CURRENT_STEP}»:${RESET}" >&2
  echo "${RED}  $1${RESET}" >&2
  if [ "$ROLLING_BACK" = false ] && { $BACKEND_TOUCHED || $FRONTEND_TOUCHED; }; then
    rollback
  fi
  echo "${RED}${BOLD}  Despliegue abortado.${RESET}" >&2
  exit 1
}

# Cualquier comando que falle sin control también termina con mensaje claro
trap 'fail "El comando falló (línea $LINENO): $BASH_COMMAND"' ERR

# Espera hasta que una URL responda 200 y (opcional) contenga un texto
wait_for_url() {
  local url="$1" expected="${2:-}" timeout="$3" elapsed=0 body
  while [ "$elapsed" -lt "$timeout" ]; do
    if body=$(curl -fsS --max-time 30 "$url" 2>/dev/null); then
      if [ -z "$expected" ] || grep -q "$expected" <<<"$body"; then
        return 0
      fi
    fi
    sleep 10; elapsed=$((elapsed + 10))
    info "esperando $url ... (${elapsed}s/${timeout}s)"
  done
  return 1
}

# Commit que está corriendo el backend publicado (vacío si no responde)
backend_commit() {
  curl -fsS --max-time 30 "$BACKEND_URL/version" 2>/dev/null | grep -oE '"commit":"[^"]+"' | cut -d'"' -f4 || true
}

# ID del despliegue de Vercel que sirve hoy el dominio de producción
vercel_production_id() {
  curl -fsS --max-time 20 -H "Authorization: Bearer $VERCEL_TOKEN" \
    "https://api.vercel.com/v13/deployments/${FRONTEND_URL#https://}" 2>/dev/null \
    | grep -oE '"id":"dpl_[^"]+"' | head -1 | cut -d'"' -f4 || true
}

# -----------------------------------------------------------------------------
# Rollback: vuelve a la versión publicada antes de este despliegue
# -----------------------------------------------------------------------------
rollback() {
  ROLLING_BACK=true
  echo
  echo "${YELLOW}${BOLD}==> Rollback: restaurando la versión anterior${RESET}"

  if $FRONTEND_TOUCHED; then
    if [ -z "$PREV_FRONTEND_ID" ]; then
      warn "Frontend: no había versión anterior registrada"
    elif [ "$(vercel_production_id)" = "$PREV_FRONTEND_ID" ]; then
      ok "Frontend: producción ya sirve la versión anterior"
    elif (cd "$FRONTEND_DIR" && vercel promote "$PREV_FRONTEND_ID" --yes --token "$VERCEL_TOKEN" >/dev/null 2>&1); then
      ok "Frontend restaurado ($PREV_FRONTEND_ID)"
    else
      warn "No se pudo restaurar el frontend. Manual: vercel promote $PREV_FRONTEND_ID"
    fi
  fi

  if $BACKEND_TOUCHED; then
    if [ -z "$PREV_BACKEND_SHA" ] || [ "$PREV_BACKEND_SHA" = "local" ]; then
      warn "Backend: no había versión anterior registrada"
    elif [ "$PREV_BACKEND_SHA" = "$LOCAL_SHA" ]; then
      ok "Backend: la versión anterior es el mismo commit, no hay nada que revertir"
    else
      # Se despliega explícitamente el commit anterior: cancela/reemplaza cualquier build del commit nuevo
      if curl -fsS -o /dev/null -X POST "${RENDER_DEPLOY_HOOK_URL}&ref=${PREV_BACKEND_SHA}"; then
        info "Render: desplegando de nuevo el commit ${PREV_BACKEND_SHA:0:7}..."
        if wait_for_url "$BACKEND_URL/version" "$PREV_BACKEND_SHA" "$BACKEND_DEPLOY_TIMEOUT"; then
          ok "Backend restaurado al commit ${PREV_BACKEND_SHA:0:7}"
        else
          warn "El backend no volvió al commit ${PREV_BACKEND_SHA:0:7} a tiempo. Revisa el panel de Render."
        fi
      else
        warn "No se pudo pedir el rollback a Render. Revisa el panel de Render."
      fi
    fi
  fi

  warn "Las migraciones de BD no se revierten: deben ser compatibles con la versión anterior."
}

# =============================================================================
# 1. VERIFICACIONES PREVIAS
# =============================================================================
step "1/7 Verificando herramientas, variables y git"

for tool in git java curl node npm vercel; do
  command -v "$tool" >/dev/null 2>&1 || fail "Falta la herramienta '$tool'. Instálala y vuelve a intentar."
done
ok "Herramientas: git, java, curl, node, npm, vercel"

# Cargar .env si existe (en CI las variables llegan del entorno)
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1090
  source <(tr -d '\r' < "$ROOT_DIR/.env")
  set +a
  ok "Variables cargadas desde .env"
fi

REQUIRED_VARS=(DB_URL DB_USER DB_PASSWORD RENDER_DEPLOY_HOOK_URL BACKEND_URL VERCEL_TOKEN FRONTEND_URL)
missing=()
for var in "${REQUIRED_VARS[@]}"; do
  [ -n "${!var:-}" ] || missing+=("$var")
done
[ ${#missing[@]} -eq 0 ] || fail "Faltan variables de entorno: ${missing[*]}. Revisa .env (usa .env.example como guía)."
BACKEND_URL="${BACKEND_URL%/}"; FRONTEND_URL="${FRONTEND_URL%/}"
ok "Variables de entorno completas"

# Render despliega lo que está en GitHub: el código local debe estar subido
cd "$ROOT_DIR"
current_branch=$(git rev-parse --abbrev-ref HEAD)
[ "$current_branch" = "$BRANCH" ] || fail "Estás en la rama '$current_branch'. Cambia a '$BRANCH' (git checkout $BRANCH)."
[ -z "$(git status --porcelain --untracked-files=no)" ] || fail "Hay cambios sin commit. Haz 'git add' y 'git commit' antes de desplegar."
git fetch --quiet origin "$BRANCH" || fail "No se pudo conectar con GitHub (git fetch)."
LOCAL_SHA=$(git rev-parse HEAD)
REMOTE_SHA=$(git rev-parse "origin/$BRANCH")
[ "$LOCAL_SHA" = "$REMOTE_SHA" ] || fail "Tu código local no coincide con GitHub. Ejecuta 'git push' (o 'git pull') y vuelve a intentar."
ok "Git: rama $BRANCH, sin cambios pendientes, sincronizada con GitHub (${LOCAL_SHA:0:7})"

# =============================================================================
# 2. PRUEBAS Y COMPILACIÓN
# =============================================================================
step "2/7 Probando y compilando"

cd "$BACKEND_DIR"
TEST_LOG="$ROOT_DIR/.backend-test.log"
if ! ./mvnw -B test >"$TEST_LOG" 2>&1; then
  grep -E "^\[ERROR\]   |^\[ERROR\] Tests run:|COMPILATION ERROR|\.java:\[" "$TEST_LOG" | head -15 >&2
  fail "Las pruebas del backend fallaron. Detalle completo en .backend-test.log"
fi
ok "Backend: $(grep -E '^\[INFO\] Tests run:' "$TEST_LOG" | tail -1 | sed 's/^\[INFO\] //')"
rm -f "$TEST_LOG"

cd "$FRONTEND_DIR"
npm ci --silent --no-audit --no-fund || fail "No se pudieron instalar las dependencias del frontend (npm ci)."
VITE_API_URL="$BACKEND_URL" npm run build --silent >/dev/null || fail "El frontend no compila (npm run build)."
ok "Frontend: compila OK"

# =============================================================================
# 3. VERSIÓN ACTUAL (para rollback)
# =============================================================================
step "3/7 Registrando la versión publicada actualmente (para rollback)"

if wait_for_url "$BACKEND_URL/version" "" "$HEALTH_TIMEOUT"; then
  PREV_BACKEND_SHA=$(backend_commit)
fi
if [ -n "$PREV_BACKEND_SHA" ]; then
  ok "Backend actual: commit ${PREV_BACKEND_SHA:0:7}"
else
  warn "No se pudo leer la versión actual del backend: no habrá rollback del backend"
fi

PREV_FRONTEND_ID=$(vercel_production_id)
if [ -n "$PREV_FRONTEND_ID" ]; then
  ok "Frontend actual: $PREV_FRONTEND_ID"
else
  warn "No se pudo leer el despliegue actual de Vercel: no habrá rollback del frontend"
fi

# =============================================================================
# 4. MIGRACIONES
# =============================================================================
step "4/7 Aplicando migraciones de base de datos (Flyway)"

cd "$BACKEND_DIR"
./mvnw -q -B flyway:migrate \
  -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD" \
  || fail "Las migraciones fallaron. Revisa el error de Flyway (no se publicó nada)."
ok "Migraciones aplicadas (o ya al día)"

# =============================================================================
# 5. BACKEND → RENDER
# =============================================================================
step "5/7 Desplegando backend en Render"

BACKEND_TOUCHED=true
http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RENDER_DEPLOY_HOOK_URL") \
  || fail "No se pudo contactar a Render."
[[ "$http_code" =~ ^2 ]] || fail "Render rechazó el despliegue (HTTP $http_code). Revisa RENDER_DEPLOY_HOOK_URL."
ok "Despliegue solicitado a Render"

info "Esperando a que Render publique el commit ${LOCAL_SHA:0:7} (puede tardar varios minutos)..."
wait_for_url "$BACKEND_URL/version" "$LOCAL_SHA" "$BACKEND_DEPLOY_TIMEOUT" \
  || fail "Render no publicó el commit ${LOCAL_SHA:0:7} en ${BACKEND_DEPLOY_TIMEOUT}s. Revisa los logs en el panel de Render."
ok "Backend corriendo el commit ${LOCAL_SHA:0:7}"

# =============================================================================
# 6. FRONTEND → VERCEL
# =============================================================================
step "6/7 Desplegando frontend en Vercel"

cd "$FRONTEND_DIR"
FRONTEND_TOUCHED=true
vercel deploy --prod --yes --token "$VERCEL_TOKEN" \
  --build-env VITE_API_URL="$BACKEND_URL" >/dev/null 2>"$ROOT_DIR/.vercel-deploy.log" \
  || fail "Vercel no pudo desplegar el frontend. Detalle en .vercel-deploy.log"
rm -f "$ROOT_DIR/.vercel-deploy.log"

NEW_FRONTEND_ID=$(vercel_production_id)
[ -z "$PREV_FRONTEND_ID" ] || [ "$NEW_FRONTEND_ID" != "$PREV_FRONTEND_ID" ] \
  || fail "Vercel no asignó el nuevo despliegue a producción (¿el proyecto quedó en estado de rollback manual? usa 'vercel promote')."
ok "Frontend publicado en Vercel (${NEW_FRONTEND_ID:-?})"

# =============================================================================
# 7. VERIFICACIÓN FINAL
# =============================================================================
step "7/7 Verificando servicios publicados"

wait_for_url "$BACKEND_URL/health" '"status":"UP"' "$HEALTH_TIMEOUT" \
  || fail "El backend no responde UP en $BACKEND_URL/health."
ok "Backend  $BACKEND_URL/health → UP"

wait_for_url "$FRONTEND_URL" "Registro de Equipos Mineros" "$HEALTH_TIMEOUT" \
  || fail "El frontend no responde correctamente en $FRONTEND_URL."
ok "Frontend $FRONTEND_URL → OK"

cors=$(curl -s -o /dev/null -w "%{http_code}" -H "Origin: $FRONTEND_URL" "$BACKEND_URL/health")
[ "$cors" = "200" ] || fail "El backend rechaza al frontend (CORS, HTTP $cors). Agrega $FRONTEND_URL a CORS_ALLOWED_ORIGINS en Render."
ok "CORS: el frontend puede llamar al backend"

if [ "${SIMULATE_FAILURE:-}" = "1" ]; then
  fail "Fallo simulado (SIMULATE_FAILURE=1) para demostrar el rollback."
fi

trap - ERR
echo
echo "${GREEN}${BOLD}✔ Despliegue completado en $(( $(date +%s) - START_TIME ))s (commit ${LOCAL_SHA:0:7})${RESET}"
echo "    Frontend: $FRONTEND_URL"
echo "    Backend:  $BACKEND_URL"
