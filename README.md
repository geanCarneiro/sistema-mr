# Sistema MR

Assistente com autenticação Google, JWT próprio, Gemini, memória de conversa no Neo4j e execução isolada de cálculos em Python.

## Arquitetura

```text
Angular/Nginx ── JWT ──> Spring Boot ──> Gemini (chat + embeddings)
                            │
                            ├──────────> Neo4j (memória + índice de chunks)
                            ├──────────> Volume de arquivos (original + contexto Markdown)
                            ├──────────> PaddleOCR local (OCR neural)
                            └──────────> Python Runner isolado
```

O backend deriva o identificador da conversa do `sub` autenticado no JWT. O cliente não escolhe nem envia IDs de conversa.

Os arquivos enviados são processados em segundo plano. O original fica preservado para download; o Tika extrai documentos com texto nativo e o PaddleOCR local processa imagens e PDFs digitalizados. Se o serviço OCR estiver saudável, mas a inferência falhar ou produzir texto insuficiente, um cliente Gemini multimodal isolado gera uma transcrição e descrição visual. A indisponibilidade do PaddleOCR é uma falha de infraestrutura e nunca aciona o fallback. Os embeddings dos chunks localizam os arquivos relevantes, mas o chat recebe a versão textual integral de cada arquivo selecionado (parent-document retrieval). O modelo de chat e do processador visual permanece `gemini-3.1-flash-lite`; embeddings usam `gemini-embedding-2` com 768 dimensões.

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
- Docker para Neo4j, PaddleOCR e para preservar o isolamento do Python Runner.

Inicie somente o Neo4j quando necessário:

```powershell
docker compose up neo4j
```

Em outro terminal, execute o runner isolado sem iniciar a stack completa:

```powershell
docker compose up python-runner
```

O PaddleOCR é uma dependência mínima do backend. Execute-o isoladamente em outro
terminal; os modelos PP-OCRv6 são baixados durante o build da imagem, não durante
uma requisição:

```powershell
docker compose up ocr-service
```

O serviço ficará disponível somente em `http://127.0.0.1:8082`. O backend valida
o endpoint `/health` na inicialização e não inicia se o modelo local não estiver
pronto.

Para experimentar o PaddleOCR diretamente no Windows, use Python 3.12 ou 3.13.
Python 3.14 ainda pode fazer dependências como o PyYAML tentarem compilar extensões
nativas e exigirem o Visual C++ Build Tools; o pacote Redistributable não contém o
compilador. A execução oficial da aplicação usa Python 3.12 dentro do container e
não depende das ferramentas C++ da Microsoft.

Os três serviços locais necessários ao backend também podem ser iniciados juntos
com `docker compose up neo4j python-runner ocr-service`. O backend e o frontend
continuam sendo executados localmente durante o desenvolvimento.

O runner ficará disponível localmente em `http://127.0.0.1:8081`. O backend iniciado pelo NetBeans usa esse endereço e `http://127.0.0.1:8082` para OCR por padrão. As ações `run`, `debug` e `profile` do NetBeans recebem as configurações locais como propriedades da JVM.

Inicie o frontend:

```powershell
cd frontend
npm start
```

O frontend acrescenta um `/api` de roteamento às URLs reais do backend. O proxy de desenvolvimento remove somente esse primeiro prefixo e encaminha a chamada para `http://localhost:8080`; o Nginx usa a mesma regra no ambiente Docker.

## Endpoints principais

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/auth/google` | Troca um Google ID Token por JWT da aplicação |
| `POST` | `/api/ai/chat` | Envia uma mensagem na conversa do usuário autenticado |
| `GET` | `/api/ai/chat/history` | Recupera o histórico do usuário autenticado |
| `POST` | `/api/ai/chat/files` | Envia até 10 arquivos em `multipart/form-data` (`files`) |
| `GET` | `/api/ai/chat/files` | Lista arquivos e o estado do processamento assíncrono |
| `GET` | `/api/ai/chat/files/{id}/download` | Baixa o arquivo original |
| `DELETE` | `/api/ai/chat/files/{id}` | Remove original, contexto e chunks |

O corpo do chat contém o prompt e, opcionalmente, os IDs de arquivos prontos que devem ser incluídos obrigatoriamente:

```json
{
  "prompt": "Resuma os pontos de divergência entre os anexos",
  "attachmentIds": ["5e445a17-5ce8-4aec-8c0a-e3a49db22355"]
}
```

Mesmo sem anexos explícitos, arquivos prontos da conversa podem ser recuperados semanticamente. Os limites e endpoint do OCR, limiares de qualidade, orçamento de contexto e modelo de embedding ficam em `app.documents` no `application.yaml`. A IA de fallback possui configuração em `app.ai.document-vision`. As instruções de sistema do chat e do processador documental ficam em `backend/src/main/resources/prompts`.

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

O Python Runner possui testes unitários em `python-runner/test_runner.py`. Eles podem ser executados dentro de um ambiente Linux com Python 3.12. O build de `ocr-service` compila o módulo Python e carrega antecipadamente o modelo PaddleOCR para validar dependências e pesos.

## Observações de segurança

- Não passe credenciais, volumes do backend ou o socket do Docker para o Python Runner.
- Não exponha a porta 8081 publicamente; no Compose ela fica vinculada somente a `127.0.0.1`.
- A rotação de `JWT_SECRET` invalida os tokens emitidos anteriormente e exige novo login.
- Para uma implantação multi-tenant de maior escala, o próximo passo é criar uma sandbox descartável por execução.
