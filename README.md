# MS2 · Viajes

Microservicio SQL de la plataforma de transporte urbano.
**CS2032 Cloud Computing** — Proyecto Parcial, ciclo 2026-2 · Parte **P2**.

|               |                            |
| ------------- | -------------------------- |
| Lenguaje      | Java 17 · Spring Boot 3.3  |
| Base de datos | MySQL 8 · `viajes_db`      |
| Puerto        | `8002`                     |
| Prefijo       | `/ms2`                     |
| Swagger UI    | `/ms2/docs`                |
| OpenAPI JSON  | `/ms2/openapi.json`        |
| Health check  | `/ms2/health`              |

MS2 es **el microservicio que consume a otro**: `POST /ms2/viajes` valida
`pasajero_id` y `conductor_id` contra MS1 antes de crear el viaje.

## Levantar en local

```bash
cp .env.example .env
docker compose up --build
```

Luego abre <http://localhost:8002/ms2/docs>.

El compose de este repo levanta **MySQL + MS2 juntos** solo para desarrollo.
En AWS, MySQL vive en `mv-bd` y el compose del equipo declara solo el servicio `ms2`.

Mientras MS1 no exista, pon `VALIDAR_CON_MS1=false` en el `.env` y el POST
deja de llamar a MS1.

## Sin Docker

```bash
export BD_HOST=localhost BD_USUARIO=app_ms2 BD_CLAVE=cambiame
mvn spring-boot:run
```

## Endpoints

| Método | Ruta                        | Descripción                                                   |
| ------ | --------------------------- | ------------------------------------------------------------- |
| GET    | `/ms2/health`               | Health check del balanceador, no toca la BD                    |
| GET    | `/ms2/viajes`               | Listar con filtros `pasajero_id`, `conductor_id`, `estado`, `distrito_origen`, `distrito_destino`, `desde`, `hasta`, `page`, `limit` |
| GET    | `/ms2/viajes/{id}`          | Detalle del viaje con sus paradas · 404 si no existe          |
| POST   | `/ms2/viajes`               | Crear · **422** si MS1 responde 404 sobre el usuario o el conductor |
| PATCH  | `/ms2/viajes/{id}/estado`   | Cambiar estado · 409 si la transición no es válida            |
| GET    | `/ms2/tarifas`              | Catálogo de las 4 tarifas                                     |

### Prueba rápida

```bash
curl -X POST http://localhost:8002/ms2/viajes \
  -H 'content-type: application/json' \
  -d '{"pasajero_id":552,"conductor_id":81,"vehiculo_id":81,"tarifa_id":2,
       "metodo_pago":"tarjeta","distrito_origen":"Miraflores",
       "distrito_destino":"San Isidro","distancia_km":6.40,"duracion_min":18,
       "paradas":[{"orden":1,"distrito":"Lince","direccion":"Av. Arequipa 2100"}]}'

curl "http://localhost:8002/ms2/viajes?conductor_id=81&estado=solicitado&limit=5"

curl -X PATCH http://localhost:8002/ms2/viajes/1/estado \
  -H 'content-type: application/json' -d '{"estado":"en_curso"}'

curl -X PATCH http://localhost:8002/ms2/viajes/1/estado \
  -H 'content-type: application/json' \
  -d '{"estado":"finalizado","distancia_km":6.40,"duracion_min":18}'
```

## Esquema de la base

El catálogo de `tarifas` (4 filas fijas) usa los mismos valores que el seed de P3
(`seed/seed_mysql.py`, repo MS3). Ese script hace `TRUNCATE` y reinserta, así que
en un entorno sembrado mandan los suyos.

El DDL está en [`docs/01-schema.sql`](docs/01-schema.sql) — cópialo a
`transporte-infra/init/mysql/01-schema.sql`.
El diagrama E/R está en [`docs/er.mmd`](docs/er.mmd) (Mermaid, GitHub lo renderiza solo).

Tres tablas, dos relaciones: `tarifas` 1—N `viajes` 1—N `paradas`.
La tabla que carga los ≥20 000 registros de fake data es **`viajes`** (ids 1..25000).

## Máquina de estados

```
solicitado ──> en_curso ──> finalizado
     │             │
     └──> cancelado <┘
```

Cualquier otra transición devuelve `409`. Al pasar a `finalizado` se calcula el monto:

```
subtotal = tarifa_base + km*costo_por_km + min*costo_por_minuto
monto    = subtotal * (hora pico ? multiplicador_hora_pico : 1.00)
```

`multiplicador_hora_pico` **multiplica el subtotal completo**, no suma un recargo.
Son hora pico las 07, 08, 09, 18, 19 y 20 **en UTC**, evaluadas sobre `iniciado_en`
(si viniera `null`, sobre `solicitado_en`). La franja y la fórmula son las mismas de
`seed/seed_mysql.py` en el repo de MS3: si no coinciden, los 25 000 montos sembrados
dejan de cuadrar con lo que calcula MS2.

El redondeo es `HALF_UP` a 2 decimales, una sola vez y al final.

Ejemplo: tarifa 2 `estandar`, 10.00 km, 30 min, iniciado 08:15 UTC →
subtotal `5.50 + 14.50 + 9.60 = 29.60`, monto `29.60 * 1.35 = 39.96`.
El mismo viaje a las 13:15 UTC paga 29.60.

## Llamada inter-servicio (Contrato §5.1 y §10.1)

`POST /ms2/viajes` llama a `GET /ms1/usuarios/{id}` y `GET /ms1/conductores/{id}`
por el balanceador interno, con **timeout 5 s y un reintento**.

| Respuesta de MS1 | Qué hace MS2                                                    |
| ---------------- | --------------------------------------------------------------- |
| 200              | sigue normal                                                    |
| 404              | responde **422** `{"error":"referencia invalida","detalle":"…"}` |
| no responde      | crea el viaje igual y agrega `"advertencias":["ms1 no disponible"]` — nunca 500 |

## Variables de entorno

| Variable          | Ejemplo                          | Para qué                                  |
| ----------------- | -------------------------------- | ----------------------------------------- |
| `PORT`            | `8002`                           | Puerto de escucha                         |
| `BD_HOST`         | `10.0.2.50`                      | IP privada de `mv-bd`                     |
| `BD_USUARIO`      | `app_ms2`                        | Nunca root desde la app (Contrato §9)     |
| `BD_CLAVE`        | `***`                            | Va en `.env`, nunca en el repo            |
| `MS1_URL`         | `http://<alb-dns>`               | Base de MS1 vía balanceador interno       |
| `VALIDAR_CON_MS1` | `false`                          | Apaga la validación cruzada               |
| `PUBLIC_URL`      | `https://…execute-api…`          | `servers:` del Swagger (lo da P3)         |

## Balanceador (lo otro que le toca a P2)

[`scripts/03-balanceador.sh`](scripts/03-balanceador.sh) → cópialo a
`transporte-infra/scripts/`. Crea el ALB **interno**, 5 target groups
(health check `GET /msN/health` cada 30 s) y las reglas de path `/msN/* → tg-msN`.
Escribe `ALB_DNS` y los ARNs en `infra.env`.

Requiere que `01-red.sh` y `02-maquinas.sh` ya hayan dejado en `infra.env`:
`VPC_ID`, `SUBNET_PUB_A`, `SUBNET_PUB_B`, `SG_ALB`, `ID_MV_PROD_A`, `ID_MV_PROD_B`.
Si en tu `infra.env` se llaman distinto, ajusta solo el bloque de variables del inicio.

```bash
./scripts/03-balanceador.sh
# desde una MV de produccion:
curl http://<alb-dns>/ms2/health
```

## Despliegue

1. `docker build -t <usuario>/transporte-ms2:1.0 . && docker push <usuario>/transporte-ms2:1.0`
2. En cada MV de producción, el compose del equipo hace `pull` de esa imagen.
3. El balanceador enruta `/ms2/*` al target group del puerto 8002.
4. El API Gateway (HTTP API + VPC Link) expone `https://…/ms2/*`.

## Notas de implementación

- **CORS no se configura aquí.** Va una sola vez en el API Gateway (Contrato §8).
- `ddl-auto: validate` a propósito: el DDL lo crea `init/mysql/01-schema.sql`, no Hibernate.
  Si el arranque falla con error de validación, el esquema y las entidades se desincronizaron.
- Todas las fechas se guardan y devuelven en UTC, formato `2026-08-14T21:58:40Z`.
- El JSON sale en `snake_case` (`spring.jackson.property-naming-strategy: SNAKE_CASE`).
- `limit` máximo 100; si piden más, se recorta.
