# gaslt-service

A small, deployable HTTP service that exposes the **sospeso** program's on-chain
state as JSON. It is the [gaslt JVM toolkit](../README.md)'s service module: it
reuses the `gaslt-java` SDK to decode accounts and reads live mainnet through a
public RPC endpoint (no API key required). The HTTP layer is the JDK's built-in
`HttpServer`, so the only runtime dependency is the SDK and its solanaj transitive.

## Endpoints

| Method & path | Returns |
|---------------|---------|
| `GET /health` | Liveness plus which program and RPC this instance reads (no RPC call). |
| `GET /pool/{address}` | A decoded pool account, or `404 pool_not_found`. |
| `GET /registry` | The singleton bar registry, or `404 registry_not_initialised`. |
| `GET /bridge/{authority}` | A bridge account derived from `["bridge", authority]`, or `404 bridge_not_registered`. |

Configuration via environment: `PORT` (default `8080`) and `GASLT_RPC` (default
the public Solana mainnet RPC).

## Run

### From the jar

```bash
# from the java/ parent
mvn -B -pl service -am package
PORT=8080 java -jar service/target/gaslt-service.jar
```

### With Docker

```bash
# build context is the java/ directory
docker build -f service/Dockerfile -t gaslt-service .
docker run --rm -p 8080:8080 gaslt-service
```

## Example output

All responses below are captured from a real run against Solana mainnet.

```text
$ PORT=8771 java -jar service/target/gaslt-service.jar
gaslt-service listening on :8771
  program : 44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW
  rpc     : https://api.mainnet-beta.solana.com
```

```text
$ curl -s localhost:8771/health
{"status":"ok","service":"gaslt-service","version":"0.1.3","program":"44gAuvd36LqRNNMqDMCvmoJFNm3eoEGGyaHWeyBeQMPW","rpc":"https://api.mainnet-beta.solana.com"}

$ curl -s localhost:8771/bridge/9prpwEhLBsN2V9JmGbryyxmsT87d2Hqc4UhqQG8zWtum
{"authority":"9prpwEhLBsN2V9JmGbryyxmsT87d2Hqc4UhqQG8zWtum","bridgePda":"B4smUxyDeW6ptTS6Sd5GsnRD2Pxm2acps5Fqo6HTmEsj","kind":"jvm","endpoint":"https://gaslt-jvm.up.railway.app","label":"gaslt-java","enabled":true,"registeredAt":1780250718,"updatedAt":1780250718}

$ curl -s localhost:8771/registry
{"error":"registry_not_initialised","registryPda":"6s2rvrg8ytn3mRJJGX6cgZfgDGMMeokC9cD7pJ4v82bm"}

$ curl -s -w ' [%{http_code}]' localhost:8771/nope
{"error":"not_found","path":"/nope"} [404]
```

The `jvm` bridge above is registered on mainnet and points at exactly this kind of
service. When an account does not exist on chain yet (the registry here), the
service returns the honest `404` shape with the derived PDA included.

## License

MIT — see [`../../LICENSE`](../../LICENSE).
