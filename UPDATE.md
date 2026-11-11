# UPDATE.md — tarefas de publicação de versão

Leia **inteiro** antes de começar e não misture com outra tarefa.
Cada tarefa tem um critério de pronto. Pular qualquer uma = versão **não publicada**.

> Automação: `bash scripts/release.sh` executa T1–T9 (`--dry-run` valida T1–T5 e não publica).
> O procedimento manual abaixo continua valendo como referência do que o script faz.

**Pré-requisitos da máquina:** `gh` autenticado (`gh auth status`), JDK 17+, Android SDK 35,
`aapt2` e `apksigner` no PATH. No Termux: `pkg install aapt2 openjdk-21 gh`.

## Política de versão

- `versionName` no padrão **MAJOR.MINOR.PATCH** (SemVer).
- `versionCode` **sempre +1** — o Android recusa a atualização se não aumentar.
- Commit da release: `release: X.Y.Z (versionCode N)`; tag anotada `vX.Y.Z`.
- Proibido `git push --force` e proibido `--no-verify`. Gate vermelho = não publica.

---

## T1 — Repositório limpo e sincronizado

```bash
git fetch origin
git status --short --branch
git rev-parse HEAD origin/main
```

**Pronto quando:** sem alterações pendentes, branch `main` e `HEAD` = `origin/main`.
Havendo trabalho não commitado: commitar antes (não misturar com a release).

## T2 — Testes unitários verdes

```bash
bash gradlew :app:testDebugUnitTest --no-daemon
grep -h -oE '(failures|errors)="[0-9]+"' app/build/test-results/testDebugUnitTest/*.xml | sort -u
```

**Pronto quando:** todos os relatórios com `failures="0"` e `errors="0"` (hoje: 24 testes).

## T3 — Incrementar a versão

Em `app/build.gradle.kts`, no bloco `defaultConfig`:

```kotlin
versionCode = 2          // sempre +1 em relação à última versão publicada
versionName = "1.0.1"    // MAJOR.MINOR.PATCH
```

**Pronto quando:** os dois campos conferem com a versão a publicar.

## T4 — Build de release (R8 ligado)

```bash
bash gradlew :app:assembleRelease --no-daemon
mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk dist/TempMonitor-X.Y.Z.apk
```

**Pronto quando:** `dist/TempMonitor-X.Y.Z.apk` existe. Release com `isMinifyEnabled` +
`isShrinkResources` fica em torno de 2 MB (o debug passa de 17 MB).

## T5 — Conferir o APK gerado

```bash
APK=dist/TempMonitor-X.Y.Z.apk
aapt2 dump badging "$APK" | head -1
apksigner verify --print-certs "$APK"
```

**Pronto quando:** `versionCode`/`versionName` batem com o T3, a assinatura valida e o
certificado é o esperado (chave de release ou, sem keystore, a `Android Debug`).

## T6 — Commit e push

```bash
git add -A
git commit -m "release: X.Y.Z (versionCode N)"
git push origin main
```

**Pronto quando:** `git rev-parse origin/main` aponta para o commit da release.

## T7 — Tag da versão

```bash
git tag -a vX.Y.Z -m "TempMonitor X.Y.Z (versionCode N)"
git push origin vX.Y.Z
```

**Pronto quando:** `git ls-remote --tags origin` lista `refs/tags/vX.Y.Z`.

## T8 — Release **com o APK anexado** (obrigatório)

O `dist/` é ignorado pelo Git: **sem a release, o APK não chega a ninguém.**

```bash
gh release create vX.Y.Z dist/TempMonitor-X.Y.Z.apk \
  --repo spigknot/TempMonitor \
  --title "TempMonitor X.Y.Z" \
  --notes "- item 1
- item 2"
```

**Pronto quando:** `gh release view vX.Y.Z --json assets` mostra o `.apk` com o mesmo
tamanho do arquivo local.

## T9 — Conferir como usuário (sem autenticação)

```bash
curl -sSIL https://github.com/spigknot/TempMonitor/releases/download/vX.Y.Z/TempMonitor-X.Y.Z.apk | head -n 5
```

**Pronto quando:** o **último** status for `HTTP 200` (o primeiro é `302` do github.com
para o CDN — por isso o `-L`) e `content-length` igual ao tamanho do APK gerado.

---

## Assinatura de release

Sem `keystore.properties` na raiz, a release é assinada com a **chave de debug** — instala
normalmente, mas **não serve para publicar em loja** e quebra se você trocar de chave depois.

Para assinar com chave própria (arquivo fica fora do Git):

```bash
keytool -genkeypair -v -keystore tempmonitor.jks -alias tempmonitor \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties` (já coberto pelo `.gitignore`):

```properties
storeFile=tempmonitor.jks
storePassword=...
keyAlias=tempmonitor
keyPassword=...
```

O Gradle detecta o arquivo sozinho e passa a assinar com ele. **Trocar de chave exige
desinstalar** o app em quem já tem a versão anterior.

---

## Definição de pronto

- [ ] T1 repositório limpo e `main` = `origin/main`
- [ ] T2 testes unitários verdes (0 falhas, 0 erros)
- [ ] T3 `versionCode` incrementado e `versionName` correto
- [ ] T4 APK de release gerado em `dist/`
- [ ] T5 versão e assinatura conferidas com `aapt2`/`apksigner`
- [ ] T6 commit `release: X.Y.Z` enviado para `main`
- [ ] T7 tag `vX.Y.Z` publicada
- [ ] T8 release criada **com o APK anexado**
- [ ] T9 download público respondendo 200 com o tamanho certo

Faltando qualquer item, a versão **não** está publicada — mesmo que o commit exista.
