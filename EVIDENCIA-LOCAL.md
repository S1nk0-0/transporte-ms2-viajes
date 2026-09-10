# Evidencia local de MS2 — Hito 1

Capturado el **9 de setiembre de 2026** (las marcas de tiempo de las respuestas están en
UTC, por eso aparecen como 2026-09-10T00:0x:xxZ).

Todo corre en la laptop, sin AWS: MySQL 8 y MS2 en Docker, sembrados con el
`seed_mysql.py` de P3 (rama `main`, PR #1 ya mergeado). Cubre dos entregables del
enunciado que no dependen de que P1 aplique Terraform:

- **≥20 000 registros en una tabla**: 25 000 filas en `viajes`.
- **Un microservicio dockerizado y conectado a su base, respondiendo**.

---

## 0. Cómo se levantó el stack

```bash
cd transporte-ms2-viajes
docker compose up -d --build
```

```
NAME         IMAGE                       SERVICE   STATUS                    PORTS
ms2-mysql    mysql:8                     mysql     Up (healthy)              0.0.0.0:3306->3306/tcp
ms2-viajes   transporte-ms2-viajes-ms2   ms2       Up                        0.0.0.0:8002->8002/tcp
```

```bash
curl -s http://localhost:8002/ms2/health
```
```json
{"status":"ok","servicio":"ms2"}
```

> **Nota de operación.** En el primer intento MS2 no arrancó:
> ```
> SchemaManagementException: Schema-validation: missing column [multiplicador_hora_pico] in table [tarifas]
> ```
> El volumen `mysql_data` era de una sesión anterior y traía el esquema viejo. El
> `docker-entrypoint-initdb.d` de MySQL **sólo corre cuando se crea el volumen**, así que
> reemplazar el `.sql` no basta:
> ```bash
> docker compose down -v && docker compose up -d
> ```
> Es exactamente el mismo problema que va a tener `mv-bd` en AWS cuando se actualice
> `transporte-infra/init/mysql/01-schema.sql`.

---

## 1. Siembra de la base

El seed hace `TRUNCATE` de `tarifas`, `viajes` y `paradas`, así que **se siembra primero y
se demuestra después**.

Como en esta máquina no hay Python en el `PATH`, el script se corrió sin instalar nada,
dentro de un contenedor conectado a la red del compose, montando la carpeta `seed/` del
repo de MS3 en sólo lectura (el script no se modificó):

```bash
docker run --rm --network transporte-ms2-viajes_default \
  -v "<clone-de-MS3>/seed:/seed:ro" -w /work \
  -e MYSQL_HOST=mysql -e MYSQL_USER=app_ms2 -e MYSQL_PASS=cambiame -e MYSQL_DB=viajes_db \
  python:3.12-slim \
  sh -c "cp /seed/*.py /work/ && pip install --quiet faker==30.0.0 mysql-connector-python==9.0.0 && python seed_mysql.py"
```

Salida:

```
Vaciando tablas...
  tarifas: 4
  2,000 viajes
  4,000 viajes
  ...
  24,000 viajes
OK  tarifas: 4
OK  viajes: 25,000
OK  paradas: 14,426
```

El equivalente que documenta P3 en `seed/README.md`, para una MV con Python instalado:

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
MYSQL_HOST=<ip> MYSQL_USER=app_ms2 MYSQL_PASS=*** MYSQL_DB=viajes_db python3 seed_mysql.py
```

---

## 2. `SELECT COUNT(*)` de las tres tablas

```bash
docker exec ms2-mysql mysql -uroot -p*** viajes_db -e "
  SELECT 'tarifas' AS tabla, COUNT(*) AS filas FROM tarifas
  UNION ALL SELECT 'viajes',  COUNT(*) FROM viajes
  UNION ALL SELECT 'paradas', COUNT(*) FROM paradas;"
```

```
tabla     filas
tarifas       4
viajes    25000
paradas   14426
```

Coherencia del dataset, sobre las 25 000 filas sembradas (`WHERE id <= 25000`):

```
estado      filas
cancelado    1499
finalizado  23501

Ventana temporal   2026-03-01 00:07:00 .. 2026-08-31 23:57:00  (UTC)
pasajero_id        3 .. 20000     (rango acordado 1..20000)
conductor_id       1 .. 600       (rango acordado 1..600)
```

Este `COUNT(*)` se tomó recién sembrada la base. Los dos viajes de demostración del §5 la
dejan hoy en 25 002 filas; para volver al número redondo basta con correr el seed otra vez.

---

## 3. `GET /ms2/tarifas`

```bash
curl -s http://localhost:8002/ms2/tarifas
```

```json
[{"id":1,"tipo_servicio":"economico","tarifa_base":4.00,"costo_por_km":1.10,"costo_por_minuto":0.25,"multiplicador_hora_pico":1.20,"activa":true},
 {"id":2,"tipo_servicio":"estandar","tarifa_base":5.50,"costo_por_km":1.45,"costo_por_minuto":0.32,"multiplicador_hora_pico":1.35,"activa":true},
 {"id":3,"tipo_servicio":"confort","tarifa_base":8.00,"costo_por_km":1.95,"costo_por_minuto":0.45,"multiplicador_hora_pico":1.50,"activa":true},
 {"id":4,"tipo_servicio":"xl","tarifa_base":11.00,"costo_por_km":2.40,"costo_por_minuto":0.55,"multiplicador_hora_pico":1.60,"activa":true}]
```

Los cuatro valores son los que inserta el seed de P3, o sea que el catálogo del DDL y el
del fake data coinciden.

---

## 4. `GET /ms2/viajes?limit=5`

```bash
curl -s "http://localhost:8002/ms2/viajes?limit=5"
```

```json
{"total":25000,"page":1,"limit":5,"items":[
 {"id":22234,"pasajero_id":695,"conductor_id":101,"vehiculo_id":101,"tarifa_id":2,
  "tipo_servicio":"estandar","estado":"finalizado","metodo_pago":"billetera",
  "distrito_origen":"San Isidro","distrito_destino":"Jesús María",
  "direccion_origen":"Glorieta de Feliciano Casanova 462 Piso 1 ",
  "direccion_destino":"Via Gema Priego 815 Piso 0 ",
  "distancia_km":10.09,"duracion_min":25,"monto_total":28.13,
  "solicitado_en":"2026-08-31T23:57:00Z","iniciado_en":"2026-09-01T00:05:00Z",
  "finalizado_en":"2026-09-01T00:30:00Z","paradas":[]},
 {"id":23782,"pasajero_id":11783,"conductor_id":236,"vehiculo_id":236,"tarifa_id":3,
  "tipo_servicio":"confort","estado":"cancelado","metodo_pago":"efectivo",
  "distrito_origen":"La Molina","distrito_destino":"Surco",
  "distancia_km":23.17,"duracion_min":90,"monto_total":93.68,
  "solicitado_en":"2026-08-31T23:50:00Z","iniciado_en":null,"finalizado_en":null,
  "paradas":[{"id":13730,"orden":1,"distrito":"San Isidro",
              "direccion":"Pasaje de Yéssica Gilabert 43",
              "latitud":-12.136789,"longitud":-77.142459,
              "llegada_en":"2026-08-14T05:23:00Z"}]}
 ]}
```

*(recortado a 2 de los 5 items; la respuesta completa trae los 5)*

Lo que demuestra esta sola respuesta: el formato de listado del contrato
(`{total, page, limit, items}`), las fechas en ISO 8601 UTC con `Z`, los nombres en
`snake_case`, los distritos con tilde, y las paradas anidadas en el viaje.

---

## 5. Ciclo completo `solicitado → en_curso → finalizado`

### 5.1 Caso fuera de hora pico

```bash
curl -s -i -X POST http://localhost:8002/ms2/viajes \
  -H 'content-type: application/json' \
  -d '{"pasajero_id":552,"conductor_id":81,"vehiculo_id":81,"tarifa_id":2,
       "metodo_pago":"tarjeta","distrito_origen":"Miraflores",
       "distrito_destino":"San Isidro","direccion_origen":"Av. Larco 100",
       "direccion_destino":"Av. Javier Prado 200",
       "paradas":[{"orden":1,"distrito":"Lince","direccion":"Av. Arequipa 2100"}]}'
```

```
HTTP/1.1 201
Location: /ms2/viajes/25001
```
```json
{"id":25001,"estado":"solicitado","monto_total":null,
 "solicitado_en":"2026-09-10T00:00:59Z","iniciado_en":null,"finalizado_en":null,
 "paradas":[{"id":14427,"orden":1,"distrito":"Lince","direccion":"Av. Arequipa 2100"}]}
```

El id **25001** sale justo después de las 25 000 filas sembradas, y la respuesta **no trae
`advertencias`**: en este entorno `VALIDAR_CON_MS1=false`, así que no se llamó a MS1.

```bash
curl -s -X PATCH http://localhost:8002/ms2/viajes/25001/estado \
  -H 'content-type: application/json' -d '{"estado":"en_curso"}'
```
```json
{"id":25001,"estado":"en_curso","iniciado_en":"2026-09-10T00:01:10Z","monto_total":null}
```

```bash
curl -s -X PATCH http://localhost:8002/ms2/viajes/25001/estado \
  -H 'content-type: application/json' \
  -d '{"estado":"finalizado","distancia_km":10.00,"duracion_min":30}'
```
```json
{"id":25001,"estado":"finalizado","distancia_km":10.00,"duracion_min":30,
 "monto_total":29.60,"finalizado_en":"2026-09-10T00:01:10Z"}
```

Cuenta: `5.50 + 10.00×1.45 + 30×0.32 = 29.60`. El viaje empezó a las 00:01 UTC, fuera de
la franja pico, así que el multiplicador es 1.00.

### 5.2 El mismo viaje en hora pico

Para forzar la franja pico hay que fijar `iniciado_en` por SQL: la aplicación lo pone con
la hora actual.

```bash
# POST + PATCH en_curso -> id 25002
docker exec ms2-mysql mysql -uroot -p*** viajes_db \
  -e "UPDATE viajes SET iniciado_en='2026-09-09 08:15:00' WHERE id=25002;"

curl -s -X PATCH http://localhost:8002/ms2/viajes/25002/estado \
  -H 'content-type: application/json' \
  -d '{"estado":"finalizado","distancia_km":10.00,"duracion_min":30}'
```
```json
{"id":25002,"estado":"finalizado","distancia_km":10.00,"duracion_min":30,
 "monto_total":39.96,"iniciado_en":"2026-09-09T08:15:00Z"}
```

Cuenta: subtotal 29.60 × 1.35 = **39.96**. Mismo viaje, 10.36 soles más por empezar dentro
de la franja `{7,8,9,18,19,20}` UTC. Es el caso de control del proyecto.

### 5.3 Transición inválida

```bash
curl -s -X PATCH http://localhost:8002/ms2/viajes/25001/estado \
  -H 'content-type: application/json' -d '{"estado":"en_curso"}'
```
```
HTTP/1.1 409
```
```json
{"error":"conflicto de estado","detalle":"no se puede pasar de finalizado a en_curso"}
```

---

## 6. Los 25 000 montos sembrados contra la fórmula de MS2

Prueba de que el seed de P3 y `ViajeService.calcularMonto()` calculan lo mismo:

```sql
SELECT COUNT(*) AS filas_comparadas,
       SUM(ROUND((t.tarifa_base + v.distancia_km*t.costo_por_km + v.duracion_min*t.costo_por_minuto)
           * IF(HOUR(v.iniciado_en) IN (7,8,9,18,19,20), t.multiplicador_hora_pico, 1.0), 2)
           <> v.monto_total) AS discrepancias
FROM viajes v JOIN tarifas t ON t.id = v.tarifa_id
WHERE v.iniciado_en IS NOT NULL AND v.id <= 25000;
```

```
filas_comparadas  discrepancias
23501             498
```

Las 498 son **todas de exactamente −0,01 sol** (ninguna diferencia supera un céntimo,
verificado). La causa es el modo de redondeo, no la fórmula:

- El seed usa el `round()` de Python, que redondea el empate al par (*banker's rounding*):
  `round(8.555, 2)` → `8.55`.
- MS2 usa `HALF_UP` (`ViajeService.calcularMonto()`), igual que `ROUND()` de MySQL:
  `8.555` → `8.56`.

Sólo se nota cuando el subtotal cae exacto en medio céntimo, que pasa en el 2,1 % de las
filas. Reparto: 162 de tarifa 1, 226 de tarifa 2, 110 de tarifa 3, 0 de tarifa 4. Suma
total de la diferencia sobre las 25 000 filas: **−4,98 soles**.

No afecta al Hito 1 ni bloquea nada: los montos sembrados quedan como están y MS2 nunca
los recalcula (sólo calcula el monto del viaje que él mismo finaliza). Queda anotado
porque es la misma familia de problema que el multiplicador, a escala de un céntimo, y
conviene decidir en equipo si se deja así o si el seed pasa a `Decimal` con `ROUND_HALF_UP`.

---

## 7. Capturas que tengo que sacar yo

El stack quedó levantado. Las cuatro primeras son las que pide el informe; la última es
opcional.

| # | Qué capturar | Dónde |
|---|--------------|-------|
| 1 | **Swagger UI completo**, con los 6 endpoints plegados y visible el título "MS2 - Viajes" | <http://localhost:8002/ms2/docs> |
| 2 | **`POST /ms2/viajes` desplegado** en Swagger, mostrando el ejemplo del body y las respuestas 201 / 400 / 422 | <http://localhost:8002/ms2/docs> → sección `viajes` → `POST /viajes` |
| 3 | **El listado con las 25 000 filas**: se ve `"total":25000` arriba de todo | <http://localhost:8002/ms2/viajes?limit=5> |
| 4 | **El catálogo de tarifas** con `multiplicador_hora_pico` | <http://localhost:8002/ms2/tarifas> |
| 5 | La terminal con el `SELECT COUNT(*)` dando 25 000 (§2) y con `docker compose ps` mostrando los dos contenedores arriba | terminal |
| 6 | *(opcional, es la más vistosa)* el ciclo de §5: el 201 con `Location`, y el `monto_total: 39.96` del caso en hora pico | terminal |

En Firefox y Chrome el JSON se ve formateado solo; si sale en una sola línea, la pestaña
"Raw/Pretty print" del navegador lo arregla. Para la #3, conviene hacer zoom hasta que se
lea `"total":25000` sin ampliar la imagen después.
