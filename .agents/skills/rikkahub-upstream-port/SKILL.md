---
name: rikkahub-upstream-port
description: Standardize upstream tracking for this RikkaHub fork. Use when syncing upstream updates into this repo, creating port-* migration branches, resolving conflicts with upstream-first plus mod-feature backfill, verifying web/static/runtime integrity, building release APKs, and publishing GitHub releases.
---

# RikkaHub Upstream Port

Use this skill to execute a repeatable, low-risk upstream merge for this repository.

## Branch Roles

- Keep `main` as upstream clean mirror only.
- Keep `master` as the only public mod mainline.
- Use `port-*` as temporary migration branches.
- Keep archive/experiment branches read-only unless explicitly requested.

## Safety Rules

- Stop and ask if unrelated unexpected changes appear.
- Never force-push `main` or `master`.
- Never resolve conflicts by deleting logic only to pass compilation.
- Keep upstream structure whenever mod behavior does not require divergence.

## Step 1: Sync Upstream Mirror

Run:

```bash
git fetch upstream --prune --tags
git switch main
git merge --ff-only upstream/master
git push origin main
```

Record upstream version from tag or commit:

```bash
git describe --tags --abbrev=0 upstream/master
git rev-parse --short upstream/master
```

## Step 2: Create Port Branch from Master

Run:

```bash
git switch master
git switch -c port-upstream-<yyyymmdd>-<version>
git merge main
```

Use a clear branch name, e.g. `port-upstream-20260305-2.1.0`.

## Step 3: Resolve Conflicts with Fixed Policy

Prefer upstream for:

- General UI refactors and icons
- Navigation/page structure updates
- Non-mod provider updates
- Web API/static framework changes from upstream

Backfill mod features for:

- App coexistence strategy (`applicationId`, signing behavior, target sdk policy)
- Firebase-disabled behavior
- Container/proot runtime and sandbox integration
- Workflow entry/toggle/overlay behavior
- Chaquopy local tool integration
- Tavily key-rotation and custom search strategy

Inspect high-risk files every merge:

- `app/build.gradle.kts`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSearchPage.kt`
- `search/src/main/java/me/rerere/search/SearchService.kt`
- `search/src/main/java/me/rerere/search/TavilySearchService.kt`
- `app/src/main/java/me/rerere/rikkahub/service/WebServerService.kt`
- `app/src/main/java/me/rerere/rikkahub/web/WebServerManager.kt`

Confirm no conflict markers remain:

```bash
rg -n "^(<<<<<<<|=======|>>>>>>>)" -S .
```

## Step 4: Validate Before Merge Back

Compile with constrained memory:

```bash
GRADLE_OPTS="-Xmx2g -XX:MaxMetaspaceSize=768m -Dkotlin.daemon.jvm.options=-Xmx1536m" ./gradlew :app:compileDebugKotlin --no-daemon --console=plain
```

Build release with current mod policy (targetSdk 28):

```bash
GRADLE_OPTS="-Xmx2g -XX:MaxMetaspaceSize=768m -Dkotlin.daemon.jvm.options=-Xmx1536m" ./gradlew :app:assembleRelease -x lintVitalRelease --no-daemon --console=plain
```

Verify web static assets are packaged:

- Ensure `:app:buildWebUiClient` and `:app:syncWebUiStatic` are executed.
- Confirm `web/src/main/resources/static/index.html` exists.

## Step 5: Merge Port Branch Back to Master

Commit on `port-*`:

```bash
git commit -m "merge: sync upstream <version> into master line"
```

Fast-forward master:

```bash
git switch master
git merge --ff-only port-upstream-<yyyymmdd>-<version>
git push origin master
```

Delete the finished port branch after master is updated:

```bash
git branch -d port-upstream-<yyyymmdd>-<version>
```

## Step 6: Publish Release

Use upstream version tag format directly (no custom prefix/suffix).
Only build and publish the `arm64-v8a` APK. Do not keep or upload `x86_64` or universal APKs.

Example:

```bash
gh release create 2.1.0 app/build/outputs/apk/release/app-arm64-v8a-release.apk#rikkahub-2.1.0-arm64.apk --repo yuxinjiang218-creator/rikkahub --target master --title "2.1.0" --notes "RikkaHub Mod 2.1.0 release (arm64)."
```

Verify:

- Release is not draft.
- Release is not prerelease.
- Asset exists and is downloadable.

## Non-Blocking Gradle Monitor Pattern

Use background process and tail logs instead of blocking:

```powershell
$p = Start-Process cmd.exe -ArgumentList '/c','gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain' -RedirectStandardOutput tmp-gradle.log -RedirectStandardError tmp-gradle.err -PassThru
```

Poll every few seconds, print log tail, and stop on timeout if needed.

## Done Criteria

- `main` == `upstream/master`
- `master` contains merged upstream + preserved mod features
- `:app:compileDebugKotlin` succeeds
- `:app:assembleRelease -x lintVitalRelease` succeeds
- Web static is present and not regressed
- `origin/master` pushed
- Finished `port-*` branch deleted locally
- Release published with correct tag and asset
