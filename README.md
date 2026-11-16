# EsiDraw

Aplicativo Android de **Amigo Oculto** (Secret Santa). Você cadastra o evento e os participantes, define quem não pode tirar quem, sorteia e entrega o resultado a cada pessoa pelo WhatsApp ou passando o celular de mão em mão.

O sorteio **nunca falha por azar**: se as regras tornarem o sorteio impossível, o app avisa exatamente quem causou o problema e quantas pessoas faltam.

> Status: **0.1.0 (alpha)**. Veja o [CHANGELOG](CHANGELOG.md).

---

## Como colocar para funcionar (passo a passo para não programadores)

### Passo 1 — Baixe o aplicativo

Na página do projeto no GitHub, abra a aba **Releases** e baixe o arquivo `.apk` mais recente (o nome tem a versão, por exemplo `EsiDraw-0.1.0.apk`).

### Passo 2 — Permita a instalação

Ao abrir o arquivo, o Android avisa que a instalação vem de fora da loja. Toque em **Configurações** e ative **Permitir desta fonte**. Depois volte e toque em **Instalar**.

> O aplicativo não usa internet e só pede acesso à agenda se você tocar em "Da agenda".

### Passo 3 — Abra o EsiDraw

O ícone é um presente azul. Na parte de baixo há quatro abas: **Evento**, **Pessoas**, **Regras** e **Sorteio**.

### Passo 4 — Monte e sorteie

Preencha as abas da esquerda para a direita (veja a tabela abaixo). Quando tudo estiver pronto, toque em **Sortear** e entregue o resultado a cada pessoa.

---

## Conhecendo o app

| Aba | Para que serve |
|---|---|
| **Evento** | Nome, data, horário, local, valor sugerido e observações. Tudo isso vai automaticamente na mensagem de cada participante. Aqui também ficam as opções **Eu participo do sorteio** e de evitar duplas isoladas. |
| **Pessoas** | Cadastro dos participantes: digitando, pela agenda do celular ou compartilhando contatos do WhatsApp. |
| **Regras** | **Bloqueios** (quem não pode tirar quem, nos dois sentidos ou só em um) e **pares forçados** (quem tem que tirar quem). |
| **Sorteio** | Faz o sorteio, mostra o diagnóstico se for impossível e controla a entrega individual de cada resultado. |

## Como usar

### Cadastrar participantes

- **Digitando:** nome e telefone com DDD, por exemplo `(31) 98888-7777`.
- **Da agenda:** toque em **Da agenda**. O app pede permissão de leitura da agenda só nesse momento. Use a busca, escolha um **rótulo** do Google Contatos para ver só aquele grupo, toque em **Marcar todos** e depois em **Importar**.
- **Planilha:** toque em **Enviar modelo** (escolha WhatsApp, e-mail, Drive...) ou **Salvar modelo** (grava no celular), preencha **Nome** e **Telefone** no Excel ou no Google Planilhas e salve no celular ou no Drive. Depois toque em **Importar planilha** e escolha o arquivo. Se a planilha for do Google, use *Arquivo > Fazer download > Microsoft Excel (.xlsx)* antes de importar. Também funciona com `.csv`. Se a planilha chegar pelo WhatsApp, abra o arquivo ou toque em Compartilhar e escolha **EsiDraw**: ela é importada na hora.
- **Colar lista:** cole um texto com uma pessoa por linha, por exemplo `Maria Silva - (31) 98888-7777`.
- **Pelo WhatsApp:** nos dados do contato, use a opção de compartilhar o contato e escolha **EsiDraw**. Vários contatos de uma vez também funcionam. Contatos sem telefone com DDD são avisados e não entram na lista.

### Grupos (para os próximos anos)

Com a lista pronta, digite um nome (ex.: Grupo da Alegria) e toque em **Salvar lista atual como grupo**. No ano seguinte:

1. Toque em **Usar** no grupo para trazer a lista de volta.
2. Adicione quem entrou, remova quem saiu e toque na pessoa para corrigir o telefone de quem mudou.
3. Toque em **Atualizar** no grupo para guardar a lista nova.

Usar um grupo substitui a lista, as regras e o sorteio atuais (o app pede confirmação).

### Definir regras

- **Bloqueio ("brigados"):** a primeira pessoa não pode tirar a segunda. Marque **Nos dois sentidos** para valer também ao contrário.
- **Par forçado ("combinado"):** a primeira pessoa tem que tirar a segunda. Cada pessoa só pode ter um par forçado e só pode ser tirada por uma pessoa. O app recusa combinações contraditórias na hora.

### Sortear

Toque em **Sortear**. Se as regras tornarem o sorteio impossível, o botão fica desligado e aparece um quadro vermelho dizendo **quem** é o gargalo e **quantas pessoas sem regras** precisam entrar para funcionar.

### Aba Resultado

Cada sorteio fica guardado com a data. Em **Resultado** você vê a conferência automática (todos têm par, todos foram sorteados, ninguém saiu duas vezes, ninguém tirou a si mesmo, regras respeitadas), abre o resultado completo e pode **apagar** um sorteio. Se o sorteio for o que está em uso, apagar também o descarta, e aí é só sortear de novo na aba Sorteio. Se você participa do sorteio, ver o resultado pede confirmação.

### Alguém entrou ou saiu depois do sorteio

Não precisa refazer tudo:

- **Entrou alguém:** adicione a pessoa normalmente em Pessoas. Em **Sorteio**, ela aparece em *Entrou depois do sorteio*; toque em **Incluir no sorteio**. O app encaixa a pessoa no sorteio que já existe e avisa quem ganhou resultado novo (normalmente 1 pessoa). Mande mensagem nova só para quem ficar **Pendente**.
- **Saiu alguém:** remova a pessoa em Pessoas. Quem a tirava passa a tirar quem ela tinha tirado (se uma regra impedir, o app ajusta mais uma pessoa) e avisa a quem mandar mensagem nova.
- **Mudou o telefone:** toque na pessoa e corrija; o sorteio não muda.
- **Não deu para encaixar:** o app avisa. Use **Refazer sorteio**.

Mudar bloqueios ou pares forçados continua apagando o sorteio, porque o resultado pode deixar de valer.

### Entregar o resultado

Na lista **Entrega individual**, cada pessoa tem o status **Pendente** ou **Enviado** e duas opções:

| Opção | O que faz | Privacidade |
|---|---|---|
| **WhatsApp** | Abre a conversa com a pessoa já com a mensagem pronta (saudação, nome sorteado em negrito, data, local, valor e observações). Você só toca em enviar. | **O organizador consegue ver.** A mensagem sai da sua conta e fica no seu histórico com aquela pessoa. Só importa se você participa do sorteio. |
| **Revelar aqui** | Você passa o celular, a pessoa toca em "Sou fulano" e vê o próprio resultado na tela. Nada é enviado. | **Privado de verdade.** O organizador não vê. |

Se você **não participa** do sorteio (desligue **Eu participo do sorteio** na aba Evento), o aviso de privacidade some e o botão **Ver resultado** mostra tudo na hora. Se você participa, o botão vira **Espiar resultado** e pede uma confirmação antes, porque estraga a sua surpresa.

## Seus dados

Tudo fica **só no seu celular** (banco local). O aplicativo não tem acesso à internet, não faz backup na nuvem e some junto com os dados se você desinstalá-lo. Se o app for fechado no meio do processo, evento, participantes, regras, sorteio e status de envio continuam salvos.

## Problemas comuns

| O que aconteceu | O que fazer |
|---|---|
| "Telefone inválido" | Informe DDD + número, por exemplo `(31) 98888-7777`. Números sem DDD não são aceitos. |
| O botão **Sortear** está desligado | Leia o quadro vermelho do diagnóstico: ele diz quem está causando o problema e quantas pessoas faltam. |
| O sorteio foi descartado | Mudar bloqueios ou pares forçados apaga o sorteio anterior, porque o resultado deixaria de valer. Sorteie de novo. Adicionar ou remover pessoas não apaga: veja *Alguém entrou ou saiu depois do sorteio*. |
| O WhatsApp não abriu | Confira se o WhatsApp (ou WhatsApp Business) está instalado. Sem ele, o link abre no navegador. |
| Quero marcar muitos contatos do WhatsApp | Salve-os na agenda com um rótulo no Google Contatos (ex.: Amigo oculto 2026) e filtre por ele em **Da agenda**. |
| A planilha não importou | Use o modelo do app (.xlsx) ou um .csv com **Nome** e **Telefone**. Planilha nativa do Google precisa ser baixada como .xlsx antes. Telefones sem DDD são listados no aviso. |
| Compartilhei um contato e nada entrou | Provavelmente ele não tem telefone com DDD. O app avisa o nome do contato na mensagem que aparece na parte de baixo. |
| Quero que eu mesmo não saiba o resultado | Use **Revelar aqui** em vez de **WhatsApp**. |

---

## Para desenvolvedores

### Stack

Kotlin 100%, Jetpack Compose com Material 3, MVVM + Clean Architecture, Room e Gradle com Kotlin DSL (AGP 9, Kotlin embutido, KSP). `minSdk` 26.

### Arquitetura

```
app/src/main/kotlin/io/github/victoresidoro/esidraw/
├── domain/              Kotlin puro, sem Android (é o que os testes cobrem)
│   ├── engine/          DrawEngine (sorteio + diagnóstico) e Matching (Kuhn/Hall)
│   ├── model/           Participant, DrawRules, DrawOutcome, Diagnosis, EventSettings...
│   ├── message/         WhatsAppMessage (mensagem + link), EventDetails, Money
│   ├── phone/           PhoneNumber (normalização de telefones brasileiros)
│   └── repository/      EsiDrawRepository (interface)
├── data/
│   ├── db/              Room: entidades, DAO e banco
│   ├── repo/            RoomEsiDrawRepository
│   ├── vcard/           VCardParser (.vcf do WhatsApp e da agenda)
│   └── contacts/        ContactsReader (seletor de contatos do Android)
└── ui/                  Compose: tema, componentes, 4 telas e EsiDrawViewModel
```

A UI só conhece o `EsiDrawRepository` (interface do domínio); o Room fica atrás dele. `MainActivity` recebe os contatos compartilhados (`ACTION_SEND` / `ACTION_VIEW` com `text/x-vcard` e `text/vcard`) e entrega o texto ao ViewModel.

### O algoritmo do sorteio

Cada participante é um vértice e "A tira B" é a aresta A→B. Um sorteio válido é um **emparelhamento perfeito** no grafo bipartido *quem tira × quem é tirado*, em que as arestas permitidas excluem: o auto-sorteio, os bloqueios e, para cada par forçado X→Y, tudo que não seja X→Y em X e em Y.

1. **Viabilidade (`analyze`).** Um emparelhamento máximo (Kuhn, caminhos de aumento) decide se existe sorteio. Se não existe, o **teorema de Hall** aponta o conjunto de pessoas que disputa poucas opções. Isso gera o diagnóstico: contradições entre regras, quem ficou sem ninguém para tirar, quem não pode ser tirado por ninguém e o gargalo de Hall.
2. **Quantas pessoas faltam.** O app simula a entrada de 1, 2, 3... participantes sem regras até o sorteio ficar possível. O resultado é o mínimo exato; se nenhuma quantidade resolve (regras contraditórias), ele diz que adicionar gente não adianta.
3. **Sorteio (`draw`).** Backtracking com **MRV (Minimum Remaining Values)**: a cada passo escolhe quem tem menos candidatos (pares forçados têm 1 e entram primeiro), com empates e ordem das opções sorteados por `SecureRandom`. Poda quando alguém fica sem opção ou quando alguém não pode mais ser tirado por ninguém.
4. **Ciclos curtos.** Tenta, nessa ordem: **ciclo único** (A→B→…→A, mantendo cabeça e cauda de cada caminho parcial e recusando fechar ciclo antes da última aresta), **sem duplas A↔B** e, por último, qualquer sorteio válido. O resultado informa qual garantia foi aplicada, e a tela mostra isso.
5. **Rede de segurança.** O backtracking tem orçamento de 20 mil nós. Se estourar, o app usa o emparelhamento que o passo 1 já provou existir, sem a garantia de ciclo.

`shuffle()` simples não serve: ele ignora as restrições e, com bloqueios, ou erra ou fica tentando de novo sem fim.

### Como compilar

Pré-requisitos: Android Studio recente (o AGP 9.4 pede **Gradle 9.6.0 ou mais novo**, que o wrapper já baixa) e JDK 21.

```bash
# testes do domínio e do parser (JVM, sem celular)
./gradlew testDebugUnitTest

# APK de teste (assinado com a chave de debug)
./gradlew assembleDebug
# saída: app/build/outputs/apk/debug/app-debug.apk
```

Pelo Android Studio: **File → Open** na pasta do projeto, aguarde o sync e rode **Run ▶**.

> O esquema do Room não é exportado (a exportação corrompia o build de release). Toda mudança de banco exige uma `Migration` escrita à mão e um teste instalando a versão nova por cima da anterior.

### Release assinado

Em **Actions → Release → Run workflow**, informe a versão (`0.1.0-test.1` ou `0.1.0`). O `.github/workflows/release.yml` roda os testes, gera o APK assinado, cria a tag `v<versão>` e publica o GitHub Release (com sufixo, como pre-release). Segredos do repositório: `ANDROID_KEYSTORE_B64` (chave em base64), `KEYSTORE_PASSWORD` e, se a chave tiver mais de um apelido, `KEYSTORE_ALIAS`. A chave nunca vai pro Git.

### Testes

`DrawEngineTest` (sorteio simples, bloqueios mútuos, pares forçados, cenários impossíveis com diagnóstico exato, concordância entre `analyze` e `draw` em regras aleatórias), `VCardParserTest`, `PhoneNumberTest`, `WhatsAppMessageTest` e `MoneyTest`.

### Versionamento

[Versionamento Semântico](https://semver.org/lang/pt-BR/). Builds de teste da mesma versão levam sufixo de pré-lançamento (`0.1.0-test.2`). Todas as mudanças ficam no [CHANGELOG](CHANGELOG.md).

### Créditos

Victor Eduardo ([@victoresidoro](https://github.com/VictorEsidoro)). Visual no padrão dos apps do autor (o mesmo do AnyGest).
