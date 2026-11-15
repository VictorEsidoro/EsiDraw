# Changelog

Todas as mudanças relevantes deste projeto ficam aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto segue o [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [Não lançado]

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
- Botão **Espiar resultado** protegido por confirmação.
- Persistência com Room: evento, participantes, regras, resultado e status de envio sobrevivem ao fechamento do app.
- Telas em Jetpack Compose + Material 3 no padrão visual dos apps do autor.
- Testes unitários: `DrawEngineTest`, `VCardParserTest`, `PhoneNumberTest`, `WhatsAppMessageTest`, `MoneyTest`.
