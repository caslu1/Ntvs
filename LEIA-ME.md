# NovaTV — app IPTV para celular, Fire TV, Mi Box e Android TV

Player IPTV (login Xtream Codes ou lista M3U) com o mesmo layout das telas de referência:

- **Início** — usuário, vencimento, banner rotativo de destaques, "Continuar assistindo" e lançamentos
- **Ao vivo** — Buscar, Favoritos, Tudo A-Z, categorias do painel, canais com programa atual e guia (EPG) por dia
- **Filmes e Séries** — Destaques, categorias, grade de capas, busca, favoritos, tela de detalhes com temporadas/episódios
- **Player** — ExoPlayer, troca de canal com ▲▼ (controle ou toque), barra LIVE / NEXT com progresso, retomar filme/episódio de onde parou
- **Histórico** — filmes, séries e canais assistidos
- **Configurações** — formato TS/HLS, player interno/externo, ordenação, abrir ao ligar, controle dos pais (PIN), limpar cache, desconectar

Um único APK funciona em celular (toque) e TV (controle remoto), e aparece na tela inicial de Android TV / Fire TV.

---

## Gerar o APK (grátis, sem instalar nada) — GitHub

1. Crie uma conta em github.com e clique em **New repository** (pode ser privado).
2. Na página do repositório: **Add file → Upload files** e arraste **todo o conteúdo** desta pasta (as pastas `app`, `gradle` e os arquivos `gradlew`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`). Clique em **Commit changes**.
3. O upload pelo navegador às vezes ignora a pasta oculta `.github`. Se não aparecer na lista, faça: **Add file → Create new file**, nome `.github/workflows/build.yml`, cole o conteúdo do arquivo `build.yml` incluído e salve.
4. Abra a aba **Actions** — a compilação começa sozinha (~5 a 8 min). Se pedir, clique em "I understand… enable".
5. Quando ficar verde ✅, vá em **Releases** (lado direito da página do repositório) e baixe o **NovaTV.apk**.

Cada vez que você alterar algo e salvar, um APK novo é gerado.

## Ou: Android Studio (no PC)

Abra a pasta no Android Studio → espere sincronizar → **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

---

## Instalar

- **Celular:** abra o APK e permita "instalar de fontes desconhecidas".
- **Fire TV / Mi Box / Android TV:** instale o app **Downloader** (by AFTVnews), ative *Configurações → Minha Fire TV → Opções do desenvolvedor → Instalar apps desconhecidos → Downloader*, e baixe o APK pelo link direto do Releases. Na Mi Box você também pode usar pendrive + gerenciador de arquivos ou "Send Files to TV".

## Usar

A tela de entrada tem duas abas:

- **Xtream Codes:** servidor (ex: `http://dominio.com:8080`), usuário e senha.
- **Lista M3U:** cole o link da lista. Se for um link de painel (`get.php?username=...&password=...`), o app entra automaticamente no modo Xtream, que é mais rápido e traz guia de programação. Qualquer outra lista M3U também funciona: canais, filmes (links .mp4/.mkv ou `/movie/`) e séries (nomes com `S01E02` ou `/series/`) são separados sozinhos pelas categorias (`group-title`). Listas M3U comuns não têm guia de programação.
No ao vivo: segurar OK (TV) ou pressionar longo (celular) em um canal adiciona aos favoritos. No celular, toque 1x no canal para ver o guia e toque de novo para assistir.

---

## Personalizar sua marca

| O quê | Onde |
|---|---|
| Nome do app | `app/src/main/res/values/strings.xml` |
| Cores (vermelho do destaque, roxo do fundo) | `app/src/main/res/values/colors.xml` |
| Ícone e logo | `app/src/main/res/mipmap-*/ic_launcher.png` e `drawable-xhdpi/logo.png` |
| Banner da TV (320×180) | `app/src/main/res/drawable-xhdpi/tv_banner.png` |
| Servidor fixo (cliente só digita usuário e senha) | `DEFAULT_SERVER` em `app/build.gradle.kts` |
| Banners "Destaques do dia" (ex: arte dos jogos) | `BANNERS_URL` em `app/build.gradle.kts` → link de um JSON como `["https://site/jogos1.jpg","https://site/jogos2.jpg"]`. Trocando as imagens no seu servidor, o app atualiza sozinho. |
| ID do app (para publicar várias marcas) | `applicationId` em `app/build.gradle.kts` |

**Assinatura:** o projeto já vem com a chave `app/novatv.keystore` (senha `novatv123`). Para uso profissional, gere a sua e troque no `app/build.gradle.kts` — e guarde-a, porque atualizações do app precisam da mesma chave.
