# BeanShell Plugin API Compatibility

Generated during the `pl` runtime compatibility port. Baseline repositories:

- WeKii: `dev` at `3c08eedd`
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

The current WeKii namespace contains 124 script-visible functions. The `pl` bootstrap exposes 196 names. The current inventory is:

| Group | WeKii | Compatibility |
|---|---:|---|
| Lifecycle and callback registration | present | same/adapted |
| Reflection and Dex lookup (`firstMethod`, `firstField`, `findClassList`, `findMemberList`, etc.) | present | adapted to WeKii reflection/Dex helpers |
| HTTP GET/POST/download | present | adapted; legacy callbacks and `Consumer` callbacks supported |
| Audio conversion and metadata | present | adapted |
| Message/contact/group/payment APIs | present | adapted |
| File/config/dialog/menu APIs | present | adapted |
| Image/video/finder download callbacks | present in host bridge | adapted where exposed by current namespace |
| `downloadImage`, `downloadVideo` pl-style top-level aliases | not independently registered | use `wa`/`audio` bridge methods or existing download APIs |
| `registerPlusMenu` | host bridge exists, top-level alias absent | adapted only through current menu API; requires signature audit |
| `hookBefore` / `hookAfter` | `JavaHookApi` exists | class-level API available; top-level alias requires script callback signature audit |
| `sendProtobufPacket` | runtime implementation exists outside `JavaEngine` | not yet exposed as a top-level JavaEngine function |
| `startTransform` | media implementation exists outside `JavaEngine` | not yet exposed as a top-level JavaEngine function |
| `uploadDeviceStep` and SNS/Protobuf host-only operations | absent or host-specific | unavailable unless a matching WeKii API is added |

## Callback Signatures

The public callback contract is kept in `me.hd.wauxv.plugin.api.callback.PluginCallBack`:

- `HttpCallback.onSuccess(int statusCode, String response)`
- `HttpCallback.onError(Exception error)`
- `DownloadCallback.onSuccess(File file)`
- `DownloadCallback.onProgress(long current, long total)`
- `DownloadCallback.onError(Exception error)`

The legacy `any` callback overloads remain in `JavaEngine` for old scripts. New code should prefer the typed public interfaces or `java.util.function.Consumer` overloads.

## Remaining Work

1. Add typed top-level aliases only after matching each `pl` method signature to the existing WeKii bridge.
2. Expose Protobuf/media transform functions only through their existing WeKii lifecycle and thread-safety contracts.
3. Keep the BeanShell fork as the base and port individual parser/preprocessor patches with tests; do not replace the fork wholesale.
4. Add runtime smoke scripts for class imports, callback construction, object-style HTTP, reflection, menu registration, and unavailable-capability behavior.
