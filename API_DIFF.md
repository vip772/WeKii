# BeanShell Plugin API Compatibility

Generated during the `pl` runtime compatibility port. Baseline repositories:

- WeKii: `dev` at `b807f1be` before this local compatibility round
- pl: `origin/dev` at `b0e2ab8`

This is a compatibility inventory, not a promise that host-only capabilities are available. `same` means the script-visible name is available; `adapted` means the name is routed through a WeKii bridge; `unavailable` means the name is intentionally exposed as `null` or is not registered because the host has no equivalent.

## Runtime

| Area | pl | WeKii | Status |
|---|---|---|---|
| BeanShell engine | bundled patched sources, 147 source files | `libs/common/bsh` fork, 147-source boundary differs | adapted, fork retained |
| Class loader | host/module script bridge | `ClassLoaders.HYBRID` plus optional `ScriptDepsPack` | adapted |
| Default imports | Java IO/net/util/reflect/function, Android, JSON, host API packages | Java IO/net/math/regex/stream/util types, Android, JSON, host API packages | adapted |
| Callback classes | `PluginCallBack`, `HttpCallback`, `DownloadCallback` | same public package and explicit `MODULE` imports | same |
| `context`, `classLoader`, process flags | preset | preset | same |
| `bridge`, `wa`, `waBridge`, `http`, `httpClient`, `audio`, `audioBridge` | bridge objects | current namespace `This`, routed to existing methods | adapted |
| `apis`, `dexKit`, `dexFinder` | host-specific objects | explicit `null` | unavailable |

## Preset Variables

| Name | WeKii status | Notes |
|---|---|---|
| `context`, `hostContext` | same | application context |
| `classLoader`, `hostLoader` | adapted | hybrid/module or host loader |
| `isMainProcess`, `isAppBrandProcess`, `processName`, `pluginProcess` | same/adapted | WeKii currently runs script process as main |
| `startedAt` | adapted | `yyyy-MM-dd HH:mm:ss`, matching `pl` value shape |
| `FieldClass`, `MethodClass`, `ConstructorClass` | same | Java reflection classes |
| `ConsumerClass`, `FunctionClass` | same | Java functional interface classes |
| `JavaHookApiClass` | same | current hook implementation |
| `WeApiClass`, `WeMessageApiClass`, `WeContactApiClass`, `WeGroupApiClass`, `WeDatabaseApiClass`, `WeServiceApiClass` | same | current WeKii API classes |
| `apis`, `dexKit`, `dexKitBridge`, `dexFinder`, `dexBridgeHolder` | unavailable | no equivalent public runtime object in WeKii |
| `XposedBridgeClass`, `XposedHelpersClass`, `XC_MethodHookClass` | unavailable | Xposed classes are not part of this host runtime |

## Function Groups

The current WeKii namespace contains 146 script-visible functions. The `pl` bootstrap exposes 195 names. The current inventory is:

| Group | WeKii | Compatibility |
|---|---:|---|
| Lifecycle and callback registration | present | same/adapted |
| Reflection and Dex lookup (`firstMethod`, `firstField`, `findClassList`, `findMemberList`, etc.) | present | adapted to WeKii reflection/Dex helpers |
| HTTP GET/POST/download | present | adapted; legacy callbacks and `Consumer` callbacks supported |
| Audio conversion and metadata | present | adapted |
| Message/contact/group/payment APIs | present | adapted |
| File/config/dialog/menu APIs | present | adapted |
| Image/video/finder download callbacks | present in host bridge | adapted where exposed by current namespace |
| `downloadImage` | absent | top-level URL download with `Consumer<File?>` callback added |
| `downloadImages` | absent | list and list-plus-prefix URL download overloads added |
| `downloadVideo` | host bridge supports URL/message overloads | URL/file-name overloads adapted; native WeChat message decryption remains unavailable |
| `registerPlusMenu` | host bridge has native plus-menu dispatcher | callable degraded adapter registered in existing message-menu dispatcher; native plus UI unavailable |
| `hookBefore` / `hookAfter` | `JavaHookApi` exists | top-level aliases added and delegated to `JavaHookApi` |
| `sendProtobufPacket` | runtime implementation exists outside `JavaEngine` | all public overloads exposed; returns explicit unsupported result because WeKii has no transport runtime |
| `startTransform` | media implementation exists outside `JavaEngine` | types `0/1/5/6/9` adapted through available WeKii codecs; AAC/M4A/FLAC/OGG-only types report structured error |
| `uploadDeviceStep` | host service exists | adapted through `WeChatService.uploadDeviceStep` |

| SNS/Protobuf host-only operations | absent or host-specific | unavailable; no matching WeKii runtime implementation |

## Callback Signatures

The public callback contract is kept in `me.hd.wauxv.plugin.api.callback.PluginCallBack`:

- `HttpCallback.onSuccess(int statusCode, String response)`
- `HttpCallback.onError(Exception error)`
- `DownloadCallback.onSuccess(File file)`
- `DownloadCallback.onProgress(long current, long total)`
- `DownloadCallback.onError(Exception error)`

The legacy `any` callback overloads remain in `JavaEngine` for old scripts. New code should prefer the typed public interfaces or `java.util.function.Consumer` overloads.

## Remaining Work

1. Add a native WeKii Protobuf transport runtime before changing `sendProtobufPacket` from explicit unsupported results to real network dispatch.
2. Add a native plus-menu hook/dispatcher before changing `registerPlusMenu` from message-menu fallback to the WeChat input-bar UI.
3. Add native video-message metadata/decryption support before exposing `downloadVideo(Object message, ...)` with full `pl` semantics.
4. Expand `startTransform` only when the corresponding codec is present in WeKii; currently common Silk/MP3 paths are adapted and unsupported types report structured errors.
5. Keep the BeanShell fork as the base and add runtime smoke scripts for imports, callbacks, HTTP, reflection, menu fallback, media conversion, and unavailable-capability behavior.
