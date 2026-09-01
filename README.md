# ShPIX

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![Minecraft 1.8 - 1.21](https://img.shields.io/badge/Minecraft-1.8%20--%201.21-blue.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-suportado-brightgreen.svg)](https://papermc.io/software/folia)

Pagamentos PIX integrados para servidores Minecraft, do **1.8 ao 1.21**, em
Spigot, Paper e **Folia**, tudo em um único jar.

O ShPIX é um fork autorizado do BRPayments, reescrito com foco em integridade
financeira, idempotência de entrega e compatibilidade real com o modelo de execução
regionalizado do Folia.

---

## Recursos

- Cobranças PIX geradas dentro do jogo (QR Code em mapa + código copia-e-cola).
- Loja em menus paginados: categorias, produtos, checkout e histórico de pedidos.
- Cupons de desconto com validação de faixa e persistência em `coupons.yml`.
- Entrega de recompensas idempotente: uma transação nunca entrega duas vezes, mesmo
  com reinício do servidor, timeout, retry ou múltiplas threads.
- Recuperação automática de pedidos pendentes após restart e entrega ao reconectar.
- Notificação de venda via webhook do Discord (opcional).
- Suporte a PlaceholderAPI (opcional).

---

## Requisitos

| Item | Versão |
|------|--------|
| Servidor | Spigot, Paper, PandaSpigot, Purpur, Pufferfish ou Folia, da **1.8 até a 1.21** |
| Java | **17 ou superior** |
| Banco de dados | MySQL 5.7+ / MariaDB 10.3+ |

O driver JDBC do MySQL já vem embutido no `.jar`. Não há build separado por
versão: o mesmo arquivo detecta a plataforma em tempo de execução.

> **Atenção ao Java.** O jar é compilado em bytecode Java 17. Servidores 1.8
> rodando em Java 8 ou 11 **não conseguem carregar o plugin**. Atualize a JVM,
> que a versão do Minecraft não precisa mudar. Servidores 1.17+ já exigem
> Java 17+ por conta própria.

### Como a compatibilidade funciona

O plugin nunca referencia uma classe que possa não existir na versão em uso.
Tudo que mudou entre o 1.8 e o 1.21 passa por uma camada em
[`compat/`](src/main/java/dev/singlehope/free/shpix/compat/):

| Recurso | 1.8 a 1.12 | 1.13+ |
|---------|------------|-------|
| Tag do item do pedido | NBT via reflection | `PersistentDataContainer` |
| Materiais | nomes legados + data value (`SKULL_ITEM:3`) | nomes pós-flattening |
| Sons | `LEVEL_UP`, `ANVIL_LAND` | `ENTITY_PLAYER_LEVELUP`, `BLOCK_ANVIL_LAND` |
| Title / action bar | pacotes NMS | API do Bukkit |
| Textura de cabeça | `GameProfile` via reflection | perfil do Paper |
| Mapa do QR Code | durabilidade do item | `MapMeta#setMapView` |
| Scheduler | `BukkitScheduler` | `BukkitScheduler` ou schedulers do Folia |

Mensagens, hover e clique usam a API de chat do Spigot
(`net.md_5.bungee.api.chat`), presente desde o 1.8. Onde um recurso não existe
na versão antiga há degradação suave: sem `COPY_TO_CLIPBOARD` (anterior ao
1.15), o código PIX é oferecido via sugestão no chat.

---

## Instalação

1. Coloque `ShPIX-<versão>.jar` em `plugins/`.
2. Inicie o servidor uma vez para gerar `plugins/ShPIX/`.
3. Edite `plugins/ShPIX/config.yml` com as credenciais do banco e o access token
   da gateway.
4. Execute `/shpix reload` ou reinicie o servidor.

Opcional: coloque uma imagem `plugins/ShPIX/logo.png` para exibi-la no centro do
QR Code.

---

## Configuração

Toda a configuração fica em `plugins/ShPIX/config.yml`. Os valores abaixo são os
mais relevantes:

```yaml
payment:
  expiration-minutes: 30      # validade da cobrança
  poll-interval-seconds: 20   # intervalo de verificação junto à gateway
  min-amount: 1.00            # valor mínimo aceito
  max-amount: 5000.00         # valor máximo aceito
  fee-percent: 0.99           # taxa repassada ao comprador

gateways:
  MERCADO_PAGO:
    enabled: true
    access-token: ""          # access token de produção
```

Produtos ficam em `plugins/ShPIX/products/*.yml`; mensagens em
`plugins/ShPIX/messages.yml`.

> O access token nunca é escrito em log. Trate `config.yml` como arquivo secreto.

---

## Comandos

| Comando | Permissão | Descrição |
|---------|-----------|-----------|
| `/shop` | `shpix.shop` | Abre a loja virtual |
| `/shop categorias` | `shpix.shop` | Lista as categorias |
| `/shop categoria <id>` | `shpix.shop` | Abre os produtos de uma categoria |
| `/shpix reload` | `shpix.admin` | Recarrega configuração, produtos e cupons |
| `/shpix status` | `shpix.admin` | Estado do banco, gateways e pedidos abertos |
| `/shpix cupom criar <nome> <%>` | `shpix.admin` | Cria um cupom |
| `/shpix cupom remover <nome>` | `shpix.admin` | Remove um cupom |
| `/shpix cupom listar` | `shpix.admin` | Lista os cupons |
| `/shpix pedido <referência>` | `shpix.admin` | Consulta um pedido |
| `/shpix reentregar <referência>` | `shpix.admin` | Reenvia a entrega de um pedido pago |

---

## Ciclo de vida de um pedido

```text
WAITING ──(gateway aprova)──> PAID ──(jogador online)──> DELIVERED
   │
   ├──(expirou)──> EXPIRED
   ├──(recusado)─> CANCELLED
   └──(estorno)──> REFUNDED
```

A transição de estado é feita por `UPDATE ... WHERE status = <estado anterior>`.
Só a thread cujo update afeta uma linha executa a entrega, o que torna a operação
idempotente entre threads, reinícios e retries.

---

## Compilação

```bash
mvn clean package
```

O artefato final é gerado em `target/ShPIX-<versão>.jar`.

---

## Licença

Distribuído sob a licença [MIT](LICENSE). Você pode usar, copiar, modificar,
mesclar, publicar, distribuir, sublicenciar e vender cópias do software, desde
que o aviso de copyright e o texto da licença sejam mantidos.

## Créditos

Baseado no [BRPayments](https://github.com/Bremado/BRPayments), de Bremado,
utilizado sob autorização do autor original.
