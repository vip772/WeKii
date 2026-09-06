# Public BeanShell plugin API names are part of the external script ABI.
-keep class me.hd.wauxv.plugin.api.callback.** { *; }

# registerMessageMenu/registerPlusMenu callbacks expose MessageInfo to scripts.
# PL-compatible scripts resolve getTalker/getContent/getText by reflection, so
# the wrapper class and its Java bean accessors must retain their names in release.
-keep class dev.ujhhgtg.wekit.features.api.core.models.MessageInfo { *; }
