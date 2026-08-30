# Backlog de evolução — Sistema MR

> Documento temporário para registrar propostas até a adoção de uma plataforma
> formal de gestão de trabalho.

- Última revisão: 2026-08-29
- Estado dos itens: proposta, sem autorização de implementação
- Escopo: upload documental, grounding, OCR, histórico e organização de conversas

## 1. Objetivo

Este documento transforma as melhorias identificadas durante a implementação de
upload e grounding documental em itens que possam ser refinados e posteriormente
migrados para issues ou histórias de usuário.

Nenhum item deste backlog representa uma decisão arquitetural definitiva. Antes
da implementação, cada item deve passar por refinamento, validação de prioridade e
confirmação dos critérios de aceite.

## 2. Baseline funcional atual

A implementação atual possui as seguintes características relevantes:

- cada usuário possui uma conversa lógica identificada por `chat-<JWT subject>`;
- o Spring AI persiste o histórico em `(:Session)-[:HAS_MESSAGE]->(:Message)`;
- a aplicação mantém o escopo documental estável em
  `(:ContextoChat)-[:POSSUI]->(:Arquivo)-[:CONTEM]->(:Chunk)`;
- o arquivo original é preservado para download;
- a versão textual integral é armazenada separadamente e usada como grounding;
- chunks e embeddings são usados para localizar arquivos, mas o conteúdo incluído
  no prompt é a versão textual integral do arquivo selecionado;
- arquivos explicitamente anexados e arquivos recuperados semanticamente são
  combinados na requisição atual;
- o prompt original, sem o contexto documental enriquecido, é persistido no
  histórico;
- PaddleOCR é uma dependência mínima da aplicação;
- indisponibilidade do serviço OCR produz erro e não chama Gemini;
- falha de inferência ou OCR insuficiente pode acionar o fallback Gemini
  multimodal isolado;
- o modelo Gemini permanece `gemini-3.1-flash-lite` por decisão de cota.

## 3. Priorização sugerida

| Ordem | ID | Item | Prioridade | Esforço estimado | Dependências |
|---:|---|---|---|---|---|
| 1 | BL-001 | Política de anexos explícitos | Alta | Pequeno | Nenhuma |
| 2 | BL-002 | Reprocessamento de arquivos com falha | Alta | Pequeno/Médio | Nenhuma |
| 3 | BL-003 | Proveniência do grounding por interação | Alta | Médio/Grande | Decisão sobre histórico de produto |
| 4 | BL-005 | Observabilidade do pipeline documental | Média | Médio | Definir solução de métricas |
| 5 | BL-004 | Protocolo binário para o OCR | Média | Médio | Contrato backend–OCR |
| 6 | BL-006 | Orçamento de contexto no frontend | Média | Médio | Exposição de tokens na API |
| 7 | BL-007 | Múltiplas conversas por usuário | Futura | Grande | Requisito de produto |
| 8 | BL-008 | Deduplicação por SHA-256 | Futura | Médio | Política de retenção e exclusão |

## 4. Itens do backlog

### BL-001 — Tornar explícita a política de seleção de arquivos

**Status:** Proposta

**Problema**

Hoje, quando o usuário anexa um arquivo à mensagem, a aplicação inclui o anexo e
continua executando a busca semântica sobre os demais arquivos da conversa. Isso
pode acrescentar documentos que o usuário não esperava e consumir contexto sem
necessidade.

**User story**

> Como usuário do chat, quero saber e controlar quais arquivos serão usados na
> minha mensagem, para que a resposta não utilize documentos inesperados.

**Requisito funcional recomendado**

- quando `attachmentIds` contiver ao menos um ID, usar somente os arquivos
  explicitamente anexados;
- quando `attachmentIds` estiver vazio, executar a recuperação semântica nos
  arquivos `READY` da conversa;
- opcionalmente, em uma evolução posterior, oferecer a opção “Também buscar
  arquivos relacionados”.

**Critérios de aceite**

1. Uma mensagem com anexos inclui todos os anexos válidos que couberem no
   orçamento e nenhum arquivo recuperado automaticamente.
2. Uma mensagem sem anexos mantém a recuperação semântica atual.
3. Anexos inexistentes, não pertencentes ao usuário ou ainda não prontos geram
   erro claro.
4. Anexos explícitos que excedam o orçamento geram erro antes da chamada ao
   Gemini.
5. O prompt enriquecido continua não sendo persistido no histórico.

**Proposta de implementação**

- ajustar `GroundingContextService.prepare` para executar
  `searchReadyFiles` somente quando `explicitIds.isEmpty()`;
- preservar a validação de propriedade, status e orçamento;
- manter `GroundingFile.explicitlyAttached` para observabilidade e compatibilidade;
- cobrir os caminhos explícito, semântico, orçamento excedido e ID inválido com
  testes unitários.

**Decisão pendente**

- confirmar se o comportamento recomendado deve substituir definitivamente o
  modo híbrido atual ou se o modo híbrido deve ser configurável.

---

### BL-002 — Permitir reprocessar arquivos com status `FAILED`

**Status:** Proposta

**Problema**

Quando a ingestão falha, o arquivo original continua armazenado, mas o usuário
precisa enviá-lo novamente. Isso cria duplicatas e dificulta a recuperação de
falhas transitórias.

**User story**

> Como usuário, quero reprocessar um arquivo que falhou sem fazer um novo upload,
> para aproveitar o original já armazenado e corrigir falhas transitórias.

**Requisitos funcionais**

- disponibilizar a ação somente para arquivos `FAILED` pertencentes ao usuário;
- exibir o motivo resumido da falha;
- reutilizar o arquivo original já armazenado;
- reiniciar o fluxo em `QUEUED` e atualizar o progresso normalmente;
- impedir duas tentativas concorrentes para o mesmo arquivo.

**Contrato HTTP proposto**

```http
POST /api/ai/chat/files/{id}/retry
```

Resposta sugerida: representação atualizada do arquivo com status `QUEUED`.

**Critérios de aceite**

1. Um arquivo `FAILED` pode ser reprocessado sem upload.
2. Arquivos de outro usuário retornam `404` ou resposta equivalente que não
   revele sua existência.
3. Arquivos `QUEUED`, `EXTRACTING` ou `EMBEDDING` não podem iniciar outra tarefa.
4. Chunks parciais e `context.md` anteriores não são usados na nova tentativa.
5. Em caso de sucesso, o arquivo chega a `READY` e permanece disponível para
   download com o mesmo ID.
6. Em nova falha, o status retorna a `FAILED` com a mensagem atualizada.

**Proposta de implementação**

- criar uma operação transacional no repositório que faça a transição
  `FAILED -> QUEUED` apenas se o estado atual ainda for `FAILED`;
- remover chunks anteriores de forma idempotente;
- remover ou sobrescrever a versão textual anterior;
- disparar `DocumentIngestionService.process(id)` após a transição bem-sucedida;
- adicionar botão “Reprocessar” no card do arquivo;
- registrar número de tentativas e `lastRetryAt` caso seja necessário diagnóstico.

**Riscos e cuidados**

- a execução assíncrona deve ser idempotente;
- uma falha depois de gerar parte dos embeddings não pode deixar chunks válidos
  ligados ao arquivo;
- deve ser definido um limite de tentativas automáticas caso retentativas
  automáticas sejam adicionadas no futuro.

---

### BL-003 — Persistir a proveniência documental de cada interação

**Status:** Proposta

**Problema**

A aplicação sabe quais arquivos foram anexados ou recuperados semanticamente
durante a requisição, mas essa informação não é persistida. Depois de recarregar
o histórico não é possível identificar quais documentos sustentaram uma resposta.

**User story**

> Como usuário, quero visualizar as fontes documentais usadas em cada resposta,
> para entender sua origem e verificar as informações apresentadas.

**User story operacional**

> Como responsável pela aplicação, quero consultar quais arquivos e scores foram
> usados em uma resposta, para investigar grounding incorreto e comportamento do
> modelo.

**Requisitos funcionais**

- persistir somente os arquivos efetivamente incluídos no prompt;
- distinguir arquivo anexado explicitamente de arquivo recuperado semanticamente;
- persistir o score de similaridade quando aplicável;
- preservar a informação após reload do frontend;
- exibir as fontes utilizadas junto à resposta do assistente;
- não armazenar novamente o conteúdo integral do arquivo em cada interação.

**Modelagem proposta**

```text
(:ContextoChat)
    └──[:TEM_INTERACAO]->(:Interacao {
          id,
          userPrompt,
          assistantResponse,
          userTimestamp,
          assistantTimestamp
       })
             └──[:USOU_ARQUIVO {
                    explicit,
                    similarity,
                    includedTokens
                }]->(:Arquivo)
```

Uma alternativa é renomear futuramente `:ContextoChat` para `:Conversa`, sem
alterar a separação em relação ao `:Session` do Spring AI.

**Por que não relacionar diretamente ao `:Message` do Spring AI**

O `Neo4jChatMemoryRepository` apaga e recria `:Session` e `:Message` ao salvar o
histórico. Relacionamentos de domínio presos a esses nós teriam ciclo de vida
instável. `:Interacao` deve pertencer à aplicação e ser independente da memória
técnica do Spring AI.

**Critérios de aceite**

1. Cada resposta final possui uma interação persistida uma única vez.
2. Chamadas intermediárias de ferramentas não criam interações duplicadas.
3. A interação registra somente arquivos efetivamente presentes no prompt.
4. O histórico retornado pela API contém a lista de fontes da resposta.
5. O frontend continua exibindo as fontes depois de recarregar a página.
6. Excluir um arquivo tem uma política explícita para relacionamentos históricos:
   manter referência histórica anonimizada ou remover a relação.

**Proposta de implementação**

- gerar um `interactionId` no início da requisição;
- fazer `GroundingContextService` devolver os arquivos selecionados com os dados
  necessários à proveniência;
- persistir a interação somente depois da resposta final do modelo;
- definir transação ou compensação para evitar resposta persistida sem fontes;
- estender `ChatMessageDto` ou criar um DTO de interação próprio;
- renderizar uma seção recolhível “Fontes utilizadas” no frontend.

**Decisões pendentes**

- retenção das interações;
- comportamento ao excluir um arquivo;
- necessidade de persistir a resposta integral fora do repositório de memória;
- relação entre `:Interacao` e mensagens envolvendo ferramentas.

---

### BL-004 — Substituir Base64 por protocolo binário no OCR

**Status:** Proposta

**Problema**

O contrato atual envia o arquivo como Base64 dentro de JSON. Isso aumenta o corpo
em aproximadamente um terço e mantém simultaneamente arquivo, string Base64 e
JSON serializado na memória do backend.

**User story técnica**

> Como mantenedor da aplicação, quero enviar os bytes do arquivo diretamente ao
> serviço OCR, para reduzir uso de memória, cópias e tráfego interno.

**Contrato preferencial proposto**

```http
POST /ocr
Content-Type: image/png
Content-Length: ...
X-Original-Name: cartaz.png

<conteúdo binário>
```

Para PDFs, `Content-Type: application/pdf`. `multipart/form-data` é uma
alternativa caso metadados adicionais se tornem necessários.

**Critérios de aceite**

1. O backend não cria representação Base64 do arquivo.
2. O serviço continua validando `Content-Length` antes de ler o corpo.
3. Os limites de arquivo e requisição permanecem aplicados.
4. O contrato aceita todos os MIME types atualmente suportados.
5. Erros HTTP mantêm mensagens estruturadas em JSON.
6. O resultado OCR permanece compatível com o contrato atual.

**Proposta de implementação**

- criar uma versão `/ocr` binária ou `/ocr/v2` para migração controlada;
- usar `Resource`, `InputStream` ou corpo binário com tamanho conhecido no
  `PaddleOcrClient`;
- evitar `Files.readAllBytes` quando o cliente HTTP permitir streaming com
  `Content-Length` explícito;
- adaptar o servidor Python para gravar o fluxo diretamente no arquivo temporário;
- adicionar testes de contrato para arquivo vazio, limite excedido, MIME inválido
  e resposta OCR válida;
- remover o contrato Base64 depois da migração e da validação em container.

**Risco principal**

- garantir que o cliente Java sempre declare o tamanho conhecido, evitando a
  regressão de `411 Length Required` já observada.

---

### BL-005 — Instrumentar o pipeline documental

**Status:** Proposta

**Problema**

Os logs atuais permitem diagnóstico pontual, mas não mostram tendências de
qualidade, custo e desempenho do OCR, embeddings e fallback Gemini.

**User story operacional**

> Como operador da aplicação, quero métricas do processamento documental, para
> detectar degradações, ajustar limiares e acompanhar o consumo do fallback.

**Métricas sugeridas**

```text
document.ingestion.total
document.ingestion.failures
document.ingestion.duration
document.ocr.duration
document.ocr.confidence
document.ocr.lines
document.ocr.failures
document.vision.fallbacks
document.vision.duration
document.embedding.duration
document.embedding.chunks
document.grounding.files
document.grounding.tokens
document.grounding.semantic_score
```

**Tags de baixa cardinalidade sugeridas**

- `mime_type` normalizado;
- `status`;
- `extraction_method`;
- `fallback_reason_category`;
- `model` com conjunto controlado de valores.

IDs, nomes de arquivo, prompts e conteúdo documental não devem ser usados como
tags de métricas.

**Critérios de aceite**

1. É possível medir taxa de sucesso e duração por etapa.
2. É possível medir quantos documentos acionam Gemini e por qual categoria.
3. Nenhuma métrica expõe conteúdo ou identificadores de alta cardinalidade.
4. Logs correlacionam uma ingestão por ID sem registrar conteúdo extraído.
5. Existe documentação de como consultar as métricas localmente.

**Proposta de implementação**

- usar Micrometer integrado ao Spring Boot;
- criar timers e counters nos limites de cada serviço, sem misturar regra de
  negócio com detalhes do backend de métricas;
- avaliar Actuator + Prometheus quando houver necessidade real de dashboard;
- incluir métricas equivalentes no serviço Python ou expô-las indiretamente pelo
  backend a partir do resultado OCR;
- iniciar apenas com métricas essenciais antes de adicionar infraestrutura de
  visualização.

---

### BL-006 — Exibir e validar orçamento de contexto no frontend

**Status:** Proposta

**Problema**

O backend conhece `contextTokenCount`, mas o usuário só descobre que os anexos
excedem o orçamento depois de tentar enviar a mensagem.

**User story**

> Como usuário, quero visualizar o tamanho dos documentos e o orçamento de
> contexto selecionado, para escolher anexos que possam ser processados juntos.

**Requisitos funcionais**

- retornar `contextTokenCount` na listagem de arquivos;
- retornar ou disponibilizar o orçamento máximo configurado;
- mostrar tokens por arquivo `READY`;
- mostrar soma dos anexos selecionados;
- alertar antes do envio quando o limite for excedido;
- manter a validação autoritativa no backend.

**Exemplo de interface**

```text
contrato.pdf       18.420 tokens
regulamento.pdf    32.180 tokens

Contexto selecionado: 50.600 / 200.000 tokens
```

**Critérios de aceite**

1. A soma é atualizada ao anexar ou remover um arquivo.
2. Arquivos ainda em processamento não participam da soma.
3. O frontend impede ou alerta claramente um envio acima do limite.
4. Alterações de configuração no backend são refletidas sem valor duplicado e
   hardcoded no frontend.
5. A API continua rejeitando requisições inválidas mesmo que o frontend seja
   contornado.

**Proposta de implementação**

- ampliar `ChatFileResponse` com `contextTokenCount` caso ainda não esteja exposto;
- criar endpoint leve de capacidades, por exemplo
  `GET /api/ai/chat/capabilities`, ou incluir o orçamento na resposta da listagem;
- calcular a soma com signals no componente Angular;
- apresentar tokens como estimativa, não como garantia exata da janela do modelo.

---

### BL-007 — Suportar múltiplas conversas por usuário

**Status:** Ideia futura; não implementar sem requisito de produto

**Problema**

Atualmente `conversationId = chat-<JWT subject>`, portanto cada usuário possui uma
única conversa lógica. Histórico e arquivos de assuntos diferentes compartilham
o mesmo escopo de recuperação.

**User story**

> Como usuário, quero criar conversas separadas, para organizar assuntos e evitar
> que arquivos de um contexto sejam recuperados em outro.

**Requisitos funcionais iniciais**

- criar, listar, renomear e excluir conversas;
- cada conversa possuir ID independente e proprietário;
- histórico, arquivos e busca semântica serem isolados por conversa;
- impedir acesso a conversation IDs de outros usuários;
- definir comportamento de exclusão e retenção de arquivos.

**Modelagem proposta**

```text
(:Usuario {subject})
    └──[:POSSUI]->(:Conversa {id, title, createdAt, updatedAt})
                      ├──[:POSSUI]->(:Arquivo)-[:CONTEM]->(:Chunk)
                      └── referência lógica pelo mesmo id a (:Session)
```

Não é recomendado ligar arquivos diretamente ao `:Session`, pois esse nó é
administrado e recriado pelo Spring AI.

**Critérios de aceite de alto nível**

1. Um usuário pode manter duas conversas com históricos e arquivos isolados.
2. A recuperação semântica nunca cruza conversas.
3. Todas as operações validam simultaneamente proprietário e conversation ID.
4. A migração associa os dados existentes a uma conversa padrão sem perda.
5. Excluir uma conversa trata mensagens, arquivos no disco, chunks e nós Neo4j
   segundo uma política explícita.

**Proposta de implementação**

- introduzir primeiro a entidade de domínio `Conversa`;
- migrar `ContextoChat` para `Conversa` ou adicionar o novo label de maneira
  compatível;
- substituir `conversationIdFor(subject)` por um ID recebido na rota e validado
  contra o usuário autenticado;
- adicionar rotas e UI de seleção de conversa;
- criar migração idempotente para os dados existentes;
- somente depois avaliar títulos automáticos e arquivamento.

**Risco principal**

- a mudança atravessa autenticação, rotas, histórico, arquivos, frontend e
  migração de dados; não deve ser tratada como simples alteração de ID.

---

### BL-008 — Deduplicar arquivos por SHA-256

**Status:** Ideia futura

**Problema**

O SHA-256 já é calculado, mas uploads idênticos podem gerar armazenamento,
extração, OCR e embeddings duplicados.

**User story**

> Como usuário, quero ser avisado quando enviar novamente o mesmo arquivo, para
> evitar duplicatas e processamento desnecessário.

**Escopo inicial recomendado**

- detectar duplicatas somente dentro da mesma conversa e do mesmo proprietário;
- retornar o arquivo existente ou pedir confirmação antes de duplicar;
- não compartilhar automaticamente arquivos ou nós entre usuários.

**Critérios de aceite**

1. Dois arquivos com o mesmo conteúdo e nomes diferentes são detectados pelo
   SHA-256.
2. A verificação respeita proprietário e conversa.
3. Uma colisão de nome sem colisão de SHA-256 não é tratada como duplicata.
4. A exclusão do arquivo existente não afeta dados de outro usuário.
5. A resposta da API permite ao frontend informar qual arquivo já existe.

**Proposta de implementação**

- criar índice composto ou consulta por `ownerSubject`, `conversationId` e
  `sha256`;
- decidir entre retornar `409 Conflict`, reutilizar o arquivo existente ou
  permitir duplicação explícita;
- avaliar cache de extração somente depois da deduplicação por conversa estar
  estável;
- não implementar deduplicação global antes de definir retenção, referência e
  isolamento de dados.

## 5. Requisitos não funcionais comuns

Todos os itens implementados a partir deste backlog devem observar:

- autorização baseada no usuário autenticado, sem confiar apenas em IDs enviados
  pelo frontend;
- isolamento estrito entre conversas e proprietários;
- operações idempotentes quando houver processamento assíncrono;
- mensagens de erro sem conteúdo documental sensível;
- não registrar prompts, transcrições ou arquivos em logs operacionais;
- compatibilidade com o modelo `gemini-3.1-flash-lite`, salvo decisão explícita;
- indisponibilidade do OCR local não deve ser mascarada por fallback Gemini;
- testes unitários para regras de seleção e autorização;
- testes de contrato para integrações HTTP;
- migrações Neo4j idempotentes e com estratégia de rollback ou recuperação;
- preservação do arquivo original para download durante seu ciclo de retenção;
- documentação de novas configurações e variáveis de ambiente.

## 6. Definition of Ready sugerida

Um item pode sair de `Backlog` para `Ready` quando possuir:

- problema e resultado esperado claramente descritos;
- user story validada;
- critérios de aceite verificáveis;
- decisões pendentes resolvidas;
- dependências identificadas;
- impacto em API, banco, arquivos e frontend analisado;
- abordagem de teste definida;
- estimativa de esforço;
- autorização explícita para implementação.

## 7. Definition of Done sugerida

Um item pode ser considerado concluído quando:

- todos os critérios de aceite estiverem atendidos;
- testes relevantes estiverem automatizados e aprovados;
- build de backend e/ou frontend estiver aprovado;
- migrações e configurações estiverem documentadas;
- fluxo principal tiver sido validado localmente quando aplicável;
- logs e métricas não expuserem conteúdo sensível;
- documentação do repositório estiver atualizada;
- alterações estiverem revisadas, commitadas e publicadas quando solicitado.

## 8. Migração para uma plataforma de gestão

### Recomendação principal: GitHub Issues + GitHub Projects

É a opção inicial recomendada porque o código já está no GitHub e não exige uma
segunda fonte de verdade. O plano GitHub Free inclui Issues e Projects. Projects
permite tabela, board, roadmap, campos customizados, gráficos e automações.

Documentação oficial:

- <https://github.com/pricing>
- <https://docs.github.com/en/issues/planning-and-tracking-with-projects/learning-about-projects/about-projects>
- <https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests>

**Campos sugeridos para o Project**

| Campo | Tipo | Valores sugeridos |
|---|---|---|
| Status | Single select | Backlog, Ready, In Progress, Review, Done |
| Priority | Single select | P0, P1, P2, P3 |
| Type | Single select | Feature, Bug, Tech Debt, Spike |
| Area | Single select | Chat, Grounding, Documents, OCR, Frontend, Infrastructure |
| Estimate | Number | Escala a definir |
| Target iteration | Iteration | Opcional |

**Labels sugeridos no repositório**

```text
type:feature
type:bug
type:tech-debt
type:spike
area:chat
area:grounding
area:documents
area:ocr
area:frontend
area:infrastructure
priority:p0
priority:p1
priority:p2
priority:p3
```

**Template sugerido para cada issue**

```markdown
## Problema

## User story

Como ...
Quero ...
Para ...

## Requisitos funcionais

## Critérios de aceite

- [ ] ...

## Proposta técnica

## Fora de escopo

## Riscos e dependências

## Estratégia de validação
```

Cada seção `BL-xxx` deste arquivo pode ser convertida em uma issue. Depois da
migração, este documento deve apontar para as issues em vez de permanecer como
uma segunda cópia editável do backlog.

### Alternativa mais formal: YouTrack Cloud

YouTrack é indicado se houver preferência por uma ferramenta dedicada e mais
próxima de Jira. O plano gratuito atual atende equipes de até 10 usuários, possui
30 GB e oferece a edição completa, exceto logo personalizado.

- <https://www.jetbrains.com/youtrack/buy/>

### Alternativa orientada a produto: Linear

Linear possui interface simples e oferece issues, projetos, ciclos e iniciativas,
mas o plano gratuito atual é limitado a 250 issues e duas equipes. É adequado para
um backlog pequeno, porém o limite pode exigir migração ou pagamento futuramente.

- <https://linear.app/pricing>

## 9. Notas para refinamento futuro

- Prioridade não equivale a autorização para implementação.
- Estimativas devem ser feitas quando o item for selecionado para refinamento.
- Mudanças no esquema Neo4j exigem plano explícito para dados já existentes.
- A plataforma escolhida deve se tornar a fonte de verdade; este arquivo deve ser
  arquivado ou reduzido a um índice após a migração.
