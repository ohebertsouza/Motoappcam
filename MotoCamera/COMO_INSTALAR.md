# Como gerar e instalar o MotoCamera no seu Moto Edge 30 Neo

## Passo 1 — Crie uma conta no GitHub (grátis)
1. Acesse https://github.com e crie uma conta
2. Clique em **New repository** (botão verde)
3. Nome: `motocamera`
4. Visibilidade: **Private** (recomendado)
5. Clique em **Create repository**

## Passo 2 — Suba os arquivos
1. No repositório criado, clique em **uploading an existing file**
2. Arraste a pasta `MotoCamera` inteira ou suba arquivo por arquivo
   - Mantenha a estrutura exata de pastas
3. Clique em **Commit changes**

## Passo 3 — O GitHub compila automaticamente
1. Vá na aba **Actions** do repositório
2. Você verá um build rodando chamado "Build APK"
3. Aguarde ~5-10 minutos (primeira vez baixa dependências)
4. Quando aparecer ✅ verde, clique nele
5. Role até **Artifacts** e clique em **MotoCamera-APK**
6. Baixe o `.zip` — dentro está o `app-release.apk`

## Passo 4 — Instale no Moto Edge 30 Neo
1. No celular: **Configurações → Sobre o telefone → Número da versão** (toque 7x para ativar Modo Dev)
2. **Configurações → Sistema → Opções do desenvolvedor → Depuração USB** — ative
3. Transfira o `app-release.apk` para o celular (cabo USB ou Google Drive)
4. No gerenciador de arquivos, toque no APK
5. Permita **Instalar de fontes desconhecidas** quando solicitado
6. Instale e abra o **MotoCamera**!

---

## Recursos do app

| Modo | O que faz |
|------|-----------|
| **AUTO** | Câmera inteligente padrão com ajuste automático |
| **NOITE** | Empilha 6 frames, alinha e faz média para reduzir ruído |
| **HDR** | Fusão de 3 exposições para alcançar mais detalhe em luz e sombra |
| **RETRATO** | ML Kit segmenta o sujeito, aplica blur progressivo no fundo |

## Pad de Estilos 2.0

- Toque no ícone **✦ Estilo** no canto inferior direito
- **Eixo horizontal**: Tom (frio/teal ↔ quente/dourado)
- **Eixo vertical**: Humor (apagado/fílmico ↔ vívido/saturado)
- Use os **presets** na fileira acima: Standard, Rich Contrast, Vibrant, Warm, Cool, Muted, Cinematic, Golden Hour
- O estilo é aplicado na captura — toque no preview para ver o resultado

## Onde as fotos são salvas
As fotos ficam em **Galeria → MotoCamera** (pasta dentro de Imagens)
