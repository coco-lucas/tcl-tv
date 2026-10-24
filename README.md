# tcl-tv

Personalização de uma TCL C7K (Google TV 14) pra ficar parecida com uma Apple TV: limpeza do sistema via ADB, tela inicial própria com dock de vidro, painel com clima e cotações, painel rápido estilo Central de Controle e os botões do controle funcionando sem a tela inicial do Google.

Tudo é feito por ADB pelo Wi‑Fi, sem root, e é reversível: cada mudança no sistema está em [`tv-changes.txt`](tv-changes.txt) com o comando pra desfazer.

## Estrutura

| Pasta | O que tem |
| --- | --- |
| `tv-detox/` | A tela inicial (app Android em Java puro, sem dependências). Fork do [tv-detox](https://github.com/Ansub/tv-detox) |
| `wall/` | Fundos da tela inicial + `names.txt` (nomes exibidos) |
| `ss/` | Fotos do screensaver noturno + `captions.txt` |
| `icons/` | Ícones Lucide (SVG e PNG) |
| `logos/` | Logos dos apps do dock, em branco |
| `fonts/` | Inter (fonte do sistema) |
| `tv-changes.txt` | Registro de tudo que mudou na TV e como desfazer |

### O app (`tv-detox/src/com/ansub/tvhome/`)

| Arquivo | Função |
| --- | --- |
| `MainActivity` | Tela inicial: dock, ícones do topo, painel de entradas e atalhos |
| `Glass` | Vidro fosco (desfoque calculado uma vez), cantos squircle e borda |
| `Grid` | Telas cheias de "Todos os apps", "Plano de fundo" e "Adicionar ao dock" |
| `InfoCards` | Clima (Open‑Meteo) e cotações (Binance + AwesomeAPI) |
| `QuickActivity` | Painel rápido que desliza da direita, por cima de qualquer app |
| `RemoteKeys` | Serviço de acessibilidade que dá destino aos botões do controle |
| `Open` | Abrir apps, entradas HDMI e telas de ajustes |
| `Previews` | Destaque ao focar um app: em alta via TMDB e imagem ao vivo da HDMI |
| `Screensaver` | Screensaver de fotos noturnas (instalado, mas desligado) |

## Como instalar

Requisitos no Mac: `brew install android-platform-tools openjdk@17 librsvg` e `brew install --cask android-commandlinetools`, depois `sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

Na TV: ative as Opções do desenvolvedor e a depuração USB/rede.

```sh
adb connect <IP_DA_TV>:5555
export ANDROID_SERIAL=<IP_DA_TV>:5555

cd tv-detox
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools sh build.sh
sh install.sh $ANDROID_SERIAL "com.netflix.ninja,com.google.android.youtube.tv,com.amazon.amazonvideo.livingroom,com.disney.disneyplus" "bluetooth"
cd ..

D=/sdcard/Android/data/com.ansub.tvhome/files
adb shell mkdir -p $D/logos $D/icons $D/fonts $D/wallpapers $D/screensaver
adb push logos/com.*.png $D/logos/
adb push icons/*.png $D/icons/
adb push fonts/Inter.ttf $D/fonts/
adb push wall/*.jpg wall/names.txt $D/wallpapers/
adb push ss/*.jpg ss/captions.txt $D/screensaver/
adb shell cmd package compile -m speed -f com.ansub.tvhome
```

Botões do controle e início automático (o bloqueador da TCL impede o serviço de religar sem isso):

```sh
adb shell appops set com.ansub.tvhome AUTO_START allow
adb shell settings put secure enabled_accessibility_services com.ansub.tvhome/com.ansub.tvhome.RemoteKeys
adb shell settings put secure accessibility_enabled 1
```

Previews ao focar um app no dock:

```sh
adb push inputs.txt $D/              # nome do aparelho por entrada (ex.: HW16=Xbox) e logo em logos/<nome>.png
adb push <arquivo-com-token> $D/tmdb.txt   # token ou chave da TMDB; fica só na TV
```

Netflix, Prime Video e Disney+ mostram o que está em alta no Brasil via TMDB; a entrada HDMI mostra a imagem ao vivo do aparelho.

A foto do perfil (pílula do topo) é um `profile.png` em `$D/`, e não fica no repositório.

A primeira compilação cria `tv-detox/key.jks`, a chave que assina o app. Guarde ela fora do repositório: as atualizações precisam ser assinadas com a mesma chave.

## Botões do controle

| Botão | Ação |
| --- | --- |
| Configurações rápidas (`4514`) | Painel rápido |
| Engrenagem (`KEYCODE_NOTIFICATION`) | Ajustes da TV |
| Canais / Guia (`4001`) | TV ao vivo; dentro dela, o guia |
| Canal +/− na tela inicial | TV ao vivo |
| Netflix, Prime Video, YouTube, Disney+, Globoplay | Abrem o app de qualquer tela |

Os códigos vêm de `/tclconfig/tvcust/aio_resources/resource/common_overseas/input_key/shortcutkey.tkl` na TV.

## Créditos e licenças

- Tela inicial baseada no [tv-detox](https://github.com/Ansub/tv-detox), de Ansub Khan (MIT, ver `tv-detox/LICENSE`).
- Ícones: [Lucide](https://lucide.dev) (ISC).
- Fonte: [Inter](https://rsms.me/inter/) (SIL Open Font License).
- Logos: [theSVG](https://thesvg.org). As marcas pertencem aos seus donos.
- Fotos: Wikimedia Commons e NASA, com licença e origem de cada uma em `wall/CREDITS.tsv` e `ss/CREDITS.tsv`.
