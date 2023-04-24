def response(room, msg, sender, replier, msg_json, db):
    print(msg_json)
    if msg == "!hi":
        replier.reply("hello")