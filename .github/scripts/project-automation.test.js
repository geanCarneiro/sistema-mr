const assert = require('node:assert/strict');
const test = require('node:test');

const { testables } = require('./project-automation');

test('reads dropdown values rendered by Issue Forms', () => {
  const body = [
    '### Área',
    '',
    'Grounding',
    '',
    '### Prioridade',
    '',
    'P1',
  ].join('\n');

  assert.equal(testables.formValue(body, 'Área'), 'Grounding');
  assert.equal(testables.formValue(body, 'Prioridade'), 'P1');
});

test('infers labels from each form', () => {
  const feature = {
    title: '[Feature] selecionar arquivos',
    body: '### Área\n\nDocuments\n\n### Prioridade\n\nP2',
  };
  const bug = {
    title: '[Bug] falha no OCR',
    body: '### Área\n\nOCR\n\n### Prioridade\n\nP0',
  };
  const technical = {
    title: '[Technical] investigar latência',
    body: '### Tipo\n\nSpike\n\n### Área\n\nInfrastructure\n\n### Prioridade\n\nP3',
  };

  assert.equal(testables.inferType(feature), 'type:feature');
  assert.equal(testables.inferArea(feature), 'area:documents');
  assert.equal(testables.inferPriority(feature), 'priority:p2');

  assert.equal(testables.inferType(bug), 'type:bug');
  assert.equal(testables.inferArea(bug), 'area:ocr');
  assert.equal(testables.inferPriority(bug), 'priority:p0');

  assert.equal(testables.inferType(technical), 'type:spike');
  assert.equal(testables.inferArea(technical), 'area:infrastructure');
  assert.equal(testables.inferPriority(technical), 'priority:p3');
});

test('maps Issue lifecycle to Project status', () => {
  assert.equal(testables.statusForIssueAction('opened'), 'Backlog');
  assert.equal(testables.statusForIssueAction('reopened'), 'Backlog');
  assert.equal(testables.statusForIssueAction('closed'), 'Done');
  assert.equal(testables.statusForIssueAction('edited'), null);
});

test('defines the Project workflow without a Ready status', () => {
  const statusDefinition = testables.FIELD_DEFINITIONS.find(
    (candidate) => candidate.name === 'Status',
  );

  assert.deepEqual(
    statusDefinition.options.map((option) => option.name),
    ['Backlog', 'In Progress', 'Review', 'Done'],
  );
  assert.deepEqual(testables.STATUS_ALIASES, {
    Backlog: ['Backlog', 'Todo'],
    'In Progress': ['In Progress', 'In progress'],
    Review: ['Review', 'In Review', 'In review'],
    Done: ['Done'],
  });
  assert.deepEqual(testables.COMMAND_STATUSES, {
    '/backlog': 'Backlog',
    '/start': 'In Progress',
    '/review': 'Review',
    '/done': 'Done',
  });
});

test('maps pull request lifecycle to Project status', () => {
  assert.equal(testables.statusForPullRequest({ draft: true }, 'opened'), 'In Progress');
  assert.equal(testables.statusForPullRequest({ draft: false }, 'opened'), 'Review');
  assert.equal(testables.statusForPullRequest({}, 'ready_for_review'), 'Review');
  assert.equal(testables.statusForPullRequest({}, 'converted_to_draft'), 'In Progress');
  assert.equal(testables.statusForPullRequest({ merged: true }, 'closed'), 'Done');
  assert.equal(testables.statusForPullRequest({ merged: false }, 'closed'), 'In Progress');
});

test('accepts default template aliases for Status', () => {
  const field = {
    name: 'Status',
    options: [
      { id: 'todo', name: 'Todo' },
      { id: 'progress', name: 'In progress' },
      { id: 'review', name: 'In review' },
      { id: 'done', name: 'Done' },
    ],
  };

  assert.equal(testables.findOption(field, 'Backlog').id, 'todo');
  assert.equal(testables.findOption(field, 'In Progress').id, 'progress');
  assert.equal(testables.findOption(field, 'Review').id, 'review');
  assert.equal(testables.findOption(field, 'Done').id, 'done');
});

test('uses a non-reserved custom field for work type', () => {
  const definition = testables.FIELD_DEFINITIONS.find(
    (candidate) => candidate.name === testables.WORK_TYPE_FIELD,
  );

  assert.equal(testables.WORK_TYPE_FIELD, 'Work Type');
  assert.ok(definition);
  assert.equal(definition.dataType, 'SINGLE_SELECT');
  assert.deepEqual(
    testables.fieldClearedByIssueEvent({ action: 'unlabeled', label: { name: 'type:bug' } }),
    ['Work Type'],
  );
});
