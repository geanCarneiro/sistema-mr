# Sistema MR

Assistente com autenticação Google, JWT próprio, Gemini, memória de conversa no Neo4j e execução isolada de cálculos em Python.

## Arquitetura

```text
Angular/Nginx ── JWT ──> Spring Boot ──> Gemini
                            │
                            ├──────────> Neo4j (memória)
                            │
                            └──────────> Python Runner isolado
```

O backend deriva o identificador da conversa do `sub` autenticado no JWT. O cliente não escolhe nem envia IDs de conversa.

O Python Runner não recebe credenciais do backend, opera em uma rede dedicada, bloqueia novas conexões de saída e limita tempo, saída, processos, CPU e memória. A porta de desenvolvimento é publicada somente em `127.0.0.1`. As bibliotecas científicas são instaladas em versões fixas durante a criação da imagem; não há instalação dinâmica de pacotes durante uma conversa.

## Configuração

Copie `.env.example` para `.env` e informe:

```dotenv
GEMINI_API_KEY=...
PASSWD_NEO4J=...
GOOGLE_API_CLIENT_ID=...
JWT_SECRET=...
```

`JWT_SECRET` deve ter ao menos 32 bytes. O `.env` é local e ignorado pelo Git.

No frontend em desenvolvimento, o Client ID público está em `frontend/public/env.js`. Na imagem Nginx, `env.js` é produzido na inicialização a partir de `GOOGLE_API_CLIENT_ID`, sem recompilar o Angular.

## Desenvolvimento local

Pré-requisitos:

- Java 21;
- Node.js 22 e npm 10;
- Docker apenas para Neo4j e para preservar o isolamento do Python Runner.

Inicie somente o Neo4j quando necessário:

```powershell
docker compose up neo4j
```

Em outro terminal, execute o runner isolado sem iniciar a stack completa:

```powershell
docker compose up python-runner
```

Se ambos forem necessários, eles também podem ser iniciados juntos com
`docker compose up neo4j python-runner`. O backend e o frontend continuam sendo
executados localmente durante o desenvolvimento.

O runner ficará disponível localmente em `http://127.0.0.1:8081`. O backend iniciado pelo NetBeans usa esse endereço por padrão. As ações `run`, `debug` e `profile` do NetBeans recebem as configurações locais como propriedades da JVM.

Inicie o frontend:

```powershell
cd frontend
npm start
```

O proxy de desenvolvimento preserva o prefixo `/api` e encaminha as chamadas para `http://localhost:8080`.

## Endpoints principais

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/auth/google` | Troca um Google ID Token por JWT da aplicação |
| `POST` | `/api/ai/chat` | Envia uma mensagem na conversa do usuário autenticado |
| `GET` | `/api/ai/chat/history` | Recupera o histórico do usuário autenticado |

O corpo do chat contém somente o prompt:

```json
{
  "prompt": "Calcule a média e o desvio padrão de 10, 20, 30 e 40"
}
```

## Validação local

Backend:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend:

```powershell
cd frontend
npm test -- --watch=false
npm run build
```

Compose, sem iniciar serviços:

```powershell
docker compose config --quiet
```

O Python Runner possui testes unitários em `python-runner/test_runner.py`. Eles podem ser executados dentro de um ambiente Linux com Python 3.12.

## Observações de segurança

- Não passe credenciais, volumes do backend ou o socket do Docker para o Python Runner.
- Não exponha a porta 8081 publicamente; no Compose ela fica vinculada somente a `127.0.0.1`.
- A rotação de `JWT_SECRET` invalida os tokens emitidos anteriormente e exige novo login.
- Para uma implantação multi-tenant de maior escala, o próximo passo é criar uma sandbox descartável por execução.
