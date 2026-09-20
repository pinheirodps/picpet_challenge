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

- Não há autenticação. Os jogos de cada leitor já estão separados (ver abaixo), mas a
  separação não é uma proteção.
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

**Optimistic locking, com `@Version`.** Cada escolha é um ler-modificar-escrever. Sem
proteção, duas escolhas em simultâneo — o mesmo jogo em dois separadores, ou um duplo clique —
liam ambas vida 10, ambas subtraíam, e a segunda escrita sobrepunha-se à primeira em silêncio:
uma das jogadas desaparecia e o leitor ficava com mais vida do que as escolhas justificavam.

Com a coluna `version`, a segunda escrita falha em vez de ganhar, e a API devolve **409** com
"This game was changed somewhere else. Reload it and try again."

Escolhido em vez de bloqueio pessimista porque os conflitos aqui são raros: um jogo pertence a
um leitor que faz uma escolha de cada vez. Bloquear a linha em cada leitura custaria a todos
os pedidos para proteger um caso que quase nunca acontece.

> **Verificado na prática:** dois `POST /choices` em paralelo no mesmo jogo devolvem 200 e 409,
> não 200 e 200.

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
| Jogos de todos na mesma lista | Retomar roubava o jogo de outro | Sem dono nas sessões | Coluna `playerId`, queries filtradas |
| Escolhas simultâneas sobrepunham-se | Uma jogada desaparecia | Sem optimistic locking | `@Version` → 409 |

**Todos têm teste de regressão.** Não foram só corrigidos.

---

## A limitação mais séria

**Os leitores estão separados, mas não autenticados.** É uma distinção que convém fazer tu
antes de ta fazerem.

O que **funciona**: cada jogo pertence a um `playerId`, e o `GET /api/games` devolve só os
desse leitor. Duas pessoas jogam ao mesmo tempo sem interferir e sem ver os jogos uma da outra.
Verificado: o leitor A vê o seu jogo, o B vê o dele, e quem não envia id não vê nenhum.

**Como funciona:** o browser gera um id na primeira visita (`crypto.randomUUID()`), guarda-o em
`localStorage`, e um interceptor HTTP envia-o no header `X-Player-Id` em cada chamada à API. O
backend grava-o na sessão e filtra por ele.

O que **não** faz:

- **Não protege.** Qualquer pessoa pode enviar qualquer id. Quem souber o id de outro leitor
  vê os jogos dele.
- **Não identifica através de dispositivos.** O mesmo utilizador noutro computador, ou depois
  de limpar os dados do site, é outro jogador e começa com a lista vazia.
- **Não cobre o `/admin`**, que continua aberto a qualquer visitante — qualquer pessoa pode
  apagar qualquer livro.

> **A frase a usar:** "isto separa leitores, não os autentica". A separação resolve um bug que
> um visitante nota no primeiro minuto; a ausência de autenticação é uma limitação que tem de
> lhe ser dita.

**Porque não há autenticação a sério:** o enunciado é de 4 horas e não pede contas de
utilizador. Acrescentar Spring Security, modelo de utilizador, registo, login e guards seria
facilmente mais tempo do que todo o resto — e não demonstra nada do que foi pedido.

**Como seria o passo seguinte:** substituir o id gerado no browser por um emitido depois do
login, o que muda pouco no resto do código — o `playerId` já atravessa o serviço e as queries,
só passaria a vir do token em vez do header. O `/admin` ficaria atrás de um papel de
administrador.

---

## Números

| Suíte | Nº | Estado |
|:--|:--|:--|
| Backend | 100 | ✅ |
| Frontend unitário | 76 | ✅ |
| Playwright E2E | 43 | ✅ |
| Cucumber BDD | 31 cenários / 140 steps | ✅ |
| **Total** | **250** | |

### Cobertura medida

Backend (JaCoCo, `mvn test` → `target/site/jacoco/index.html`):

| Métrica | Valor |
|:--|:--|
| Instruções | 94,5% |
| Ramos | 87,5% |
| Linhas | 94,2% |
| Métodos | 95,4% |

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

1. **Flyway.** Substitui o `ddl-auto: update` por migrações versionadas. Já se sentiu a falta:
   acrescentar as colunas `player_id` e `version` obrigou a apagar a base de dados local, o que
   num sistema real não é opção.
2. **Testes nos caminhos de erro do `loader`.** É o pacote com menor cobertura (77,6%).
3. **Diálogos de confirmação próprios.** Polimento, não correção.
4. **Autenticação a sério**, se o âmbito do projeto mudasse — ver a secção anterior.

> Os dois primeiros itens desta lista (o `playerId` e o `@Version`) já foram feitos; ficam
> descritos acima, entre as correções.

Autenticação completa ficaria depois de tudo isto — e só se o âmbito do projeto mudasse.
