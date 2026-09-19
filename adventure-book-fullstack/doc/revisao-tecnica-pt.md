# Revisão técnica — Adventure Book

Auto-avaliação honesta do estado do projeto. Não é um relatório de vendas: o objetivo é que
saibas exatamente onde o projeto é forte e onde é atacável, **antes** de alguém o atacar.

Ver também: [arquitetura-pt.md](arquitetura-pt.md) e [apresentacao-pt.md](apresentacao-pt.md).

> **Nota sobre o `technical-review-report.md`** que também está nesta pasta: foi produzido por
> um agente de revisão a meio do desenvolvimento e depois atualizado. Dá notas entre 9.7 e 10
> e menciona um "Cache" que já não existe (foi removido de propósito — ver abaixo). Trata-o
> como registo histórico, não como avaliação do estado atual.

---

## Resumo

**O que o enunciado pedia está feito, por ordem, e testado.** Os cinco objetivos, as quatro
regras de validação, a validação partilhada entre livros de exemplo e livros submetidos, e o
cabeçalho com nome do livro, vida e paragem do jogo.

**O que é genuinamente forte:**

- As regras de jogo vivem no domínio, não espalhadas por services.
- Uma única definição de "livro válido", partilhada entre o carregamento inicial e a API.
- As estratégias de fetch são deliberadas e documentadas, incluindo o erro que foi cometido.
- Há um teste que corre a aplicação inteira, não só fatias simuladas.
- Os bugs encontrados têm testes de regressão, não apenas correções.

**O que é atacável, e deve ser dito por ti primeiro:**

- Não há autenticação, e isso tem uma consequência funcional concreta (abaixo).
- Não há optimistic locking no `GameSession`.
- `ddl-auto: update` em vez de migrações versionadas.
- Os pacotes `loader` e `exception` têm cobertura mais baixa que o resto (~78-80%).

---

## Cobertura dos requisitos

| Requisito do enunciado | Estado | Onde |
|:--|:--|:--|
| Java + Spring Boot + Maven | ✅ | Java 21, Boot 3.3.4 |
| Angular no frontend | ✅ | Angular 19, standalone |
| README com instruções de build e execução | ✅ | `README.md` |
| Objetivo 1 — biblioteca com pesquisa e filtro | ✅ | `LibraryPageComponent`, `GET /api/books` |
| Objetivo 2 — começar jogo e interagir | ✅ | `GamePageComponent`, `POST /api/games` |
| Objetivo 3 — consequências, vida, fim de jogo | ✅ | `GameSession.choose()` |
| Objetivo 4 — guardar progresso | ✅ | Automático em cada escolha |
| Objetivo 5 — adicionar livros | ✅ | `BookEditorPageComponent` |
| Exatamente um BEGIN | ✅ | `SingleBeginningRule` |
| Pelo menos um END | ✅ | `HasEndingRule` |
| `gotoId` válidos | ✅ | `ValidNextSectionIdRule` |
| Secções não-finais com opções | ✅ | `NonEndingHasOptionsRule` |
| Vida inicial 10, morte a zero | ✅ | `GameSession` |
| Cabeçalho: nome, vida, parar, guardar | ✅ | `GamePageComponent` |
| Objetivos tratados por ordem | ✅ | Ver [apresentacao-pt.md](apresentacao-pt.md) |

**Acrescentado além do pedido:** uma quinta regra de validação (números de secção únicos),
rever e remover livros, e o ecrã `/admin`.

**Deliberadamente não acrescentado:** a sinopse, as etiquetas de género e o tempo de leitura
que o mockup mostra. Esses dados não existem nos JSON fornecidos, e inventá-los seria mostrar
informação fabricada sobre livros reais. A contagem de capítulos foi acrescentada por ser
derivável de dados que existem.

---

## Qualidade do backend

### Forte

**As regras no domínio.** O `GameSession.choose()` concentra o movimento, a consequência, a
morte e o final. O `GameService` só coordena. Isto é Information Expert do GRASP, e o
resultado prático é que a maior classe de testes do projeto (14 testes no `GameSessionTest`)
não precisa de contexto Spring.

**A interface selada para as regras.** Uma classe por regra, `sealed` para que acrescentar uma
seja deliberado. O `BookValidator` junta todos os motivos de falha em vez de parar no
primeiro.

**As queries.** Nenhum N+1 nos caminhos que interessam: a listagem usa uma query agrupada para
as contagens, o carregamento de um livro usa duas queries em vez de um fetch join duplo, e a
lista de jogos guardados faz `join fetch` só do livro.

**O tratamento de erros.** Um formato único, com `messages` sempre em array. Inclui um
handler para corpo de pedido inválido, acrescentado depois de se descobrir que devolvia 500.

### Pontos discutíveis

**O `@Cacheable` foi removido, não corrigido.** Existia em `BookService.loadPlayableBook`, a
cachear um grafo de entidades geridas. Com `open-in-view: false`, entregar uma cópia desligada
a uma transação posterior é exatamente como se produz um `LazyInitializationException` em
produção. Como só é chamado uma vez por jogo, a cache não comprava nada em troca desse risco.

> **Se perguntarem porque não há cache:** esta é a resposta. É uma decisão, não um esquecimento.

**`ddl-auto: update`.** Aceitável aqui porque o esquema vem das entidades e os dados são
recarregáveis. Num sistema real seria Flyway ou Liquibase. Está nas limitações do README.

**Sem optimistic locking.** Duas escolhas simultâneas no mesmo jogo podem sobrepor-se. Um
único jogador num browser não provoca isto; duas abas do mesmo jogo, sim. Correção: uma coluna
`@Version`.

---

## Qualidade do frontend

### Forte

**Angular moderno, consistente.** Standalone, signals, `OnPush`, `inject()`, control flow
novo. Sem NgModules e sem construtores de injeção.

**A pipeline única de pesquisa.** Pesquisa, filtro e paginação num só fluxo, com `debounceTime`
e `switchMap`. O `switchMap` é o detalhe que interessa: impede que uma resposta lenta se
sobreponha a uma mais recente.

**O formulário não reimplementa as regras.** Verifica campos obrigatórios e mostra o que o
backend rejeitou. Evita duas implementações das mesmas regras a divergir.

### Pontos discutíveis

**A confirmação usa `confirm()` do browser.** Funciona e é acessível, mas um diálogo próprio
seria mais polido. Decisão consciente de âmbito: num teste de 4 horas, um modal próprio é
tempo gasto onde não demonstra nada de novo.

**O `confirm()` é a única parte da UI sem testes de interação próprios.** Está coberto
indiretamente pelos E2E, que aceitam e recusam o diálogo, mas não há teste do texto
apresentado.

---

## Bugs encontrados e corrigidos

Esta secção é a mais útil numa entrevista. Contar um bug que encontraste e corrigiste vale
mais do que fingir que correu tudo à primeira.

| Bug | Sintoma | Causa | Correção |
|:--|:--|:--|:--|
| Produto cartesiano no fetch | Jogos recusavam arrancar | Dois `join fetch` de coleções paralelas | Duas queries no mesmo contexto |
| O mesmo, no `GameSessionRepository` | Latente | Mesmo padrão | Mesma correção |
| Pesquisa morta após a 1.ª | Filtro parecia ignorado | `distinctUntilChanged` sobre `Subject<void>` | Signals + `switchMap` |
| Editor em branco | Página vazia, sem erro visível | Getter lido durante a construção do form | Passar `nextId` por parâmetro |
| Texto da consequência perdido | Vida caía sem explicação | Não era guardado na sessão | Campo `lastConsequence` |
| 500 em pedido malformado | "Erro do nosso lado" | Faltava handler | `HttpMessageNotReadableException` → 400 |
| 500 ao editar mantendo números | Edição mais comum falhava | INSERT antes de DELETE, constraint única | Duas fases com flush |
| Texto das secções perdido na edição | Campos vazios ao editar | `sectionFrom` não copiava o texto | Corrigido, com teste |
| Jogos duplicados no mesmo livro | Entradas indistinguíveis | `POST /api/games` criava sempre | Retomar em vez de duplicar |
| Lista "Continue Playing" sem limite | Empurrava a biblioteca | Sem recorte | 4 + "Show all" |

**Todos têm teste de regressão.** Não foram só corrigidos.

---

## A limitação mais séria

**Não há noção de "os meus jogos".**

O que **funciona**: duas pessoas podem jogar ao mesmo tempo sem interferir. Cada jogo é a sua
própria linha, o backend não guarda estado entre pedidos, e isto foi verificado com dois jogos
simultâneos no mesmo livro.

O que **não funciona**: o `GET /api/games` devolve os jogos em curso de toda a gente. A lista
"Continue Playing" de uma pessoa mostra o jogo de outra, e retomá-lo rouba-o. O `/admin` está
igualmente aberto a qualquer visitante, o que significa que qualquer pessoa pode apagar
qualquer livro.

**Porque não foi resolvido:** o enunciado é de 4 horas e não pede contas de utilizador.
Acrescentar Spring Security, modelo de utilizador, registo, login e guards seria facilmente
mais tempo do que todo o resto — e não demonstra nada do que foi pedido.

**A correção mínima honesta**, se perguntarem: uma coluna `playerId` no `GameSession`,
preenchida a partir de um id gerado no browser e enviado em cada pedido, com a query de jogos
guardados filtrada por ela. Separa jogadores sem os autenticar. Contas a sério é o que o
`/admin` precisaria.

---

## Números

| Suíte | Nº | Estado |
|:--|:--|:--|
| Backend | 94 | ✅ |
| Frontend unitário | 67 | ✅ |
| Playwright E2E | 39 | ✅ |
| Cucumber BDD | 31 cenários / 140 steps | ✅ |
| **Total** | **231** | |

### Cobertura medida

Backend (JaCoCo, `mvn test` → `target/site/jacoco/index.html`):

| Métrica | Valor |
|:--|:--|
| Instruções | 94,3% |
| Ramos | 88,1% |
| Linhas | 93,7% |
| Métodos | 95,3% |

Por pacote, o que interessa é **onde** a cobertura está alta:

| Pacote | Linhas | Comentário |
|:--|:--|:--|
| `validation` | 100% | As cinco regras, totalmente cobertas |
| `dto` | 100% | |
| `repository` | 100% | |
| `domain` | 99,1% | Onde vivem as regras do jogo |
| `service` | 97,2% | |
| `web` | 91,7% | |
| `exception` | 80,0% | Handlers de casos raros |
| `loader` | 77,6% | Caminhos de erro na leitura dos JSON |

Frontend (Karma, `npm run test:coverage`): **94,5% de linhas**, 94,5% de instruções, 78,8% de
ramos, 93,8% de funções.

> **O que dizer sobre isto:** a cobertura alta está exatamente onde devia — validação a 100% e
> domínio a 99%, que são as partes onde um erro seria um erro de regra de negócio. Os pacotes
> mais baixos são caminhos de erro do carregador de ficheiros e handlers de exceções raras.

O número que mais vale a pena defender, ainda assim, não é nenhuma percentagem — é o **teste
de aplicação completa**. Tudo o resto simula pelo menos uma costura; esse corre contexto real,
carregamento real dos livros, validação real e HTTP real.

---

## Se tivesse mais tempo, pela ordem que faria

1. **`playerId` nas sessões.** Resolve o problema funcional mais visível com pouco código.
2. **`@Version` no `GameSession`.** Uma linha, elimina a condição de corrida.
3. **Flyway.** Substitui o `ddl-auto: update` por migrações versionadas.
4. **Testes nos caminhos de erro do `loader`.** É o pacote com menor cobertura.
5. **Diálogos de confirmação próprios.** Polimento, não correção.

Autenticação completa ficaria depois de tudo isto — e só se o âmbito do projeto mudasse.
