# Backlog de evolução — Sistema MR

O backlog deste repositório é mantido nas
[GitHub Issues](https://github.com/geanCarneiro/sistema-mr/issues) e visualizado no
[GitHub Project Sistema MR](https://github.com/users/geanCarneiro/projects/2).

Este arquivo é apenas um índice. As Issues são a fonte de verdade para escopo,
critérios de aceite, classificação e evolução dos itens.

## Itens migrados

| ID | Issue | Classificação inicial |
|---|---|---|
| BL-001 | [#2 — Tornar explícita a política de seleção de arquivos](https://github.com/geanCarneiro/sistema-mr/issues/2) | `type:feature` · `area:grounding` · `priority:p1` |
| BL-002 | [#3 — Permitir reprocessar arquivos com status FAILED](https://github.com/geanCarneiro/sistema-mr/issues/3) | `type:feature` · `area:documents` · `priority:p1` |
| BL-003 | [#4 — Persistir a proveniência documental de cada interação](https://github.com/geanCarneiro/sistema-mr/issues/4) | `type:feature` · `area:grounding` · `priority:p1` |
| BL-004 | [#5 — Substituir Base64 por protocolo binário no OCR](https://github.com/geanCarneiro/sistema-mr/issues/5) | `type:tech-debt` · `area:ocr` · `priority:p2` |
| BL-005 | [#6 — Instrumentar o pipeline documental](https://github.com/geanCarneiro/sistema-mr/issues/6) | `type:tech-debt` · `area:infrastructure` · `priority:p2` |
| BL-006 | [#7 — Exibir e validar orçamento de contexto no frontend](https://github.com/geanCarneiro/sistema-mr/issues/7) | `type:feature` · `area:frontend` · `priority:p2` |
| BL-007 | [#8 — Suportar múltiplas conversas por usuário](https://github.com/geanCarneiro/sistema-mr/issues/8) | `type:feature` · `area:chat` · `priority:p3` |
| BL-008 | [#9 — Deduplicar arquivos por SHA-256](https://github.com/geanCarneiro/sistema-mr/issues/9) | `type:tech-debt` · `area:documents` · `priority:p3` |

Todos os itens foram migrados inicialmente para o status `Backlog`, equivalente
ao estado de trabalho ainda não iniciado (`Todo`) deste Project.

## Operação

As regras de classificação, sincronização e transição de status estão descritas
em [`.github/PROJECT_AUTOMATION.md`](../.github/PROJECT_AUTOMATION.md).

Prioridade e presença no backlog não representam autorização para implementação.
