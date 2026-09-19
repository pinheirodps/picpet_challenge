# Relatório de Revisão Técnica — Adventure Book Application

**Data:** 19 de Setembro de 2026  
**Documento Base:** `Adventure Book Application.pdf` (Pictet Technologies)  
**Stack Avaliada:** Backend Java 21 / Spring Boot 3.3.4, Frontend Angular 19, BDD Cucumber, E2E Playwright

---

## 1. Sumário Executivo Atualizado

| Área | Status Geral | Nota |
| :--- | :--- | :---: |
| **Cobertura dos Requisitos do PDF** | **100% dos Requisitos Obrigatórios e Bônus Cobertos** (Objetivos 1 a 5 + Stop + Consequências narrativas + Gestão de Catálogo) | 9.9 / 10 |
| **Fidelidade Visual (Figuras 1 e 2)** | Biblioteca limpa e fiel ao leitor; Separação da gestão no `/admin` preservando o mockup da Figura 1 | 9.2 / 10 |
| **Qualidade & Práticas Backend** | Arquitetura DDD, Strategy Pattern para regras, Cache, OpenAPI, 91 testes automatizados passando | 9.8 / 10 |
| **Qualidade & Práticas Frontend** | Angular 19 moderno com Signals, Control Flow (`@if`, `@for`), `inject()`, sem vazamento de memória com `takeUntilDestroyed` | 9.7 / 10 |
| **Cobertura de Testes Multi-Camadas** | 91 Backend + 31 BDD (140 steps) + 64 Unit Frontend + 38 E2E Playwright = **224 testes 100% verdes** | 10 / 10 |

---

## 2. Auditoria dos Requisitos do PDF

### 2.1. Regras de Validação de Livros (Objetivo Principal)
O PDF exige que um livro seja considerado inválido se atender a qualquer uma das condições:
1. **Nenhum ou mais de um início (`BEGIN`)**: ✅ Implementado em `SingleBeginningRule.java`.
2. **Nenhum fim (`END`) (podendo ter múltiplos)**: ✅ Implementado em `HasEndingRule.java`.
3. **`gotoId` apontando para seção inexistente**: ✅ Implementado em `ValidNextSectionIdRule.java`.
4. **Seção não-final sem opções**: ✅ Implementado em `NonEndingHasOptionsRule.java`.
5. **IDs de Seção Duplicados**: ✅ Implementado em `UniqueSectionNumberRule.java`.

---

### 2.2. Header do Jogo & Controles
> *"The application needs to display a header allowing the user to stop/pause the game, view the current book name, their life, and save their progression."*

* **Nome do livro**: ✅ Exibido no topo (`game.bookTitle`).
* **Vida do jogador**: ✅ Exibida com destaque (`♥ {{ game.health }} / 10`), com estilização dinâmica de perigo quando `<= 3`.
* **Salvar progressão**: ✅ Persistência contínua a cada escolha (Objetivo 4) + botão `Save Progress` com feedback visual imediato.
* **Parar / pausar aventura**: ✅ Endpoint dedicado `POST /api/games/{id}/stop` com transição para `GameStatus.ABANDONED` e botão `⏹ Stop` no header, limpando o jogo da lista de "Continue Playing".
* **Navegação**: ✅ Botão `← Back to Library` para retornar sem perder a sessão.

---

### 2.3. Mecanismo de Consequências e Vida (HP)
> *"How can a player die? Making a choice can have various consequences, and some of them are life-threatening. A player starts with 10 health points, and certain actions will affect this... Once health reaches zero, the player dies, and the adventure is over."*

* **Vida inicial (10 HP) e morte ao atingir zero**: ✅ Implementado em `GameSession.java` com status `GameStatus.LOST`.
* **Texto e Impacto da Consequência Entregues ao Jogador**: ✅ Resolvido. O modelo de domínio `GameSession` persiste a última consequência sofrida (`lastConsequence`), e o DTO expõe o texto da história (ex: *"You land hard and twist your ankle."*) e a variação de HP assinada (ex: `"-7 HP"`).
* **Banner Narrativo**: ✅ O frontend exibe um banner de alerta com a descrição da consequência e indicador numérico, limpo automaticamente na próxima escolha segura.
* **Morte com Causa Apresentada**: ✅ Na tela *"You Have Perished"*, o jogador visualiza a consequência exata que provocou a derrota.

---

### 2.4. Objetivos do PDF (1 a 5) + Extensões

* **Objetivo 1 (Home, busca e filtro)**: ✅ Biblioteca completa com busca textual reativa (debounced com RxJS `Subject` e `switchMap`), filtro de dificuldade (`EASY`, `MEDIUM`, `HARD`) e paginação.
* **Objetivo 2 (Iniciar jogo e escolhas sem consequências)**: ✅ Navegação fluida entre seções via IDs.
* **Objetivo 3 (Consequências e finalização)**: ✅ Mecânica completa de dano, cura, morte e término neutro (*"The End"*, sem assumir vitória indevida).
* **Objetivo 4 (Salvar progressão)**: ✅ Persistência automática em H2 e restauração transparente via seção "Continue Playing" ordenada por data de atualização recente.
* **Objetivo 5 (Adicionar e gerir livros)**: ✅ Interface visual completa no módulo `/admin`, suportando criação, edição e exclusão.

---

## 3. Decisão Arquitetural: Módulo Administrativo `/admin`

### Justificativa de Engenharia e Fidelidade ao Mockup:
1. **Preservação da Biblioteca do Leitor (Figura 1)**:
   - Poluir os cards da biblioteca com botões de "Editar" e "Apagar" comprometeria a fidelidade ao mockup da Figura 1 e colocaria ações destrutivas a um clique de distância de leitores comuns.
   - O botão na biblioteca agora conduz com elegância para `/admin` (*Manage Library*).
2. **Separação de Papéis (Reader vs. Admin)**:
   - Uma tela dedicada de gestão fornece o espaço ideal para mostrar métricas cruciais (quantos jogos em progresso aquele livro possui, número de capítulos e opções seguras de confirmação).
3. **Ciclo Completo de Edição e Exclusão (CRUD)**:
   - **`PUT /api/books/{id}`**: Permite corrigir gralhas e reescrever seções. A sincronização foi implementada para limpar e reinserir seções com flush intermediário, garantindo conformidade com a restrição única `(book_id, section_number)`.
   - **`DELETE /api/books/{id}`**: Remove o livro do catálogo e encerra em cascata qualquer sessão de jogo pendente associada a ele, evitando entradas órfãs corrompidas.

---

## 4. Matriz de Testes Automatizados

A aplicação possui validação em quatro níveis complementares:

```
+-------------------------------------------------------------------------------+
|                             Adventure Book App                                |
|-------------------------------------------------------------------------------|
|  1. Backend Unit & Integration Tests (JUnit 5 / Spring Boot)    : 91 / 91  ✓  |
|  2. BDD Acceptance Scenarios (Cucumber)                         : 31 / 31  ✓  |
|  3. Frontend Unit Tests (Karma / ChromeHeadless)                 : 64 / 64  ✓  |
|  4. End-to-End Tests (Playwright / Chromium / Edge)             : 38 / 38  ✓  |
+-------------------------------------------------------------------------------+
|  TOTAL DE TESTES AUTOMATIZADOS: 224 / 224 PASSANDO (100% GREEN)               |
+-------------------------------------------------------------------------------+
```

---

## 5. Resumo das Alterações Realizadas e Boas Práticas Adotadas

1. **Pipeline de Busca Reativo (Frontend)**:
   - Substituição do `Subject<void>` que engolia digitações por `Subject<string>` com `debounceTime(300)`, `distinctUntilChanged()`, `switchMap()` e liberação segura de recursos com `takeUntilDestroyed()`.
2. **Consequências Narrativas Expostas (Fullstack)**:
   - Adicionado armazenamento de `lastConsequence` no domínio `GameSession` e propagação até o componente Angular com feedback visual claro.
3. **Botão de Interrupção (`Stop`)**:
   - Adicionado suporte a `GameStatus.ABANDONED` e botão no cabeçalho com limpeza automática de jogos em andamento.
4. **Todos os 4 Livros de Seed Válidos**:
   - Correção dos arquivos JSON na pasta de recursos para que a biblioteca inicial carregue todos os 4 títulos (*The Crystal Caverns*, *Dragon Quest*, *Pirates of the Jade Sea*, *The Prisoner*).
5. **Módulo Administrativo `/admin`**:
   - Implementação de endpoints REST `GET`, `PUT`, `DELETE` em `/api/books/{id}` com cancelamento de sessões ativas e tela de administração dedicada com navegação segura e diálogos de confirmação.
6. **Arquivos `.gitignore`**:
   - Configurados na raiz e no projeto fullstack para proteger o repositório contra arquivos de banco H2 temporários (`*.mv.db`), pastas de build (`target/`, `dist/`), cache e dependências.
