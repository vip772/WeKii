// WeKii BeanShell plugin example
// Available callbacks: onLoad(), onUnload(), onHandleMsg(MsgInfoBean)
void onLoad() {
    log("plugin loaded");
}

void onUnload() {
    log("plugin unloaded");
}

void onHandleMsg(MsgInfoBean msg) {
    // Uncomment to reply to text messages:
    // if (msg.msgType == 1) sendText(msg.talker, "收到：" + msg.content);
}
