#!/usr/bin/env bash
# =============================================================================
#  deploy.sh — Despliega backend (Render) + frontend (Vercel) con un solo comando
#
#  Uso:   ./deploy.sh
#
#  Pasos:
#    1. Verificar herramientas, variables de entorno y estado de git
#    2. Probar y compilar (backend + frontend)
#    3. Aplicar migraciones de base de datos (Flyway → Aiven MySQL)
#    4. Desplegar backend en Render y esperar a que corra el commit nuevo
#    5. Desplegar frontend en Vercel
#    6. Verificar que ambos servicios responden (/health en el backend)
#
#  Si cualquier paso falla: mensaje claro y código de salida distinto de 0.
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
  echo >&2
  echo "${RED}${BOLD}✖ ERROR en el paso «${CURRENT_STEP}»:${RESET}" >&2
  echo "${RED}  $1${RESET}" >&2
  echo "${RED}  Despliegue abortado.${RESET}" >&2
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

# =============================================================================
# 1. VERIFICACIONES PREVIAS
# =============================================================================
step "1/6 Verificando herramientas, variables y git"

for tool in git java curl node npm vercel; do
  command -v "$tool" >/dev/null 2>&1 || fail "Falta la herramienta '$tool'. Instálala y vuelve a intentar."
done
ok "Herramientas: git, java, curl, node, npm, vercel"

# Cargar .env si existe (en CI las variables llegan del entorno)
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
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
step "2/6 Probando y compilando"

cd "$BACKEND_DIR"
./mvnw -q -B test || fail "Las pruebas del backend fallaron. Revisa la salida de arriba."
ok "Backend: pruebas OK"

cd "$FRONTEND_DIR"
npm ci --silent --no-audit --no-fund || fail "No se pudieron instalar las dependencias del frontend (npm ci)."
VITE_API_URL="$BACKEND_URL" npm run build --silent >/dev/null || fail "El frontend no compila (npm run build)."
ok "Frontend: compila OK"

# =============================================================================
# 3. MIGRACIONES
# =============================================================================
step "3/6 Aplicando migraciones de base de datos (Flyway)"

cd "$BACKEND_DIR"
./mvnw -q -B flyway:migrate \
  -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD" \
  || fail "Las migraciones fallaron. La base de datos no se modificó parcialmente: revisa el error de Flyway."
ok "Migraciones aplicadas (o ya al día)"

# =============================================================================
# 4. BACKEND → RENDER
# =============================================================================
step "4/6 Desplegando backend en Render"

http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RENDER_DEPLOY_HOOK_URL") \
  || fail "No se pudo contactar a Render."
[[ "$http_code" =~ ^2 ]] || fail "Render rechazó el despliegue (HTTP $http_code). Revisa RENDER_DEPLOY_HOOK_URL."
ok "Despliegue solicitado a Render"

info "Esperando a que Render publique el commit ${LOCAL_SHA:0:7} (puede tardar varios minutos)..."
wait_for_url "$BACKEND_URL/version" "$LOCAL_SHA" "$BACKEND_DEPLOY_TIMEOUT" \
  || fail "Render no publicó el commit ${LOCAL_SHA:0:7} en ${BACKEND_DEPLOY_TIMEOUT}s. Revisa los logs en el panel de Render (la versión anterior sigue activa)."
ok "Backend corriendo el commit ${LOCAL_SHA:0:7}"

# =============================================================================
# 5. FRONTEND → VERCEL
# =============================================================================
step "5/6 Desplegando frontend en Vercel"

cd "$FRONTEND_DIR"
vercel deploy --prod --yes --token "$VERCEL_TOKEN" \
  --build-env VITE_API_URL="$BACKEND_URL" >/dev/null 2>"$ROOT_DIR/.vercel-deploy.log" \
  || fail "Vercel no pudo desplegar el frontend. Detalle en .vercel-deploy.log"
rm -f "$ROOT_DIR/.vercel-deploy.log"
ok "Frontend publicado en Vercel"

# =============================================================================
# 6. VERIFICACIÓN FINAL
# =============================================================================
step "6/6 Verificando servicios publicados"

wait_for_url "$BACKEND_URL/health" '"status":"UP"' "$HEALTH_TIMEOUT" \
  || fail "El backend no responde UP en $BACKEND_URL/health."
ok "Backend  $BACKEND_URL/health → UP"

wait_for_url "$FRONTEND_URL" "Registro de Equipos Mineros" "$HEALTH_TIMEOUT" \
  || fail "El frontend no responde correctamente en $FRONTEND_URL."
ok "Frontend $FRONTEND_URL → OK"

cors=$(curl -s -o /dev/null -w "%{http_code}" -H "Origin: $FRONTEND_URL" "$BACKEND_URL/health")
[ "$cors" = "200" ] || fail "El backend rechaza al frontend (CORS, HTTP $cors). Agrega $FRONTEND_URL a CORS_ALLOWED_ORIGINS en Render."
ok "CORS: el frontend puede llamar al backend"

trap - ERR
echo
echo "${GREEN}${BOLD}✔ Despliegue completado en $(( $(date +%s) - START_TIME ))s (commit ${LOCAL_SHA:0:7})${RESET}"
echo "    Frontend: $FRONTEND_URL"
echo "    Backend:  $BACKEND_URL"
