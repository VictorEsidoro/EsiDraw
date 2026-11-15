# Changelog

Todas as mudanças relevantes deste projeto ficam aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto segue o [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [Não lançado]

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
