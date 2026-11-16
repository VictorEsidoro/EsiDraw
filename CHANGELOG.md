# Changelog

Todas as mudanças relevantes deste projeto ficam aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto segue o [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [Não lançado]

## [0.4.1] - 2026-11-16

### Corrigido

- **Data e horário do evento agora são independentes e opcionais.** Antes, ao escolher a data o app preenchia o horário sozinho (19:00) e não havia como tirar. Agora dá para preencher só a data, só o horário ou nenhum dos dois, e cada um tem o botão **Limpar**. Na mensagem do WhatsApp aparece só o que foi preenchido (data com "às HH:mm" quando há os dois, só a data, ou só "Horário").
- Banco versão 4, com migração a partir da versão 3: eventos já salvos mantêm data e horário.

## [0.4.0] - 2026-11-16

### Adicionado

- **Aba Resultado:** cada sorteio feito fica guardado com data e hora (do mais recente ao mais antigo). Cada um mostra a conferência, abre o **resultado completo em tela cheia com rolagem** e pode ser **apagado**. Apagar o sorteio em uso também o descarta, para refazer.
- **Validação automática** (`DrawValidator`), independente do motor: todos têm alguém para presentear, todos foram sorteados, ninguém saiu duas vezes, ninguém tirou a si mesmo, só gente da lista, bloqueios e pares fixos respeitados, e o tipo de ciclo. O sorteio em uso é conferido com os mesmos dados das mensagens.
- Banco versão 3 (histórico de sorteios), com migração a partir da versão 2. O histórico guarda nomes, não telefones.
- Testes do validador, incluindo 300 sorteios de 33 pessoas feitos pelo motor.

### Corrigido

- O resultado na aba Sorteio ficava cortado quando a lista era longa (33 pessoas): agora abre em tela cheia e rola.

## [0.3.0] - 2026-11-16

### Adicionado

- **Planilha de participantes:** **Salvar modelo** gera um `.xlsx` vazio com as colunas Nome e Telefone (telefone já como texto, para o Excel não estragar o número); **Importar planilha** lê esse modelo, outros `.xlsx` e `.csv` (separador `;`, `,` ou tabulação, UTF-8 ou Latin-1). As colunas são achadas pelo cabeçalho; sem cabeçalho, vale nome e telefone. O arquivo pode vir do celular ou do Drive.
- **Editar o sorteio depois de pronto** (`DrawEditor`):
  - quem entra depois fica "fora do sorteio" e o botão **Incluir no sorteio** o encaixa no meio de uma seta existente: normalmente só 1 pessoa antiga ganha resultado novo, e o ciclo único é mantido;
  - ao remover alguém que já estava no sorteio, quem o tirava passa a tirar quem ele tirou; se uma regra impedir, o app ajusta mais uma pessoa;
  - só quem mudou volta para **Pendente** e precisa de mensagem nova; o resto continua valendo;
  - regras (bloqueios e pares forçados) são sempre respeitadas e um par forçado nunca é desfeito;
  - se nenhuma opção servir, o app avisa e a saída é **Refazer sorteio**.
- **Enviar modelo:** o modelo da planilha pode ser enviado por qualquer app (WhatsApp, e-mail, Drive...) pela lista de compartilhamento; **Salvar modelo** continua gravando no celular.
- O EsiDraw aparece ao **compartilhar ou abrir** uma planilha `.xlsx`/`.csv` (por exemplo, recebida no WhatsApp): importa direto.
- Testes de `DrawEditor` e de leitura de planilhas (xlsx gerado pelo Excel/openpyxl, csv, modelo vazio).

### Corrigido

- Importar planilha `.xlsx` falhava no Android ("Não consegui ler a planilha"): o parser de XML do sistema não aceitava uma configuração que funcionava no computador. O `.xlsx` agora é lido por um leitor próprio, igual nos dois.

### Alterado

- Banco: a exportação de esquema do Room foi desligada, porque o KSP corrompia o arquivo no build de release.
- Adicionar pessoas com sorteio pronto não apaga mais o sorteio.
- Textos corridos agora são justificados.

## [0.2.0] - 2026-11-15

### Adicionado

- **Da agenda** agora abre uma tela cheia com busca (sem acento), caixinhas para marcar vários contatos, **Marcar todos** e filtro por **rótulo** (os rótulos do Google Contatos aparecem aqui). Contatos sem telefone com DDD aparecem desabilitados.
- **Colar lista**: cole texto com uma pessoa por linha (`Maria Silva - (31) 98888-7777`); o app mostra quantas pessoas foram reconhecidas antes de importar. Aceita marcadores e numeração.
- **Grupos** (ex.: Grupo da Alegria): salvar a lista atual, **Usar** (substitui a lista), **Atualizar** (grava a lista atual no grupo) e **Excluir**.
- Editar nome e telefone de um participante (toque na pessoa). Mudar só o nome ou o telefone não apaga o sorteio; quem trocou de telefone volta a **Pendente**.
- Importações em lote não duplicam quem já está na lista e informam quantos entraram, quantos já existiam e quem ficou sem telefone.
- Migração do banco da versão 1 para a 2 (grupos), sem perder dados.
- `TextListParser` com testes.

### Alterado

- Todos os campos de texto agora têm cantos bem arredondados.
- **Da agenda** deixou de abrir o seletor de um contato só do Android.

### Corrigido

- Contatos com celular antigo de 8 dígitos (ex.: `(31) 9295-8748`) agora ganham o 9 na frente (`99295-8748`), o formato atual no país; telefone fixo não muda e o `waid` do WhatsApp continua exatamente como veio.
- Release: o KSP das variantes debug e release escrevia o mesmo arquivo de esquema do Room ao mesmo tempo e corrompia o JSON; o workflow agora compila em sequência.

### Observação

- As regras (bloqueios e pares forçados) ainda não são guardadas nos grupos; só nome e telefone. Fica para a 0.3.0.

## [0.1.0] - 2026-11-15

Primeira versão (alpha).

### Adicionado

- Cadastro do evento: nome, data, horário, local, valor sugerido do presente e observações.
- Importação de participantes de três formas: digitando, pela agenda do Android (`PickContact` + `READ_CONTACTS`) e compartilhando contatos do WhatsApp (`.vcf`, `text/x-vcard` e `text/vcard`).
- `VCardParser` para vCard 2.1, 3.0 e 4.0: vários contatos por arquivo, linhas dobradas, quoted-printable e o parâmetro `waid` do WhatsApp.
- Normalização de telefones brasileiros (DDI 55, DDD válido, 9º dígito), com recusa de números sem DDD.
- `DrawEngine`: emparelhamento bipartido com backtracking e heurística MRV; bloqueios unidirecionais ou bidirecionais; pares forçados; zero auto-sorteio.
- Prevenção de ciclos curtos em três níveis (ciclo único, sem duplas A↔B, sem garantia), com o nível aplicado informado na tela.
- Diagnóstico de inviabilidade: aponta quem causa o gargalo (teorema de Hall) e quantas pessoas sem regras faltam, por simulação.
- Entrega individual com status **Pendente**/**Enviado**: link do WhatsApp com mensagem pronta (nome sorteado em negrito) e modo **Revelar aqui**, sem enviar nada.
- Opção **Eu participo do sorteio**: participando, o aviso de privacidade aparece e **Espiar resultado** pede confirmação; não participando, o aviso some e **Ver resultado** é direto.
- Botão **Espiar resultado** protegido por confirmação (quando o organizador participa).
- Persistência com Room: evento, participantes, regras, resultado e status de envio sobrevivem ao fechamento do app.
- Telas em Jetpack Compose + Material 3 no padrão visual dos apps do autor.
- Testes unitários: `DrawEngineTest`, `VCardParserTest`, `PhoneNumberTest`, `WhatsAppMessageTest`, `MoneyTest`.
