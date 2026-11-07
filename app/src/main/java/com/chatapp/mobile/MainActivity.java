package com.chatapp.mobile;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root, content, toolbar;
    private String currentConversationId = "";
    private String currentConversationTitle = "";
    private boolean inChat = false;
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (inChat && !isFinishing()) loadMessages(false);
            if (inChat) handler.postDelayed(this, 5000);
        }
    };

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF263750));
        return b;
    }
    private void base(String title, String action, View.OnClickListener listener) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF101827);
        setContentView(root);
        toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(16),dp(10),dp(12),dp(10));
        toolbar.setBackgroundColor(0xFF17243A); root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView heading=text(title,20,Color.WHITE,true); toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if (action != null) { Button a=button(action); toolbar.addView(a); a.setOnClickListener(listener); }
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(14),dp(16),dp(12));
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String token=ChatSession.token(this);
        if (token.isEmpty()) showAuth(); else showHome();
    }
    private void showAuth() {
        inChat=false; base("CHAT","",null);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView brand=text("CHAT",34,Color.WHITE,true); brand.setLetterSpacing(0.15f);
        content.addView(brand); TextView sub=text("Stay close. Talk freely.",14,0xFFAAB6C8,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2); sp.bottomMargin=dp(28); content.addView(sub,sp);
        EditText email=field("Email address",InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass=field("Password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(email,match(dp(54))); content.addView(pass,match(dp(54)));
        Button signIn=button("Sign in"), signUp=button("Create account");
        LinearLayout.LayoutParams bp=match(dp(52)); bp.topMargin=dp(14); content.addView(signIn,bp);
        LinearLayout.LayoutParams bp2=match(dp(52)); bp2.topMargin=dp(8); content.addView(signUp,bp2);
        TextView status=text("",13,0xFFF2A7A7,false); content.addView(status);
        signIn.setOnClickListener(v->auth(email.getText().toString().trim(),pass.getText().toString(),false,status));
        signUp.setOnClickListener(v->auth(email.getText().toString().trim(),pass.getText().toString(),true,status));
    }
    private EditText field(String hint,int type) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setInputType(type);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xFFAAB6C8); e.setPadding(dp(14),0,dp(14),0);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF35C7A5)); return e;
    }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1,height); }
    private void auth(String email,String password,boolean signUp,TextView status) {
        if(email.isEmpty()||password.length()<6){status.setText("Enter a valid email and password (6+ characters).");return;}
        status.setText("Connecting…");
        io.execute(()->{
            try{
                JSONObject r=SupabaseAuth.authenticate(email,password,signUp);
                String access=r.optString("access_token","");
                String refresh=r.optString("refresh_token","");
                JSONObject user=r.optJSONObject("user");
                String uid=user==null?"":user.optString("id","");
                if(!access.isEmpty()){
                    ChatSession.save(this,access,refresh,uid,email);
                    runOnUiThread(this::showHome);
                }else{
                    runOnUiThread(()->status.setText("Account request sent. If email confirmation is enabled, confirm your email, then sign in."));
                }
            }catch(Exception e){runOnUiThread(()->status.setText(e.getMessage()));}
        });
    }
    private String token(){return ChatSession.token(this);}
    private String uid(){return ChatSession.userId(this);}
    private void showHome() {
        inChat=false; handler.removeCallbacks(refreshRunnable);
        base("Your chats","＋",v->showSearch());
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button find=button("Find people"), group=button("New group"), profile=button("Profile");
        actions.addView(find,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(group,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(profile,new LinearLayout.LayoutParams(0,dp(48),1));
        content.addView(actions); find.setOnClickListener(v->showSearch()); group.setOnClickListener(v->createGroupDialog());
        profile.setOnClickListener(v->showProfile());
        TextView hint=text("Private messages and groups",13,0xFFAAB6C8,false);
        LinearLayout.LayoutParams hp=match(dp(34)); hp.topMargin=dp(12); content.addView(hint,hp);
        ScrollView scroll=new ScrollView(this); content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        TextView loading=text("Loading conversations…",14,0xFFAAB6C8,false); list.addView(loading);
        io.execute(()->{
            try{
                String path="/rest/v1/conversation_members?select=conversation_id,conversations(id,title,is_group,updated_at)&user_id=eq."+SupabaseClient.enc(uid())+"&order=joined_at.desc";
                JSONArray rows=SupabaseClient.getArray(path,token());
                List<ChatItem> items=new ArrayList<>();
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.optJSONObject(i); JSONObject c=row==null?null:row.optJSONObject("conversations");
                    if(c==null && row!=null && row.optJSONArray("conversations")!=null && row.optJSONArray("conversations").length()>0)c=row.optJSONArray("conversations").optJSONObject(0);
                    if(c!=null)items.add(new ChatItem(c.optString("id"),c.optString("title"),c.optBoolean("is_group")));
                }
                runOnUiThread(()->{
                    list.removeAllViews();
                    if(items.isEmpty()){list.addView(text("No conversations yet. Find someone to start chatting.",15,0xFFAAB6C8,false));return;}
                    for(ChatItem item:items){
                        Button rowButton=button((item.group?"👥  ":"●  ")+item.title+"    ›");
                        rowButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
                        LinearLayout.LayoutParams p=match(dp(62)); p.bottomMargin=dp(7); list.addView(rowButton,p);
                        rowButton.setOnClickListener(v->openChat(item));
                    }
                });
            }catch(Exception e){runOnUiThread(()->{list.removeAllViews();list.addView(text("Could not load chats: "+e.getMessage(),13,0xFFF2A7A7,false));});}
        });
    }
    private void showSearch() {
        inChat=false; handler.removeCallbacks(refreshRunnable);
        base("Find people","Back",v->showHome());
        EditText query=field("Search by username or name",InputType.TYPE_CLASS_TEXT);
        content.addView(query,match(dp(54))); Button go=button("Search"); content.addView(go,match(dp(48)));
        ScrollView sc=new ScrollView(this); content.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); sc.addView(results);
        go.setOnClickListener(v->searchProfiles(query.getText().toString().trim(),results));
        query.setOnEditorActionListener((v,id,event)->{searchProfiles(query.getText().toString().trim(),results);return true;});
    }
    private void searchProfiles(String q,LinearLayout results) {
        if(q.length()<2){message("Type at least 2 letters.");return;}
        results.removeAllViews(); results.addView(text("Searching…",14,0xFFAAB6C8,false));
        io.execute(()->{
            try{
                String term="*"+q.replace("*","")+"*";
                String path="/rest/v1/profiles?select=id,username,display_name,bio,is_online& id=neq."+SupabaseClient.enc(uid())+
                    "&or=(username.ilike."+SupabaseClient.enc(term)+",display_name.ilike."+SupabaseClient.enc(term)+")&limit=30";
                path=path.replace("&%20id","&id").replace("& id","&id");
                JSONArray users=SupabaseClient.getArray(path,token());
                runOnUiThread(()->{
                    results.removeAllViews();
                    if(users.length()==0){results.addView(text("No users found.",14,0xFFAAB6C8,false));return;}
                    for(int i=0;i<users.length();i++){
                        JSONObject u=users.optJSONObject(i); if(u==null)continue;
                        String id=u.optString("id"), name=u.optString("display_name",u.optString("username","User"));
                        String username=u.optString("username","");
                        Button b=button(name+(username.isEmpty()?"":"  @"+username)+(u.optBoolean("is_online")?"  • online":""));
                        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); LinearLayout.LayoutParams p=match(dp(58));p.bottomMargin=dp(6);results.addView(b,p);
                        b.setOnClickListener(v->startDirect(id,name));
                    }
                });
            }catch(Exception e){runOnUiThread(()->{results.removeAllViews();results.addView(text(e.getMessage(),13,0xFFF2A7A7,false));});}
        });
    }
    private void startDirect(String otherId,String name) {
        message("Opening conversation…");
        io.execute(()->{
            try{
                JSONObject conv=new JSONObject(); conv.put("is_group",false);conv.put("created_by",uid());conv.put("title",name);
                JSONArray created=SupabaseClient.post("/rest/v1/conversations",token(),conv);
                String cid=created.getJSONObject(0).getString("id");
                JSONObject me=new JSONObject();me.put("conversation_id",cid);me.put("user_id",uid());me.put("role","member");
                SupabaseClient.post("/rest/v1/conversation_members",token(),me);
                JSONObject other=new JSONObject();other.put("conversation_id",cid);other.put("user_id",otherId);other.put("role","member");
                SupabaseClient.post("/rest/v1/conversation_members",token(),other);
                runOnUiThread(()->openChat(new ChatItem(cid,name,false)));
            }catch(Exception e){runOnUiThread(()->message("Could not start chat: "+e.getMessage()));}
        });
    }
    private void openChat(ChatItem item) {
        currentConversationId=item.id; currentConversationTitle=item.title; inChat=true;
        base(item.title,"Back",v->{inChat=false;handler.removeCallbacks(refreshRunnable);showHome();});
        ScrollView sc=new ScrollView(this); sc.setFillViewport(false); content.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); sc.addView(messages);
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(0,dp(8),0,0);
        EditText input=field("Write a message…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Button attach=button("＋"); Button send=button("Send");
        composer.addView(attach,new LinearLayout.LayoutParams(dp(48),dp(52))); composer.addView(input,new LinearLayout.LayoutParams(0,dp(52),1)); composer.addView(send,new LinearLayout.LayoutParams(dp(82),dp(52)));
        attach.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,410);});
        content.addView(composer);
        send.setOnClickListener(v->{String body=input.getText().toString().trim();if(!body.isEmpty()){input.setText("");sendMessage(body);}});
        loadMessages(true); handler.removeCallbacks(refreshRunnable); handler.postDelayed(refreshRunnable,5000);
    }
    private void loadMessages(boolean showLoading) {
        if(currentConversationId.isEmpty())return;
        // Fetch in the background; refresh the current chat without blocking typing.
        io.execute(()->{
            try{
                String path="/rest/v1/messages?select=id,sender_id,body,created_at,attachment_path,attachment_type,deleted_at&conversation_id=eq."+SupabaseClient.enc(currentConversationId)+"&deleted_at=is.null&order=created_at.asc&limit=200";
                JSONArray rows=SupabaseClient.getArray(path,token());
                runOnUiThread(()->renderMessages(rows));
            }catch(Exception e){if(showLoading)runOnUiThread(()->message("Messages: "+e.getMessage()));}
        });
    }
    private void renderMessages(JSONArray rows) {
        if(!inChat||content==null)return;
        ScrollView sc=null; LinearLayout msgs=null;
        for(int i=0;i<content.getChildCount();i++){
            View v=content.getChildAt(i); if(v instanceof ScrollView){sc=(ScrollView)v; if(sc.getChildCount()>0&&sc.getChildAt(0) instanceof LinearLayout)msgs=(LinearLayout)sc.getChildAt(0);break;}
        }
        if(msgs==null)return; msgs.removeAllViews();
        if(rows.length()==0)msgs.addView(text("Say hello 👋",14,0xFFAAB6C8,false));
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.optJSONObject(i);if(m==null)continue;
            boolean mine=uid().equals(m.optString("sender_id"));
            String attachment=m.optString("attachment_path","");
            String body=m.optString("body","");
            if(!attachment.isEmpty())body="📎 Open attachment ("+m.optString("attachment_type","file")+")";
            TextView bubble=text(body,15,mine?Color.WHITE:0xFFE6EDF7,false);
            bubble.setPadding(dp(14),dp(10),dp(14),dp(10));
            bubble.setBackgroundTintList(null);
            bubble.setBackgroundColor(mine?0xFF176B63:0xFF263750);
            if(!attachment.isEmpty())bubble.setOnClickListener(v->openAttachment(attachment));
            LinearLayout line=new LinearLayout(this);line.setGravity(mine?Gravity.END:Gravity.START);
            line.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
            LinearLayout.LayoutParams p=match(-2);p.topMargin=dp(5);msgs.addView(line,p);
        }
        final ScrollView scrollTarget=sc;
        if(scrollTarget!=null)scrollTarget.post(()->scrollTarget.fullScroll(View.FOCUS_DOWN));
    }
    private void openAttachment(String path) {
        io.execute(()->{try{String url=SupabaseStorage.signedUrl(path,token());Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(url));runOnUiThread(()->{try{startActivity(view);}catch(Exception e){message("No app can open this file.");}});}catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==410&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri selected=data.getData();String cid=currentConversationId;message("Uploading attachment…");
            io.execute(()->{try{String uploaded=SupabaseStorage.upload(this,selected,cid,token());String[] parts=uploaded.split("\\|",2);JSONObject msg=new JSONObject();msg.put("conversation_id",cid);msg.put("sender_id",uid());msg.put("attachment_path",parts[0]);msg.put("attachment_type",parts.length>1?parts[1]:"application/octet-stream");SupabaseClient.post("/rest/v1/messages",token(),msg);runOnUiThread(()->{message("Attachment sent.");loadMessages(false);});}catch(Exception e){runOnUiThread(()->message("Upload failed: "+e.getMessage()));}});
        }
    }
    private void sendMessage(String body) {
        final String cid=currentConversationId;
        io.execute(()->{
            try{
                JSONObject m=new JSONObject();m.put("conversation_id",cid);m.put("sender_id",uid());m.put("body",body);
                SupabaseClient.post("/rest/v1/messages",token(),m);
                runOnUiThread(()->loadMessages(false));
            }catch(Exception e){runOnUiThread(()->message("Message not sent: "+e.getMessage()));}
        });
    }
    private void createGroupDialog() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),0,dp(16),0);
        EditText title=field("Group name",InputType.TYPE_CLASS_TEXT);
        EditText usernames=field("Participant usernames, comma separated",InputType.TYPE_CLASS_TEXT);
        box.addView(title,match(dp(54)));box.addView(usernames,match(dp(54)));
        new AlertDialog.Builder(this).setTitle("Create group").setView(box)
            .setPositiveButton("Create",(d,w)->createGroup(title.getText().toString().trim(),usernames.getText().toString()))
            .setNegativeButton("Cancel",null).show();
    }
    private void createGroup(String title,String names) {
        if(title.isEmpty()||names.trim().isEmpty()){message("Enter a group name and at least one username.");return;}
        io.execute(()->{
            try{
                JSONObject c=new JSONObject();c.put("title",title);c.put("is_group",true);c.put("created_by",uid());
                String cid=SupabaseClient.post("/rest/v1/conversations",token(),c).getJSONObject(0).getString("id");
                JSONObject self=new JSONObject();self.put("conversation_id",cid);self.put("user_id",uid());self.put("role","owner");
                SupabaseClient.post("/rest/v1/conversation_members",token(),self);
                String[] handles=names.split(",");
                for(String h:handles){
                    String handle=h.trim().replace("@","");
                    if(handle.isEmpty())continue;
                    JSONArray found=SupabaseClient.getArray("/rest/v1/profiles?select=id&username=eq."+SupabaseClient.enc(handle)+"&limit=1",token());
                    if(found.length()>0){
                        String memberId=found.getJSONObject(0).getString("id");
                        if(!memberId.equals(uid())){
                            JSONObject m=new JSONObject();m.put("conversation_id",cid);m.put("user_id",memberId);m.put("role","member");
                            SupabaseClient.post("/rest/v1/conversation_members",token(),m);
                        }
                    }
                }
                runOnUiThread(()->openChat(new ChatItem(cid,title,true)));
            }catch(Exception e){runOnUiThread(()->message("Group creation failed: "+e.getMessage()));}
        });
    }
    private void showProfile() {
        base("Profile","Back",v->showHome());
        content.addView(text("Signed in as",14,0xFFAAB6C8,false));
        content.addView(text(ChatSession.email(this),18,Color.WHITE,true));
        Button logout=button("Sign out");LinearLayout.LayoutParams p=match(dp(52));p.topMargin=dp(22);content.addView(logout,p);
        logout.setOnClickListener(v->{ChatSession.clear(this);showAuth();});
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refreshRunnable);io.shutdown();super.onDestroy();}
}
