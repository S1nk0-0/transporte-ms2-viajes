#!/usr/bin/env bash
# =====================================================================
#  03-balanceador.sh  ·  P2  ·  Fase 3
#  Crea el ALB INTERNO, los 5 target groups y las reglas por path.
#  Va en transporte-infra/scripts/ y se corre DESPUES de 01-red.sh y
#  02-maquinas.sh, que dejan sus resultados en infra.env.
#
#  Uso:  ./scripts/03-balanceador.sh
# =====================================================================
set -euo pipefail

cd "$(dirname "$0")/.."
[[ -f config.env ]] && source config.env
[[ -f infra.env  ]] || { echo "Falta infra.env. Corre 01-red.sh y 02-maquinas.sh primero."; exit 1; }
source infra.env

# --- Variables que 01/02 deben haber dejado en infra.env --------------
#   VPC_ID, SUBNET_PUB_A, SUBNET_PUB_B, SG_ALB, ID_MV_PROD_A, ID_MV_PROD_B
# Si en tu infra.env se llaman distinto, ajusta SOLO este bloque.
: "${VPC_ID:?falta VPC_ID en infra.env}"
: "${SUBNET_PUB_A:?falta SUBNET_PUB_A en infra.env}"
: "${SUBNET_PUB_B:?falta SUBNET_PUB_B en infra.env}"
: "${SG_ALB:?falta SG_ALB en infra.env}"
: "${ID_MV_PROD_A:?falta ID_MV_PROD_A en infra.env}"
: "${ID_MV_PROD_B:?falta ID_MV_PROD_B en infra.env}"

REGION="${REGION:-us-east-1}"
NOMBRE_ALB="${NOMBRE_ALB:-transporte-alb}"

echo "==> Region $REGION · VPC $VPC_ID"

# ---------------------------------------------------------------------
# 1. ALB interno
# ---------------------------------------------------------------------
ALB_ARN=$(aws elbv2 describe-load-balancers --names "$NOMBRE_ALB" --region "$REGION" \
            --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || true)

if [[ -z "$ALB_ARN" || "$ALB_ARN" == "None" ]]; then
  echo "==> Creando ALB interno $NOMBRE_ALB"
  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "$NOMBRE_ALB" \
    --type application \
    --scheme internal \
    --ip-address-type ipv4 \
    --subnets "$SUBNET_PUB_A" "$SUBNET_PUB_B" \
    --security-groups "$SG_ALB" \
    --region "$REGION" \
    --query 'LoadBalancers[0].LoadBalancerArn' --output text)
else
  echo "==> El ALB ya existe, se reutiliza"
fi

echo "==> Esperando a que el ALB este activo (puede tardar ~2 min)"
aws elbv2 wait load-balancer-available --load-balancer-arns "$ALB_ARN" --region "$REGION"

ALB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" --region "$REGION" \
            --query 'LoadBalancers[0].DNSName' --output text)
echo "==> ALB DNS: $ALB_DNS"

# ---------------------------------------------------------------------
# 2. Un target group por microservicio + registro de las 2 MVs
#    Health check: GET /msN/health cada 30 s (Contrato §2.2)
# ---------------------------------------------------------------------
declare -a TG_ARNS=()

crear_tg () {
  local ms="$1" puerto="$2"
  local nombre="tg-${ms}"
  local arn
  arn=$(aws elbv2 describe-target-groups --names "$nombre" --region "$REGION" \
          --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)

  if [[ -z "$arn" || "$arn" == "None" ]]; then
    echo "==> Creando target group $nombre (puerto $puerto)"
    arn=$(aws elbv2 create-target-group \
      --name "$nombre" \
      --protocol HTTP --port "$puerto" \
      --vpc-id "$VPC_ID" \
      --target-type instance \
      --health-check-protocol HTTP \
      --health-check-path "/${ms}/health" \
      --health-check-interval-seconds 30 \
      --health-check-timeout-seconds 5 \
      --healthy-threshold-count 2 \
      --unhealthy-threshold-count 3 \
      --matcher HttpCode=200 \
      --region "$REGION" \
      --query 'TargetGroups[0].TargetGroupArn' --output text)
  fi

  aws elbv2 register-targets --target-group-arn "$arn" --region "$REGION" \
    --targets Id="$ID_MV_PROD_A",Port="$puerto" Id="$ID_MV_PROD_B",Port="$puerto" >/dev/null
  echo "    registradas mv-prod-a y mv-prod-b en $nombre"
  echo "$arn"
}

TG_MS1=$(crear_tg ms1 8001 | tail -1)
TG_MS2=$(crear_tg ms2 8002 | tail -1)
TG_MS3=$(crear_tg ms3 8003 | tail -1)
TG_MS4=$(crear_tg ms4 8004 | tail -1)
TG_MS5=$(crear_tg ms5 8005 | tail -1)

# ---------------------------------------------------------------------
# 3. Listener HTTP :80  (el HTTPS lo pone el API Gateway por delante)
#    Accion por defecto: 404 en texto plano.
# ---------------------------------------------------------------------
LISTENER_ARN=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --region "$REGION" \
                 --query 'Listeners[?Port==`80`].ListenerArn | [0]' --output text 2>/dev/null || true)

if [[ -z "$LISTENER_ARN" || "$LISTENER_ARN" == "None" ]]; then
  echo "==> Creando listener :80"
  LISTENER_ARN=$(aws elbv2 create-listener \
    --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP --port 80 \
    --default-actions 'Type=fixed-response,FixedResponseConfig={StatusCode=404,ContentType=application/json,MessageBody="{\"error\":\"ruta no enrutada por el balanceador\"}"}' \
    --region "$REGION" \
    --query 'Listeners[0].ListenerArn' --output text)
fi

# ---------------------------------------------------------------------
# 4. Reglas por path: /msN/* -> tg-msN
# ---------------------------------------------------------------------
regla () {
  local prioridad="$1" ms="$2" tg="$3"
  # borra la regla previa con esa prioridad, si existe
  local vieja
  vieja=$(aws elbv2 describe-rules --listener-arn "$LISTENER_ARN" --region "$REGION" \
            --query "Rules[?Priority=='${prioridad}'].RuleArn | [0]" --output text 2>/dev/null || true)
  [[ -n "$vieja" && "$vieja" != "None" ]] && aws elbv2 delete-rule --rule-arn "$vieja" --region "$REGION" >/dev/null

  aws elbv2 create-rule \
    --listener-arn "$LISTENER_ARN" \
    --priority "$prioridad" \
    --conditions "Field=path-pattern,Values=/${ms}/*" \
    --actions "Type=forward,TargetGroupArn=${tg}" \
    --region "$REGION" >/dev/null
  echo "    /${ms}/* -> tg-${ms}"
}

echo "==> Reglas de enrutamiento"
regla 10 ms1 "$TG_MS1"
regla 20 ms2 "$TG_MS2"
regla 30 ms3 "$TG_MS3"
regla 40 ms4 "$TG_MS4"
regla 50 ms5 "$TG_MS5"

# ---------------------------------------------------------------------
# 5. Guardar resultados para los siguientes scripts
# ---------------------------------------------------------------------
{
  echo "ALB_ARN=$ALB_ARN"
  echo "ALB_DNS=$ALB_DNS"
  echo "LISTENER_ARN=$LISTENER_ARN"
  echo "TG_MS1=$TG_MS1"
  echo "TG_MS2=$TG_MS2"
  echo "TG_MS3=$TG_MS3"
  echo "TG_MS4=$TG_MS4"
  echo "TG_MS5=$TG_MS5"
} >> infra.env

cat <<FIN

=====================================================================
 Balanceador listo.

   ALB DNS   http://$ALB_DNS
   MS1_URL   http://$ALB_DNS      <- ponlo en el .env de MS2
   Prueba    curl http://$ALB_DNS/ms2/health   (desde una MV, es interno)

 Este DNS va en:
   · CONTRATO.md §9  (campo dns: ____)
   · el .env de cada microservicio que llame a otro
   · el VPC Link del API Gateway que arma P3
=====================================================================
FIN
