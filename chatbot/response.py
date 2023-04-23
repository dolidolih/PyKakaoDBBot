def response(room, msg, sender, replier, msg_json):
    print(msg)
    if msg == "!hi":
        replier.reply("hello")