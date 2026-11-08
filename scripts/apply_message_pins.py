from pathlib import Path

p=Path("app/src/main/java/com/chatapp/mobile/MainActivity.java")
s=p.read_text(encoding="utf-8")

def once(old,new):
    global s
    if new in s: return
    if old not in s: raise SystemExit("Missing pin feature anchor")
    s=s.replace(old,new,1)

once('    private boolean inChat = false;','    private boolean inChat = false;\n    private boolean currentConversationIsGroup = false;')
once('        currentConversationId=item.id; currentConversationTitle=item.title; inChat=true;','        currentConversationId=item.id; currentConversationTitle=item.title; currentConversationIsGroup=item.group; inChat=true;')
once('message_reactions(emoji,user_id)&conversation_id=eq.','message_reactions(emoji,user_id),message_pins(pinned_by)&conversation_id=eq.')
once('        if(mine){items.add("Edit message");items.add("Delete message");}','        if(currentConversationIsGroup)items.add("Pin / unpin message");\n        if(mine){items.add("Edit message");items.add("Delete message");}')
once('            else if("Forward".equals(action)){showForwardTargets(msg);}',
'''            else if("Forward".equals(action)){showForwardTargets(msg);}
            else if("Pin / unpin message".equals(action)){toggleMessagePin(id);}''')
once('    private void showForwardTargets(JSONObject source){',
'''    private void toggleMessagePin(String messageId){
        io.execute(()->{try{
            String filter="/rest/v1/message_pins?select=message_id&message_id=eq."+SupabaseClient.enc(messageId);
            JSONArray found=SupabaseClient.getArray(filter,token());
            if(found.length()>0)SupabaseClient.delete("/rest/v1/message_pins?message_id=eq."+SupabaseClient.enc(messageId),token());
            else {JSONObject pin=new JSONObject();pin.put("message_id",messageId);pin.put("conversation_id",currentConversationId);pin.put("pinned_by",uid());SupabaseClient.post("/rest/v1/message_pins",token(),pin);}
            runOnUiThread(()->{message(found.length()>0?"Message unpinned.":"Message pinned.");loadMessages(false);});
        }catch(Exception e){runOnUiThread(()->message("Pin action failed: "+e.getMessage()));}});
    }
    private void showForwardTargets(JSONObject source){''')
p.write_text(s,encoding="utf-8")
