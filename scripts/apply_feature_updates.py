from pathlib import Path

def replace_once(path, old, new):
    p = Path(path)
    source = p.read_text(encoding="utf-8")
    if new in source:
        return
    if old not in source:
        raise SystemExit("Expected source section not found: " + path)
    p.write_text(source.replace(old, new, 1), encoding="utf-8")

main = "app/src/main/java/com/chatapp/mobile/MainActivity.java"
community = "app/src/main/java/com/chatapp/mobile/CommunityActivity.java"

replace_once(main,
'''        Button save=button("Save profile");LinearLayout.LayoutParams sp=match(dp(50));sp.topMargin=dp(12);content.addView(save,sp);''',
'''        Button save=button("Save profile");LinearLayout.LayoutParams sp=match(dp(50));sp.topMargin=dp(12);content.addView(save,sp);
        Button changePassword=button("Change password");LinearLayout.LayoutParams cp=match(dp(48));cp.topMargin=dp(8);content.addView(changePassword,cp);
        Button resetPassword=button("Send password reset email");LinearLayout.LayoutParams rp=match(dp(48));rp.topMargin=dp(6);content.addView(resetPassword,rp);
        changePassword.setOnClickListener(v->showChangePasswordDialog());
        resetPassword.setOnClickListener(v->sendPasswordResetEmail());''')

replace_once(main,
'''    private void showMessageActions(JSONObject msg) {''',
'''    private void showChangePasswordDialog(){
        EditText password=field("New password (at least 8 characters)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm=field("Confirm new password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(password,match(dp(54)));box.addView(confirm,match(dp(54)));
        new AlertDialog.Builder(this).setTitle("Change password").setView(box).setPositiveButton("Update",(d,w)->{
            String a=password.getText().toString(),b=confirm.getText().toString();
            if(a.length()<8){message("Password must contain at least 8 characters.");return;}
            if(!a.equals(b)){message("Passwords do not match.");return;}
            io.execute(()->{try{JSONObject body=new JSONObject();body.put("password",a);SupabaseClient.request("PUT","/auth/v1/user",token(),body,null);runOnUiThread(()->message("Password changed successfully."));}catch(Exception e){runOnUiThread(()->message("Password change failed: "+e.getMessage()));}});
        }).setNegativeButton("Cancel",null).show();
    }
    private void sendPasswordResetEmail(){
        new AlertDialog.Builder(this).setTitle("Send password reset email?").setMessage("A secure reset link will be sent to "+ChatSession.email(this)+" if the email is registered.").setPositiveButton("Send",(d,w)->io.execute(()->{try{
            JSONObject body=new JSONObject();body.put("email",ChatSession.email(this));
            SupabaseClient.request("POST","/auth/v1/recover",SupabaseConfig.PUBLISHABLE_KEY,body,null);
            runOnUiThread(()->message("If the address is registered, a password reset email has been sent."));
        }catch(Exception e){runOnUiThread(()->message("Could not send reset email: "+e.getMessage()));}})).setNegativeButton("Cancel",null).show();
    }

    private void showMessageActions(JSONObject msg) {''')

replace_once(main,
'''        items.add("Reply"); items.add("Copy"); items.add("React 👍");''',
'''        items.add("Reply"); items.add("Copy"); items.add("Forward"); items.add("React 👍");''')

replace_once(main,
'''            else if("React 👍".equals(action)){io.execute(()->{try{JSONObject r=new JSONObject();r.put("message_id",id);r.put("user_id",uid());r.put("emoji","👍");SupabaseClient.upsert("/rest/v1/message_reactions?on_conflict=message_id,user_id,emoji",token(),r);runOnUiThread(()->message("Reaction added."));}catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});}''',
'''            else if("Forward".equals(action)){showForwardTargets(msg);}
            else if("React 👍".equals(action)){io.execute(()->{try{JSONObject r=new JSONObject();r.put("message_id",id);r.put("user_id",uid());r.put("emoji","👍");SupabaseClient.upsert("/rest/v1/message_reactions?on_conflict=message_id,user_id,emoji",token(),r);runOnUiThread(()->message("Reaction added."));}catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});}''')

replace_once(main,
'''    private void showUserActions(String userId,String name) {''',
'''    private void showForwardTargets(JSONObject source){
        io.execute(()->{try{
            JSONArray rows=SupabaseClient.getArray("/rest/v1/conversation_members?select=conversation_id,conversations(id,title,is_group)&user_id=eq."+SupabaseClient.enc(uid()),token());
            ArrayList<String> labels=new ArrayList<>();ArrayList<String> ids=new ArrayList<>();
            for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i),c=row==null?null:row.optJSONObject("conversations");if(c==null)continue;String cid=c.optString("id");if(cid.isEmpty()||cid.equals(currentConversationId))continue;ids.add(cid);labels.add(c.optString("title",c.optBoolean("is_group")?"Group":"Chat"));}
            runOnUiThread(()->{if(labels.isEmpty()){message("No other chats available to forward to.");return;}new AlertDialog.Builder(this).setTitle("Forward to").setItems(labels.toArray(new String[0]),(d,w)->io.execute(()->{try{
                JSONObject forwarded=new JSONObject();forwarded.put("conversation_id",ids.get(w));forwarded.put("sender_id",uid());forwarded.put("body",source.optString("body",""));forwarded.put("forwarded_from_message_id",source.optString("id"));
                String path=source.optString("attachment_path","");if(!path.isEmpty()){forwarded.put("attachment_path",path);forwarded.put("attachment_type",source.optString("attachment_type","file"));}
                SupabaseClient.post("/rest/v1/messages",token(),forwarded);runOnUiThread(()->message("Message forwarded."));
            }catch(Exception e){runOnUiThread(()->message("Forward failed: "+e.getMessage()));}})).setNegativeButton("Cancel",null).show();});
        }catch(Exception e){runOnUiThread(()->message("Could not load chats: "+e.getMessage()));}});
    }
    private void showUserActions(String userId,String name) {''')

replace_once(main,
'''bubbleWrap.addView(bubble);bubbleWrap.addView(status);''',
'''                bubbleWrap.addView(bubble);bubbleWrap.addView(status);
                String reactionText=reactionSummary(m.optJSONArray("message_reactions"));if(!reactionText.isEmpty()){TextView reactions=text(reactionText,11,0xFFB7D7F3,false);reactions.setPadding(dp(6),dp(2),dp(6),dp(2));bubbleWrap.addView(reactions);}''')

replace_once(main,
'''                receivedWrap.addView(bubble,new LinearLayout.LayoutParams(-2,-2));''',
'''                receivedWrap.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
                String reactionText=reactionSummary(m.optJSONArray("message_reactions"));if(!reactionText.isEmpty()){TextView reactions=text(reactionText,11,0xFFB7D7F3,false);reactions.setPadding(dp(6),dp(2),dp(6),dp(2));receivedWrap.addView(reactions);}''')

replace_once(main,
'''    private void translateMessage(String original,TextView output,Runnable finished) {''',
'''    private String reactionSummary(JSONArray reactions){
        if(reactions==null||reactions.length()==0)return "";
        java.util.LinkedHashMap<String,Integer> counts=new java.util.LinkedHashMap<>();
        for(int i=0;i<reactions.length();i++){JSONObject r=reactions.optJSONObject(i);if(r==null)continue;String emoji=r.optString("emoji","");if(!emoji.isEmpty())counts.put(emoji,counts.containsKey(emoji)?counts.get(emoji)+1:1);}
        StringBuilder out=new StringBuilder();for(java.util.Map.Entry<String,Integer> e:counts.entrySet()){if(out.length()>0)out.append("   ");out.append(e.getKey()).append(" ").append(e.getValue());}return out.toString();
    }
    private void translateMessage(String original,TextView output,Runnable finished) {''')

replace_once(community,
'''else if(kind.equals("text"))new AlertDialog.Builder(this).setMessage(bodyText).setPositiveButton("Close",null).show();else openMedia(r.optString("media_path"));''',
'''else {io.execute(()->{try{JSONObject seen=new JSONObject();seen.put("status_id",id);seen.put("viewer_id",uid());SupabaseClient.upsert("/rest/v1/status_views?on_conflict=status_id,viewer_id",token(),seen);}catch(Exception ignored){}});if(kind.equals("text"))new AlertDialog.Builder(this).setMessage(bodyText).setPositiveButton("Close",null).show();else openMedia(r.optString("media_path"));}''')
