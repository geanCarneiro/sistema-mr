# Contexto técnico do repositório

> Resumo derivado das configurações versionadas do projeto (`backend/pom.xml`,
> `frontend/package.json`, `frontend/package-lock.json`, `frontend/angular.json`,
> `*/requirements.txt`, Dockerfiles e `docker-compose.yml`). As versões abaixo
> devem ser reconfirmadas quando esses arquivos forem alterados.

## Visão geral

O Sistema MR é um assistente web full-stack com autenticação Google, JWT próprio,
chat com Gemini, memória de conversa e grounding documental no Neo4j, OCR local
com PaddleOCR e execução isolada de cálculos Python. A arquitetura é composta por
Angular servido por Nginx, API Spring Boot, Neo4j, um serviço OCR e um Python
Runner isolado.

## Linguagens e runtimes

- **Java 21**: linguagem principal da API em `backend/`.
- **TypeScript 5.9.3**: frontend Angular; o projeto compila para ES2022 com
  `strict` e opções estritas do compilador Angular habilitadas.
- **Python 3.12**: `ocr-service/` usa a imagem exata `python:3.12.11-slim-bookworm`;
  `python-runner/` usa `python:3.12-slim-bookworm`.
- **Node.js 22.x e npm 10.9.7**: toolchain do frontend; o Dockerfile usa
  `node:22-alpine`.
- **Maven 3.9.16**: distribuição usada pelo Maven Wrapper local
  (`backend/.mvn/wrapper/maven-wrapper.properties`); a imagem de build do backend
  usa `maven:3.9.6-eclipse-temurin-21`.

## Frameworks e dependências principais

### Backend Java

- **Spring Boot 4.1.0**, com starters Web MVC, Validation, Security, OAuth2
  Resource Server e Neo4j; os starters de teste e DevTools seguem a versão
  gerenciada pelo Spring Boot 4.1.0.
- **Spring AI 2.0.0**, via BOM, com chat Google GenAI, embeddings Google GenAI,
  memória de chat Neo4j e leitor documental Tika.
- **Google Cloud Libraries BOM 26.86.0**.
- **`google-api-client` 2.7.2** (versão resolvida no classpath local).
- **Neo4j Java Driver 6.1.0** (dependência transitiva resolvida pelos starters).
- Modelos configurados em `backend/src/main/resources/application.yaml`:
  `gemini-3.1-flash-lite` para chat/visão e `gemini-embedding-2` com 768
  dimensões para embeddings.

### Frontend Angular

As versões abaixo são as versões exatas resolvidas no `frontend/package-lock.json`
(o `package.json` declara faixas compatíveis):

- **Angular 21.2.20**: `@angular/common`, `@angular/compiler`, `@angular/core`,
  `@angular/forms`, `@angular/platform-browser` e `@angular/router`.
- **Angular CDK 21.2.14**.
- **Angular CLI e build 21.2.21**: `@angular/cli` e `@angular/build`.
- **Angular Compiler CLI 21.2.20**.
- **PrimeNG 21.1.9**, **`@primeicons/angular` 8.0.0** e
  **`@primeuix/themes` 2.0.3**.
- **RxJS 7.8.2** e **tslib 2.8.1**.
- Ferramentas: **Vitest 4.1.10**, **jsdom 28.1.0** e **Prettier 3.9.6**.

### Serviços Python e infraestrutura

- `ocr-service/requirements.txt`: **PaddleOCR 3.7.0** e **ONNX Runtime 1.23.2**.
- `python-runner/requirements.txt`: **NumPy 2.2.6**, **pandas 2.3.1**,
  **SciPy 1.16.0** e **SymPy 1.14.0**.
- Compose: **Neo4j 5.26.0 Community** (`neo4j:5.26.0-community`), backend,
  frontend, `python-runner` e `ocr-service` em redes separadas conforme o
  serviço. O Runner publica `127.0.0.1:8081` e o OCR publica
  `127.0.0.1:8082`.
- O Python Runner usa somente a biblioteca padrão para o servidor HTTP, executa
  código em diretórios temporários e aplica limites de tempo, saída, processos,
  CPU e memória. O Dockerfile executa `test_runner.py` durante o build.

## Estrutura de pastas e convenções

- `backend/`: aplicação Spring Boot. O código fica em
  `src/main/java/br/com/geangc/sistema_mr/`, separado em `configuration`,
  `controller`/`controller/dto`, `model`, `repository`, `service`, `storage` e
  `tool_calling`; testes espelham o pacote em `src/test/java`. Configuração e
  prompts ficam em `src/main/resources/application.yaml` e `prompts/`.
- `frontend/`: aplicação Angular standalone. O bootstrap é feito por
  `src/main.ts`/`bootstrapApplication`, a configuração por `src/app/app.config.ts`
  e as rotas por `src/app/app.routes.ts`. As rotas principais são `login` e
  `chat`; `login` usa `redirectIfAuthenticatedGuard` e `chat` usa `authGuard`.
- No Angular 21, arquivos públicos são mantidos em **`frontend/public/`** e
  copiados pelo `angular.json`; não criar ou presumir `src/assets/`. O arquivo
  `public/env.js` contém a configuração de desenvolvimento e, na imagem Nginx,
  `env.js` é gerado em runtime a partir de `GOOGLE_API_CLIENT_ID`.
- Componentes Angular usam SCSS e nomenclatura com ponto, por exemplo
  `chat.component.ts`, `chat.component.html`, `chat.component.scss` e
  `chat.component.spec.ts`; imports e providers são standalone. Serviços,
  guards, interceptors e interfaces compartilhados ficam em
  `frontend/src/shared/`.
- `frontend/proxy.conf.json` encaminha `/api` para `http://localhost:8080` e
  remove apenas o primeiro prefixo `/api`; o Nginx usa a mesma regra em Docker.
- `ocr-service/`: servidor HTTP Python baseado em `http.server`, com `/health` e
  `/ocr`; processa imagens e PDFs digitalizados com PaddleOCR local.
- `python-runner/`: servidor HTTP Python baseado em `http.server`, com `/health`
  e `/execute`; `test_runner.py` contém os testes unitários.
- `docker-compose.yml`: define Neo4j, backend, frontend, OCR e Runner, volumes
  de dados e redes `application`, `runner` e `ocr`. No fluxo local, Neo4j, OCR e
  Runner são os serviços executados via Compose; backend e frontend devem ser
  executados localmente no host. O backend usa variáveis de ambiente como
  `GEMINI_API_KEY`, `JWT_SECRET`, `PASSWD_NEO4J`, `PYTHON_RUNNER_URL`,
  `OCR_SERVICE_URL` e `FILE_STORAGE_ROOT`; segredos locais ficam em `.env`, que
  é ignorado pelo Git.
- O fluxo documental preserva o original, extrai texto nativo via Tika e usa
  PaddleOCR para imagens/PDFs digitalizados. O fallback visual Gemini é usado
  somente quando a inferência OCR falha em qualidade; indisponibilidade da
  infraestrutura OCR é tratada como falha. O grounding usa chunks/embeddings
  para localizar arquivos e pode incluir anexos explicitamente selecionados.

## Comandos principais

Os comandos abaixo são executados a partir da pasta indicada. `npm ci` deve ser
preferido para instalar exatamente o lockfile.

### Backend

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd spring-boot:run
```

Se o repositório Maven padrão não estiver acessível no ambiente local, pode-se
usar o cache do projeto: `$env:MAVEN_OPTS='-Dmaven.repo.local=.\target\m2-repository'`.

### Frontend

```powershell
cd frontend
npm ci
npm start
npm run watch
npm run build
npm test -- --watch=false
npx prettier --check .
```

Não há script `lint` nem configuração versionada de ESLint, Stylelint, Checkstyle,
Ruff ou outra ferramenta equivalente em nenhuma das áreas. O
`npx prettier --check .` é a verificação de formato disponível para o frontend;
não substituí-la por um lint inexistente.

### Python e Compose

```powershell
cd python-runner
python -m unittest -v test_runner.py

cd ..\ocr-service
python -m py_compile ocr_service.py

cd ..
docker compose config --quiet
```

Os testes e a execução direta dos serviços Python são voltados a Linux/container
por causa do ambiente de execução do Runner e dos modelos OCR. As imagens podem
ser construídas por serviço com `docker compose build <serviço>`. O Compose
define também `backend`, `frontend` e `ocr-service`, mas os comandos permitidos
para iniciar serviços permanecem estritamente os descritos nas instruções
operacionais abaixo.

# Instruções para agentes

## Fases de trabalho e autorização

- Por padrão, discussões sobre requisitos, arquitetura, implementação, bugs,
  refatorações e possíveis mudanças permanecem na fase de debate.
- Na fase de debate, investigue o repositório, esclareça comportamentos, compare
  alternativas e proponha um plano, mas não altere arquivos, dependências,
  configuração, schemas, dados ou serviços.
- Durante o debate, faça somente alterações que o usuário solicitar de forma
  específica e explícita. Uma autorização pontual vale apenas para a ação ou para
  os arquivos nomeados e não inicia a fase de execução do restante do trabalho.
- Inicie a fase de execução somente quando o usuário autorizar explicitamente a
  implementação do escopo debatido.
- Na fase de execução, implemente apenas o que foi acordado. Se surgir uma mudança
  materialmente diferente ou fora do escopo, explique-a e aguarde nova autorização.
- O usuário pode acompanhar a execução e corrigir a rota; incorpore essas correções
  sem ampliar implicitamente o escopo autorizado.

## Ambiente de desenvolvimento local

- Considere este repositório um ambiente de desenvolvimento local.
- Nunca execute `docker compose up` sem indicar explicitamente um serviço.
- Não inicie uma stack do Compose que inclua `backend` ou `frontend`; esses dois
  serviços devem rodar localmente no próprio host.
- Os únicos serviços permitidos com `docker compose up` são `neo4j`, `ocr-service` e `python-runner`, isolados ou juntos, e somente quando forem necessários para a tarefa ou validação atual.
- São permitidos `docker compose up neo4j`, `docker compose up python-runner`, `docker compose up ocr-service`, `docker compose up neo4j python-runner`, `docker compose up neo4j ocr-service` e `docker compose up neo4j python-runner ocr-service`.
- Nunca inclua `backend`, `frontend` ou qualquer outro serviço não listado em um
  comando `docker compose up` sem nova autorização explícita do usuário.
- Prefira validações locais, estáticas e unitárias quando forem adequadas, mas
  também execute validações que dependam de serviços em execução quando forem
  relevantes para a tarefa.
- Validações funcionais usando CDP são encorajadas quando agregarem valor, embora
  não sejam obrigatórias; o usuário também pode executá-las manualmente.
- Quando forem necessários serviços persistentes, backend e frontend devem ser
  iniciados localmente no host, e os serviços Compose permitidos podem ser
  iniciados conforme o escopo autorizado da tarefa ou solicitação explícita do
  usuário.

## GitHub Issues e Project

- Use as Issues do repositório como unidade de trabalho e o Project pessoal
  <https://github.com/users/geanCarneiro/projects/2> como visualização; não mantenha
  um segundo backlog detalhado em arquivos.
- Não crie nem altere Issue, comentário, PR ou Project sem solicitação do usuário ou
  sem que essa ação faça parte explícita do fluxo autorizado.
- Para uma Issue acompanhada, mantenha a label `tracked` e exatamente uma label de
  cada grupo: `type:*`, `area:*` e `priority:*`.
- As labels são a fonte de verdade para `Work Type`, `Area` e `Priority`; a automação
  copia esses valores para o Project. Elas podem aparecer alguns segundos depois da
  criação da Issue. O campo `Status` no Project é a fonte de verdade do andamento;
  o usuário normalmente move o item antes de iniciar a atuação.
- Use comentários para transições solicitadas: `/backlog`, `/start`, `/review`
  e `/done`. O último comando também fecha a Issue.
- Ao criar uma PR para uma Issue, inclua `Closes #<numero>` no corpo. O fluxo move a
  Issue para `In Progress`, `Review` ou `Done` conforme o estado da PR.
- Para uma Issue antiga, adicione `tracked` e as labels de classificação. Se precisar
  forçar a sincronização, execute manualmente o workflow `Project automation` com o
  número da Issue e o status desejado.
- Não altere diretamente no Project valores derivados de labels, pois uma execução
  posterior pode sobrescrevê-los. Respeite o `Status` definido no Project como a
  fonte de verdade do andamento, inclusive quando ele tiver sido movido pelo usuário
  antes do início da atuação.
- Consulte `.github/PROJECT_AUTOMATION.md` para configuração, operação, bootstrap e
  solução de problemas. Alterações na automação devem incluir os testes em
  `.github/scripts/project-automation.test.js`.

## Padrão de commits

- Use o formato Conventional Commits no título: `tipo(escopo): resumo curto`.
- O corpo deve identificar a Issue atendida com `Issue: #<numero>`.
- O corpo deve conter uma seção `Changelog:` com uma lista objetiva das mudanças
  observáveis, sem incluir segredos, tokens ou valores sensíveis.
- Exemplo:

  ```text
  feat(chat): persiste a proveniência das interações

  Issue: #4

  Changelog:
  - adiciona a entidade Interacao e as fontes documentais utilizadas;
  - preserva as fontes no histórico e trata arquivos excluídos logicamente.
  ```
