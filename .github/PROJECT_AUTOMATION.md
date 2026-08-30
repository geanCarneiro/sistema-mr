# Automação do GitHub Project

Este repositório sincroniza Issues com o Project pessoal
<https://github.com/users/geanCarneiro/projects/2>.

## O que é automático

- os Issue Forms aplicam `tracked`, tipo, área e prioridade;
- Issues com `tracked` são adicionadas ao Project;
- `Priority`, `Type` e `Area` são derivados das labels e copiados para o Project;
- Issues novas ou reabertas entram no backlog;
- comandos em comentários alteram o `Status`;
- PRs vinculadas por `Closes #123` movem a Issue para implementação, revisão ou conclusão;
- o bootstrap cria labels e os campos ausentes `Status`, `Priority`, `Type`, `Area` e `Estimate`.

## Configuração inicial obrigatória

### 1. Criar um personal access token classic

1. No GitHub, abra **Settings** da conta pessoal.
2. Abra **Developer settings**.
3. Entre em **Personal access tokens** e depois **Tokens (classic)**.
4. Selecione **Generate new token (classic)**.
5. Use uma identificação como `sistema-mr-project-automation`.
6. Escolha uma expiração adequada. Noventa dias reduz exposição, mas exige renovação.
7. Marque os escopos `repo` e `project`.
8. Gere e copie o token. O GitHub não volta a exibir seu valor completo.

Nunca coloque o token em `.env`, YAML, Issue, comentário, commit ou log.

### 2. Salvar o token como secret do repositório

1. Abra <https://github.com/geanCarneiro/sistema-mr/settings/secrets/actions>.
2. Em **Repository secrets**, selecione **New repository secret**.
3. Em **Name**, informe exatamente `PROJECT_TOKEN`.
4. Em **Secret**, cole o token.
5. Selecione **Add secret**.

O local correto é **Secrets and variables > Actions**. Não use Agents ou Codespaces.

### 3. Executar o bootstrap

Esta etapa só aparece depois que o workflow estiver publicado na branch padrão.

1. Abra <https://github.com/geanCarneiro/sistema-mr/actions>.
2. Na lista de workflows, abra **Project automation**.
3. Selecione **Run workflow**.
4. Mantenha `bootstrap` habilitado.
5. Deixe `issue_number` vazio e `status` como `Keep`.
6. Confirme em **Run workflow**.
7. Abra a execução e confirme que o job **Synchronize Issues with Project** ficou verde.

O bootstrap é idempotente: pode ser executado novamente para verificar ou reparar
labels e campos ausentes. Ele preserva opções extras que já existam no Project.

## Status por comandos

Em uma Issue rastreada, publique um comentário contendo um dos comandos:

| Comando | Status no Project |
|---|---|
| `/backlog` | Backlog, ou Todo quando o template usa esse nome |
| `/ready` | Ready |
| `/start` | In Progress |
| `/review` | Review, ou In review quando o template usa esse nome |
| `/done` | Done e fecha a Issue |

Somente comandos enviados pelo proprietário ou por colaboradores são aceitos.
Quando o comando é processado, a automação adiciona uma reação de foguete.

## Fluxo por pull request

A PR deve conter uma closing keyword apontando para a Issue:

```markdown
Closes #123
```

| Evento da PR | Status da Issue vinculada |
|---|---|
| PR draft aberta | In Progress |
| PR pronta para revisão | Review |
| PR convertida novamente para draft | In Progress |
| PR mesclada | Done |
| PR fechada sem merge | In Progress |

O workflow usa `pull_request_target` apenas para ler metadados da PR. O código de
automação é sempre carregado da branch padrão; código vindo da PR não é executado
com o secret.

## Sincronização manual de uma Issue

Na execução manual do workflow:

1. mantenha `bootstrap` habilitado;
2. informe o número em `issue_number`, sem `#`;
3. escolha um `status` ou mantenha `Keep`;
4. execute o workflow.

Isso é útil para migrar uma Issue antiga. Ela precisa ter a label `tracked`.

## Regras de fonte de verdade

- `Type`, `Area` e `Priority` têm as labels como fonte de verdade;
- `Status` tem o campo do Project como fonte de verdade;
- Issues e PRs continuam sendo a fonte de verdade para título, responsável,
  labels, fechamento e vínculo entre implementação e demanda;
- `docs/BACKLOG.md` deve virar índice depois que seus itens forem migrados.

## Renovação do token

Quando o token expirar, as execuções falharão com erro de autenticação. Gere um
novo token com os mesmos escopos e substitua o valor de `PROJECT_TOKEN`; não é
necessário alterar ou commitar arquivos.
