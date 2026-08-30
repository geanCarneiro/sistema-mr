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
- Não inicie a stack completa do Docker Compose.
- Os únicos serviços permitidos com `docker compose up` são `neo4j` e `python-runner`, isolados ou juntos, e somente quando forem necessários para a tarefa ou validação atual.
- São permitidos `docker compose up neo4j`, `docker compose up python-runner` e `docker compose up neo4j python-runner`.
- Nunca inclua `backend`, `frontend` ou qualquer outro serviço em um comando `docker compose up` sem nova autorização explícita do usuário.
- Prefira validações locais, estáticas e unitárias que não dependam de serviços em execução.
- Não inicie frontend, backend ou outros serviços persistentes sem solicitação explícita do usuário.

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
  criação da Issue.
- Use comentários para transições solicitadas: `/backlog`, `/start`, `/review`
  e `/done`. O último comando também fecha a Issue.
- Ao criar uma PR para uma Issue, inclua `Closes #<numero>` no corpo. O fluxo move a
  Issue para `In Progress`, `Review` ou `Done` conforme o estado da PR.
- Para uma Issue antiga, adicione `tracked` e as labels de classificação. Se precisar
  forçar a sincronização, execute manualmente o workflow `Project automation` com o
  número da Issue e o status desejado.
- Não altere diretamente no Project valores derivados de labels, pois uma execução
  posterior pode sobrescrevê-los. O campo `Status` permanece a fonte de verdade do
  andamento.
- Consulte `.github/PROJECT_AUTOMATION.md` para configuração, operação, bootstrap e
  solução de problemas. Alterações na automação devem incluir os testes em
  `.github/scripts/project-automation.test.js`.
