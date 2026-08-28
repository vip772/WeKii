# 群聊分析

## 功能入口

功能位于设置的“聊天”分类，默认关闭。启用后，长按群聊中的任意消息可选择“群聊分析”。功能设置页用于配置 OpenAI 兼容接口、API Key、模型、统计天数和提取消息上限。

## 数据口径

- 总人数：复用 `WeDatabaseApi.getGroupMembers()`，读取经现有公共 API 解析的 `chatroom.memberlist`。
- 发言人数：在指定天数内，从 `message` 表选择已确认存在的 `content`、`isSend`、`createTime` 字段，仅统计文本消息（`type = 1`）；自己使用当前账号 ID，群成员从消息内容前缀解析，并按发送者去重。
- 活跃度：`发言人数 / 总人数 × 100%`。
- 发言排行：按发送者统计文本消息数，显示前十名。
- 消息数：指定时段内参与本次文本分析的消息总数。

数据库不可用或 SQL 失败时不把结果当作空数据，功能会显示读取失败并记录错误日志。

## AI 总结

聊天记录按时间升序拼接，超出配置上限时在整个时段内均匀抽样。请求使用 OpenAI Chat Completions 兼容格式：

- `POST` 用户配置的接口地址；
- `Authorization: Bearer <API Key>`；
- 请求字段为 `model`、`temperature`、`messages`；
- 当前版本使用非流式 JSON 响应，读取 `choices[0].message.content`。

API Key 仅保存在模块偏好配置中，请用户自行选择可信服务。

## 逆向依据

实现口径来自用户提供的 `me.yun.fkwechat` 1.2.6 APK 静态逆向结果：关键类为 `x6/f2`、`x6/s1`、`x6/n1`、`x6/u1`、`x6/d2`、`x6/z0` 和 `l6/b`。本项目实现复用自身已有的 `WeDatabaseApi`，不复制附件中的混淆代码或微信 Hook 定位。
