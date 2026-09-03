# Java BeanShell 插件格式

将插件目录复制到 `/storage/emulated/0/Android/media/<微信包名>/<模块标签>/plugins/<id>/`，目录至少包含：

- `info.prop`：插件元数据；
- `main.java`：BeanShell 脚本。

支持回调：

```java
void onLoad();
void onUnload();
void onHandleMsg(MsgInfoBean msg);
void onClickSendBtn(String text);
```

插件管理页的开关通过 `disabled.flag` 持久化。脚本异常会被隔离并写入 WeKii 日志。
