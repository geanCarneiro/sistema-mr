# AgentRuntime do Sistema MR

> **Status:** referência arquitetural consolidada para implementação
> **Escopo:** BL-014
> **Última revisão:** 2026-09-06

Este documento descreve o modelo de execução autônoma planejado para o Sistema
MR. Ele serve como referência permanente para a implementação do `AgentRuntime`
e como contrato conceitual para as Issues que dependem da BL-014.

As decisões deste documento representam a direção arquitetural adotada para o
Sistema MR. Detalhes de implementação podem evoluir, mas uma alteração que
mude estes contratos deve atualizar este documento e as Issues afetadas.

## 1. Contexto

O fluxo atual do Sistema MR é essencialmente:

```text
mensagem do usuário
    → preparação do contexto
    → uma chamada ao modelo
    → resposta textual
    → persistência da interação
```

A direção futura é transformar a IA no motor de decisão do sistema. O backend
deixa de concentrar cada fluxo de negócio e passa a fornecer contexto,
ferramentas, persistência, políticas e garantias de execução.

Na experiência do usuário, haverá um chat geral e chats específicos organizados
por abas. O chat geral funciona como a entrada principal: uma mensagem pode
continuar um assunto existente, iniciar implicitamente um novo assunto ou exigir
esclarecimento. Quando o usuário escolhe um assunto específico, esse assunto
passa a ser o contexto presumido da mensagem, sem impedir que o sistema detecte
um possível engano.

```text
evento
    → seleção ou criação de um assunto
    → AgentRun
    → decisão da IA
    → ferramenta ou resposta
    → observação do resultado
    → nova decisão, espera ou finalização
```

O backend continua responsável por autenticação, autorização, isolamento por
usuário, validação, limites, idempotência, consentimento, auditoria e execução
segura. A IA decide o próximo passo, mas não acessa diretamente o banco nem
ultrapassa as políticas do sistema.

## 2. Objetivos

O `AgentRuntime` deve:

- substituir gradualmente o fluxo de uma chamada única por um ciclo controlado;
- representar uma execução como uma unidade persistível e retomável;
- permitir vários assuntos paralelos para o mesmo usuário;
- oferecer um chat geral capaz de classificar mensagens em assuntos existentes,
  criar um novo assunto ou pedir esclarecimento;
- oferecer chats específicos com o assunto selecionado como contexto presumido;
- manter o chat geral fixo e visível enquanto as abas dos assuntos podem rolar;
- pausar uma execução sem manter uma chamada ativa ao modelo;
- retomar uma execução por mensagem, resposta, evento ou agendamento;
- escolher e trocar modelos ou provedores conforme capacidade, política,
  disponibilidade e quota;
- manter o contexto de cada assunto separado do histórico global;
- permitir que uma execução consulte assuntos relacionados de forma explícita;
- proteger o sistema contra loops, duplicidade, falhas e consumo ilimitado;
- preservar compatibilidade com o histórico conversacional existente.

## 3. Fora do escopo imediato

O `AgentRuntime` não define, por si só:

- todos os tipos de documento do domínio;
- todas as ferramentas que a IA poderá usar;
- um provedor específico de modelos;
- um modelo local específico;
- o schema final de cada estado de negócio;
- canais externos de notificação;
- uma política definitiva de retenção para todos os dados;
- valores imutáveis para as quotas dos provedores;
- agrupamento de invocações de vários assuntos ou usuários em uma chamada.

Esses temas podem ser implementados pelas Issues seguintes, desde que respeitem
os contratos definidos aqui.

## 4. Princípios arquiteturais

### 4.1 A IA decide; o backend garante

A IA pode decidir consultar um documento, guardar uma memória, atualizar um
estado ou pedir confirmação. A operação efetiva sempre passa por uma ferramenta
ou serviço do backend, que verifica a política aplicável.

### 4.2 Uma execução é finita

Um `AgentRun` não fica permanentemente ativo. Ele executa uma quantidade limitada
de passos e termina em um estado terminal ou suspenso.

Um assunto, por outro lado, pode permanecer aberto por muito tempo e possuir
vários `AgentRun`s sucessivos.

```text
Assunto: organizar receita médica
    ├── AgentRun 1: extrair informações
    ├── AgentRun 2: aguardar confirmação
    └── AgentRun 3: criar lembrete após confirmação
```

### 4.3 Provedores são substituíveis

O domínio e o runtime não devem depender de Gemini, Grok, Groq, Ollama ou de
qualquer outro fornecedor. O provedor concreto é um adaptador selecionado em
tempo de execução.

### 4.4 Esperar não é falhar

Uma execução que precisa de uma resposta do usuário, de um horário, de uma
quota ou de um serviço pode ser suspensa. A suspensão deve preservar contexto e
permitir retomada sem recriar a execução do zero.

### 4.5 Contexto explícito

O modelo recebe o contexto necessário para a decisão atual. O sistema não deve
enviar automaticamente todo o histórico do usuário ou todos os assuntos
paralelos.

## 5. Conceitos principais

### 5.1 Assunto

O **Assunto** é o contexto durável de um trabalho ou intenção do usuário.

Exemplos:

- acompanhar uma receita médica;
- organizar uma conta;
- responder a um convite;
- planejar uma viagem;
- acompanhar uma tarefa recorrente.

Um assunto pode conter:

- objetivo;
- título e descrição;
- usuário proprietário;
- mensagens relacionadas;
- documentos e evidências;
- estado operacional;
- pendências;
- lembretes e alertas;
- `AgentRun`s históricos;
- data da próxima ação ou revisão.

O assunto não precisa estar ativo continuamente. Ele pode permanecer aguardando
uma mensagem, evento, data ou decisão do usuário.

### 5.1.1 Chat geral e chats de assunto

O sistema deve possuir um chat geral, que funciona como porta de entrada para
assuntos novos e para mensagens cujo contexto ainda não foi determinado. A
navegação entre os assuntos será feita por abas.

A aba do chat geral deve ficar fixa, fora da faixa rolável das demais abas. Os
assuntos podem se acumular e rolar horizontalmente, mas o chat geral permanece
sempre acessível. Não haverá um botão separado de “novo assunto”: a criação é
implícita quando uma mensagem enviada ao chat geral não se encaixa em nenhum
assunto existente.

Quando uma mensagem chega ao chat geral, uma etapa de roteamento pode receber:

- a mensagem atual;
- os assuntos disponíveis para o usuário;
- seus títulos, estados e resumos mínimos;
- referências temporais ou documentais relevantes.

Essa etapa deve retornar uma decisão estruturada internamente:

```text
CONTINUE_SUBJECT
CREATE_SUBJECT
ASK_CLARIFICATION
LINK_MULTIPLE_SUBJECTS
```

O chat específico assume o assunto selecionado como contexto principal. Ainda
assim, o sistema pode avisar quando a mensagem parecer incompatível e pedir
confirmação ou sugerir o assunto mais provável.

As sugestões de roteamento, pedidos de esclarecimento e avisos de divergência
devem aparecer como mensagens naturais da própria IA no chat. A interface não
deve obrigar o usuário a manipular uma tela técnica de classificação para
continuar a conversa.

Os protótipos das alternativas avaliadas estão em
`docs/prototypes/subject-navigation/`. A opção escolhida para implementação é a
de abas.

### 5.2 AgentRun

O **AgentRun** é uma execução limitada do agente para avançar um assunto.

Ele deve possuir, no mínimo:

- identificador único;
- usuário e assunto principal;
- motivo do disparo;
- data de criação, início, pausa e término;
- estado atual;
- contador e orçamento de passos;
- chamadas de modelo e ferramentas realizadas;
- contexto utilizado ou referência para reconstruí-lo;
- resultado ou motivo da suspensão/falha;
- política de provedor utilizada;
- versão do contrato de execução.

Um `AgentRun` pode produzir uma resposta, alterar um estado, criar uma
pendência, aguardar um evento ou finalizar sem ação externa.

### 5.3 ModelInvocation

Uma **ModelInvocation** é uma chamada individual a um modelo dentro de um
`AgentRun`.

Essa separação permite que um mesmo run use mais de um provedor:

```text
AgentRun
    ├── ModelInvocation 1 → provedor A
    ├── ferramenta local
    ├── ModelInvocation 2 → provedor B
    └── resposta final
```

Cada chamada deve registrar o provedor, modelo, motivo da seleção, consumo,
latência, resultado e falha, quando houver.

### 5.4 Message

Uma **Message** é uma mensagem conversacional persistida.

O histórico persistido é único. A interface pode projetar esse histórico de
formas diferentes: uma linha do tempo global no chat geral e recortes por
assunto nos chats específicos.

Modelo inicial proposto:

```text
Message
    - id
    - usuário
    - conteúdo
    - timestamp
    - assunto principal opcional
    - assuntos relacionados opcionais
    - origem
```

Uma mensagem não deve ser duplicada apenas para aparecer em mais de um contexto.
Os vínculos com assuntos devem ser representados separadamente quando houver
mais de uma relação. A mensagem continua sendo um único registro na linha do
tempo global.

No chat geral, a classificação pode ser feita por uma IA local ou outro
componente autorizado. A classificação deve retornar confiança, justificativa
curta e ação sugerida. Baixa confiança não deve produzir associação silenciosa.

No chat específico, a associação inicial é determinada pela seleção do usuário.
Uma divergência forte pode gerar uma sugestão como:

```text
“Esta mensagem parece tratar do assunto ‘Oportunidade de emprego’.
Deseja continuar nesse assunto ou permanecer em ‘Consulta médica’?”
```

### 5.5 Contexto de execução

O **contexto de execução** é o conjunto selecionado para uma decisão específica.

Ele pode incluir:

- mensagem atual;
- histórico relevante do assunto;
- estado atual;
- memórias pertinentes;
- tarefas pendentes;
- documentos ou trechos selecionados;
- assuntos relacionados autorizados;
- instruções e ferramentas disponíveis;
- orçamento restante.

O contexto não é necessariamente igual ao histórico completo.

### 5.6 Provider e Model

Um **Provider** é uma origem ou plataforma de inferência. Um **Model** é um
modelo disponibilizado por essa origem.

Uma instalação, projeto, endpoint ou credencial concreta pode ser tratada como
uma implantação do provedor.

```text
Provider
    └── Model
          └── Deployment
```

Essa distinção evita que o domínio dependa diretamente de nomes externos.

### 5.7 Tool

Uma **Tool** é uma capacidade executada pelo backend a pedido da IA.

As ferramentas devem possuir contratos estruturados, declarar seus requisitos e
retornar resultados normalizados. A IA não acessa diretamente bancos,
repositórios ou integrações externas.

## 6. Ciclo de vida do AgentRun

O ciclo básico é:

```text
criado
  → RUNNING
  → decisão do modelo
  ├── resposta final     → COMPLETED
    ├── chamada de tool     → execução → nova decisão
    ├── pergunta ao usuário → WAITING_FOR_USER
    ├── espera por serviço   → WAITING_FOR_TOOL
    ├── espera por quota     → WAITING_FOR_CAPACITY
  ├── agendamento          → SCHEDULED
  ├── pausa explícita      → PAUSED
  ├── erro recuperável     → retentativa ou espera
  ├── erro definitivo      → FAILED
  └── cancelamento         → CANCELLED
```

### 6.1 Estados de execução

| Estado | Significado | Chamada ativa? | Pode retomar? |
|---|---|---:|---:|
| `RUNNING` | O runtime está executando um passo | Possivelmente | Sim |
| `WAITING_FOR_USER` | Falta resposta, dado ou confirmação | Não | Sim |
| `WAITING_FOR_TOOL` | Ferramenta ou serviço ainda não respondeu | Não necessariamente | Sim |
| `WAITING_FOR_CAPACITY` | Não há quota, provedor ou recurso disponível | Não | Sim |
| `SCHEDULED` | Execução aguardando uma data ou evento | Não | Sim |
| `PAUSED` | Execução suspensa explicitamente | Não | Sim |
| `COMPLETED` | Objetivo da execução concluído | Não | Não, salvo nova execução |
| `FAILED` | Execução não pôde ser concluída | Não | Por novo run ou retry seguro |
| `CANCELLED` | Execução cancelada | Não | Por novo run, se permitido |

`WAITING_FOR_USER`, `WAITING_FOR_TOOL`, `WAITING_FOR_CAPACITY`, `SCHEDULED` e
`PAUSED` representam estados suspensos, não processamento contínuo.

Esses estados têm semânticas diferentes:

- `PAUSED`: o assunto não terminou, mas não há uma decisão a ser tomada agora;
  uma nova mensagem do usuário ou outra retomada explícita poderá iniciar o
  próximo run;
- `WAITING_FOR_CAPACITY`: existe uma limitação técnica de provider, modelo,
  quota ou recurso; o sistema deve tentar novamente conforme uma política de
  retry/cooldown;
- `SCHEDULED`: a próxima execução está prevista para um momento ou evento
  específico.

### 6.2 Estado terminal e estado do assunto

O estado do `AgentRun` não deve ser confundido com o estado do assunto.

```text
AgentRun: COMPLETED
Assunto: OPEN
```

Isso significa que uma ativação terminou, mas o assunto ainda pode precisar de
novas ações no futuro.

## 7. Orquestração de provedores e modelos

### 7.1 Contrato interno

O runtime deve trabalhar com um contrato interno normalizado, semelhante a:

```text
ModelRequest
    - runId
    - assunto principal
    - contextPack
    - mensagens
    - ferramentas disponíveis
    - restrições de dados
    - capacidades exigidas
    - política de seleção
    - orçamento restante

ModelResponse
    - providerId
    - modelId
    - texto ou resposta estruturada
    - tool calls
    - finish reason
    - usage
    - latência
    - erro normalizado
```

Os SDKs externos devem ser usados somente nos adaptadores.

### 7.2 Arquitetura de adapters

O runtime deve depender de uma porta interna, não de uma implementação concreta:

```text
AgentRuntime
    → ModelGateway
        → ProviderRouter
            → ProviderRegistry
                → ProviderAdapter
```

O adapter pode ser específico para um SDK ou genérico para uma família de APIs
compatíveis. O identificador do provedor não deve espalhar condicionais pelo
domínio.

### 7.3 Catálogo de providers e models

**Proposta:** manter um catálogo simples e configurável para descrever providers
e seus models. Os dados específicos de autenticação permanecem na configuração
operacional do provider, enquanto capacidades, detalhes e limites técnicos ficam
no mapping versionado.

Exemplo conceitual:

```yaml
ai:
  routing:
    default-policy: AUTO
  providers:
    provider-a:
      models:
        fast-text:
          adapter: ADAPTER_TYPE
          internal-role: FAST_TEXT
          capabilities: [TEXT, STRUCTURED_OUTPUT, TOOL_CALLING]
          allowed-data: [PUBLIC, NORMAL]
          technical-limits:
            context-tokens: PROVIDER_DEFINED
            max-output-tokens: PROVIDER_DEFINED
          priority: 10
```

O formato acima é ilustrativo. A decisão de schema e arquivo definitivo deve
considerar a configuração já existente no backend.

O mapping deve ser validado na inicialização. Dados dinâmicos como quota
restante, cooldown, saúde e latência não devem ser tratados como valores fixos
desse arquivo.

### 7.4 Capacidade, política e disponibilidade

Esses conceitos devem permanecer separados:

```text
Capacidade:
  o modelo consegue processar imagem?

Política:
  é permitido enviar esta categoria de dado?

Disponibilidade:
  o modelo está saudável e possui quota agora?
```

Um modelo pode tecnicamente aceitar uma imagem, mas não estar autorizado a
receber um documento médico. Também pode estar autorizado e tecnicamente apto,
mas indisponível por quota ou falha operacional.

### 7.5 Seleção e troca

A elegibilidade de uma rota deve considerar:

```text
capacidades exigidas
  AND política de dados
  AND saúde do provider
  AND quota disponível
  AND orçamento do run
  AND compatibilidade com a etapa atual
```

O runtime pode trocar de provedor entre invocações:

```text
invocação 1 → provider A
tool local
invocação 2 → provider B, porque A está próximo da quota
```

Cada troca deve ser registrada. A troca não deve ocorrer no meio de uma
ferramenta já iniciada.

Políticas sugeridas:

```text
AUTO
PREFER_PROVIDER
REQUIRE_PROVIDER
LOCAL_FIRST
LOCAL_ONLY
CLOUD_ONLY
```

`PREFER_PROVIDER` permite fallback. `REQUIRE_PROVIDER` impede a troca quando a
continuidade com um provider específico for necessária.

### 7.6 Quota e capacidade

O runtime deve tratar quota como um orçamento multidimensional:

- por run;
- por provider;
- por janela de tempo;
- global da aplicação.

O orçamento por run continua necessário para impedir loops e execuções
ilimitadas. O limite de quota do provider é uma restrição operacional do
Sistema MR, não um plano de consumo escolhido pelo usuário. Planos de uso por
usuário ficam fora do escopo atual. Se um provider expuser quotas separadas por
model, essa informação pode ser registrada como detalhe técnico do provider sem
virar uma política de uso por usuário.

Estados operacionais possíveis:

```text
AVAILABLE
NEAR_LIMIT
EXHAUSTED
UNKNOWN
COOLDOWN
UNAVAILABLE
```

O runtime não deve depender de números fixos no código. Limites concretos devem
ser configuráveis e, quando possível, atualizados a partir de informações do
provider.

Quando nenhuma rota for elegível, o run deve aguardar capacidade ou falhar de
forma recuperável. Não deve trocar para cloud automaticamente quando a política
for `LOCAL_ONLY` ou quando os dados não puderem sair do ambiente local.

### 7.7 IA local

“Local” significa que o modelo não depende de um serviço remoto ou de uma quota
externa. Isso não garante que o serviço esteja operacional naquele instante.

O runtime deve distinguir:

```text
LOCAL_ONLY
  → aguarda ou falha de forma recuperável se o serviço local não estiver apto

LOCAL_FIRST
  → tenta local; pode usar cloud se política e capacidades permitirem
```

Uma verificação local pode considerar processo, saúde, modelo carregado,
memória, concorrência, modalidade e suporte a ferramentas.

O roteamento de uma mensagem do chat geral para um assunto é um bom candidato
para uma IA local, desde que o resultado seja estruturado e a baixa confiança
possa gerar uma solicitação de esclarecimento.

## 8. Assuntos, histórico e contexto múltiplo

### 8.1 Linha global e histórico por assunto

O sistema deve oferecer simultaneamente:

```text
Linha do tempo global
  Todas as mensagens em ordem cronológica

Histórico do assunto
  Mensagens associadas a um assunto

Contexto do AgentRun
  Recorte selecionado para a decisão atual
```

O histórico global não deve obrigar o runtime a enviar todos os assuntos ao
modelo.

### 8.2 Associação de mensagens

Uma mensagem pode ter um assunto principal e assuntos relacionados. O sistema
deve evitar copiar o conteúdo apenas para montar históricos diferentes.

No chat geral, a IA pode escolher um assunto existente, criar implicitamente um
novo assunto, identificar mais de um assunto ou pedir esclarecimento. No chat
específico, o assunto selecionado é presumido, mas a IA pode sinalizar uma
divergência relevante.

Para o usuário, a decisão deve aparecer como continuação natural da conversa.
Por exemplo:

```text
“Entendi. Essa mensagem parece estar relacionada à sua entrevista de emprego,
e não somente à consulta médica. Posso manter os dois assuntos relacionados?”
```

O sistema pode manter uma decisão estruturada internamente, mas não deve exigir
que o usuário opere uma tela técnica de classificação.

A associação deve ser persistida com sua origem, por exemplo:

```text
EXPLICIT_USER
  usuário escolheu ou confirmou o assunto

MODEL_SUGGESTED
  associação sugerida por classificação automática

SYSTEM_CONFIRMED
  regra ou confirmação posterior consolidou a associação
```

Uma sugestão de baixa confiança não deve alterar silenciosamente o histórico do
assunto.

Uma mensagem ambígua não deve ser associada silenciosamente ao assunto errado.
Quando necessário, o sistema pode pedir confirmação ou criar um contexto de
coordenação.

### 8.3 Execução envolvendo vários assuntos

O padrão é um assunto principal por execução. Uma execução pode, porém,
consultar assuntos relacionados de forma explícita, inclusive quando a própria
mensagem fizer referência a outro assunto:

```text
Assunto principal: organizar minha semana
Contextos autorizados:
  - receita médica
  - agenda
  - tarefas
```

Exemplo conversacional:

```text
Assunto atual: consulta médica
Mensagem: “Não posso marcar nesse dia porque tenho uma entrevista de emprego.”
Referência detectada: assunto “oportunidade de emprego”
```

Essa referência não precisa mover a mensagem para outro assunto. Ela pode
permanecer no assunto principal e criar um vínculo contextual com o assunto
paralelo.

Alterações em assuntos secundários devem identificar claramente o destino e
passar pelas políticas de cada assunto.

### 8.4 Agrupamento de invocações

O agrupamento de invocações não faz parte do núcleo inicial da BL-014. Ele deve
ser analisado em uma Spike própria antes de qualquer implementação.

O agrupamento pode reduzir o número de requisições, mas aumenta o risco de:

- mistura de contextos;
- associação de uma decisão ao assunto errado;
- vazamento entre usuários;
- falha de um assunto afetar outros;
- dificuldade para repetir apenas uma parte da chamada;
- aplicação incorreta de ferramentas ou permissões.

A futura Spike deve analisar, entre outros pontos, se o agrupamento é seguro para
assuntos do mesmo usuário, se pode envolver usuários diferentes, como os
contextos serão isolados e se a economia de quota compensa a complexidade.

## 9. Espera, retomada e alertas

Uma pendência deve ser persistida como parte do assunto e relacionada ao run que
a criou.

```text
PendingInquiry
    - pergunta
    - assunto
    - AgentRun de origem
    - campos necessários
    - prioridade
    - prazo ou reminderAt
    - status
    - última notificação
```

O sistema deve separar:

```text
alertar o usuário sobre uma pendência
≠
executar novamente o agente
```

Um alerta simples pode ser emitido sem chamar o modelo. Uma nova execução só é
necessária quando houver resposta, evento ou decisão que exija raciocínio.

O alerta pode ser proativo: o Sistema MR não precisa esperar uma nova requisição
do usuário para lembrar uma pendência. Um agendador pode verificar assuntos em
`WAITING_FOR_USER`, avaliar prazo, prioridade, silenciamento e último alerta, e
emitir a notificação no momento adequado.

Um assunto pausado ou suspenso pode ser retomado por diferentes gatilhos:

- nova mensagem do usuário;
- resposta a uma pergunta ou confirmação pendente;
- véspera ou vencimento de uma agenda;
- passagem de dias após um vencimento ou prazo;
- data e horário definidos pelo assunto;
- documento processado ou atualizado;
- mudança em uma entidade ou estado relacionado;
- mensagem em assunto paralelo que crie uma relação relevante;
- retorno de provider ou ferramenta que estava aguardando;
- liberação de quota ou capacidade de execução.

O gatilho deve criar ou retomar um `AgentRun` conforme a natureza do estado.
Um alerta proativo simples pode ser emitido sem nova chamada ao modelo; uma
retomada com decisão exige uma nova execução.

## 10. Limites, idempotência e concorrência

O runtime deve aplicar limites em pelo menos estas dimensões:

- número de iterações;
- número de invocações de modelo;
- tokens de entrada e saída;
- tempo total;
- chamadas por ferramenta;
- execuções simultâneas por assunto;
- orçamento por provider e global da aplicação.

Chamadas de ferramentas que produzem efeitos devem possuir uma chave de
idempotência associada ao run e ao passo. Um retry não pode criar duas tarefas,
enviar duas notificações ou aplicar duas vezes a mesma transição.

Aqui, “concorrentes” significa runs prontos ou ativos ao mesmo tempo — não
usuários pagando por cotas diferentes. O Sistema MR pode usar uma fila de
execução como comportamento padrão e permitir paralelismo controlado quando os
runs forem independentes.

Regras iniciais:

- no máximo um run alterando um mesmo assunto por vez;
- runs de assuntos independentes podem executar em paralelo, dentro do limite
  global de workers e providers;
- runs que dependem de outro devem aguardar sua conclusão ou resultado;
- runs que consultam ou alteram assuntos relacionados precisam declarar essa
  relação antes da execução;
- um lock, lease ou mecanismo equivalente deve impedir alterações concorrentes
  incompatíveis;
- falta de capacidade deve colocar o run na fila ou em `WAITING_FOR_CAPACITY`.

## 11. Falhas e retomada

Falhas devem ser classificadas, no mínimo, como:

```text
RECOVERABLE
  timeout, quota temporária, provider indisponível, falha transitória

REQUIRES_USER
  confirmação ausente, campo ambíguo, autorização necessária

POLICY_BLOCKED
  operação não permitida pelas regras de dados ou autonomia

INVALID_REQUEST
  contrato ou entrada inválida

PERMANENT
  falha que não deve ser repetida automaticamente
```

Uma retomada deve reconstruir o contexto a partir de dados persistidos, sem
depender apenas da memória do processo que foi interrompido.

## 12. Privacidade e segurança

O roteamento deve considerar a classificação dos dados antes de escolher um
provider.

Regras mínimas:

- dados sensíveis não vão para cloud por seleção implícita;
- a IA não autoriza sozinha a liberação de dados;
- o contexto enviado deve ser minimizado;
- documentos completos só devem ser incluídos quando necessários e permitidos;
- tokens, logs e métricas não devem expor conteúdo sensível por padrão;
- cada ferramenta valida usuário, assunto, permissão e escopo;
- trocas de provider ficam auditáveis;
- o provider escolhido não altera as regras de autorização do backend.

## 13. Relação com as demais Issues

| Issue | Relação com o AgentRuntime |
|---|---|
| BL-015 | Define ferramentas, contratos de chamada e política de autonomia. |
| BL-016 | Persiste entidades, observações e transições de estado. |
| BL-017 | Fornece memória semântica e snapshot contextual. |
| BL-018 | Define representações documentais completas e anonimizadas. |
| BL-019 | Implementa privacidade, roteamento e catálogo concreto de providers. |
| BL-020 | Evolui recuperação documental e seleção de evidências. |
| BL-021 | Dispara, pausa e retoma runs por eventos e perguntas. |
| BL-022 | Implementa notificações, aprovações e autonomia progressiva. |
| BL-023 | Persiste auditoria, observabilidade, retenção e recuperação operacional. |

O `AgentRuntime` define o ciclo e os contratos. As outras Issues fornecem as
capacidades usadas dentro desse ciclo.

## 14. Resultado esperado da BL-014

A BL-014 será considerada concluída quando houver uma definição consistente e
utilizável de:

- `Assunto`;
- `AgentRun`;
- `ModelInvocation`;
- estados e transições;
- pausa, retomada e finalização;
- orçamento e limites;
- seleção e troca de providers;
- roteamento automático de mensagens do chat geral e detecção de divergência em
  chats específicos;
- contrato normalizado de modelos;
- relação entre mensagens, assuntos e contextos;
- tratamento de quota, indisponibilidade e fallback;
- compatibilidade com o fluxo conversacional atual.

O resultado deve incluir documentação, contratos internos, configuração,
interfaces e um núcleo mínimo executável que desacople o `ChatModel` da
configuração padrão de um único provider. A implementação completa de cada
ferramenta, estado de domínio, memória, evento ou notificação pertence às
Issues específicas.

O núcleo mínimo deve permitir associar em tempo de execução uma configuração de
provider/model a um adaptador interno, sem exigir que o `AgentRuntime` conheça
detalhes do SDK ou da autenticação do provider.

## 15. Decisões consolidadas e trabalho posterior

As seguintes decisões estão consolidadas para a implementação da BL-014:

- a navegação de assuntos será feita por abas;
- a aba do chat geral ficará fixa e sempre visível;
- as abas dos assuntos poderão rolar quando se acumularem;
- não haverá botão de “novo assunto” nessa navegação;
- uma mensagem no chat geral poderá continuar um assunto, criar implicitamente
  um novo assunto, relacionar vários assuntos ou gerar uma pergunta de
  esclarecimento;
- a classificação ocorrerá de forma transparente, como parte da conversa com a
  IA, e não como uma tela técnica separada;
- no chat específico, o assunto selecionado será o contexto presumido;
- uma divergência relevante será informada pela própria IA na conversa;
- o histórico persistido será único, com projeções por assunto na interface;
- o chat geral exibirá a linha do tempo ampla, enquanto os chats específicos
  exibirão o recorte de cada assunto;
- uma mensagem poderá manter um assunto principal e referências a assuntos
  paralelos;
- um `AgentRun` terá um assunto principal, mas poderá consultar assuntos
  relacionados explicitamente;
- `PAUSED`, `WAITING_FOR_CAPACITY` e `SCHEDULED` serão estados distintos;
- o scheduler poderá emitir alertas proativos sem uma requisição nova do usuário;
- a execução usará fila por padrão e paralelismo controlado para assuntos
  independentes;
- o orçamento de uso externo será controlado por provider e pela aplicação, não
  por usuário;
- a configuração será provider-agnostic e o catálogo de capabilities ficará
  separado das credenciais;
- a BL-014 entregará o esqueleto que desacopla o `ChatModel` da configuração
  automática de um provider único;
- a persistência mínima necessária para reconstrução e retomada começará na
  BL-014;
- observabilidade detalhada, visualização operacional, retenção e limpeza serão
  aprofundadas na BL-023.

### 15.1 Catálogo mínimo de providers e models

O catálogo deve permanecer simples. A estrutura conceitual recomendada é:

```yaml
provider:
  adapter: ADAPTER_TYPE
  models:
    INTERNAL_MODEL_NAME:
      external-model: PROVIDER_MODEL_NAME
      capabilities: []
      allowed-data: []
      technical-limits: {}
```

Os dados de autenticação, endpoint e habilitação operacional permanecem no
`application.yaml` ou em seus profiles. O mapping descreve como cada provider e
model se traduzem para as entidades internas, o que conseguem processar, o que
é permitido enviar e quais limites técnicos conhecem.

O estado dinâmico — quota restante, saúde, cooldown, latência e falhas — fica
fora do mapping estático.

### 15.2 Fila e paralelismo

“Assuntos concorrentes” significa assuntos com runs prontos ou ativos ao mesmo
tempo. Não representa planos ou cotas individuais de usuários.

O comportamento inicial recomendado é:

```text
fila global de execução
    ├── run do assunto A → executa
    ├── run do assunto B → executa em paralelo se independente
    ├── run do assunto A → aguarda o run anterior
    └── run que altera A e B → declara dependência e coordena acesso
```

O número de workers e o paralelismo por provider são limites operacionais da
aplicação.

### 15.3 Trabalho posterior: agrupamento de invocações

Agrupar vários assuntos ou usuários em uma única chamada não faz parte da
implementação inicial. Deve ser criado como uma Spike própria para avaliar:

- viabilidade prática e economia real de quota;
- isolamento de contexto entre assuntos;
- impossibilidade de vazamento entre usuários;
- separação de tool calls e respostas;
- isolamento de falhas e retries;
- impacto sobre auditoria e permissões.

Até essa análise, o runtime deve processar cada assunto separadamente, podendo
apenas consultar referências relacionadas quando isso fizer parte explícita do
contexto da execução.

### 15.4 Mudanças posteriores no contrato

O contrato pode evoluir durante a implementação, mas mudanças que alterem o
significado de `Assunto`, `AgentRun`, estados, seleção de provider, associação de
mensagens ou regras de retomada devem atualizar este documento antes de serem
adotadas nas Issues dependentes.

### 15.5 Implementação inicial da BL-024

A primeira implementação concreta mantém o endpoint de chat compatível, mas
move sua orquestração para um serviço de aplicação e para o `AgentRuntime`.
O controller não escolhe diretamente o `ChatModel`, prepara o grounding nem
persiste a interação.

O runtime depende de um `ModelGateway`. A primeira rota configurada é o adapter
do Gemini, mas o provider e o model são registrados em um catálogo configurável
e a seleção informa o motivo utilizado. A política de dados é transportada no
`ModelRequest` como restrição de execução, sem ser implementada pelo runtime
nesta etapa; a decisão efetiva ficará na BL-019.

O `conversationId` continua sendo o limite interno da conversa universal de um
usuário, resolvido deterministicamente como `chat-<ownerSubject>`. Ele não faz
parte da experiência pública do frontend e não é substituído pelo `subjectId`:
assuntos são o contexto presumido de um `AgentRun` e uma projeção de navegação,
enquanto histórico, memória e anexos continuam universais ao usuário.

O endpoint de assuntos fornece a base para as abas do chat. A aba geral fica
fixa e os demais assuntos podem rolar horizontalmente. A seleção envia somente
o `subjectId`; o backend resolve o contexto, valida a posse do assunto e usa o
mesmo `conversationId` universal para memória, histórico e anexos.

Chamadas de ferramentas passam a ser reservadas antes da execução com uma chave
composta por `runId` e `toolCallId`. Isso impede a repetição silenciosa de uma
operação quando uma execução é retomada ou sofre uma falha entre a execução e o
registro do resultado.

O estado do `AgentRun` é persistido no Neo4j, mas a fila inicial é mantida em
memória. Após reinício, runs em estados retomáveis são reidratados na fila a
partir do estado persistido; o banco não é tratado como uma fila volátil. A
reconstrução automática do contexto completo de mensagens e callbacks ainda é
uma etapa posterior do runtime.

Para a configuração atual do projeto, a rota `gemini-3.1-flash-lite` registra
limite técnico de 1.048.576 tokens de entrada e 65.536 de saída. A quota
operacional observada no AI Studio para o nível gratuito é de 15 RPM, 250.000
TPM e 500 RPD. Esses valores são limites do projeto e devem continuar
configuráveis, distintos do orçamento por `AgentRun`.

### 15.6 Gateway local de privacidade e acompanhamento da execução

A BL-019 adiciona o serviço `local-ai-service` como uma fronteira única para
embeddings, decisão de privacidade, chat/tool calling e interpretação visual.
O serviço executa localmente em CPU, usando Gemma 3 4B IT quantizado em Q4 para
as operações generativas. A rota `gemma-local` é registrada no catálogo do
runtime e o `ModelGatewayRouter` escolhe o gateway local ou o Gemini conforme a
política de dados; a indisponibilidade do modelo local não provoca envio
automático para a nuvem.

A política calcula a maior sensibilidade entre os documentos selecionados:

```text
SENSITIVE | RESTRICTED | UNKNOWN → LOCAL_ONLY
PERSONAL                    → CLOUD_MINIMIZED
NORMAL                      → CLOUD_MINIMIZED
```

`CLOUD_FULL` não é inferido. Ele só pode resultar de uma autorização expressa
em linguagem natural e, mesmo assim, não é permitido quando o material contém
sensibilidade diferente de `NORMAL`. O modelo pode interpretar a intenção do
usuário, mas o backend continua sendo responsável por validar a política,
selecionar a rota e autorizar qualquer liberação.

Quando o OCR não produz uma representação utilizável, o extrator tenta a visão
local. Se a visão local não estiver disponível ou não conseguir interpretar o
material, o documento vai para revisão; não há fallback cloud implícito nesse
fluxo. Uma futura liberação para cloud multimodal deverá passar por um gateway
de release auditável compatível com esta política.

O chat público usa execução assíncrona: o `POST /ai/chat` retorna imediatamente
um `runId` e uma confirmação contextual, enquanto
`GET /ai/chat/runs/{runId}/events` mantém uma espera longa de até 55 segundos.
Cada resposta pode carregar a fase atual ou o resultado final. O frontend mostra
a fase como texto transitório e discreto, preservando a sensação de conversa;
streaming de tokens permanece fora do MVP.

Nesta primeira versão, o sinal de long polling e o resultado transitório ficam
em memória no processo da API. O `AgentRun` persistido continua sendo a fonte
de estado durável, mas a reconstrução completa de uma execução interrompida e
seus callbacks ainda precisa ser implementada antes de tratar reinício como
transparente para o usuário.
