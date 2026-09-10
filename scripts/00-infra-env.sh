#!/usr/bin/env bash
# =====================================================================
#  00-infra-env.sh  ·  P2  ·  se corre ANTES de 03-balanceador.sh
#
#  Arma el infra.env que 03-balanceador.sh necesita, combinando:
#    · terraform output  del repo de P1 (transporte-infra/terraform)
#    · aws ec2 describe-instances  para los instance id de mv-prod-a/b,
#      que su Terraform no publica como output.
#
#  Mapeo (auditoria PENDIENTES-P2.md §1.1):
#    VPC_ID        <- output vpc_id
#    SUBNET_PUB_A  <- output subred_publica_id
#    SUBNET_PUB_B  <- output subred_publica_b_id
#    SG_ALB        <- output sg_alb
#    ID_MV_PROD_A  <- describe-instances tag:Name=mv-prod-a
#    ID_MV_PROD_B  <- describe-instances tag:Name=mv-prod-b
#    BD_HOST       <- output ip_privada_mv_bd   (lo usa el .env de MS2)
#
#  Uso:
#    ./scripts/00-infra-env.sh [ruta/a/transporte-infra/terraform]
#    TF_DIR=... REGION=us-east-1 ./scripts/00-infra-env.sh
#    ./scripts/00-infra-env.sh --force      # respalda el infra.env que ya exista
#
#  No crea ni modifica nada en AWS: solo lee.
# =====================================================================
set -euo pipefail

cd "$(dirname "$0")/.."
DESTINO="infra.env"
REGION="${REGION:-us-east-1}"

FORCE=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --force) FORCE=1 ;;
    *)       ARGS+=("$a") ;;
  esac
done

TF_DIR="${ARGS[0]:-${TF_DIR:-../transporte-infra/terraform}}"

morir () { echo "ERROR: $*" >&2; exit 1; }

# --- Comprobaciones previas: mejor fallar aca que dejar un infra.env a medias ---
command -v terraform >/dev/null || morir "no encuentro 'terraform' en el PATH."
command -v aws       >/dev/null || morir "no encuentro 'aws' en el PATH."

[[ -d "$TF_DIR" ]] || morir "no existe el directorio de Terraform: $TF_DIR
       Pasalo como argumento:  ./scripts/00-infra-env.sh /ruta/transporte-infra/terraform"

aws sts get-caller-identity --region "$REGION" >/dev/null 2>&1 \
  || morir "las credenciales de AWS no estan activas (proba: aws sts get-caller-identity)."

if [[ -e "$DESTINO" && "$FORCE" -eq 0 ]]; then
  morir "$DESTINO ya existe. Revisalo primero; si queres reemplazarlo, corre con --force
       (guarda una copia en $DESTINO.bak antes de escribir)."
fi

# --- Lectura ---------------------------------------------------------
salida_tf () {
  local nombre="$1" valor
  valor=$(terraform -chdir="$TF_DIR" output -raw "$nombre" 2>/dev/null || true)
  [[ -n "$valor" ]] || morir "el output '$nombre' no existe o esta vacio en $TF_DIR.
       Puede que P1 todavia no haya corrido 'terraform apply', o que el output se llame
       distinto: revisalo con  terraform -chdir=$TF_DIR output"
  printf '%s' "$valor"
}

id_instancia () {
  local etiqueta="$1" valor
  valor=$(aws ec2 describe-instances --region "$REGION" \
            --filters "Name=tag:Name,Values=$etiqueta" \
                      "Name=instance-state-name,Values=running" \
            --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null || true)
  [[ -n "$valor" && "$valor" != "None" ]] \
    || morir "no encontre ninguna instancia 'running' con el tag Name=$etiqueta en $REGION."
  [[ $(wc -w <<< "$valor") -eq 1 ]] \
    || morir "hay mas de una instancia 'running' con el tag Name=$etiqueta: $valor
       El balanceador necesita una sola. Apaga las de sobra o registrala a mano."
  printf '%s' "$valor"
}

echo "==> Terraform: $TF_DIR"
VPC_ID=$(salida_tf vpc_id)
SUBNET_PUB_A=$(salida_tf subred_publica_id)
SUBNET_PUB_B=$(salida_tf subred_publica_b_id)
SG_ALB=$(salida_tf sg_alb)
BD_HOST=$(salida_tf ip_privada_mv_bd)

echo "==> EC2 en $REGION"
ID_MV_PROD_A=$(id_instancia mv-prod-a)
ID_MV_PROD_B=$(id_instancia mv-prod-b)

# Las 2 subredes tienen que estar en AZ distintas o el ALB no se crea.
AZ_A=$(aws ec2 describe-subnets --region "$REGION" --subnet-ids "$SUBNET_PUB_A" \
         --query 'Subnets[0].AvailabilityZone' --output text)
AZ_B=$(aws ec2 describe-subnets --region "$REGION" --subnet-ids "$SUBNET_PUB_B" \
         --query 'Subnets[0].AvailabilityZone' --output text)
[[ "$AZ_A" != "$AZ_B" ]] \
  || morir "las dos subredes publicas estan en la misma AZ ($AZ_A). El ALB exige dos."

# --- Escritura: primero a un temporal, y recien al final al destino ---
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

{
  echo "# Generado por scripts/00-infra-env.sh el $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "REGION=$REGION"
  echo "VPC_ID=$VPC_ID"
  echo "SUBNET_PUB_A=$SUBNET_PUB_A          # $AZ_A"
  echo "SUBNET_PUB_B=$SUBNET_PUB_B          # $AZ_B"
  echo "SG_ALB=$SG_ALB"
  echo "ID_MV_PROD_A=$ID_MV_PROD_A"
  echo "ID_MV_PROD_B=$ID_MV_PROD_B"
  echo "BD_HOST=$BD_HOST                    # IP privada de mv-bd, para el .env de MS2"
} > "$TMP"

if [[ -e "$DESTINO" ]]; then
  cp "$DESTINO" "$DESTINO.bak"
  echo "==> copia previa en $DESTINO.bak"
fi
mv "$TMP" "$DESTINO"
trap - EXIT

echo
cat "$DESTINO"
echo
echo "Listo. Siguiente paso:  ./scripts/03-balanceador.sh"
