# Local setup, step by step

Goal: see the aggregator running on your own machine at **http://localhost:5173**.
Total time: ~10 minutes (first run downloads dependencies).

## 0. Prerequisites

| Tool | Version | Check |
| :-- | :-- | :-- |
| JDK | 17 | `java -version` |
| Node.js + npm | 20 or newer | `node -v` |
| Docker Desktop (running) | any recent | `docker --version` |
| Git | any | `git --version` |

You do **not** need Maven installed (the repo ships `mvnw`), a Gemini key, or any `.env` file.
You do need internet access on the first run (Maven/npm downloads; Spring also contacts Google once to read its OIDC
discovery document for the optional Google login).

## 1. Get the code

```bash
git clone <your-repo-url> trust-aggregator
cd trust-aggregator
```
(or unzip the project and open a terminal in the folder that contains `pom.xml`).

## 2. Start PostgreSQL, Redis and Kafka

```bash
docker compose up -d
docker compose ps        # postgres, redis, kafka should be "running"/"healthy"
```
PostgreSQL is published on **host port 5433** (so it does not clash with a local Postgres on 5432). The backend's default
config already points there.

## 3. Start the backend (terminal 1)

```bash
./mvnw spring-boot:run          # macOS / Linux / Git-Bash
mvnw.cmd spring-boot:run        # Windows cmd / PowerShell
```
Wait for `Started SecondHandApplication`. On first start Hibernate creates the tables and a demo seeder inserts 3 sellers,
12 marketplace listings and 4 community-shared listings (idempotent). Verify:

```bash
curl http://localhost:8080/api/v1/aggregator/health
# {"status":"UP","sources":6}
```
Swagger UI: http://localhost:8080/swagger-ui.html

## 4. Start the frontend (terminal 2)

```bash
cd secondhand-frontend
npm install
npm run dev
```
Open **http://localhost:5173**.

## 5. Use it

Click an example chip or type, for example:

- `laptop under 40k in Bengaluru, trusted seller`
- `PG or hostel near SIT Tumkur under 8000`
- `cheapest used bicycle in Tumkur`
- `iPhone 14 in good condition` ← the demo scam listing appears flagged "High risk" and is ranked down
- `under 5000 in Tumkur` ← too vague: the agent asks what you are looking for instead of guessing

On each result click **Why this score?** to see every trust signal, and open **Agent trace** to see the pipeline steps.
The original escrow marketplace is still available at http://localhost:5173/marketplace (not linked from the aggregator header). Demo seller logins: `demo.seller1@secondhand.local` /
`Demo@1234` (also seller2, seller3).

### Share a real listing

Open **http://localhost:5173/share**, fill in a listing you found on another platform (title, price, city, link to the original
post) and submit. It becomes searchable immediately as an *unverified* "Community Shared" listing. Search for it to see the
trust score reflect that. Check the saved data with:

```bash
docker exec -it secondhand_postgres_dev psql -U postgres -d secondhand \
  -c "select query_text, total_ranked, top_trust_score from aggregator_search_history order by id desc limit 5;"
```

## 6. Optional: use the Gemini LLM (understand, plan and explain)

Get a key at https://aistudio.google.com/apikey, then **before step 3** set it in the same terminal:

```bash
export GEMINI_API_KEY=your_key              # macOS / Linux
$env:GEMINI_API_KEY="your_key"              # PowerShell
set GEMINI_API_KEY=your_key                 # Windows cmd
```
With a key the agent also (a) lets the LLM choose which sources to query (shown as "skipped by the planner (AI)") and (b) shows an
"AI-written" comparison of the top results. The trace lists a `Plan` and an `Explain` step. If the key is wrong or the call fails,
everything silently falls back to the built-in rules.

## 7. Run the tests

```bash
./mvnw test -Dtest='com.serhat.secondhand.aggregator.**.*Test'
```

## 8. Stop / reset

```bash
# Ctrl+C in both terminals, then
docker compose down         # keep data
docker compose down -v      # wipe database too (demo data is re-seeded on next start)
```

## Run everything in Docker instead (no JDK/Node needed)

```bash
docker compose -f docker-compose.full.yml up --build -d    # first build takes several minutes
# app http://localhost:3000   API http://localhost:8080
docker compose -f docker-compose.full.yml logs -f backend
docker compose -f docker-compose.full.yml down -v
```
Stop the infra-only stack first (`docker compose down`) - both publish the same ports. Optional settings go in a `.env`
(copy `.env.template`).

## Troubleshooting

| Symptom | Cause / fix |
| :-- | :-- |
| UI shows "Cannot reach the backend" | Backend not running or still starting; check `curl http://localhost:8080/api/v1/aggregator/health`. |
| `Connection refused` to `localhost:5433` | `docker compose up -d` not run / Docker Desktop not started. |
| Port 8080 / 5173 / 5433 / 6379 / 9092 already in use | Stop the other process, or change the host port in `docker-compose.yml` (and `DB_URL` for Postgres). |
| Backend fails at startup mentioning `accounts.google.com` / issuer | No internet on first start (Google OIDC discovery). Connect and retry. |
| `JAVA_HOME` / `release version 17 not supported` | Install JDK 17 and make it the active one. |
| Kafka warnings `Connection to node -1 could not be established` | Kafka container not up. Harmless for the aggregator, but run `docker compose up -d` to remove them. |
| Wrong DB password after changing `DB_PASSWORD` | The volume keeps the old one: `docker compose down -v`. |
| Search is slow the first time | JIT warm-up; with a Gemini key there is also one LLM call (falls back to rules on failure). |
