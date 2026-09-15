# Keycloak demo realm

`restless-demo-realm.json` is auto-imported by the `keycloak` service in the repo-root
`docker-compose.yml` (`start-dev --import-realm`). It backs `example`'s
`SecurityConfig`, which validates JWTs against
`http://localhost:8080/realms/restless-demo`.

## Client

- `restless-example` - public client, direct access grants enabled (Resource Owner
  Password Credentials grant), no client secret. Good enough for curling demo tokens;
  not something you'd use for a real user-facing login flow.

## Demo users

| username        | password            | realm role | `scopedLastName` | `canViewSalary` |
|-----------------|---------------------|------------|-------------------|------------------|
| `alice-admin`   | `alice-admin-pw`    | `admin`    | -                 | -                |
| `bob-manager`   | `bob-manager-pw`    | `manager`  | `Hopper`          | `false`          |
| `carol-manager` | `carol-manager-pw`  | `manager`  | `Lovelace`        | `true`           |
| `dave-employee` | `dave-employee-pw`  | `employee` | -                 | -                |

`scopedLastName` and `canViewSalary` are custom user attributes, exposed as JWT claims
by the client's protocol mappers - `CerbosPrincipalResolver` reads them as Cerbos
principal attributes (see `policies/employee.yaml`'s row-scoping and `view`/salary
rules).

`admin`'s Cerbos policy rules are unconditional (`actions: ["*"]`) across all three
policies (`employee`/`department`/`project`), so `alice-admin` has no need for any of
these attributes; `manager` is unconditional on `department`/`project` too, only
`employee` (`policies/employee.yaml`) scopes it by `scopedLastName`.

`dave-employee`'s `employee` role has no direct access to `/employees-dynamic/**` at
all (no rule in `policies/employee.yaml` grants it), and is scoped to their own
department on `/projects` (`policies/project.yaml`) - but "their own department" isn't
a JWT claim like `scopedLastName` is. It's resolved by `ProjectRestlessResource` from
the authenticated principal's own `Employee` row (matched by the JWT's built-in
`email` claim, which Keycloak already issues via its default `email` scope), so make
sure an `Employee` exists with a matching `email` and a `departmentCode` set before
expecting `dave-employee` to see any projects - see the root README's "Running the
full demo" section for the exact curl sequence.

## Minting a token

```bash
curl -s http://localhost:8080/realms/restless-demo/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=restless-example \
  -d username=carol-manager \
  -d password=carol-manager-pw \
  | jq -r .access_token
```

Use the returned token as a `Bearer` token against `example`'s
`/employees-dynamic/**` routes, e.g.:

```bash
TOKEN=$(curl -s http://localhost:8080/realms/restless-demo/protocol/openid-connect/token \
  -d grant_type=password -d client_id=restless-example \
  -d username=bob-manager -d password=bob-manager-pw | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/employees-dynamic/list | jq
```

(Port depends on how `example` is run - `mvn spring-boot:run`'s default is `8080`,
which collides with Keycloak's own `8080` in this compose file; run the app on a
different port, e.g. `--server.port=8081`, or stop Keycloak's port mapping, when
running both at once locally.)
