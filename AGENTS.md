# Instruções para agentes

## Ambiente de desenvolvimento local

- Considere este repositório um ambiente de desenvolvimento local.
- Nunca execute `docker compose up` sem indicar explicitamente um serviço.
- Não inicie a stack completa do Docker Compose.
- Os únicos serviços permitidos com `docker compose up` são `neo4j` e `python-runner`, isolados ou juntos, e somente quando forem necessários para a tarefa ou validação atual.
- São permitidos `docker compose up neo4j`, `docker compose up python-runner` e `docker compose up neo4j python-runner`.
- Nunca inclua `backend`, `frontend` ou qualquer outro serviço em um comando `docker compose up` sem nova autorização explícita do usuário.
- Prefira validações locais, estáticas e unitárias que não dependam de serviços em execução.
- Não inicie frontend, backend ou outros serviços persistentes sem solicitação explícita do usuário.
