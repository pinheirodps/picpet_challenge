# Guia de apresentação — Adventure Book

Como a aplicação foi construída, pela ordem em que foi construída, e porque cada decisão foi
tomada assim. Escrito para ser falado.

Ver também: [arquitetura-pt.md](arquitetura-pt.md) para o desenho das camadas, e
[revisao-tecnica-pt.md](revisao-tecnica-pt.md) para a avaliação honesta do resultado.

> **O enunciado pede que os objetivos sejam tratados por ordem**, e foram. Cada um ficou a
> funcionar e testado antes de o seguinte começar — por isso a aplicação era demonstrável em
> todas as fases, não só no fim.

---

## Antes do objetivo 1: a fundação

Três coisas tinham de existir antes de uma biblioteca poder listar seja o que for.

### O modelo de domínio

`Book → Section → Option → Consequence`. A forma vem dos JSON de exemplo, com uma diferença
deliberada: o `id` de uma secção no JSON é guardado como `sectionNumber`, separado da chave
primária JPA.

> **Se perguntarem "porque não usar o id do JSON como chave primária?"**
> Porque o mesmo número repete-se entre livros — dois livros terem uma secção 1 é normal.
> Fazê-lo chave primária obriga a chave composta ou gera colisão. Mantê-los separados também
> deixa os `gotoId` legíveis como a numeração do próprio livro.

### As cinco regras de validação

Quatro vêm do enunciado; a quinta foi acrescentada:

| Regra | Origem |
|:--|:--|
| Exatamente um BEGIN | Enunciado |
| Pelo menos um END | Enunciado |
| Todo o `gotoId` aponta para secção existente | Enunciado |
| Secções não-finais têm opções | Enunciado |
| Números de secção únicos dentro do livro | **Acrescentada** |

> **Sobre a quinta regra:** sem ela, o `findBySectionNumber` não tem resposta única. Duas
> secções número 7 fazem com que um `gotoId: 7` encaminhe os leitores de forma imprevisível —
> o jogo pareceria funcionar e mandaria silenciosamente leitores diferentes para sítios
> diferentes.

### Os livros de exemplo precisavam de correção

**Os quatro livros fornecidos falhavam a validação.** Vale a pena dizer isto cedo, porque
demonstra o validador a funcionar em vez de ser um contratempo:

- `dragon-quest.json` chegou vazio — 0 bytes.
- Todos tinham uma secção `666` do tipo `NODE` sem opções, o que quebra a quarta regra do
  próprio enunciado.
- `pirates-jade-sea.json` apontava uma opção para a secção `999`, que não existe.

Perguntámos, e a resposta foi: *"Please ignore the errors if you found some, and if necessary
you can do the changes you think are needed."*

Então foram corrigidos: a secção 666 passou a `END` onde era alcançável e foi removida onde
nada apontava para ela, `999` passou a `20`, e o dragon-quest foi escrito de raiz. Os
originais estão intactos em `books/`; as cópias corrigidas que a aplicação carrega estão em
`backend/src/main/resources/books/`.

> **Um plano anterior era deixar dois livros a falhar**, como prova de que o validador
> funcionava. Foi abandonado: uma biblioteca que arranca com livros partidos demonstra mal, e
> o teste de integração prova o validador igualmente bem.

---

## Objetivo 1 — a biblioteca

**Backend.** `GET /api/books` com pesquisa livre, filtro de dificuldade e paginação, sobre
`Specification` do Spring Data para os filtros se combinarem em vez de precisarem de quatro
métodos de query.

**A decisão sobre N+1.** Cada cartão mostra a contagem de capítulos. A implementação óbvia —
`book.getSections().size()` por linha — é uma query por livro. Em vez disso, uma query
agrupada para a página inteira. A listagem nunca chega a carregar secções.

**Frontend.** Componentes standalone, signals, `OnPush`. Pesquisa, filtro e paginação
alimentam **uma** pipeline RxJS, com debounce de 300 ms e `switchMap`.

> **O que mostrar se demonstrares uma coisa técnica no frontend:** escreve depressa na caixa
> de pesquisa. Sai um pedido, não um por tecla, e os resultados não podem chegar fora de
> ordem.

### O que correu mal aqui

**A pesquisa estava completamente partida**, e os testes unitários passavam na mesma.

A pipeline tinha `distinctUntilChanged()` sobre um `Subject<void>` — a comparar `undefined`
com `undefined`, portanto engolia todas as emissões depois da primeira. A primeira pesquisa
funcionava e mais nenhuma.

> **A lição que vale a pena dizer:** os testes simulavam o serviço e verificavam que era
> chamado, o que é verdade mesmo com a pipeline morta após um uso. O teste E2E que agora
> guarda isto escreve três pesquisas seguidas.

---

## Objetivo 2 — jogar

**A decisão de desenho mais importante.** As regras do jogo vivem no domínio:

```java
public void choose(int optionIndex) { ... }   // em GameSession
```

> **Se perguntarem "porque não no service?"**
> Porque assim as regras espalham-se por quem as chama, e testá-las exige montar um service
> com repositórios simulados. Aqui testam-se com `new`.

### O bug do produto cartesiano

Carregar um livro para jogar precisa de secções **e** opções. A query natural está errada:

```java
// Errado: produz um produto cartesiano
left join fetch b.sections s left join fetch s.options
```

Dois joins de coleções paralelas multiplicam-se. Um livro de 12 secções vinha com 21, cada
uma duplicada. O `distinct` na entidade raiz **não** elimina duplicados dentro da coleção.

**Sintoma real:** o `beginnings()` devolvia 2 em vez de 1, e os jogos recusavam arrancar com
*"Book must have exactly one beginning"*.

**Solução:** duas queries no mesmo contexto de persistência, que o Hibernate funde.

> **O mesmo erro existia no `GameSessionRepository`** e foi encontrado por o procurar
> deliberadamente depois de perceber o padrão. Ambos os repositórios documentam o problema, e
> o `BookRepositoryIntegrationTest` é a defesa contra regressão.

---

## Objetivo 3 — consequências, vida, finais

Acrescentado ao ciclo de turnos do objetivo 2.

### Duas decisões de nomenclatura a defender

**`FINISHED`, não `WON`.** Uma secção END pode ser um final mau — *"the trap closes"* também
é um final. O estado regista que o leitor chegou a um, em vez de reclamar uma vitória que a
aplicação não consegue verificar.

**Uma escolha fatal move o leitor à mesma.** A morte é decidida *depois* do movimento:

```java
currentSectionNumber = chosen.getGotoId();     // move primeiro
settleOutcome();                               // e só depois decide o que isso significou
```

Assim o ecrã mostra a sala onde o leitor morreu, não aquela de onde saiu. O porquê de o
`settleOutcome()` ser um `if/else` e não um Strategy está em
[arquitetura-pt.md](arquitetura-pt.md).

### O texto da consequência

Os livros trazem texto para cada mudança de vida — *"You land hard and twist your ankle."*
Uma versão inicial aplicava a mudança e **deitava o texto fora**, deixando o jogador a ver o
número cair de 10 para 3 sem explicação. O `lastConsequence` é guardado na sessão para a UI
poder dizer porquê.

> **Sugestão de demonstração:** faz o caminho da morte em The Crystal Caverns (ponte →
> saltar → continuar → nadar). Mostra o aviso da consequência, a vida a passar de 10 → 3 → 0,
> o aviso a limpar numa escolha inofensiva, e a morte a nomear a causa.
>
> **Uma ressalva honesta:** a secção onde se morre diz *"shivering but alive"*. É a escrita do
> próprio livro de exemplo, não um bug — vale a pena dizê-lo antes que perguntem.

---

## Objetivo 4 — guardar o progresso

**Este objetivo quase não precisou de código novo, e é essa a parte interessante.**

Cada escolha já persistia a sessão — é isso que jogar *é*. Portanto um jogo guardado é
simplesmente um cujo estado ainda é `PLAYING`, e "guardar" não é uma operação nem uma entidade
separada.

O que foi acrescentado: `GET /api/games` a listar jogos retomáveis, e a zona "Continue
Playing".

> **Se perguntarem "onde está o endpoint de save?"**
> Não existe, deliberadamente. Uma ação de guardar separada significaria poder perder
> progresso por não a carregar. O leitor pode fechar o separador a meio de uma frase e não
> perde nada.

### O cabeçalho, e porque tem três controlos

O enunciado pede *"a header allowing the user to stop/pause the game, view the current book
name, their life, and save their progression"*. O nome e a vida são mostrados; o resto são quatro botões, cada um com a sua intenção:

| Controlo | O que faz |
|:--|:--|
| ← Back to Library | Sai já. O jogo guarda o lugar, como fechar o livro em cima da mesa. |
| 💾 Save Progress | Confirma que o progresso está guardado, e **fica no jogo**. Não faz pedido nenhum. |
| ⏸ Pause | Os dois juntos: confirma que ficou guardado e sai. |
| ⏹ Stop | Termina a aventura de vez, e pergunta antes. |

> **Se perguntarem porque há um Stop se a Figura 1 não o mostra:** o texto do enunciado pede
> ("stop/pause the game"), mesmo que o mockup o omita — tal como omite a vida, que o texto
> também pede. Tratei o texto como o requisito e a figura como um esboço.

> **E porque o Stop é separado da pausa:** "stop/pause" tanto pode significar afastar-se como
> desistir. A Pause cobre o primeiro; o Stop cobre o segundo, não tem retorno, e é por isso o
> único que pede confirmação.

### Dois refinamentos que vieram de usar a aplicação

Ambos invisíveis até a aplicação ser usada com estado real acumulado — vale a pena dizê-lo,
porque é o tipo de coisa que só uma aplicação a correr revela.

**Começar um jogo num livro já em curso agora retoma-o.** O `POST /api/games` criava uma
sessão de cada vez, portanto carregar "Begin Quest" duas vezes deixava dois jogos no mesmo
livro. Na lista ficam indistinguíveis — mesmo título, muitas vezes a mesma vida — e o leitor
não sabe qual tem o seu progresso. Jogar um livro duas vezes em paralelo continua possível:
para o primeiro e começa de novo.

**"Continue Playing" recolhe para os 4 mais recentes.** Sem limite, empurrava a biblioteca —
o ponto da página — para fora do ecrã. Agora mostra quatro, com o total no título e um botão
"Show all".

> **Isto mudou a suíte E2E de forma instrutiva.** Oito testes falharam depois da mudança,
> todos a assumir que "Begin Quest" começa sempre do zero. Essa suposição nunca foi
> declarada, apenas usada. A correção foi um helper que termina qualquer jogo pendente
> primeiro, o que tornou a pré-condição explícita em vez de acidental.

---

## Objetivo 5 — adicionar livros

O formulário submete e mostra o que o backend rejeitou. Deliberadamente **não** reimplementa
as regras — nada de verificações de BEGIN/END/`gotoId` em TypeScript.

> **Porque isso importa:** duas implementações das mesmas regras divergem. O backend é quem
> decide, e lista **todos** os motivos de uma vez em vez de parar no primeiro — assim quem
> corrige um livro não submete seis vezes para descobrir seis problemas.

### O que correu mal aqui

**O editor renderizava uma página em branco.** O `newSection()` lia o getter `sections` — que
delega para `this.form.get(...)` — *durante* a construção do próprio `this.form`. Um
`TypeError` que matava o arranque do Angular em silêncio, sem erro na consola a apontar para
a causa. Resolvido passando o `nextId` como parâmetro.

---

## Além do enunciado: rever e remover livros

Não foi pedido. Acrescentado porque publicar um livro com um erro deixa-o na biblioteca para
sempre, o que torna o objetivo 5 muito menos útil do que parece.

**Porquê um ecrã `/admin` separado.** A biblioteca é a montra do leitor — o design sugerido
mostra-a como sítio para escolher uma aventura. Um botão de apagar a um clique de "Begin
Quest" não serve nenhum dos dois papéis.

**Ambas as operações terminam jogos em curso nesse livro, e avisam antes.** A posição
guardada do leitor é um número de secção na versão em que começou. Depois de as secções
mudarem, esse número pode apontar para outro sítio — retomar silenciosamente uma história
reescrita é pior do que ser avisado.

**Uma revisão é validada pelas mesmas regras que um livro novo.**

### O que correu mal aqui

**Editar um livro mantendo os mesmos números de secção devolvia 500.** A edição mais comum de
todas — corrigir uma gralha sem renumerar. O Hibernate emitia os `INSERT` das secções novas
antes dos `DELETE` das antigas, e a constraint única `(book_id, section_number)` disparava.
Resolvido separando a operação em duas fases com um flush entre elas.

**Carregar um livro para edição perdia o texto de todas as secções.** Apanhado por um teste
unitário escrito para verificar que o formulário carregado era válido, antes de qualquer
teste manual dar por isso.

---

## Testes

| Suíte | Nº | O que cobre |
|:--|:--|:--|
| Backend | 100 | Unitários, slice, e um de aplicação completa |
| Frontend unitário | 76 | Componentes e serviços |
| Playwright E2E | 43 | Browser real contra API real, sem mocks |
| Cucumber BDD | 31 cenários | As mesmas regras em inglês corrente, projeto à parte |

**O teste de aplicação completa é o que vale a pena mencionar.** Tudo o resto simula pelo
menos uma costura. O `ApplicationIntegrationTest` corre contexto real, carregamento real dos
livros, validação real e HTTP real — é o que apanha um livro que deixa de carregar ou uma
regra que o Spring não descobre.

**O projeto BDD é um exercício à parte**, não faz parte da entrega. Só mencionar se houver
tempo. A propriedade interessante: começou com uma cópia própria do domínio, divergiu três
vezes enquanto passava a 100%, e agora depende do jar real do backend — uma mudança de regra
que quebre um cenário faz falhar aquele build.

---

## Limitações — dizer antes de perguntarem

**Os leitores estão separados, mas não autenticados.** Cada jogo pertence a um `playerId` que
o browser gera e guarda em `localStorage`, enviado no header `X-Player-Id`. O
`GET /api/games` filtra por ele, portanto cada pessoa vê apenas os seus jogos.

O que isso **não** é: qualquer pessoa pode enviar qualquer id, não há proteção nenhuma. O
mesmo utilizador noutro dispositivo é outro jogador. E o `/admin` continua aberto a quem lá
chegar.

> **A frase a usar:** "isto separa leitores, não os autentica". Separar resolve um bug que se
> nota no primeiro minuto; autenticar é outra conversa, e seria o passo seguinte — trocar o id
> do browser por um emitido no login, o que muda pouco no resto do código.

**`ddl-auto: update`.** Aceitável quando o esquema vem das entidades e os dados são
recarregáveis; um deployment real usaria Flyway ou Liquibase.

**Apagar um livro destrói os jogos jogados nele.** Uma posição guardada não significa nada sem
as secções a que se refere — mas torna o apagar definitivo, e é por isso que pergunta antes.

**A sinopse, as etiquetas de género e o tempo de leitura do mockup não foram acrescentados.**
Esses dados não existem nos JSON fornecidos. Inventá-los seria mostrar aos leitores informação
fabricada sobre livros reais. A contagem de capítulos **foi** acrescentada, porque é derivável
de dados que existem mesmo.

---

## Se tiveres cinco minutos, demonstra isto

**Antes:** para o backend, apaga `backend/data/`, arranca outra vez. Os quatro livros
recarregam e a biblioteca fica como um avaliador a veria pela primeira vez. Correr a suíte
E2E deixa livros publicados para trás, e uma prateleira cheia de
`The Whispering Woods 1789822718297` estraga a demonstração.

1. **Biblioteca** — pesquisa "Ashfell", mostra que encontra pelo autor, não só pelo título.
2. **Jogar The Crystal Caverns** — ponte, saltar (−7, o aviso explica porquê), continuar (o
   aviso limpa), nadar (morte, com a causa nomeada).
3. **Voltar à biblioteca** — o jogo está em Continue Playing; retoma-o, mesmo sítio, mesma
   vida. Nenhum botão de guardar foi carregado.
4. **Manage Library** — edita um livro, mostra que todos os campos voltam preenchidos,
   parte-o (muda o END para NODE) e mostra o backend a recusar com o motivo.
5. **Swagger** — `/swagger-ui.html`, todos os endpoints documentados com os formatos de erro.
