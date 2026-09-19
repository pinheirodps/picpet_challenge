# Arquitetura — Adventure Book

Como a aplicação está montada e porquê. Documento de apoio à apresentação; os outros dois
são [apresentacao-pt.md](apresentacao-pt.md), com o percurso passo a passo, e
[revisao-tecnica-pt.md](revisao-tecnica-pt.md), com a avaliação honesta do resultado.

---

## Visão geral

```
┌─────────────────────────┐         ┌──────────────────────────────┐
│   Angular 19            │  HTTP   │   Spring Boot 3.3            │
│   localhost:4200        │ ──────▶ │   localhost:8080             │
│                         │  JSON   │                              │
│   • biblioteca          │         │   • REST + Swagger           │
│   • jogo                │         │   • regras no domínio        │
│   • gestão (/admin)     │         │   • validação de livros      │
└─────────────────────────┘         └──────────────┬───────────────┘
                                                   │ JPA
                                    ┌──────────────▼───────────────┐
                                    │   H2 (ficheiro local)        │
                                    │   backend/data/              │
                                    └──────────────────────────────┘
```

Sem Docker e sem base de dados a instalar. Java 21, Maven, Node 20 — nada mais.

---

## Backend

### Camadas

```
com.pictet.adventurebook
├── domain/        Book → Section → Option → Consequence, e GameSession
├── validation/    as cinco regras e o validador que as executa
├── repository/    Spring Data JPA, com estratégias de fetch deliberadas
├── service/       fronteiras transacionais e coordenação
│   └── loader/    lê os JSON de exemplo para a base no arranque
├── web/           controladores e DTOs — só moldam pedido e resposta
│   └── dto/
├── exception/     exceções de domínio e o handler que as mapeia para HTTP
└── config/        CORS, locale, serialização de páginas
```

A regra que mantém isto limpo: **cada camada só conhece a de baixo**. O `web` não sabe o que
é uma transação, o `service` não sabe o que é um pedido HTTP, e o `domain` não sabe que
existe uma base de dados.

### As regras do jogo vivem no domínio

Esta é a decisão de desenho mais importante do backend.

```java
// GameSession.java — não num service
public void choose(int optionIndex) {
    if (status != GameStatus.PLAYING) {
        throw new IllegalStateException("Game already finished with status " + status);
    }

    Option chosen = currentSection().getOptions().get(optionIndex);

    lastConsequence = chosen.getConsequence();
    if (chosen.hasConsequence()) {
        applyHealthChange(chosen.getConsequence().signedValue());
    }

    currentSectionNumber = chosen.getGotoId();

    if (health <= MIN_HEALTH) {
        status = GameStatus.DEAD;
    } else if (currentSection().isEnding()) {
        status = GameStatus.FINISHED;
    }
    updatedAt = Instant.now();
}
```

Mover entre secções, aplicar a consequência e decidir se o jogo acabou são operações sobre o
estado do próprio objeto. É o **Information Expert** do GRASP: quem tem os dados tem o
comportamento.

### Porque o desfecho não é um Strategy

A decisão final do `choose()` está num método próprio:

```java
private void settleOutcome() {
    if (health <= MIN_HEALTH) {
        status = GameStatus.DEAD;
    } else if (currentSection().isEnding()) {
        status = GameStatus.FINISHED;
    }
}
```

A pergunta legítima é: porquê um `if/else` aqui, quando as regras de validação são um Strategy
com cinco classes? Se aparecer uma terceira condição, não fica feio?

A diferença está em **que tipo de variação** cada caso tem:

| | Regras de validação | Desfecho do jogo |
|:--|:--|:--|
| Nº de casos | 5, e pode crescer | 2, fixos pelas regras do jogo |
| São independentes? | Sim — cada uma verifica algo diferente | Não — é uma decisão com precedência |
| A ordem importa? | Não | **Sim** — morrer ganha a chegar ao fim |
| Lêem estado privado? | Não, recebem o `Book` | Sim — `health` e `currentSection()` |

Três razões concretas para não abstrair:

1. **A precedência é a regra.** Uma escolha fatal que aterra numa secção END é morte, não
   final. O `else if` diz isso em duas linhas; numa lista de strategies, essa regra passaria a
   estar na *ordem da lista*, noutro ficheiro — escondida em vez de declarada.
2. **Quebraria o Information Expert.** As condições lêem o estado do `GameSession`. Uma
   strategy externa receberia o próprio `GameSession` e decidiria sobre ele — que é o oposto
   do princípio que justifica esta classe ter comportamento.
3. **É uma entidade JPA.** Injetar colaboradores num objeto gerido pelo Hibernate obriga a
   campos estáticos ou a passá-los em cada chamada; nenhuma das opções é limpa.

**Quando é que mudaria de ideias:** se aparecesse um desfecho que variasse por si — um tempo
limite, um estado de envenenamento, uma condição definida pelo próprio livro. Aí deixariam de
ser duas condições fixas e passariam a ser um conjunto aberto, que é exatamente o caso em que
o Strategy compensa. O Javadoc do método diz isto, para quem lá chegar saber onde está a
fronteira.

> Um `switch` não seria alternativa: as duas condições são sobre campos diferentes
> (`health` e o tipo da secção), não sobre um valor único que se possa comparar.

O `GameService` limita-se a carregar, delegar e gravar. Não tem uma única regra.

**Consequência prática:** o `GameSessionTest` é a maior classe de testes do projeto (14
testes) e não precisa de contexto Spring nenhum — constrói-se com `new`.

### Validação como Strategy, com interface selada

```java
public sealed interface ValidationRule
        permits SingleBeginningRule, HasEndingRule, ValidNextSectionIdRule,
                NonEndingHasOptionsRule, UniqueSectionNumberRule {

    List<String> check(Book book);
}
```

Uma classe por regra, cada uma testável isoladamente. O `BookValidator` corre todas e junta
**todos** os motivos de falha, em vez de parar no primeiro.

**Porquê `sealed`:** acrescentar uma regra é uma alteração deliberada ao que significa
"válido" — não algo que outra classe deva conseguir introduzir só por implementar a
interface. Alargar a lista é uma edição ao `permits`, o que obriga a pensar.

**Porquê importa para o enunciado:** o mesmo validador corre no arranque (livros de exemplo)
e no `POST /api/books` (livros submetidos). Há **uma** definição de válido, não duas que
divergem com o tempo.

### Estratégias de fetch — deliberadas, não acidentais

Três casos, três abordagens:

| Caso | Estratégia | Porquê |
|:--|:--|:--|
| Listar a biblioteca | Nunca carrega secções; contagem por query agrupada | Carregar secções seria trabalho desperdiçado multiplicado por cada linha |
| Carregar livro para jogar | Duas queries: secções, depois opções | Um único fetch join das duas coleções dá produto cartesiano |
| Listar jogos guardados | `join fetch` só do livro | A lista só precisa do título; evita N+1 sem carregar o resto |

A contagem de capítulos, sem N+1:

```java
@Query("select s.book.id, count(s) from Section s where s.book.id in :bookIds group by s.book.id")
List<Object[]> countSectionsByBookIds(@Param("bookIds") Collection<Long> bookIds);
```

O produto cartesiano é o ponto mais interessante — está detalhado em
[apresentacao-pt.md](apresentacao-pt.md), porque foi um bug real com sintomas visíveis.

### Endpoints

| Método | Rota | Objetivo |
|:--|:--|:--|
| GET | `/api/books` | 1 — listar com pesquisa, filtro e paginação |
| POST | `/api/books` | 5 — submeter livro novo |
| GET | `/api/books/{id}` | Extra — ler livro completo para edição |
| PUT | `/api/books/{id}` | Extra — rever livro |
| DELETE | `/api/books/{id}` | Extra — remover livro |
| POST | `/api/games` | 2 — começar (ou retomar) um jogo |
| GET | `/api/games` | 4 — listar jogos retomáveis |
| GET | `/api/games/{id}` | 2 — estado atual do jogo |
| POST | `/api/games/{id}/choices` | 2 e 3 — fazer uma escolha |
| POST | `/api/games/{id}/stop` | 4 — parar o jogo |

Documentados em Swagger: **http://localhost:8080/swagger-ui.html**

### Tratamento de erros

Um `@RestControllerAdvice` traduz exceções para um formato único:

```json
{
  "timestamp": "2026-09-19T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "messages": ["Book has no ending section", "Section 2 is not an ending but has no options"]
}
```

`messages` é sempre um array, mesmo com uma mensagem só — o cliente tem um formato para
tratar, não dois. Um livro rejeitado traz **todos** os motivos de uma vez.

---

## Frontend

### Estrutura

```
src/app/
├── core/
│   ├── models/     espelham os DTOs da API
│   └── services/   BookService e GameService — HTTP puro, sem lógica
└── features/
    ├── library/     página inicial e os cartões
    ├── game/        ecrã de jogo
    ├── admin/       gestão da biblioteca
    └── book-editor/ formulário, usado para criar e para editar
```

### Angular moderno

Componentes **standalone** (sem NgModules), estado em **signals**, `OnPush` em todo o lado,
`inject()` em vez de construtores, e o control flow novo (`@if`, `@for`, `@switch`).

### A pipeline de pesquisa

Pesquisa, filtro e paginação alimentam **uma** pipeline, não três:

```typescript
private readonly criteria = computed(() => ({
  query: this.query(),
  difficulty: this.selectedDifficulty(),
  page: this.pageIndex()
}));

private readonly state = toSignal(
  toObservable(this.criteria).pipe(
    debounceTime(300),
    distinctUntilChanged((a, b) => /* ... */),
    switchMap(({ query, difficulty, page }) => this.bookService.search(...))
  ),
  { initialValue: LOADING }
);
```

Três detalhes que valem a pena explicar:

- **`debounceTime(300)`** — escrever precisa disso, e 300 ms num clique de filtro é
  impercetível. Melhor do que manter duas pipelines em concorrência.
- **`switchMap`** — cancela um pedido ultrapassado. Sem ele, uma resposta lenta de `"ca"`
  pode chegar depois da de `"caverns"` e sobrepor-se à grelha.
- **Inicializada num campo, não em `ngOnInit`** — o `toObservable` precisa de contexto de
  injeção.

### Separação leitor / gestor

```
/                        biblioteca (leitor)
/play/:bookId            jogar
/play/resume/:gameId     retomar
/admin                   gerir biblioteca
/admin/books/new         criar
/admin/books/:id/edit    editar
/create                  → redireciona para /admin/books/new
```

Tudo o que **altera** o catálogo vive em `/admin`. A biblioteca é a montra do leitor — um
botão de apagar ao lado de "Begin Quest" não serve nenhum dos dois papéis.

### O formulário não reimplementa as regras

O editor verifica apenas que os campos obrigatórios estão preenchidos. Submete e mostra o que
o backend rejeitou.

**Porquê:** duas implementações das mesmas regras divergem. O backend decide; o frontend
mostra.

---

## Persistência

**JPA + H2 em ficheiro.** Os livros de exemplo são tratados como *seed data* carregada uma
vez, não como fonte de verdade em runtime.

> **Se perguntarem "porquê JPA e não guardar os JSON como estão?"**
> O formato de entrada não tem de ser o modelo de persistência. Precisamos de pesquisar por
> título e autor, filtrar por dificuldade, paginar, contar secções por livro e guardar
> progresso ligado a um livro. Isso é um modelo relacional. Ler e reescrever ficheiros JSON a
> cada jogada seria pior em tudo.

> **E "porquê não NoSQL?"**
> Um livro é uma árvore, o que favorece um documento — mas as consultas que a aplicação faz
> são relacionais, e um `GameSession` aponta para um livro. Além disso a máquina de
> desenvolvimento não corre Docker bem, e H2 em ficheiro não precisa de nada instalado.

### Concorrência: `@Version` no `GameSession`

Cada escolha é um ler-modificar-escrever. Sem proteção, duas em simultâneo — o mesmo jogo em
dois separadores, ou um duplo clique — leriam ambas vida 10, ambas subtrairiam, e a segunda
escrita sobrepor-se-ia à primeira. Uma das jogadas desaparecia em silêncio.

```java
@Version
private Long version;
```

O Hibernate compara a versão que carregou com a que está na base. Se diferirem, a escrita
falha, e o `GlobalExceptionHandler` traduz isso para **409 Conflict** com "This game was
changed somewhere else. Reload it and try again." — nada está avariado, a cópia do cliente é
que está velha.

**Otimista e não pessimista** porque os conflitos aqui são raros: um jogo pertence a um leitor
que faz uma escolha de cada vez. Bloquear a linha em cada leitura custaria a todos os pedidos
para proteger um caso quase inexistente.

### Separação de leitores: `playerId`

Cada `GameSession` pertence a um `playerId`. O browser gera um id na primeira visita, guarda-o
em `localStorage`, e um interceptor HTTP envia-o no header `X-Player-Id`:

```
Browser                              Backend
  │  X-Player-Id: 3f2a...              │
  ├───────────────────────────────────▶│  where playerId = :playerId
  │  GET /api/games                    │  and status = PLAYING
```

Pedidos sem header caem num balde partilhado `"anonymous"`, para a API continuar utilizável a
partir do Swagger ou de um `curl`.

**Identifica, não autentica.** Qualquer pessoa pode enviar qualquer id — é uma forma de manter
as listas de jogos separadas, não uma fronteira de segurança. O código diz isto em três
sítios: na entidade, no serviço e no controlador. Contas a sério substituiriam o id gerado no
browser por um emitido no login, sem mexer no resto.

### Entidades e Lombok

Nas entidades JPA, apenas `@Getter`:

```java
@Entity
@Getter
public class Book { ... }
```

**Nunca `@Data` ou `@EqualsAndHashCode`.** O equals/hashCode gerado ou arrasta a coleção
mutável de secções — a igualdade muda enquanto a entidade é preenchida — ou obriga a
exclusões à mão, e parte a identidade que os proxies do Hibernate assumem.

Já o `Consequence` é um `record`, porque é um `@Embeddable`: não tem identidade própria e é
sempre construído por inteiro. É exatamente para isso que os records servem, e o Hibernate 6
mapeia-os diretamente.

---

## Testes

| Suíte | Nº | O que cobre |
|:--|:--|:--|
| Backend | 94 | Unitários, slice, e um de aplicação completa |
| Frontend unitário | 67 | Componentes e serviços (Karma + Jasmine) |
| Playwright E2E | 39 | Browser real contra API real, sem mocks |
| Cucumber BDD | 31 cenários | As mesmas regras em inglês corrente, projeto à parte |

**O teste de aplicação completa é o que vale a pena mencionar.** Tudo o resto simula pelo
menos uma costura. O `ApplicationIntegrationTest` corre contexto real, carregamento real dos
livros, validação real e HTTP real.

---

## Decisões em resumo

| Decisão | Alternativa recusada | Motivo |
|:--|:--|:--|
| Regras no `GameSession` | Regras no service | Information Expert; testável sem Spring |
| `sealed ValidationRule` | Interface aberta | Acrescentar regra deve ser deliberado |
| Duas queries para livro+opções | Um fetch join duplo | Produto cartesiano duplica secções |
| Validação partilhada seed/API | Caminhos separados | Uma definição de "válido" |
| Sem endpoint de save | Botão que grava | Guardar já é o que jogar faz |
| `FINISHED` em vez de `WON` | `WON` | Um END pode ser um final mau |
| Mover antes de decidir morte | Decidir e parar | O leitor vê onde morreu |
| Um jogo por livro | Vários em paralelo | Duplicados são indistinguíveis na lista |
| `/admin` separado | Botões nos cartões | Ler e gerir são papéis diferentes |
| JPA + H2 | JSON em ficheiro / NoSQL | As consultas são relacionais |
| Desfecho num `if/else` | Strategy | Dois casos fixos com precedência; abstrair esconderia a ordem |
| `@Version` (otimista) | Bloqueio pessimista | Conflitos raros; não vale o custo em cada leitura |
| `playerId` do browser | Autenticação completa | Separa leitores sem inventar um modelo de utilizador |
