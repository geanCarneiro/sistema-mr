const LABEL_DEFINITIONS = [
  { name: 'tracked', color: '1D76DB', description: 'Item acompanhado no GitHub Project' },
  { name: 'blocked', color: 'B60205', description: 'Trabalho bloqueado por uma dependência' },
  { name: 'type:feature', color: 'A2EEEF', description: 'Nova funcionalidade ou evolução de produto' },
  { name: 'type:bug', color: 'D73A4A', description: 'Comportamento incorreto ou regressão' },
  { name: 'type:tech-debt', color: 'FBCA04', description: 'Dívida técnica ou melhoria interna' },
  { name: 'type:spike', color: 'D4C5F9', description: 'Investigação técnica com resultado definido' },
  { name: 'area:chat', color: '0E8A16', description: 'Chat e interação com o modelo' },
  { name: 'area:grounding', color: '006B75', description: 'Grounding, recuperação e memória' },
  { name: 'area:documents', color: '5319E7', description: 'Upload, persistência e processamento documental' },
  { name: 'area:ocr', color: 'BFDADC', description: 'OCR e extração visual' },
  { name: 'area:frontend', color: 'C5DEF5', description: 'Interface Angular' },
  { name: 'area:infrastructure', color: '0052CC', description: 'Infraestrutura, containers e operação' },
  { name: 'priority:p0', color: 'B60205', description: 'Crítico e imediato' },
  { name: 'priority:p1', color: 'D93F0B', description: 'Alta prioridade' },
  { name: 'priority:p2', color: 'FBCA04', description: 'Prioridade normal' },
  { name: 'priority:p3', color: 'C2E0C6', description: 'Baixa prioridade' },
];

const WORK_TYPE_FIELD = 'Work Type';

const FIELD_DEFINITIONS = [
  {
    name: 'Status',
    dataType: 'SINGLE_SELECT',
    options: [
      option('Backlog', 'GRAY', 'Item recebido e ainda não refinado'),
      option('In Progress', 'YELLOW', 'Implementação em andamento'),
      option('Review', 'PURPLE', 'Aguardando revisão ou validação'),
      option('Done', 'GREEN', 'Trabalho concluído'),
    ],
  },
  {
    name: 'Priority',
    dataType: 'SINGLE_SELECT',
    options: [
      option('P0', 'RED', 'Crítico e imediato'),
      option('P1', 'ORANGE', 'Alta prioridade'),
      option('P2', 'YELLOW', 'Prioridade normal'),
      option('P3', 'GREEN', 'Baixa prioridade'),
    ],
  },
  {
    name: WORK_TYPE_FIELD,
    dataType: 'SINGLE_SELECT',
    options: [
      option('Feature', 'BLUE', 'Funcionalidade ou evolução'),
      option('Bug', 'RED', 'Defeito ou regressão'),
      option('Tech Debt', 'YELLOW', 'Dívida técnica'),
      option('Spike', 'PURPLE', 'Investigação técnica'),
    ],
  },
  {
    name: 'Area',
    dataType: 'SINGLE_SELECT',
    options: [
      option('Chat', 'BLUE', 'Chat e interação com o modelo'),
      option('Grounding', 'GREEN', 'Grounding, recuperação e memória'),
      option('Documents', 'PURPLE', 'Pipeline documental'),
      option('OCR', 'YELLOW', 'OCR e extração visual'),
      option('Frontend', 'PINK', 'Interface Angular'),
      option('Infrastructure', 'GRAY', 'Infraestrutura e operação'),
    ],
  },
  { name: 'Estimate', dataType: 'NUMBER' },
];

const STATUS_ALIASES = {
  Backlog: ['Backlog', 'Todo'],
  'In Progress': ['In Progress', 'In progress'],
  Review: ['Review', 'In Review', 'In review'],
  Done: ['Done'],
};

const COMMAND_STATUSES = {
  '/backlog': 'Backlog',
  '/start': 'In Progress',
  '/review': 'Review',
  '/done': 'Done',
};

const TRUSTED_ASSOCIATIONS = new Set(['OWNER', 'MEMBER', 'COLLABORATOR']);

function option(name, color, description) {
  return { name, color, description };
}

function normalize(value) {
  return String(value || '').trim().toLocaleLowerCase('en-US');
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function formValue(body, heading) {
  const expression = new RegExp(
    `###\\s+${escapeRegExp(heading)}\\s*\\r?\\n+([\\s\\S]*?)(?=\\r?\\n###\\s|$)`,
    'i',
  );
  const match = String(body || '').match(expression);
  if (!match) return null;

  const value = match[1]
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line && line !== '_No response_');

  return value || null;
}

function labelNames(issue) {
  return (issue.labels || []).map((label) => (typeof label === 'string' ? label : label.name));
}

async function ensureRepositoryLabels(github, owner, repo, core) {
  const existing = await github.paginate(github.rest.issues.listLabelsForRepo, {
    owner,
    repo,
    per_page: 100,
  });
  const existingNames = new Set(existing.map((label) => normalize(label.name)));

  for (const definition of LABEL_DEFINITIONS) {
    if (existingNames.has(normalize(definition.name))) continue;

    await github.rest.issues.createLabel({ owner, repo, ...definition });
    core.info(`Label criada: ${definition.name}`);
  }
}

async function getProject(github, login, number) {
  const response = await github.graphql(
    `query ProjectConfiguration($login: String!, $number: Int!) {
      user(login: $login) {
        projectV2(number: $number) {
          id
          title
          fields(first: 50) {
            nodes {
              __typename
              ... on ProjectV2Field {
                id
                name
                dataType
              }
              ... on ProjectV2SingleSelectField {
                id
                name
                dataType
                options {
                  id
                  name
                  color
                  description
                }
              }
              ... on ProjectV2IterationField {
                id
                name
                dataType
              }
            }
          }
        }
      }
    }`,
    { login, number },
  );

  if (!response.user?.projectV2) {
    throw new Error(`Project pessoal ${login}/projects/${number} não foi encontrado.`);
  }

  return response.user.projectV2;
}

function optionAliases(fieldName, expectedName) {
  if (fieldName === 'Status') return STATUS_ALIASES[expectedName] || [expectedName];
  return [expectedName];
}

function findOption(field, expectedName) {
  const aliases = optionAliases(field.name, expectedName).map(normalize);
  return (field.options || []).find((candidate) => aliases.includes(normalize(candidate.name)));
}

async function createProjectField(github, projectId, definition) {
  const input = {
    projectId,
    name: definition.name,
    dataType: definition.dataType,
  };
  if (definition.options) input.singleSelectOptions = definition.options;

  await github.graphql(
    `mutation CreateProjectField($input: CreateProjectV2FieldInput!) {
      createProjectV2Field(input: $input) {
        projectV2Field {
          __typename
          ... on ProjectV2Field { id name }
          ... on ProjectV2SingleSelectField { id name }
        }
      }
    }`,
    { input },
  );
}

async function addMissingOptions(github, field, definition) {
  const missing = definition.options.filter((expected) => !findOption(field, expected.name));
  if (missing.length === 0) return false;

  const existing = field.options.map((current) => ({
    id: current.id,
    name: current.name,
    color: current.color,
    description: current.description || '',
  }));

  await github.graphql(
    `mutation UpdateProjectField($input: UpdateProjectV2FieldInput!) {
      updateProjectV2Field(input: $input) {
        projectV2Field {
          __typename
          ... on ProjectV2SingleSelectField { id name }
        }
      }
    }`,
    { input: { fieldId: field.id, singleSelectOptions: [...existing, ...missing] } },
  );
  return true;
}

async function ensureProjectSchema(github, login, number, core) {
  let project = await getProject(github, login, number);

  for (const definition of FIELD_DEFINITIONS) {
    const field = project.fields.nodes.find((candidate) => normalize(candidate?.name) === normalize(definition.name));

    if (!field) {
      core.info(`Criando campo no Project: ${definition.name}`);
      try {
        await createProjectField(github, project.id, definition);
      } catch (error) {
        throw new Error(`Falha ao criar o campo ${definition.name} no Project: ${error.message}`, { cause: error });
      }
      core.info(`Campo criado no Project: ${definition.name}`);
      project = await getProject(github, login, number);
      continue;
    }

    if (field.dataType !== definition.dataType) {
      throw new Error(
        `O campo ${definition.name} existe como ${field.dataType}, mas a automação espera ${definition.dataType}.`,
      );
    }

    if (definition.options && (await addMissingOptions(github, field, definition))) {
      core.info(`Opções ausentes adicionadas ao campo ${definition.name}.`);
      project = await getProject(github, login, number);
    }
  }

  return project;
}

async function replaceGroupedLabel(github, owner, repo, issueNumber, currentNames, prefix, desired) {
  const currentGroup = currentNames.filter((name) => normalize(name).startsWith(`${prefix}:`));

  for (const current of currentGroup) {
    if (normalize(current) === normalize(desired)) continue;
    await github.rest.issues.removeLabel({ owner, repo, issue_number: issueNumber, name: current }).catch((error) => {
      if (error.status !== 404) throw error;
    });
  }

  if (desired && !currentNames.some((name) => normalize(name) === normalize(desired))) {
    await github.rest.issues.addLabels({ owner, repo, issue_number: issueNumber, labels: [desired] });
  }
}

function inferType(issue) {
  const selected = formValue(issue.body, 'Tipo');
  const source = normalize(selected || issue.title);
  if (source.includes('tech debt') || source.startsWith('[technical]') && normalize(selected) === 'tech debt') {
    return 'type:tech-debt';
  }
  if (source.includes('spike')) return 'type:spike';
  if (source.startsWith('[bug]')) return 'type:bug';
  if (source.startsWith('[feature]')) return 'type:feature';
  return null;
}

function inferArea(issue) {
  const selected = normalize(formValue(issue.body, 'Área'));
  const values = {
    chat: 'area:chat',
    grounding: 'area:grounding',
    documents: 'area:documents',
    ocr: 'area:ocr',
    frontend: 'area:frontend',
    infrastructure: 'area:infrastructure',
  };
  return values[selected] || null;
}

function inferPriority(issue) {
  const selected = normalize(formValue(issue.body, 'Prioridade')).split(/\s+/)[0];
  return /^p[0-3]$/.test(selected) ? `priority:${selected}` : null;
}

async function normalizeFormLabels(github, owner, repo, issue, core) {
  const desiredType = inferType(issue);
  const desiredArea = inferArea(issue);
  const desiredPriority = inferPriority(issue);
  if (!desiredType && !desiredArea && !desiredPriority) return issue;

  let current = labelNames(issue);
  if (!current.some((name) => normalize(name) === 'tracked')) {
    await github.rest.issues.addLabels({ owner, repo, issue_number: issue.number, labels: ['tracked'] });
    current.push('tracked');
  }

  if (desiredType) {
    await replaceGroupedLabel(github, owner, repo, issue.number, current, 'type', desiredType);
  }
  if (desiredArea) {
    await replaceGroupedLabel(github, owner, repo, issue.number, current, 'area', desiredArea);
  }
  if (desiredPriority) {
    await replaceGroupedLabel(github, owner, repo, issue.number, current, 'priority', desiredPriority);
  }

  core.info(`Labels do formulário normalizadas na Issue #${issue.number}.`);
  const refreshed = await github.rest.issues.get({ owner, repo, issue_number: issue.number });
  return refreshed.data;
}

async function addIssueToProject(github, projectId, contentId) {
  const response = await github.graphql(
    `mutation AddIssueToProject($project: ID!, $content: ID!) {
      addProjectV2ItemById(input: { projectId: $project, contentId: $content }) {
        item { id }
      }
    }`,
    { project: projectId, content: contentId },
  );
  return response.addProjectV2ItemById.item.id;
}

async function setSingleSelectValue(github, projectId, itemId, field, optionName) {
  const selected = findOption(field, optionName);
  if (!selected) throw new Error(`A opção ${optionName} não existe no campo ${field.name}.`);

  await github.graphql(
    `mutation SetProjectField($project: ID!, $item: ID!, $field: ID!, $option: String!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $project
        itemId: $item
        fieldId: $field
        value: { singleSelectOptionId: $option }
      }) {
        projectV2Item { id }
      }
    }`,
    { project: projectId, item: itemId, field: field.id, option: selected.id },
  );
}

async function clearFieldValue(github, projectId, itemId, fieldId) {
  await github.graphql(
    `mutation ClearProjectField($project: ID!, $item: ID!, $field: ID!) {
      clearProjectV2ItemFieldValue(input: {
        projectId: $project
        itemId: $item
        fieldId: $field
      }) {
        projectV2Item { id }
      }
    }`,
    { project: projectId, item: itemId, field: fieldId },
  );
}

function optionFromLabels(names, prefix, mapping) {
  const selected = names.find((name) => normalize(name).startsWith(`${prefix}:`));
  if (!selected) return null;
  return mapping[normalize(selected)] || null;
}

async function synchronizeIssue(github, project, issue, requestedStatus, core, fieldsToClear = []) {
  const names = labelNames(issue);
  if (!names.some((name) => normalize(name) === 'tracked')) {
    core.info(`Issue #${issue.number} ignorada porque não possui a label tracked.`);
    return false;
  }

  const itemId = await addIssueToProject(github, project.id, issue.node_id || issue.id);
  const fields = new Map(project.fields.nodes.filter(Boolean).map((field) => [normalize(field.name), field]));

  const fieldValues = [
    {
      field: 'Priority',
      value: optionFromLabels(names, 'priority', {
        'priority:p0': 'P0',
        'priority:p1': 'P1',
        'priority:p2': 'P2',
        'priority:p3': 'P3',
      }),
    },
    {
      field: WORK_TYPE_FIELD,
      value: optionFromLabels(names, 'type', {
        'type:feature': 'Feature',
        'type:bug': 'Bug',
        'type:tech-debt': 'Tech Debt',
        'type:spike': 'Spike',
      }),
    },
    {
      field: 'Area',
      value: optionFromLabels(names, 'area', {
        'area:chat': 'Chat',
        'area:grounding': 'Grounding',
        'area:documents': 'Documents',
        'area:ocr': 'OCR',
        'area:frontend': 'Frontend',
        'area:infrastructure': 'Infrastructure',
      }),
    },
  ];

  for (const entry of fieldValues) {
    const field = fields.get(normalize(entry.field));
    if (!field) continue;
    if (entry.value) await setSingleSelectValue(github, project.id, itemId, field, entry.value);
    else if (fieldsToClear.includes(entry.field)) {
      await clearFieldValue(github, project.id, itemId, field.id);
    }
  }

  if (requestedStatus) {
    const statusField = fields.get('status');
    if (!statusField) throw new Error('O Project não possui o campo Status.');
    await setSingleSelectValue(github, project.id, itemId, statusField, requestedStatus);
  }

  core.info(`Issue #${issue.number} sincronizada com o Project ${project.title}.`);
  return true;
}

async function getIssue(github, owner, repo, issueNumber) {
  const response = await github.rest.issues.get({ owner, repo, issue_number: issueNumber });
  return response.data;
}

function statusForIssueAction(action) {
  if (action === 'opened' || action === 'reopened') return 'Backlog';
  if (action === 'closed') return 'Done';
  return null;
}

function fieldClearedByIssueEvent(payload) {
  if (payload.action !== 'unlabeled') return [];
  const removed = normalize(payload.label?.name);
  if (removed.startsWith('priority:')) return ['Priority'];
  if (removed.startsWith('type:')) return [WORK_TYPE_FIELD];
  if (removed.startsWith('area:')) return ['Area'];
  return [];
}

function statusForPullRequest(pullRequest, action) {
  if (action === 'closed') return pullRequest.merged ? 'Done' : 'In Progress';
  if (action === 'ready_for_review') return 'Review';
  if (action === 'converted_to_draft') return 'In Progress';
  if (action === 'opened' || action === 'reopened') return pullRequest.draft ? 'In Progress' : 'Review';
  return null;
}

async function linkedIssuesForPullRequest(github, owner, repo, number) {
  const response = await github.graphql(
    `query ClosingIssues($owner: String!, $repo: String!, $number: Int!) {
      repository(owner: $owner, name: $repo) {
        pullRequest(number: $number) {
          closingIssuesReferences(first: 20) {
            nodes {
              id
              number
              state
              title
              labels(first: 50) { nodes { name } }
            }
          }
        }
      }
    }`,
    { owner, repo, number },
  );

  return (response.repository?.pullRequest?.closingIssuesReferences?.nodes || []).map((issue) => ({
    ...issue,
    node_id: issue.id,
    labels: issue.labels.nodes,
  }));
}

async function processIssueComment(github, context, project, owner, repo, core) {
  if (context.payload.issue.pull_request) return false;
  if (!TRUSTED_ASSOCIATIONS.has(context.payload.comment.author_association)) {
    core.info('Comando ignorado porque o autor não possui vínculo de escrita com o repositório.');
    return false;
  }

  const command = normalize(context.payload.comment.body).split(/\s+/)[0];
  const requestedStatus = COMMAND_STATUSES[command];
  if (!requestedStatus) return false;

  const issueNumber = context.payload.issue.number;
  if (command === '/done') {
    await github.rest.issues.update({ owner, repo, issue_number: issueNumber, state: 'closed' });
  }

  const issue = await getIssue(github, owner, repo, issueNumber);
  await synchronizeIssue(github, project, issue, requestedStatus, core);
  await github.rest.reactions
    .createForIssueComment({
      owner,
      repo,
      comment_id: context.payload.comment.id,
      content: 'rocket',
    })
    .catch((error) => core.warning(`Não foi possível reagir ao comando: ${error.message}`));
  return true;
}

module.exports = async ({ github, context, core }) => {
  const owner = process.env.PROJECT_OWNER;
  const projectNumber = Number(process.env.PROJECT_NUMBER);
  const { owner: repositoryOwner, repo } = context.repo;

  if (!owner || !Number.isInteger(projectNumber)) {
    throw new Error('PROJECT_OWNER e PROJECT_NUMBER precisam estar configurados no workflow.');
  }

  await ensureRepositoryLabels(github, repositoryOwner, repo, core);
  const shouldBootstrap =
    context.eventName === 'workflow_dispatch' && normalize(process.env.MANUAL_BOOTSTRAP) === 'true';
  const project = shouldBootstrap
    ? await ensureProjectSchema(github, owner, projectNumber, core)
    : await getProject(github, owner, projectNumber);
  let synchronized = 0;

  if (context.eventName === 'workflow_dispatch') {
    const issueNumber = Number(process.env.MANUAL_ISSUE_NUMBER || 0);
    const manualStatus = process.env.MANUAL_STATUS === 'Keep' ? null : process.env.MANUAL_STATUS;
    if (issueNumber) {
      let issue = await getIssue(github, repositoryOwner, repo, issueNumber);
      issue = await normalizeFormLabels(github, repositoryOwner, repo, issue, core);
      synchronized += Number(await synchronizeIssue(github, project, issue, manualStatus, core));
    }
  } else if (context.eventName === 'issues') {
    let issue = await getIssue(github, repositoryOwner, repo, context.payload.issue.number);
    if (context.payload.action === 'opened' || context.payload.action === 'edited') {
      issue = await normalizeFormLabels(github, repositoryOwner, repo, issue, core);
    }
    const requestedStatus = statusForIssueAction(context.payload.action);
    synchronized += Number(
      await synchronizeIssue(
        github,
        project,
        issue,
        requestedStatus,
        core,
        fieldClearedByIssueEvent(context.payload),
      ),
    );
  } else if (context.eventName === 'issue_comment') {
    synchronized += Number(await processIssueComment(github, context, project, repositoryOwner, repo, core));
  } else if (context.eventName === 'pull_request_target') {
    const requestedStatus = statusForPullRequest(context.payload.pull_request, context.payload.action);
    const issues = await linkedIssuesForPullRequest(
      github,
      repositoryOwner,
      repo,
      context.payload.pull_request.number,
    );
    for (const issue of issues) {
      synchronized += Number(await synchronizeIssue(github, project, issue, requestedStatus, core));
    }
    if (issues.length === 0) {
      core.warning('A PR não possui Issue vinculada por uma closing keyword, como "Closes #123".');
    }
  }

  await core.summary
    .addHeading('GitHub Project automation')
    .addTable([
      [
        { data: 'Item', header: true },
        { data: 'Resultado', header: true },
      ],
      ['Project', `${project.title} (#${projectNumber})`],
      ['Labels/campos', shouldBootstrap ? 'Criados/verificados' : 'Conexão verificada'],
      ['Issues sincronizadas', String(synchronized)],
    ])
    .write();
};

module.exports.testables = {
  COMMAND_STATUSES,
  FIELD_DEFINITIONS,
  STATUS_ALIASES,
  WORK_TYPE_FIELD,
  fieldClearedByIssueEvent,
  findOption,
  formValue,
  inferArea,
  inferPriority,
  inferType,
  statusForIssueAction,
  statusForPullRequest,
};
